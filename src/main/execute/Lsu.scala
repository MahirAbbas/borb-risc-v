package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import borb.dispatch.SrcPlugin._
import borb.common.MicroCode._
import borb.common.Common._

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
  }

  val logic = new stage.Area {
    // Address Generation: RS1 + sign-extended immediate
    val effectiveAddr = (up(RS1).asSInt + up(IMMED).asSInt).asUInt

    // Extract funct3 from MicroCode to determine access size
    val isStore = up(MicroCode).mux(
      uopSB -> True,
      uopSH -> True,
      uopSW -> True,
      uopSD -> True,
      default -> False
    ).setName("LSU_isStore")

    // Misalignment Check
    val misaligned = Bool()
    misaligned := up(MicroCode).mux(
      uopSH -> (effectiveAddr(0) =/= False),
      uopSW -> (effectiveAddr(1 downto 0) =/= 0),
      uopSD -> (effectiveAddr(2 downto 0) =/= 0),
      default -> False
    )

    // Raise Trap if misaligned
    val localTrap = misaligned && isStore

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

    // Load Logic
    val isLoad = up(MicroCode).mux(
      uopLB -> True, uopLH -> True, uopLW -> True, uopLD -> True,
      uopLBU -> True, uopLHU -> True, uopLWU -> True,
      default -> False
    ).setName("LSU_isLoad")

    val waitingResponse = RegInit(False)
    val nextId = Reg(UInt(16 bits)) init 1
    val waitId = Reg(UInt(16 bits))

    // Firing logic
    val fireLoad = isLoad && up(VALID) && !waitingResponse

    // Drive Data Bus Command
    // Suppress write if misaligned or trap pending
    val suppress = misaligned 
    
    io.dBus.cmd.valid := (isStore || fireLoad) && up(VALID) && up(LANE_SEL) && !suppress
    io.dBus.cmd.payload.address := effectiveAddr
    io.dBus.cmd.payload.data := storeData
    io.dBus.cmd.payload.mask := writeMask
    io.dBus.cmd.payload.id := Mux(isStore, U(0, 16 bits), nextId)
    io.dBus.cmd.payload.write := isStore

    // Stall Logic
    when(isLoad && up(VALID)) {
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
             haltIt()
             when(io.dBus.rsp.valid && io.dBus.rsp.id === waitId) {
                 waitingResponse := False
                 // Data is captured in this cycle
             }
        }
    }

    // Load Data Processing
    val rspData = io.dBus.rsp.payload.data // Use payload accessor
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
    when(isLoad) {
        down(IntAlu.RESULT).data.allowOverride := loadResult
        down(IntAlu.RESULT).valid.allowOverride := True 
        down(IntAlu.RESULT).address.allowOverride := up(borb.frontend.Decoder.RD_ADDR).asUInt
    }

    // Propagate payloads for RVFI (Store & Load)
    // riscv-formal expects NORMALIZED (unshifted) data and mask
    val isSendToAgu = up(SENDTOAGU)
    // Suppress RVFI side-effects if misaligned
    down(MEM_ADDR) := Mux(isSendToAgu && (isStore || isLoad), effectiveAddr, U(0, 64 bits))
    down(MEM_WDATA) := Mux(isSendToAgu && isStore && !suppress, rawStoreData, B(0, 64 bits))
    down(MEM_WMASK) := Mux(isStore && !suppress, accessSizeMask, B(0, 8 bits))
    // For Loads, we need to populate MEM_RMASK and MEM_RDATA
    // RDATA should be the FINAL value written to RD (loadResult)
    // RMASK should be the bytes read (similar to writeMask but for Read)
    val readMask = accessSizeMask // Logic is identical (bytes selected by instruction)
    
    down(MEM_RMASK) := Mux(isLoad && !suppress, readMask, B(0, 8 bits))
    down(MEM_RDATA) := Mux(isLoad && !suppress, loadResult, B(0, 64 bits))

    // Stores do not write to register file
    // RESULT payload should remain 0/invalid (handled by IntAlu defaults)
  }
}
