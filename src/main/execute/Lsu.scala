package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin._
import borb.common.MicroCode._
import borb.common.Common._
import borb.dispatch.RegFileWrite
import borb.fetch.Fetch

// Data Bus Command for Store operations
case class DataBusCmd(addressWidth: Int, dataWidth: Int, idWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
  val data = Bits(dataWidth bits)
  val mask = Bits(dataWidth / 8 bits)  // Byte enables
  val id = UInt(idWidth bits)
  val write = Bool()  // True = Store, False = Load
}

// Data Bus Response for Load operations
case class DataBusRsp(dataWidth: Int, idWidth: Int) extends Bundle {
  val data = Bits(dataWidth bits)
  val id = UInt(idWidth bits)
}

// Data Bus Bundle
case class DataBus(addressWidth: Int, dataWidth: Int, idWidth: Int) extends Bundle with IMasterSlave {
  val cmd = Stream(DataBusCmd(addressWidth, dataWidth, idWidth))
  val rsp = Flow(DataBusRsp(dataWidth, idWidth))

  override def asMaster() = {
    master(cmd)
    slave(rsp)
  }
}

// LSU Payloads for RVFI integration
object Lsu extends AreaObject {
  val MEM_ADDR = Payload(UInt(64 bits)).setName("LSU_MEM_ADDR")
  val MEM_WDATA = Payload(Bits(64 bits)).setName("LSU_MEM_WDATA")
  val MEM_WMASK = Payload(Bits(8 bits)).setName("LSU_MEM_WMASK")
  val MEM_RMASK = Payload(Bits(8 bits)).setName("LSU_MEM_RMASK")
  val MEM_RDATA = Payload(Bits(64 bits)).setName("LSU_MEM_RDATA")
}

