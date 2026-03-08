package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._

import borb.common.Common._
import borb.frontend.Decoder.INSTRUCTION

object Fetch extends AreaObject {
  val addressWidth = 64
}

case class Fetch(
  cmdStage: CtrlLink,
  rspStage: CtrlLink,
  addressWidth: Int,
  dataWidth: Int,
  withCompressed: Boolean = false
) extends Area {
  private val frontendConfig = FrontendConfig(
    addressWidth = addressWidth,
    dataWidth = dataWidth,
    withCompressed = withCompressed
  )

  private val archBase = U(BigInt("80000000", 16), addressWidth bits)
  private def archAddr(addr: UInt): UInt = Mux(addr < archBase, addr + archBase, addr)

  val io = new Bundle {
    val source = new FetchSourceBus(addressWidth, dataWidth, frontendConfig.epochWidth)
    val flush = Bool()
    val currentEpoch = UInt(frontendConfig.epochWidth bits)
    val pcAdvance = Bool()
    val pcStep = UInt(3 bits)
  }

  val fifo = StreamFifo(
    FetchPacket(frontendConfig.addressWidth, frontendConfig.dataWidth, frontendConfig.epochWidth),
    depth = frontendConfig.beatBufferDepth
  )

  io.pcAdvance := False
  io.pcStep := U(4, 3 bits)

  val inflight = RegInit(U(0, 4 bits))
  val cmdFire = io.source.req.fire
  val rspFire = io.source.rsp.valid
  inflight := inflight + U(cmdFire) - U(rspFire)
  val epoch = RegInit(U(0, frontendConfig.epochWidth bits))
  when(io.flush) {
    epoch := epoch + 1
  }
  val activeEpoch = epoch + U(io.flush)

  val receiveStage = new Area {
    fifo.io.push.valid := io.source.rsp.valid
    fifo.io.push.payload.data := io.source.rsp.data
    fifo.io.push.payload.epoch := io.source.rsp.epoch
    fifo.io.push.payload.beatAddr := io.source.rsp.address
    fifo.io.flush := io.flush
  }

  val alignState = new Area {
    val needSecondBeat = Reg(Bool()) init(False)
    val secondBeatAddr = Reg(UInt(addressWidth bits)) init(0)
    val firstHalfword = Reg(Bits(16 bits)) init(0)
    val replayGuardValid = RegInit(False)
    val lastTakenPc = Reg(UInt(addressWidth bits)) init(0)
    val lastTakenEpoch = Reg(UInt(frontendConfig.epochWidth bits)) init(0)
    val lastTakenBeatAddr = Reg(UInt(addressWidth bits)) init(0)

    when(io.flush) {
      needSecondBeat := False
      replayGuardValid := False
    }
  }

  val requestStage = new cmdStage.Area {
    val beatAddr = UInt(addressWidth bits)
    beatAddr := archAddr(cmdStage(PC.PC))
    beatAddr(2 downto 0) := 0

    val reqAddr = UInt(addressWidth bits)
    reqAddr := alignState.needSecondBeat ? alignState.secondBeatAddr | beatAddr

    val currentBeatBuffered = fifo.io.pop.valid &&
      (fifo.io.pop.payload.epoch === activeEpoch) &&
      (fifo.io.pop.payload.beatAddr === reqAddr)

    io.source.req.valid := cmdStage.up.isValid && (inflight === 0) && !io.flush && !currentBeatBuffered
    io.source.req.payload.address := reqAddr
    io.source.req.payload.epoch := activeEpoch

    haltWhen(io.source.req.valid && !io.source.req.fire)
  }

  val alignStage = new rspStage.Area {
    val beatAddr = UInt(addressWidth bits)
    beatAddr := archAddr(rspStage(PC.PC))
    beatAddr(2 downto 0) := 0

    val srcValid = fifo.io.pop.valid
    val srcData = fifo.io.pop.payload.data
    val srcEpoch = fifo.io.pop.payload.epoch
    val srcBeatAddr = fifo.io.pop.payload.beatAddr

    val expectedBeat = UInt(addressWidth bits)
    expectedBeat := alignState.needSecondBeat ? alignState.secondBeatAddr | beatAddr

    val stalePacket = srcValid && (srcEpoch =/= activeEpoch)
    val beatMismatch = srcValid && (srcBeatAddr =/= expectedBeat)
    val duplicatePc = srcValid &&
      alignState.replayGuardValid &&
      (rspStage(PC.PC) === alignState.lastTakenPc) &&
      (srcEpoch === alignState.lastTakenEpoch) &&
      (srcBeatAddr === alignState.lastTakenBeatAddr)

    when(alignState.replayGuardValid && (rspStage(PC.PC) =/= alignState.lastTakenPc)) {
      alignState.replayGuardValid := False
    }

    throwWhen(stalePacket)
    throwWhen(duplicatePc)

    val hw0 = srcData(15 downto 0)
    val hw1 = srcData(31 downto 16)
    val hw2 = srcData(47 downto 32)
    val hw3 = srcData(63 downto 48)

    val hwIndex = rspStage(PC.PC)(2 downto 1)
    val first16 = hwIndex.mux(
      U(0) -> hw0,
      U(1) -> hw1,
      U(2) -> hw2,
      default -> hw3
    )
    val needs32 = first16(1 downto 0) === B"11"
    val straddle = needs32 && (hwIndex === U(3))

    val assembledInsn = Bits(32 bits)
    assembledInsn := B"32'h00000013"
    when(alignState.needSecondBeat) {
      assembledInsn := srcData(15 downto 0) ## alignState.firstHalfword
    } otherwise {
      if(withCompressed) {
        when(!needs32) {
          assembledInsn := B"16'h0000" ## first16
        } otherwise {
          switch(hwIndex) {
            is(U(0)) { assembledInsn := hw1 ## hw0 }
            is(U(1)) { assembledInsn := hw2 ## hw1 }
            is(U(2)) { assembledInsn := hw3 ## hw2 }
            default { assembledInsn := B"32'h00000013" }
          }
        }
      } else {
        assembledInsn := Mux(rspStage(PC.PC)(2), srcData(63 downto 32), srcData(31 downto 0))
      }
    }

    rspStage.down(INSTRUCTION) := assembledInsn
    rspStage.down(SPEC_EPOCH) := srcEpoch

    val waitingSecond = Bool()
    if(withCompressed) {
      waitingSecond := straddle && !alignState.needSecondBeat
    } else {
      waitingSecond := False
    }

    haltWhen((!srcValid || beatMismatch || waitingSecond) && !stalePacket)

    val takeInsn = rspStage.down.isFiring && srcValid && !stalePacket && !beatMismatch && !waitingSecond
    val stepBytes = UInt(3 bits)
    if(withCompressed) {
      stepBytes := (assembledInsn(1 downto 0) =/= B"11") ? U(2, 3 bits) | U(4, 3 bits)
    } else {
      stepBytes := U(4, 3 bits)
    }
    val nextPc = rspStage(PC.PC) + stepBytes.resize(addressWidth)
    val nextBeatAddr = UInt(addressWidth bits)
    nextBeatAddr := archAddr(nextPc)
    nextBeatAddr(2 downto 0) := 0

    when(waitingSecond && srcValid && !stalePacket && !beatMismatch) {
      alignState.needSecondBeat := True
      alignState.secondBeatAddr := beatAddr + U(frontendConfig.beatBytes, addressWidth bits)
      alignState.firstHalfword := first16
    }

    when(takeInsn) {
      alignState.replayGuardValid := True
      alignState.lastTakenPc := rspStage(PC.PC)
      alignState.lastTakenEpoch := srcEpoch
      alignState.lastTakenBeatAddr := srcBeatAddr
      io.pcAdvance := True
      io.pcStep := stepBytes
      when(alignState.needSecondBeat) {
        alignState.needSecondBeat := False
      }
    }

    val consumeBeat = Bool()
    if(withCompressed) {
      consumeBeat := waitingSecond || (takeInsn && (nextBeatAddr =/= srcBeatAddr))
    } else {
      consumeBeat := takeInsn && rspStage(PC.PC)(2)
    }

    fifo.io.pop.ready := consumeBeat || stalePacket || beatMismatch || duplicatePc
  }

  val sourceAvailable = fifo.io.pop.valid
  val beatBufferAvailability = fifo.io.availability
}
