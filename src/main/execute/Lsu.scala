package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin._
import borb.common.MicroCode._
import borb.common.Common._
import borb.common.LaneKey
import borb.dispatch.RegFileWrite
import borb.fetch.Fetch
import borb.frontend.ExecutionUnitEnum

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
  val SupportedUops = Seq(
    uopLB,
    uopLBU,
    uopLH,
    uopLHU,
    uopLW,
    uopLWU,
    uopLD,
    uopSB,
    uopSH,
    uopSW,
    uopSD,
    uopFLH,
    uopFLW,
    uopFLD,
    uopFSH,
    uopFSW,
    uopFSD,
    uopAMOSWAPW,
    uopAMOSWAPD,
    uopAMOADDW,
    uopAMOADDD,
    uopAMOXORW,
    uopAMOXORD,
    uopAMOANDW,
    uopAMOANDD,
    uopAMOORW,
    uopAMOORD,
    uopAMOMINW,
    uopAMOMIND,
    uopAMOMAXW,
    uopAMOMAXD,
    uopAMOMINUW,
    uopAMOMINUD,
    uopAMOMAXUW,
    uopAMOMAXUD,
    uopCBOZERO
  )
}

case class Lsu(stage: CtrlLink, wbStage: CtrlLink, currentEpoch: UInt, killOutstanding: Bool = False, killCboZero: Bool = False) extends FunctionalUnit(ExecutionUnitEnum.AGU) {
  import Lsu._
  import borb.dispatch.Dispatch._

  Lsu.SupportedUops.foreach(add)

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
    val epochMatches = up(SPEC_EPOCH, LaneKey.Lane0) === currentEpoch
    val amoSwapW = up(MicroCode, LaneKey.Lane0) === uopAMOSWAPW
    val amoSwapD = up(MicroCode, LaneKey.Lane0) === uopAMOSWAPD
    val amoAddW = up(MicroCode, LaneKey.Lane0) === uopAMOADDW
    val amoAddD = up(MicroCode, LaneKey.Lane0) === uopAMOADDD
    val amoXorW = up(MicroCode, LaneKey.Lane0) === uopAMOXORW
    val amoXorD = up(MicroCode, LaneKey.Lane0) === uopAMOXORD
    val amoAndW = up(MicroCode, LaneKey.Lane0) === uopAMOANDW
    val amoAndD = up(MicroCode, LaneKey.Lane0) === uopAMOANDD
    val amoOrW = up(MicroCode, LaneKey.Lane0) === uopAMOORW
    val amoOrD = up(MicroCode, LaneKey.Lane0) === uopAMOORD
    val amoMinW = up(MicroCode, LaneKey.Lane0) === uopAMOMINW
    val amoMinD = up(MicroCode, LaneKey.Lane0) === uopAMOMIND
    val amoMaxW = up(MicroCode, LaneKey.Lane0) === uopAMOMAXW
    val amoMaxD = up(MicroCode, LaneKey.Lane0) === uopAMOMAXD
    val amoMinuW = up(MicroCode, LaneKey.Lane0) === uopAMOMINUW
    val amoMinuD = up(MicroCode, LaneKey.Lane0) === uopAMOMINUD
    val amoMaxuW = up(MicroCode, LaneKey.Lane0) === uopAMOMAXUW
    val amoMaxuD = up(MicroCode, LaneKey.Lane0) === uopAMOMAXUD
    val isCboZero = up(MicroCode, LaneKey.Lane0) === uopCBOZERO

    val amoIsWord = amoSwapW || amoAddW || amoXorW || amoAndW || amoOrW || amoMinW || amoMaxW || amoMinuW || amoMaxuW
    val isAmo = amoIsWord || amoSwapD || amoAddD || amoXorD || amoAndD || amoOrD || amoMinD || amoMaxD || amoMinuD || amoMaxuD

    // Address Generation: RS1 + sign-extended immediate for normal loads/stores,
    // RS1 only for AMOs.
    val rawAguAddr = Mux(isAmo, up(RS1, LaneKey.Lane0).asUInt, (up(RS1, LaneKey.Lane0).asSInt + up(IMMED, LaneKey.Lane0).asSInt).asUInt)
    val cboZeroBlockAddr = up(RS1, LaneKey.Lane0).asUInt & U(BigInt("FFFFFFFFFFFFFFC0", 16), 64 bits)
    val aguEffectiveAddr = Mux(isCboZero, cboZeroBlockAddr, rawAguAddr)

    // Extract funct3 from MicroCode to determine access size
    val isStoreBase = up(MicroCode, LaneKey.Lane0).mux(
      uopSB -> True,
      uopSH -> True,
      uopSW -> True,
      uopFSH -> True,
      uopFSW -> True,
      uopFSD -> True,
      uopSD -> True,
      default -> False
    )
    val isStore = (isStoreBase || isAmo || isCboZero).setName("LSU_isStore")

    // Load Logic
    val isLoadBase = up(MicroCode, LaneKey.Lane0).mux(
      uopLB -> True, uopLH -> True, uopLW -> True, uopLD -> True,
      uopLBU -> True, uopLHU -> True, uopLWU -> True, uopFLH -> True, uopFLW -> True, uopFLD -> True,
      default -> False
    )
    val isLoad = (isLoadBase || isAmo).setName("LSU_isLoad")
    val aguPayloadValid = up.isValid && up(VALID, LaneKey.Lane0) && up(LANE_SEL, LaneKey.Lane0) && up(SENDTOAGU, LaneKey.Lane0)
    val currentSeq = up(Fetch.FETCH_SEQ, LaneKey.Lane0)
    val duplicateInWb = wbStage.up.isValid &&
      wbStage(VALID, LaneKey.Lane0) &&
      wbStage(LANE_SEL, LaneKey.Lane0) &&
      (wbStage(Fetch.FETCH_SEQ) === currentSeq)

    val waitingResponse = RegInit(False)
    val nextId = Reg(UInt(16 bits)) init 1
    val waitId = Reg(UInt(16 bits))
    val amoWaitingResponse = RegInit(False)
    val amoWaitId = Reg(UInt(16 bits)) init 0
    val amoStorePending = RegInit(False)
    val amoStoreData = Reg(Bits(64 bits)) init 0
    val amoWbData = Reg(Bits(64 bits)) init 0
    val cboZeroActive = RegInit(False)
    val cboZeroBeat = Reg(UInt(3 bits)) init 0
    val cboZeroBase = Reg(UInt(64 bits)) init 0
    val cboZeroBusBase = Reg(UInt(64 bits)) init 0

    // Misalignment Check
    val waitAddr = Reg(UInt(64 bits)) init(0)
    val activeAddr = UInt(64 bits)
    activeAddr := aguEffectiveAddr
    when(waitingResponse || amoWaitingResponse || amoStorePending) {
      activeAddr := waitAddr
    }
    when(cboZeroActive) {
      activeAddr := cboZeroBase
    }
    val effectiveAddr = activeAddr

    val misaligned = Bool()
    misaligned := up(MicroCode, LaneKey.Lane0).mux(
      uopLH -> (activeAddr(0) =/= False),
      uopLHU -> (activeAddr(0) =/= False),
      uopSH -> (activeAddr(0) =/= False),
      uopFLH -> (activeAddr(0) =/= False),
      uopFLW -> (activeAddr(1 downto 0) =/= 0),
      uopFLD -> (activeAddr(2 downto 0) =/= 0),
      uopFSH -> (activeAddr(0) =/= False),
      uopFSW -> (activeAddr(1 downto 0) =/= 0),
      uopFSD -> (activeAddr(2 downto 0) =/= 0),
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

    // Integer/FP misaligned accesses are handled by the byte-mask path.
    // AMOs remain architecturally trapped on misalignment.
    val misalignedTrap = misaligned && isAmo
    val localTrap = (misalignedTrap || io.pmpFault) && (isStore || isLoad)

    // Byte offset within doubleword (for alignment)
    val byteOffset = activeAddr(2 downto 0)

    // Generate access mask (Normalized / Unshifted) - for both Load and Store
    val accessSizeMask = Bits(8 bits)
    accessSizeMask := up(MicroCode, LaneKey.Lane0).mux(
      uopSB -> B"00000001",
      uopSH -> B"00000011",
      uopSW -> B"00001111",
      uopSD -> B"11111111",
      uopLB -> B"00000001",
      uopLBU -> B"00000001",
      uopLH -> B"00000011",
      uopLHU -> B"00000011",
      uopFLH -> B"00000011",
      uopFLW -> B"00001111",
      uopFLD -> B"11111111",
      uopLW -> B"00001111",
      uopLWU -> B"00001111",
      uopLD -> B"11111111",
      uopFSH -> B"00000011",
      uopFSW -> B"00001111",
      uopFSD -> B"11111111",
      uopCBOZERO -> B"11111111",
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

    // DCache places the byte window using the address offset.
    val writeMask = accessSizeMask

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

    // Keep store bytes packed at byte lane zero; DCache places them by address.
    val rawStoreData = Bits(64 bits)
    rawStoreData := up(MicroCode, LaneKey.Lane0).mux(
      uopSB -> (up(RS2, LaneKey.Lane0)(7 downto 0)).resize(64),
      uopSH -> (up(RS2, LaneKey.Lane0)(15 downto 0)).resize(64),
      uopSW -> (up(RS2, LaneKey.Lane0)(31 downto 0)).resize(64),
      uopSD -> up(RS2, LaneKey.Lane0),
      default -> B(0, 64 bits)
    )

    val endianStoreData = Bits(64 bits)
    endianStoreData := rawStoreData
    when(io.bigEndian) {
      endianStoreData := reverseBytesByMask(rawStoreData, accessSizeMask)
    }

    val storeData = endianStoreData
    val cboZeroBeatOffset = UInt(64 bits)
    cboZeroBeatOffset := U(0, 64 bits)
    when(cboZeroActive) {
      cboZeroBeatOffset := cboZeroBeat.resize(64) |<< 3
    }

    // Drive Data Bus Command
    // Suppress memory side effects for traps (misaligned or illegal instruction).
    val illegalInsn = up(Decoder.DECODED_INSTRUCTION, LaneKey.Lane0)(1 downto 0) =/= B"11"
    val suppress = misalignedTrap || illegalInsn || io.pmpFault || duplicateInWb
    val cboZeroStart = isCboZero && aguPayloadValid && epochMatches && !suppress && !cboZeroActive
    val cboZeroIssue = cboZeroStart || cboZeroActive
    // Firing logic
    val fireLoad = isLoadBase && aguPayloadValid && epochMatches && !waitingResponse
    val amoIssueLoad = isAmo && aguPayloadValid && epochMatches && !suppress && !amoWaitingResponse && !amoStorePending
    val amoIssueStore = isAmo && aguPayloadValid && epochMatches && !suppress && amoStorePending
    val cboZeroStoreFire = cboZeroIssue && io.dBus.cmd.ready
    val storeIssueFire = ((isStoreBase && aguPayloadValid && epochMatches && !suppress) || amoIssueStore || cboZeroStoreFire) && io.dBus.cmd.ready
    val busAddr = UInt(64 bits)
    busAddr := activeAddr
    when(io.useTranslatedAddr) {
      busAddr := io.translatedAddr
    }
    when(cboZeroIssue) {
      busAddr := Mux(cboZeroActive, cboZeroBusBase, io.useTranslatedAddr ? io.translatedAddr | activeAddr) + cboZeroBeatOffset
    }

    io.dBus.cmd.valid := ((isStoreBase || fireLoad) && aguPayloadValid && epochMatches && !suppress) || amoIssueLoad || amoIssueStore || cboZeroIssue
    io.dBus.cmd.payload.address := busAddr
    io.dBus.cmd.payload.data := Mux(cboZeroIssue, B(0, 64 bits), Mux(amoIssueStore, amoStoreData, storeData))
    io.dBus.cmd.payload.mask := Mux(cboZeroIssue, B"11111111", writeMask)
    io.dBus.cmd.payload.id := Mux((isStoreBase || amoIssueStore || cboZeroIssue), U(0, 16 bits), nextId)
    io.dBus.cmd.payload.write := isStoreBase || amoIssueStore || cboZeroIssue
    io.storeCommit := storeIssueFire
    io.storeAddr := busAddr
    io.storeData := io.dBus.cmd.payload.data
    io.storeMask := io.dBus.cmd.payload.mask

    // Stores must wait for command acceptance. Otherwise writes can be dropped
    // when the bus is temporarily not ready.
    val storeBlocked = isStoreBase && aguPayloadValid && epochMatches && !suppress && !io.dBus.cmd.ready
    haltWhen(storeBlocked)

    when(cboZeroIssue) {
      when(io.dBus.cmd.ready) {
        when(!cboZeroActive) {
          cboZeroBase := aguEffectiveAddr
          cboZeroBusBase := io.useTranslatedAddr ? io.translatedAddr | aguEffectiveAddr
          cboZeroBeat := U(1, 3 bits)
          cboZeroActive := True
          haltIt()
        } otherwise {
          when(cboZeroBeat === U(7, 3 bits)) {
            cboZeroActive := False
          } otherwise {
            cboZeroBeat := cboZeroBeat + 1
            haltIt()
          }
        }
      } otherwise {
        haltIt()
      }
    }
    when(isCboZero && aguPayloadValid && !cboZeroActive && (!epochMatches || suppress)) {
      cboZeroActive := False
    }

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
          val shifted = rspData
          val shiftedEndian = Bits(64 bits)
          shiftedEndian := shifted
          when(io.bigEndian) {
            shiftedEndian := reverseBytesByMask(shifted, accessSizeMask)
          }
          val oldWord = shiftedEndian(31 downto 0)
          val rs2Word = up(RS2, LaneKey.Lane0)(31 downto 0)
          val oldWordS = oldWord.asSInt
          val rs2WordS = rs2Word.asSInt
          val oldD = shiftedEndian
          val rs2D = up(RS2, LaneKey.Lane0)
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
    when(killCboZero) {
      cboZeroActive := False
    }

    // Load Data Processing - use LIVE data when response is arriving, latched data otherwise
    // This is critical: on the cycle response arrives, we use live data since that's what gets captured
    val responseArriving = isLoadBase && aguPayloadValid && loadResponseArriving
    val rspData = Mux(responseArriving, io.dBus.rsp.payload.data, latchedRspData)
    val shiftedLoadData = rspData
    val shiftedEndianLoadData = Bits(64 bits)
    shiftedEndianLoadData := shiftedLoadData
    when(io.bigEndian) {
      shiftedEndianLoadData := reverseBytesByMask(shiftedLoadData, accessSizeMask)
    }
    val loadResult = Bits(64 bits)
    loadResult := up(MicroCode, LaneKey.Lane0).mux(
       uopLB -> shiftedEndianLoadData(7 downto 0).asSInt.resize(64).asBits,
       uopLBU -> shiftedEndianLoadData(7 downto 0).resize(64),
       uopLH -> shiftedEndianLoadData(15 downto 0).asSInt.resize(64).asBits,
       uopLHU -> shiftedEndianLoadData(15 downto 0).resize(64),
       uopFLH -> shiftedEndianLoadData(15 downto 0).resize(64),
       uopFLW -> shiftedEndianLoadData(31 downto 0).resize(64),
       uopFLD -> shiftedEndianLoadData,
       uopLW -> shiftedEndianLoadData(31 downto 0).asSInt.resize(64).asBits,
       uopLWU -> shiftedEndianLoadData(31 downto 0).resize(64),
       uopLD -> shiftedEndianLoadData,
       default -> B(0, 64 bits)
    )

    // Writeback Result
    // Decoder should set REG_WRITE for loads
    // Enforce x0 invariant: writes to x0 must have data=0 (RVFI expectation)
    val rdAddr = up(borb.frontend.Decoder.RD_ADDR, LaneKey.Lane0).asUInt
    val isX0 = rdAddr === 0
    val maskedLoadResult = isX0 ? B(0, 64 bits) | loadResult
    
    when(isLoadBase && aguPayloadValid && !illegalInsn && !suppress) {
        down(WriteBack.RESULT, LaneKey.Lane0).data.allowOverride := maskedLoadResult
        down(WriteBack.RESULT, LaneKey.Lane0).valid.allowOverride := True 
        down(WriteBack.RESULT, LaneKey.Lane0).address.allowOverride := rdAddr
    }
    when(isAmo && aguPayloadValid && !illegalInsn && !suppress && amoStorePending && io.dBus.cmd.ready) {
      down(WriteBack.RESULT, LaneKey.Lane0).data.allowOverride := isX0 ? B(0, 64 bits) | amoWbData
      down(WriteBack.RESULT, LaneKey.Lane0).valid.allowOverride := True
      down(WriteBack.RESULT, LaneKey.Lane0).address.allowOverride := rdAddr
    }

    // Propagate payloads for RVFI (Store & Load)
    // riscv-formal expects RAW/SHIFTED data and mask matching the address
    val isSendToAgu = up(SENDTOAGU, LaneKey.Lane0)
    // Suppress RVFI side-effects if misaligned
    down(MEM_ADDR) := Mux(aguPayloadValid && (isStore || isLoad), activeAddr, U(0, 64 bits))
    
    // MEM_WMASK and MEM_WDATA (for Stores)
    // Use shifted data/mask to match architectural expectation if address is unaligned?
    // Usually RVFI expects aligned accesses if possible, but for unaligned, it expects bus values.
    val rvfiStoreData = Bits(64 bits)
    rvfiStoreData := rawStoreData
    when(isAmo) {
      rvfiStoreData := amoStoreData
    }
    down(MEM_WMASK) := Mux(aguPayloadValid && isStore && !suppress, accessSizeMask, B(0, 8 bits))
    down(MEM_WDATA) := Mux(aguPayloadValid && isStore && !suppress, rvfiStoreData, B(0, 64 bits))
    
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
