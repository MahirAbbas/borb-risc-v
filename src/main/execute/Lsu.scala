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

case class Lsu(stage: CtrlLink) extends Area {
  import Lsu._
  import borb.dispatch.Dispatch._

  val io = new Bundle {
    val dBus = DataBus(addressWidth = 64, dataWidth = 64, idWidth = 16)
    val pmpFault = Bool()
  }

  val logic = new stage.Area {
    val isAmoAddW = up(MicroCode) === uopAMOADDW
    val isAmoAddD = up(MicroCode) === uopAMOADDD
    val isAmo = isAmoAddW || isAmoAddD
    val amoIsWord = isAmoAddW

    // Address Generation: RS1 + sign-extended immediate for normal loads/stores,
    // RS1 only for AMOs.
    val effectiveAddr = Mux(isAmo, up(RS1).asUInt, (up(RS1).asSInt + up(IMMED).asSInt).asUInt)

    // Extract funct3 from MicroCode to determine access size
    val isStoreBase = up(MicroCode).mux(
      uopSB -> True,
      uopSH -> True,
      uopSW -> True,
      uopSD -> True,
      default -> False
    )
    val isStore = (isStoreBase || isAmo).setName("LSU_isStore")

    // Load Logic
    val isLoadBase = up(MicroCode).mux(
      uopLB -> True, uopLH -> True, uopLW -> True, uopLD -> True,
      uopLBU -> True, uopLHU -> True, uopLWU -> True,
      default -> False
    )
    val isLoad = (isLoadBase || isAmo).setName("LSU_isLoad")

    // Misalignment Check
    val misaligned = Bool()
    misaligned := up(MicroCode).mux(
      uopLH -> (effectiveAddr(0) =/= False),
      uopLHU -> (effectiveAddr(0) =/= False),
      uopSH -> (effectiveAddr(0) =/= False),
      uopLW -> (effectiveAddr(1 downto 0) =/= 0),
      uopLWU -> (effectiveAddr(1 downto 0) =/= 0),
      uopSW -> (effectiveAddr(1 downto 0) =/= 0),
      uopLD -> (effectiveAddr(2 downto 0) =/= 0),
      uopSD -> (effectiveAddr(2 downto 0) =/= 0),
      uopAMOADDW -> (effectiveAddr(1 downto 0) =/= 0),
      uopAMOADDD -> (effectiveAddr(2 downto 0) =/= 0),
      default -> False
    )

    // Raise trap on any misaligned memory access.
    val localTrap = (misaligned || io.pmpFault) && (isStore || isLoad)

    // Byte offset within doubleword (for alignment)
    val byteOffset = effectiveAddr(2 downto 0)

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
      uopLW -> B"00001111",
      uopLWU -> B"00001111",
      uopLD -> B"11111111",
      uopAMOADDW -> B"00001111",
      uopAMOADDD -> B"11111111",
      default -> B(0, 8 bits)
    )

    // Shifted mask for dBus
    val writeMask = accessSizeMask |<< byteOffset

    // Align store data to the correct byte lanes
    val rawStoreData = Bits(64 bits)
    rawStoreData := up(MicroCode).mux(
      uopSB -> (up(RS2)(7 downto 0)).resize(64),
      uopSH -> (up(RS2)(15 downto 0)).resize(64),
      uopSW -> (up(RS2)(31 downto 0)).resize(64),
      uopSD -> up(RS2),
      default -> B(0, 64 bits)
    )
    
    val storeData = rawStoreData |<< (byteOffset << 3)

    val waitingResponse = RegInit(False)
    val nextId = Reg(UInt(16 bits)) init 1
    val waitId = Reg(UInt(16 bits))
    val amoWaitingResponse = RegInit(False)
    val amoWaitId = Reg(UInt(16 bits)) init 0
    val amoStorePending = RegInit(False)
    val amoStoreData = Reg(Bits(64 bits)) init 0
    val amoWbData = Reg(Bits(64 bits)) init 0

    // Drive Data Bus Command
    // Suppress memory side effects for traps (misaligned or illegal instruction).
    val illegalInsn = up(Decoder.DECODED_INSTRUCTION)(1 downto 0) =/= B"11"
    val suppress = misaligned || illegalInsn || io.pmpFault
    // Firing logic
    val fireLoad = isLoadBase && up(VALID) && !waitingResponse
    val amoIssueLoad = isAmo && up(VALID) && up(LANE_SEL) && !suppress && !amoWaitingResponse && !amoStorePending
    val amoIssueStore = isAmo && up(VALID) && up(LANE_SEL) && !suppress && amoStorePending
    
    io.dBus.cmd.valid := ((isStoreBase || fireLoad) && up(VALID) && up(LANE_SEL) && !suppress) || amoIssueLoad || amoIssueStore
    io.dBus.cmd.payload.address := effectiveAddr
    io.dBus.cmd.payload.data := Mux(amoIssueStore, amoStoreData |<< (byteOffset << 3), storeData)
    io.dBus.cmd.payload.mask := writeMask
    io.dBus.cmd.payload.id := Mux((isStoreBase || amoIssueStore), U(0, 16 bits), nextId)
    io.dBus.cmd.payload.write := isStoreBase || amoIssueStore

    // Stores must wait for command acceptance. Otherwise writes can be dropped
    // when the bus is temporarily not ready.
    val storeBlocked = isStoreBase && up(VALID) && up(LANE_SEL) && !suppress && !io.dBus.cmd.ready
    haltWhen(storeBlocked)

    // Stall Logic
    // IMPORTANT: When the response arrives, the pipeline advances at end of that cycle.
    // The down() signals are captured based on COMBINATORIAL values.
    // So we must use io.dBus.rsp.payload.data directly on the cycle response arrives.
    val latchedRspData = Reg(Bits(64 bits))
    val responseArriving = isLoadBase && up(VALID) && waitingResponse && io.dBus.rsp.valid && (io.dBus.rsp.id === waitId)
    
    when(isLoadBase && up(VALID) && !suppress) {
        when(!waitingResponse) {
             when(io.dBus.cmd.ready && !suppress && up(LANE_SEL)) {
                 waitingResponse := True
                 waitId := nextId
                 nextId := nextId + 1
                 haltIt()
             } otherwise {
                 haltIt()
             }
        } otherwise {
             // Waiting for response
             when(responseArriving) {
                 waitingResponse := False
                 latchedRspData := io.dBus.rsp.payload.data  // Also latch for potential future use
                 // Pipeline advances at end of this cycle - use live data for down()
             } otherwise {
                 haltIt()  // Only halt if response hasn't arrived yet
             }
        }
    }
    when(isLoadBase && up(VALID) && suppress) {
      // Faulting/suppressed loads must not enter the response wait state.
      waitingResponse := False
    }

    // AMOADD implementation: read old value, compute/store new value, write old
    // value to rd after store command is accepted.
    val amoResponseArriving = isAmo && up(VALID) && amoWaitingResponse && io.dBus.rsp.valid && (io.dBus.rsp.id === amoWaitId)
    when(isAmo && up(VALID) && !suppress) {
      when(!amoWaitingResponse && !amoStorePending) {
        when(io.dBus.cmd.ready && up(LANE_SEL)) {
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
          val oldWord = shifted(31 downto 0)
          val rs2Word = up(RS2)(31 downto 0)
          val newWord = (oldWord.asUInt + rs2Word.asUInt).asBits
          amoWbData := Mux(amoIsWord, oldWord.asSInt.resize(64).asBits, shifted)
          amoStoreData := Mux(
            amoIsWord,
            newWord.resize(64),
            (shifted.asUInt + up(RS2).asUInt).asBits
          )
          amoWaitingResponse := False
          amoStorePending := True
          haltIt()
        } otherwise {
          haltIt()
        }
      } elsewhen(amoStorePending) {
        when(io.dBus.cmd.ready && up(LANE_SEL)) {
          amoStorePending := False
        } otherwise {
          haltIt()
        }
      }
    }
    when(isAmo && up(VALID) && suppress) {
      amoWaitingResponse := False
      amoStorePending := False
    }

    // Load Data Processing - use LIVE data when response is arriving, latched data otherwise
    // This is critical: on the cycle response arrives, we use live data since that's what gets captured
    val rspData = Mux(responseArriving, io.dBus.rsp.payload.data, latchedRspData)
    val shiftedLoadData = rspData >> (byteOffset << 3)
    val loadResult = Bits(64 bits)
    loadResult := up(MicroCode).mux(
       uopLB -> shiftedLoadData(7 downto 0).asSInt.resize(64).asBits,
       uopLBU -> shiftedLoadData(7 downto 0).resize(64),
       uopLH -> shiftedLoadData(15 downto 0).asSInt.resize(64).asBits,
       uopLHU -> shiftedLoadData(15 downto 0).resize(64),
       uopLW -> shiftedLoadData(31 downto 0).asSInt.resize(64).asBits,
       uopLWU -> shiftedLoadData(31 downto 0).resize(64),
       uopLD -> shiftedLoadData,
       default -> B(0, 64 bits)
    )

    // Writeback Result
    // Decoder should set REG_WRITE for loads
    // Enforce x0 invariant: writes to x0 must have data=0 (RVFI expectation)
    val rdAddr = up(borb.frontend.Decoder.RD_ADDR).asUInt
    val isX0 = rdAddr === 0
    val maskedLoadResult = isX0 ? B(0, 64 bits) | loadResult
    
    when(isLoadBase && !illegalInsn && !suppress) {
        down(WriteBack.RESULT).data.allowOverride := maskedLoadResult
        down(WriteBack.RESULT).valid.allowOverride := True 
        down(WriteBack.RESULT).address.allowOverride := rdAddr
    }
    when(isAmo && !illegalInsn && !suppress && amoStorePending && io.dBus.cmd.ready && up(LANE_SEL)) {
      down(WriteBack.RESULT).data.allowOverride := isX0 ? B(0, 64 bits) | amoWbData
      down(WriteBack.RESULT).valid.allowOverride := True
      down(WriteBack.RESULT).address.allowOverride := rdAddr
    }

    // Propagate payloads for RVFI (Store & Load)
    // riscv-formal expects RAW/SHIFTED data and mask matching the address
    val isSendToAgu = up(SENDTOAGU)
    // Suppress RVFI side-effects if misaligned
    down(MEM_ADDR) := Mux(isSendToAgu && (isStore || isLoad), effectiveAddr, U(0, 64 bits))
    
    // MEM_WMASK and MEM_WDATA (for Stores)
    // Use shifted data/mask to match architectural expectation if address is unaligned?
    // Usually RVFI expects aligned accesses if possible, but for unaligned, it expects bus values.
    down(MEM_WMASK) := Mux(isStore && !suppress, accessSizeMask, B(0, 8 bits))
    down(MEM_WDATA) := Mux(isStore && !suppress, rawStoreData, B(0, 64 bits))
    
    // For Loads:
    // MEM_RMASK should be shifted (to match address lanes).
    // MEM_RDATA should be shifted/raw from bus (rspData).
    val readMaskShifted = accessSizeMask |<< byteOffset
    
    down(MEM_RMASK) := Mux(isLoad && !suppress, accessSizeMask, B(0, 8 bits))
    // MEM_RDATA: riscv-formal expects the raw extracted data (shifted to LSB, BEFORE sign-extension)
    // The formal model applies its own sign-extension based on instruction type
    down(MEM_RDATA) := Mux(isLoad && !suppress, shiftedLoadData, B(0, 64 bits))

    // Stores do not write to register file
    // RESULT payload should remain 0/invalid (handled by IntAlu defaults)
  }
}
