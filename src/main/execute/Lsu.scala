package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import borb.dispatch.SrcPlugin._
import borb.common.MicroCode._
import borb.common.Common._

// Data Bus Command for Store operations
case class DataBusCmd(addressWidth: Int, dataWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
  val data = Bits(dataWidth bits)
  val mask = Bits(dataWidth / 8 bits)  // Byte enables
  val write = Bool()  // True = Store, False = Load (future)
}

// Data Bus Response for Load operations (future)
case class DataBusRsp(dataWidth: Int) extends Bundle {
  val data = Bits(dataWidth bits)
}

// Data Bus Bundle
case class DataBus(addressWidth: Int, dataWidth: Int) extends Bundle with IMasterSlave {
  val cmd = Stream(DataBusCmd(addressWidth, dataWidth))
  val rsp = Flow(DataBusRsp(dataWidth))

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
    val dBus = DataBus(addressWidth = 64, dataWidth = 64)
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

    // Generate write mask (Normalized / Unshifted)
    val rawWriteMask = Bits(8 bits)
    rawWriteMask := up(MicroCode).muxDc(
      uopSB -> B"00000001",
      uopSH -> B"00000011",
      uopSW -> B"00001111",
      uopSD -> B"11111111"
    )

    // Shifted mask for dBus
    val writeMask = rawWriteMask |<< byteOffset

    // Align store data to the correct byte lanes
    val rawStoreData = Bits(64 bits)
    rawStoreData := up(MicroCode).muxDc(
      uopSB -> (up(RS2)(7 downto 0)).resize(64),
      uopSH -> (up(RS2)(15 downto 0)).resize(64),
      uopSW -> (up(RS2)(31 downto 0)).resize(64),
      uopSD -> up(RS2)
    )
    
    val storeData = rawStoreData |<< (byteOffset << 3)

    // Drive Data Bus Command
    // Suppress write if misaligned or trap pending
    val suppress = misaligned 
    io.dBus.cmd.valid := isStore && up(VALID) && up(LANE_SEL) && !suppress
    io.dBus.cmd.payload.address := effectiveAddr
    io.dBus.cmd.payload.data := storeData
    io.dBus.cmd.payload.mask := writeMask
    io.dBus.cmd.payload.write := True

    // Data Bus Response (ignored for Stores, used for Loads later)
    // Note: Flow doesn't have ready signal, it's always received

    // Propagate payloads for RVFI (only for Store instructions)
    // riscv-formal expects NORMALIZED (unshifted) data and mask
    val isSendToAgu = up(SENDTOAGU)
    // Suppress RVFI side-effects if misaligned
    down(MEM_ADDR) := Mux(isSendToAgu && isStore, effectiveAddr, U(0, 64 bits))
    down(MEM_WDATA) := Mux(isSendToAgu && isStore && !suppress, rawStoreData, B(0, 64 bits))
    down(MEM_WMASK) := Mux(isStore && !suppress, rawWriteMask, B(0, 8 bits))
    down(MEM_RMASK) := B(0, 8 bits)  // No read for stores
    down(MEM_RDATA) := B(0, 64 bits)  // No read data for stores

    // Stores do not write to register file
    // RESULT payload should remain 0/invalid (handled by IntAlu defaults)
  }
}