case class Lsu(stage: CtrlLink, wbStage: CtrlLink, currentEpoch: UInt, killOutstanding: Bool = False) extends Area {
  import Lsu._
  import borb.dispatch.Dispatch._

  val io = new Bundle {
    val dBus = DataBus(addressWidth = 64, dataWidth = 64, idWidth = 16)
    val pmpFault = Bool()
    val bigEndian = Bool()
    val storeCommit = Bool()
    val storeAddr = UInt(64 bits)
    val storeData = Bits(64 bits)
    val storeMask = Bits(8 bits)
    val translatedAddr = UInt(64 bits)
    val useTranslatedAddr = Bool()
  }

  val logic = new stage.Area {
    val epochMatches = up(SPEC_EPOCH) === currentEpoch
    val amoSwapW = up(MicroCode) === uopAMOSWAPW
    val amoSwapD = up(MicroCode) === uopAMOSWAPD
    val amoAddW = up(MicroCode) === uopAMOADDW
    val amoAddD = up(MicroCode) === uopAMOADDD
    val amoXorW = up(MicroCode) === uopAMOXORW
    val amoXorD = up(MicroCode) === uopAMOXORD
    val amoAndW = up(MicroCode) === uopAMOANDW
    val amoAndD = up(MicroCode) === uopAMOANDD
    val amoOrW = up(MicroCode) === uopAMOORW
    val amoOrD = up(MicroCode) === uopAMOORD
    val amoMinW = up(MicroCode) === uopAMOMINW
    val amoMinD = up(MicroCode) === uopAMOMIND
    val amoMaxW = up(MicroCode) === uopAMOMAXW
    val amoMaxD = up(MicroCode) === uopAMOMAXD
    val amoMinuW = up(MicroCode) === uopAMOMINUW
    val amoMinuD = up(MicroCode) === uopAMOMINUD
    val amoMaxuW = up(MicroCode) === uopAMOMAXUW
    val amoMaxuD = up(MicroCode) === uopAMOMAXUD

    val amoIsWord = amoSwapW || amoAddW || amoXorW || amoAndW || amoOrW || amoMinW || amoMaxW || amoMinuW || amoMaxuW
    val isAmo = amoIsWord || amoSwapD || amoAddD || amoXorD || amoAndD || amoOrD || amoMinD || amoMaxD || amoMinuD || amoMaxuD

    // Address Generation: RS1 + sign-extended immediate for normal loads/stores,
    // RS1 only for AMOs.
    val aguEffectiveAddr = Mux(isAmo, up(RS1).asUInt, (up(RS1).asSInt + up(IMMED).asSInt).asUInt)

    // Extract funct3 from MicroCode to determine access size
    val isStoreBase = up(MicroCode).mux(
      uopSB -> True,
      uopSH -> True,
      uopSW -> True,
      uopFSW -> True,
      uopSD -> True,
      default -> False
    )
    val isStore = (isStoreBase || isAmo).setName("LSU_isStore")

    // Load Logic
    val isLoadBase = up(MicroCode).mux(
      uopLB -> True, uopLH -> True, uopLW -> True, uopLD -> True,
      uopLBU -> True, uopLHU -> True, uopLWU -> True, uopFLW -> True,
      default -> False
    )
    val isLoad = (isLoadBase || isAmo).setName("LSU_isLoad")
    val aguPayloadValid = up.isValid && up(VALID) && up(LANE_SEL) && up(SENDTOAGU)
    val currentSeq = up(Fetch.FETCH_SEQ)
    val duplicateInWb = wbStage.up.isValid &&
      wbStage(VALID) &&
      wbStage(LANE_SEL) &&
      (wbStage(Fetch.FETCH_SEQ) === currentSeq)

    val waitingResponse = RegInit(False)
    val nextId = Reg(UInt(16 bits)) init 1
    val waitId = Reg(UInt(16 bits))
    val amoWaitingResponse = RegInit(False)
    val amoWaitId = Reg(UInt(16 bits)) init 0
    val amoStorePending = RegInit(False)
    val amoStoreData = Reg(Bits(64 bits)) init 0
    val amoWbData = Reg(Bits(64 bits)) init 0

    // Misalignment Check
    val waitAddr = Reg(UInt(64 bits)) init(0)
    val activeAddr = UInt(64 bits)
    activeAddr := aguEffectiveAddr
    when(waitingResponse || amoWaitingResponse || amoStorePending) {
      activeAddr := waitAddr
    }
    val effectiveAddr = activeAddr

    val misaligned = Bool()
    misaligned := up(MicroCode).mux(
      uopLH -> (activeAddr(0) =/= False),
      uopLHU -> (activeAddr(0) =/= False),
      uopSH -> (activeAddr(0) =/= False),
      uopFLW -> (activeAddr(1 downto 0) =/= 0),
      uopFSW -> (activeAddr(1 downto 0) =/= 0),
      uopLW -> (activeAddr(1 downto 0) =/= 0),
      uopLWU -> (activeAddr(1 downto 0) =/= 0),
      uopSW -> (activeAddr(1 downto 0) =/= 0),
      uopLD -> (activeAddr(2 downto 0) =/= 0),
      uopSD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOSWAPW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOADDW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOXORW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOANDW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOORW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOMINW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOMAXW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOMINUW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOMAXUW -> (activeAddr(1 downto 0) =/= 0),
      uopAMOSWAPD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOADDD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOXORD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOANDD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOORD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOMIND -> (activeAddr(2 downto 0) =/= 0),
      uopAMOMAXD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOMINUD -> (activeAddr(2 downto 0) =/= 0),
      uopAMOMAXUD -> (activeAddr(2 downto 0) =/= 0),
      default -> False
    )

    // Raise trap on any misaligned memory access.
    val localTrap = (misaligned || io.pmpFault) && (isStore || isLoad)

    // Byte offset within doubleword (for alignment)
    val byteOffset = activeAddr(2 downto 0)

    // Generate access mask (Normalized / Unshifted) - for both Load and Store
    val accessSizeMask = Bits(8 bits)
    accessSizeMask := up(MicroCode).mux(
      uopSB -> B"00000001",
      uopSH -> B"00000011",
      uopSW -> B"00001111",
      uopSD -> B"11111111",
      uopLB -> B"00000001",
      uopLBU -> B"00000001",
      uopLH -> B"00000011",
      uopLHU -> B"00000011",
      uopFLW -> B"00001111",
      uopLW -> B"00001111",
      uopLWU -> B"00001111",
      uopLD -> B"11111111",
      uopFSW -> B"00001111",
      uopAMOSWAPW -> B"00001111",
      uopAMOADDW -> B"00001111",
      uopAMOXORW -> B"00001111",
      uopAMOANDW -> B"00001111",
      uopAMOORW -> B"00001111",
      uopAMOMINW -> B"00001111",
      uopAMOMAXW -> B"00001111",
      uopAMOMINUW -> B"00001111",
      uopAMOMAXUW -> B"00001111",
      uopAMOSWAPD -> B"11111111",
      uopAMOADDD -> B"11111111",
      uopAMOXORD -> B"11111111",
      uopAMOANDD -> B"11111111",
      uopAMOORD -> B"11111111",
      uopAMOMIND -> B"11111111",
      uopAMOMAXD -> B"11111111",
      uopAMOMINUD -> B"11111111",
      uopAMOMAXUD -> B"11111111",
      default -> B(0, 8 bits)
    )

    // Shifted mask for dBus
    val writeMask = accessSizeMask |<< byteOffset

    def reverseBytesByMask(data: Bits, mask: Bits): Bits = {
      val out = Bits(64 bits)
      out := data
      switch(mask) {
        is(B"00000011") {
          out(15 downto 0) := data(7 downto 0) ## data(15 downto 8)
        }
        is(B"00001111") {
          out(31 downto 0) := data(7 downto 0) ## data(15 downto 8) ## data(23 downto 16) ## data(31 downto 24)
        }
        is(B"11111111") {
          out := data(7 downto 0) ## data(15 downto 8) ## data(23 downto 16) ## data(31 downto 24) ##
            data(39 downto 32) ## data(47 downto 40) ## data(55 downto 48) ## data(63 downto 56)
        }
      }
      out
    }

    // Align store data to the correct byte lanes
    val rawStoreData = Bits(64 bits)
    rawStoreData := up(MicroCode).mux(
      uopSB -> (up(RS2)(7 downto 0)).resize(64),
      uopSH -> (up(RS2)(15 downto 0)).resize(64),
      uopSW -> (up(RS2)(31 downto 0)).resize(64),
      uopSD -> up(RS2),
      default -> B(0, 64 bits)
    )

    val endianStoreData = Bits(64 bits)
    endianStoreData := rawStoreData
    when(io.bigEndian) {
      endianStoreData := reverseBytesByMask(rawStoreData, accessSizeMask)
    }

    val storeData = endianStoreData |<< (byteOffset << 3)

    // Drive Data Bus Command
    // Suppress memory side effects for traps (misaligned or illegal instruction).
    val illegalInsn = up(Decoder.DECODED_INSTRUCTION)(1 downto 0) =/= B"11"
    val suppress = misaligned || illegalInsn || io.pmpFault || duplicateInWb
    // Firing logic
    val fireLoad = isLoadBase && aguPayloadValid && epochMatches && !waitingResponse
    val amoIssueLoad = isAmo && aguPayloadValid && epochMatches && !suppress && !amoWaitingResponse && !amoStorePending
    val amoIssueStore = isAmo && aguPayloadValid && epochMatches && !suppress && amoStorePending
    val storeIssueFire = ((isStoreBase && aguPayloadValid && epochMatches && !suppress) || amoIssueStore) && io.dBus.cmd.ready
    val busAddr = UInt(64 bits)
    busAddr := activeAddr
    when(io.useTranslatedAddr) {
      busAddr := io.translatedAddr
    }

    io.dBus.cmd.valid := ((isStoreBase || fireLoad) && aguPayloadValid && epochMatches && !suppress) || amoIssueLoad || amoIssueStore
    io.dBus.cmd.payload.address := busAddr
    io.dBus.cmd.payload.data := Mux(amoIssueStore, amoStoreData |<< (byteOffset << 3), storeData)
    io.dBus.cmd.payload.mask := writeMask
    io.dBus.cmd.payload.id := Mux((isStoreBase || amoIssueStore), U(0, 16 bits), nextId)
    io.dBus.cmd.payload.write := isStoreBase || amoIssueStore
    io.storeCommit := storeIssueFire
    io.storeAddr := busAddr
    io.storeData := io.dBus.cmd.payload.data
    io.storeMask := io.dBus.cmd.payload.mask

    // Stores must wait for command acceptance. Otherwise writes can be dropped
    // when the bus is temporarily not ready.
    val storeBlocked = isStoreBase && aguPayloadValid && epochMatches && !suppress && !io.dBus.cmd.ready
    haltWhen(storeBlocked)

    // Stall Logic
    // IMPORTANT: When the response arrives, the pipeline advances at end of that cycle.
    // The down() signals are captured based on COMBINATORIAL values.
    // So we must use io.dBus.rsp.payload.data directly on the cycle response arrives.
    val latchedRspData = Reg(Bits(64 bits))
    val loadResponseArriving = waitingResponse && !killOutstanding && io.dBus.rsp.valid && (io.dBus.rsp.id === waitId)

    // Outstanding LSU response state must be retired by matching the bus response,
    // not by whatever instruction currently occupies execute.
    when(loadResponseArriving) {
      waitingResponse := False
      latchedRspData := io.dBus.rsp.payload.data
    }
    
    when(isLoadBase && aguPayloadValid && epochMatches && !suppress) {
        when(!waitingResponse) {
             when(io.dBus.cmd.ready && !suppress) {
                 waitingResponse := True
                 waitAddr := aguEffectiveAddr
                 waitId := nextId
                 nextId := nextId + 1
                 haltIt()
             } otherwise {
                 haltIt()
             }
        } otherwise {
             // Waiting for response
             when(loadResponseArriving) {
                 // Pipeline advances at end of this cycle - use live data for down()
             } otherwise {
                 haltIt()  // Only halt if response hasn't arrived yet
             }
        }
    }
    when(isLoadBase && aguPayloadValid && (!epochMatches || suppress)) {
      // Faulting/suppressed loads must not enter the response wait state.
      waitingResponse := False
    }

    // AMO implementation: read old value, compute/store new value, write old
    // value to rd after store command is accepted.
    val amoResponseArriving = amoWaitingResponse && !killOutstanding && io.dBus.rsp.valid && (io.dBus.rsp.id === amoWaitId)
    when(amoResponseArriving) {
      amoWaitingResponse := False
    }
    when(isAmo && aguPayloadValid && epochMatches && !suppress) {
      when(!amoWaitingResponse && !amoStorePending) {
        when(io.dBus.cmd.ready) {
          waitAddr := aguEffectiveAddr
          amoWaitingResponse := True
          amoWaitId := nextId
          nextId := nextId + 1
          haltIt()
        } otherwise {
          haltIt()
        }
      } elsewhen(amoWaitingResponse) {
        when(amoResponseArriving) {
          val rspData = io.dBus.rsp.payload.data
          val shifted = rspData >> (byteOffset << 3)
          val shiftedEndian = Bits(64 bits)
          shiftedEndian := shifted
          when(io.bigEndian) {
            shiftedEndian := reverseBytesByMask(shifted, accessSizeMask)
          }
          val oldWord = shiftedEndian(31 downto 0)
          val rs2Word = up(RS2)(31 downto 0)
          val oldWordS = oldWord.asSInt
          val rs2WordS = rs2Word.asSInt
          val oldD = shiftedEndian
          val rs2D = up(RS2)
          val oldDS = oldD.asSInt
          val rs2DS = rs2D.asSInt

          val newWord = Bits(32 bits)
          newWord := oldWord
          when(amoSwapW) { newWord := rs2Word }
          when(amoAddW) { newWord := (oldWord.asUInt + rs2Word.asUInt).asBits }
          when(amoXorW) { newWord := oldWord ^ rs2Word }
          when(amoAndW) { newWord := oldWord & rs2Word }
          when(amoOrW) { newWord := oldWord | rs2Word }
          when(amoMinW) { newWord := Mux(oldWordS < rs2WordS, oldWord, rs2Word) }
          when(amoMaxW) { newWord := Mux(oldWordS > rs2WordS, oldWord, rs2Word) }
          when(amoMinuW) { newWord := Mux(oldWord.asUInt < rs2Word.asUInt, oldWord, rs2Word) }
          when(amoMaxuW) { newWord := Mux(oldWord.asUInt > rs2Word.asUInt, oldWord, rs2Word) }

          val newD = Bits(64 bits)
          newD := oldD
          when(amoSwapD) { newD := rs2D }
          when(amoAddD) { newD := (oldD.asUInt + rs2D.asUInt).asBits }
          when(amoXorD) { newD := oldD ^ rs2D }
          when(amoAndD) { newD := oldD & rs2D }
          when(amoOrD) { newD := oldD | rs2D }
          when(amoMinD) { newD := Mux(oldDS < rs2DS, oldD, rs2D) }
          when(amoMaxD) { newD := Mux(oldDS > rs2DS, oldD, rs2D) }
          when(amoMinuD) { newD := Mux(oldD.asUInt < rs2D.asUInt, oldD, rs2D) }
          when(amoMaxuD) { newD := Mux(oldD.asUInt > rs2D.asUInt, oldD, rs2D) }

          amoWbData := Mux(amoIsWord, oldWord.asSInt.resize(64).asBits, shifted)
          val rawAmoStoreData = Bits(64 bits)
          rawAmoStoreData := Mux(amoIsWord, newWord.resize(64), newD)
          val storeEndianData = Bits(64 bits)
          storeEndianData := rawAmoStoreData
          when(io.bigEndian) {
            storeEndianData := reverseBytesByMask(rawAmoStoreData, accessSizeMask)
          }
          amoStoreData := storeEndianData
          amoStorePending := True
          haltIt()
        } otherwise {
          haltIt()
        }
      } elsewhen(amoStorePending) {
        when(io.dBus.cmd.ready) {
          amoStorePending := False
        } otherwise {
          haltIt()
        }
      }
    }
    when(isAmo && aguPayloadValid && (!epochMatches || suppress)) {
      amoWaitingResponse := False
      amoStorePending := False
    }
    when(killOutstanding) {
      waitingResponse := False
      amoWaitingResponse := False
      amoStorePending := False
    }

    // Load Data Processing - use LIVE data when response is arriving, latched data otherwise
    // This is critical: on the cycle response arrives, we use live data since that's what gets captured
    val responseArriving = isLoadBase && aguPayloadValid && loadResponseArriving
    val rspData = Mux(responseArriving, io.dBus.rsp.payload.data, latchedRspData)
    val shiftedLoadData = rspData >> (byteOffset << 3)
    val shiftedEndianLoadData = Bits(64 bits)
    shiftedEndianLoadData := shiftedLoadData
    when(io.bigEndian) {
      shiftedEndianLoadData := reverseBytesByMask(shiftedLoadData, accessSizeMask)
    }
    val loadResult = Bits(64 bits)
    loadResult := up(MicroCode).mux(
       uopLB -> shiftedEndianLoadData(7 downto 0).asSInt.resize(64).asBits,
       uopLBU -> shiftedEndianLoadData(7 downto 0).resize(64),
       uopLH -> shiftedEndianLoadData(15 downto 0).asSInt.resize(64).asBits,
       uopLHU -> shiftedEndianLoadData(15 downto 0).resize(64),
       uopFLW -> shiftedEndianLoadData(31 downto 0).resize(64),
       uopLW -> shiftedEndianLoadData(31 downto 0).asSInt.resize(64).asBits,
       uopLWU -> shiftedEndianLoadData(31 downto 0).resize(64),
       uopLD -> shiftedEndianLoadData,
       default -> B(0, 64 bits)
    )

    // Writeback Result
    // Decoder should set REG_WRITE for loads
    // Enforce x0 invariant: writes to x0 must have data=0 (RVFI expectation)
    val rdAddr = up(borb.frontend.Decoder.RD_ADDR).asUInt
    val isX0 = rdAddr === 0
    val maskedLoadResult = isX0 ? B(0, 64 bits) | loadResult
    
    when(isLoadBase && aguPayloadValid && !illegalInsn && !suppress) {
        down(WriteBack.RESULT).data.allowOverride := maskedLoadResult
        down(WriteBack.RESULT).valid.allowOverride := True 
        down(WriteBack.RESULT).address.allowOverride := rdAddr
    }
    when(isAmo && aguPayloadValid && !illegalInsn && !suppress && amoStorePending && io.dBus.cmd.ready) {
      down(WriteBack.RESULT).data.allowOverride := isX0 ? B(0, 64 bits) | amoWbData
      down(WriteBack.RESULT).valid.allowOverride := True
      down(WriteBack.RESULT).address.allowOverride := rdAddr
    }

    // Propagate payloads for RVFI (Store & Load)
    // riscv-formal expects RAW/SHIFTED data and mask matching the address
    val isSendToAgu = up(SENDTOAGU)
    // Suppress RVFI side-effects if misaligned
    down(MEM_ADDR) := Mux(aguPayloadValid && (isStore || isLoad), activeAddr, U(0, 64 bits))
    
    // MEM_WMASK and MEM_WDATA (for Stores)
    // Use shifted data/mask to match architectural expectation if address is unaligned?
    // Usually RVFI expects aligned accesses if possible, but for unaligned, it expects bus values.
    down(MEM_WMASK) := Mux(aguPayloadValid && isStore && !suppress, accessSizeMask, B(0, 8 bits))
    down(MEM_WDATA) := Mux(aguPayloadValid && isStore && !suppress, rawStoreData, B(0, 64 bits))
    
    // For Loads:
    // MEM_RMASK should be shifted (to match address lanes).
    // MEM_RDATA should be shifted/raw from bus (rspData).
    val readMaskShifted = accessSizeMask |<< byteOffset
    
    down(MEM_RMASK) := Mux(aguPayloadValid && isLoad && !suppress, accessSizeMask, B(0, 8 bits))
    // MEM_RDATA: riscv-formal expects the raw extracted data (shifted to LSB, BEFORE sign-extension)
    // The formal model applies its own sign-extension based on instruction type
    down(MEM_RDATA) := Mux(aguPayloadValid && isLoad && !suppress, shiftedEndianLoadData, B(0, 64 bits))

    // Stores do not write to register file
    // RESULT payload should remain 0/invalid (handled by IntAlu defaults)
  }
}
