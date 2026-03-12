package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import borb.frontend.Decoder
import borb.frontend.YESNO
import borb.dispatch.SrcPlugin._
import borb.dispatch.ExecutionRoute._
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

case class Lsu(stage: CtrlLink, wbStage: CtrlLink, currentEpoch: UInt) extends Area {
  import Lsu._
  import borb.dispatch.Dispatch._

  val io = new Bundle {
    val dBus = DataBus(addressWidth = 64, dataWidth = 64, idWidth = 16)
    val pmpFault = Bool()
    val pageFault = Bool()
    val accessFault = Bool()
    val cmdBusy = Bool()
    val physAddr = UInt(64 bits)
  }

  val logic = new stage.Area {
    val epochMatches = up(SPEC_EPOCH) === currentEpoch
    val newAguRoute =
      up(NEW_ROUTE_VALID) &&
      (up(NEW_EU_ID) === EuId.AguEu) &&
      (up(NEW_FU_KIND) === FuKind.MemoryAccess)
    val legacyFpAguRoute = up(SENDTOAGU) && (up(Decoder.IS_FP) === YESNO.Y)
    val isAguRoute = newAguRoute || legacyFpAguRoute
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
    val aguEffectiveAddr = Mux(isAmo, up(RS1).asUInt, (up(RS1).asUInt + up(IMMED).asUInt).resize(64))

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
    val aguPayloadValid = up(VALID) && up(LANE_SEL) && isAguRoute
    val currentSeq = up(Fetch.FETCH_SEQ)
    val duplicateInWb = wbStage.up.isValid &&
      wbStage(VALID) &&
      wbStage(LANE_SEL) &&
      (wbStage(Fetch.FETCH_SEQ) === currentSeq)

    val waitingResponse = RegInit(False)
    val loadCompleted = RegInit(False)
    val loadCompletedSeq = Reg(UInt(currentSeq.getWidth bits)) init 0
    val nextId = Reg(UInt(16 bits)) init 1
    val waitId = Reg(UInt(16 bits))
    val amoWaitingResponse = RegInit(False)
    val amoWaitId = Reg(UInt(16 bits)) init 0
    val amoOp = Reg(up(MicroCode).clone()) init uopNOP
    val amoRd = Reg(UInt(5 bits)) init 0
    val amoRs2 = Reg(Bits(64 bits)) init 0
    val amoEpoch = Reg(UInt(currentEpoch.getWidth bits)) init 0
    val amoStorePending = RegInit(False)
    val amoStoreData = Reg(Bits(64 bits)) init 0
    val amoWbData = Reg(Bits(64 bits)) init 0

    // Misalignment Check
    val activeMicroCode = cloneOf(up(MicroCode))
    activeMicroCode := up(MicroCode)
    when(amoWaitingResponse || amoStorePending) {
      activeMicroCode := amoOp
    }
    val activeAmoSwapW = activeMicroCode === uopAMOSWAPW
    val activeAmoSwapD = activeMicroCode === uopAMOSWAPD
    val activeAmoAddW = activeMicroCode === uopAMOADDW
    val activeAmoAddD = activeMicroCode === uopAMOADDD
    val activeAmoXorW = activeMicroCode === uopAMOXORW
    val activeAmoXorD = activeMicroCode === uopAMOXORD
    val activeAmoAndW = activeMicroCode === uopAMOANDW
    val activeAmoAndD = activeMicroCode === uopAMOANDD
    val activeAmoOrW = activeMicroCode === uopAMOORW
    val activeAmoOrD = activeMicroCode === uopAMOORD
    val activeAmoMinW = activeMicroCode === uopAMOMINW
    val activeAmoMinD = activeMicroCode === uopAMOMIND
    val activeAmoMaxW = activeMicroCode === uopAMOMAXW
    val activeAmoMaxD = activeMicroCode === uopAMOMAXD
    val activeAmoMinuW = activeMicroCode === uopAMOMINUW
    val activeAmoMinuD = activeMicroCode === uopAMOMINUD
    val activeAmoMaxuW = activeMicroCode === uopAMOMAXUW
    val activeAmoMaxuD = activeMicroCode === uopAMOMAXUD
    val activeAmoIsWord =
      activeAmoSwapW || activeAmoAddW || activeAmoXorW || activeAmoAndW ||
        activeAmoOrW || activeAmoMinW || activeAmoMaxW || activeAmoMinuW || activeAmoMaxuW
    val waitAddr = Reg(UInt(64 bits)) init(0)
    val activeAddr = UInt(64 bits)
    activeAddr := aguEffectiveAddr
    when(waitingResponse || amoWaitingResponse || amoStorePending) {
      activeAddr := waitAddr
    }
    val effectiveAddr = activeAddr

    val misaligned = Bool()
    misaligned := activeMicroCode.mux(
      uopLH -> (activeAddr(0) =/= False),
      uopLHU -> (activeAddr(0) =/= False),
      uopSH -> (activeAddr(0) =/= False),
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
    val localTrap = (misaligned || io.pmpFault || io.pageFault || io.accessFault) && (isStore || isLoad)

    // Byte offset within doubleword (for alignment)
    val byteOffset = activeAddr(2 downto 0)

    // Generate access mask (Normalized / Unshifted) - for both Load and Store
    val accessSizeMask = Bits(8 bits)
    accessSizeMask := activeMicroCode.mux(
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

    // Drive Data Bus Command
    // Suppress memory side effects for traps (misaligned or illegal instruction).
    val illegalInsn = up(Decoder.DECODED_INSTRUCTION)(1 downto 0) =/= B"11"
    val fatalSuppress = misaligned || illegalInsn || io.pmpFault || io.pageFault || io.accessFault
    val commandSuppress = fatalSuppress || duplicateInWb
    val cmdPending = up(VALID) && up(LANE_SEL) && epochMatches && (isStore || isLoad) && io.cmdBusy && !commandSuppress
    // Firing logic
    val fireLoad = isLoadBase && up(VALID) && epochMatches && !waitingResponse
    val amoIssueLoad = isAmo && up(VALID) && up(LANE_SEL) && epochMatches && !commandSuppress && !amoWaitingResponse && !amoStorePending
    val amoIssueStore = amoStorePending && (amoEpoch === currentEpoch)
    
    io.dBus.cmd.valid := ((isStoreBase || fireLoad) && up(VALID) && up(LANE_SEL) && epochMatches && !commandSuppress) || amoIssueLoad || amoIssueStore
    io.dBus.cmd.payload.address := activeAddr
    io.dBus.cmd.payload.data := Mux(amoIssueStore, amoStoreData |<< (byteOffset << 3), storeData)
    io.dBus.cmd.payload.mask := writeMask
    io.dBus.cmd.payload.id := Mux((isStoreBase || amoIssueStore), U(0, 16 bits), nextId)
    io.dBus.cmd.payload.write := isStoreBase || amoIssueStore

    // Stores must wait for command acceptance. Otherwise writes can be dropped
    // when the bus is temporarily not ready.
    val storeBlocked = isStoreBase && up(VALID) && up(LANE_SEL) && epochMatches && !commandSuppress && !io.dBus.cmd.ready
    haltWhen(storeBlocked)
    haltWhen(cmdPending)

    // Stall Logic
    // IMPORTANT: When the response arrives, the pipeline advances at end of that cycle.
    // The down() signals are captured based on COMBINATORIAL values.
    // So we must use io.dBus.rsp.payload.data directly on the cycle response arrives.
    val latchedRspData = Reg(Bits(64 bits))
    val loadResponseArriving = waitingResponse && io.dBus.rsp.valid && (io.dBus.rsp.id === waitId)
    val loadCompletedForCurrent = loadCompleted && up(VALID) && (up(Fetch.FETCH_SEQ) === loadCompletedSeq)
    val loadDataReady = loadCompletedForCurrent || loadResponseArriving

    // Outstanding LSU response state must be retired by matching the bus response,
    // not by whatever instruction currently occupies execute.
    when(loadResponseArriving) {
      waitingResponse := False
      loadCompleted := True
      loadCompletedSeq := up(Fetch.FETCH_SEQ)
      latchedRspData := io.dBus.rsp.payload.data
    }
    
    when(isLoadBase && up(VALID) && epochMatches && !commandSuppress && !loadCompletedForCurrent) {
        when(!waitingResponse) {
             when(io.dBus.cmd.ready && !commandSuppress && up(LANE_SEL)) {
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
    when(isLoadBase && up(VALID) && (!epochMatches || fatalSuppress)) {
      // Faulting/suppressed loads must not enter the response wait state.
      waitingResponse := False
      loadCompleted := False
    }
    when(
      loadCompleted &&
      up(VALID) &&
      (
        !isLoadBase ||
        (up(Fetch.FETCH_SEQ) =/= loadCompletedSeq)
      )
    ) {
      loadCompleted := False
    }

    // AMO implementation: read old value, compute/store new value, write old
    // value to rd after store command is accepted.
    val amoResponseArriving = amoWaitingResponse && io.dBus.rsp.valid && (io.dBus.rsp.id === amoWaitId)
    when(amoResponseArriving) {
      amoWaitingResponse := False
    }
    when(amoIssueLoad) {
      when(io.dBus.cmd.ready) {
        waitAddr := aguEffectiveAddr
        amoWaitingResponse := True
        amoWaitId := nextId
        amoOp := up(MicroCode)
        amoRd := up(Decoder.RD_ADDR).asUInt
        amoRs2 := up(RS2)
        amoEpoch := up(SPEC_EPOCH)
        nextId := nextId + 1
        haltIt()
      } otherwise {
        haltIt()
      }
    }
    when(amoWaitingResponse) {
      when(amoResponseArriving) {
        val rspData = io.dBus.rsp.payload.data
        val shifted = rspData >> (byteOffset << 3)
        val oldWord = shifted(31 downto 0)
        val rs2Word = amoRs2(31 downto 0)
        val oldWordS = oldWord.asSInt
        val rs2WordS = rs2Word.asSInt
        val oldD = shifted
        val rs2D = amoRs2
        val oldDS = oldD.asSInt
        val rs2DS = rs2D.asSInt

        val newWord = Bits(32 bits)
        newWord := oldWord
        when(activeAmoSwapW) { newWord := rs2Word }
        when(activeAmoAddW) { newWord := (oldWord.asUInt + rs2Word.asUInt).asBits }
        when(activeAmoXorW) { newWord := oldWord ^ rs2Word }
        when(activeAmoAndW) { newWord := oldWord & rs2Word }
        when(activeAmoOrW) { newWord := oldWord | rs2Word }
        when(activeAmoMinW) { newWord := Mux(oldWordS < rs2WordS, oldWord, rs2Word) }
        when(activeAmoMaxW) { newWord := Mux(oldWordS > rs2WordS, oldWord, rs2Word) }
        when(activeAmoMinuW) { newWord := Mux(oldWord.asUInt < rs2Word.asUInt, oldWord, rs2Word) }
        when(activeAmoMaxuW) { newWord := Mux(oldWord.asUInt > rs2Word.asUInt, oldWord, rs2Word) }

        val newD = Bits(64 bits)
        newD := oldD
        when(activeAmoSwapD) { newD := rs2D }
        when(activeAmoAddD) { newD := (oldD.asUInt + rs2D.asUInt).asBits }
        when(activeAmoXorD) { newD := oldD ^ rs2D }
        when(activeAmoAndD) { newD := oldD & rs2D }
        when(activeAmoOrD) { newD := oldD | rs2D }
        when(activeAmoMinD) { newD := Mux(oldDS < rs2DS, oldD, rs2D) }
        when(activeAmoMaxD) { newD := Mux(oldDS > rs2DS, oldD, rs2D) }
        when(activeAmoMinuD) { newD := Mux(oldD.asUInt < rs2D.asUInt, oldD, rs2D) }
        when(activeAmoMaxuD) { newD := Mux(oldD.asUInt > rs2D.asUInt, oldD, rs2D) }

        amoWbData := Mux(activeAmoIsWord, oldWord.asSInt.resize(64).asBits, shifted)
        amoStoreData := Mux(activeAmoIsWord, newWord.resize(64), newD)
        amoStorePending := True
        haltIt()
      } otherwise {
        haltIt()
      }
    }
    when(amoStorePending) {
      when(io.dBus.cmd.ready) {
        amoStorePending := False
      } otherwise {
        haltIt()
      }
    }
    when(isAmo && up(VALID) && (!epochMatches || fatalSuppress)) {
      amoWaitingResponse := False
      amoStorePending := False
    }
    when((amoWaitingResponse || amoStorePending) && (amoEpoch =/= currentEpoch)) {
      amoWaitingResponse := False
      amoStorePending := False
    }

    // Load Data Processing - use LIVE data when response is arriving, latched data otherwise
    // This is critical: on the cycle response arrives, we use live data since that's what gets captured
    val responseArriving = isLoadBase && up(VALID) && loadResponseArriving
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
    
    when(isLoadBase && !illegalInsn && !fatalSuppress && !duplicateInWb && loadDataReady) {
        down(WriteBack.RESULT).data.allowOverride := maskedLoadResult
        down(WriteBack.RESULT).valid.allowOverride := True 
        down(WriteBack.RESULT).address.allowOverride := rdAddr
    }
    when(amoStorePending && io.dBus.cmd.ready && (amoEpoch === currentEpoch)) {
      down(WriteBack.RESULT).data.allowOverride := (amoRd === 0) ? B(0, 64 bits) | amoWbData
      down(WriteBack.RESULT).valid.allowOverride := True
      down(WriteBack.RESULT).address.allowOverride := amoRd
    }

    // Propagate payloads for RVFI (Store & Load)
    // riscv-formal expects RAW/SHIFTED data and mask matching the address
    val isSendToAgu = isAguRoute
    // Suppress RVFI side-effects if misaligned
    down(MEM_ADDR) := Mux(isSendToAgu && (isStore || isLoad), activeAddr, U(0, 64 bits))
    
    // MEM_WMASK and MEM_WDATA (for Stores)
    // Use shifted data/mask to match architectural expectation if address is unaligned?
    // Usually RVFI expects aligned accesses if possible, but for unaligned, it expects bus values.
    down(MEM_WMASK) := Mux(isStore && !fatalSuppress && !duplicateInWb, accessSizeMask, B(0, 8 bits))
    down(MEM_WDATA) := Mux(isStore && !fatalSuppress && !duplicateInWb, rawStoreData, B(0, 64 bits))
    
    // For Loads:
    // MEM_RMASK should be shifted (to match address lanes).
    // MEM_RDATA should be shifted/raw from bus (rspData).
    val readMaskShifted = accessSizeMask |<< byteOffset
    
    down(MEM_RMASK) := Mux(isLoad && !fatalSuppress && !duplicateInWb && loadDataReady, accessSizeMask, B(0, 8 bits))
    // MEM_RDATA: riscv-formal expects the raw extracted data (shifted to LSB, BEFORE sign-extension)
    // The formal model applies its own sign-extension based on instruction type
    down(MEM_RDATA) := Mux(isLoad && !fatalSuppress && !duplicateInWb && loadDataReady, shiftedLoadData, B(0, 64 bits))

    // Stores do not write to register file
    // RESULT payload should remain 0/invalid (handled by IntAlu defaults)
  }
}
