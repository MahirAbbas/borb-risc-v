package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.misc.pipeline._

import borb.common.Common._
import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION
import borb.frontend.RVC

object Fetch extends AreaObject {
  val addressWidth = 64
  val FETCH_SEQ = Payload(UInt(32 bits))
  val FETCH_FTQ_IDX = Payload(UInt(8 bits))
  val FETCH_PREDICTED_VALID = Payload(Bool())
  val FETCH_PREDICTED_TAKEN = Payload(Bool())
  val FETCH_PREDICTED_TARGET = Payload(UInt(addressWidth bits))
}

case class Fetch(
  cmdStage: CtrlLink,
  rspStage: CtrlLink,
  addressWidth: Int,
  dataWidth: Int,
  idWidth: Int = 16,
  withCompressed: Boolean = false,
  fetchBufferDepth: Int = 8,
  xlen: Int = 64,
  frontendConfig: FrontendConfig = FrontendConfig(addressWidth = 64, dataWidth = 64, withCompressed = true)
) extends Area {
  import Fetch._

  val ARCH_BASE = U(BigInt("80000000", 16), addressWidth bits)
  private val cfg = frontendConfig.copy(
    addressWidth = addressWidth,
    dataWidth = dataWidth,
    withCompressed = withCompressed
  )

  private val axiConfig = Axi4Config(
    addressWidth = addressWidth,
    dataWidth = dataWidth,
    idWidth = idWidth,
    useId = true,
    useRegion = false,
    useLock = false,
    useQos = false,
    useProt = false,
    useCache = false
  )

  val io = new Bundle {
    val iAxi = Axi4Shared(axiConfig)
    val flush = Bool()
    val currentEpoch = UInt(16 bits)
    val pcAdvance = Bool()
    val pcStep = UInt(3 bits)
    val vmTranslateVirt = UInt(addressWidth bits)
    val vmTranslatePhys = UInt(addressWidth bits)
    val vmTranslateEnable = Bool()
    val predictedJump = Flow(JumpCmd(addressWidth))
    val learn = Flow(BranchLearn(frontendConfig))
  }

  case class FetchRequest() extends Bundle {
    val baseAddr = UInt(addressWidth bits)
    val epoch = UInt(16 bits)
    val slotIndex = UInt(log2Up(fetchBufferDepth) bits)
    val resetQueue = Bool()
  }

  case class FtqState() extends Bundle {
    val valid = Bool()
    val history = UInt(cfg.gshareHistoryWidth bits)
    val ras = RasCheckpoint(cfg)
    val blockPc = UInt(addressWidth bits)
  }

  case class BtbEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(addressWidth bits)
    val target = UInt(addressWidth bits)
    val takenByteOffset = UInt(cfg.fetchBlockOffsetWidth bits)
    val isConditional = Bool()
    val isReturn = Bool()
    val isIndirect = Bool()
    val isCall = Bool()
    val quality = UInt(2 bits)
  }

  case class IndirectEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(cfg.indirectTagWidth bits)
    val target = UInt(addressWidth bits)
    val ctr = UInt(cfg.indirectCtrBits bits)
  }

  case class LoopEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(addressWidth bits)
    val target = UInt(addressWidth bits)
    val fallthrough = UInt(addressWidth bits)
    val tripCount = UInt(8 bits)
    val iterCount = UInt(8 bits)
    val confidence = UInt(2 bits)
  }

  case class TageEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(cfg.tageTagWidth bits)
    val ctr = UInt(cfg.tageCtrBits bits)
    val useful = UInt(cfg.tageUsefulBits bits)
  }

  case class FetchBeat() extends Bundle {
    val valid = Bool()
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
  }

  case class FetchPacket() extends Bundle {
    val valid = Bool()
    val pc = UInt(addressWidth bits)
    val insn = Bits(32 bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
    val step = UInt(3 bits)
    val seq = UInt(32 bits)
    val ftqIndex = UInt(cfg.ftqIndexWidth bits)
    val predictedValid = Bool()
    val predictedTaken = Bool()
    val predictedTarget = UInt(addressWidth bits)
  }

  val beats = Vec.fill(fetchBufferDepth)(Reg(FetchBeat()) init (FetchBeat().getZero))
  val packet = Reg(FetchPacket()) init (FetchPacket().getZero)
  val pendingReqValid = RegInit(False)
  val pendingReq = Reg(FetchRequest()) init (FetchRequest().getZero)
  val queueHead = Reg(UInt(log2Up(fetchBufferDepth) bits)) init (0)
  val queueCount = Reg(UInt(log2Up(fetchBufferDepth + 1) bits)) init (0)
  val packetValid = RegInit(False)
  val streamNextValid = RegInit(False)
  val streamNextAddr = Reg(UInt(addressWidth bits)) init (0)
  val compressedNextReqValid = RegInit(False)
  val compressedNextReqAddr = Reg(UInt(addressWidth bits)) init (0)
  val packetEnqueue = Bool()
  val packetEnqueuePc = UInt(addressWidth bits)
  val packetEnqueueInsn = Bits(32 bits)
  val packetEnqueueEpoch = UInt(16 bits)
  val packetEnqueueBeatAddr = UInt(addressWidth bits)
  val packetEnqueueStep = UInt(3 bits)
  val packetEnqueueSeq = UInt(32 bits)
  val packetEnqueueFtqIndex = UInt(cfg.ftqIndexWidth bits)
  val packetEnqueuePredictedValid = Bool()
  val packetEnqueuePredictedTaken = Bool()
  val packetEnqueuePredictedTarget = UInt(addressWidth bits)
  val packetPop = Bool()
  val packetAccepted = Bool()

  packetEnqueue.allowOverride := False
  packetEnqueuePc.allowOverride := 0
  packetEnqueueInsn.allowOverride := 0
  packetEnqueueEpoch.allowOverride := 0
  packetEnqueueBeatAddr.allowOverride := 0
  packetEnqueueStep.allowOverride := 0
  packetEnqueueSeq.allowOverride := 0
  packetEnqueueFtqIndex.allowOverride := 0
  packetEnqueuePredictedValid.allowOverride := False
  packetEnqueuePredictedTaken.allowOverride := False
  packetEnqueuePredictedTarget.allowOverride := 0
  packetPop.allowOverride := False
  packetAccepted.allowOverride := False

  val nextPacketSeq = Reg(UInt(32 bits)) init (0)
  val speculativeHistory = Reg(UInt(cfg.gshareHistoryWidth bits)) init(0)
  val committedHistory = Reg(UInt(cfg.gshareHistoryWidth bits)) init(0)
  val rasSpecSp = Reg(UInt(log2Up(cfg.rasDepth max 2) bits)) init(0)
  val rasSpecCount = Reg(UInt(log2Up(cfg.rasDepth + 1) bits)) init(0)
  val rasArchSp = Reg(UInt(log2Up(cfg.rasDepth max 2) bits)) init(0)
  val rasArchCount = Reg(UInt(log2Up(cfg.rasDepth + 1) bits)) init(0)
  val rasStack = Vec.fill(cfg.rasDepth)(Reg(UInt(addressWidth bits)) init(0))
  val ftq = Vec.fill(cfg.ftqDepth)(Reg(FtqState()) init(FtqState().getZero))
  val ftqAllocPtr = Reg(UInt(cfg.ftqIndexWidth bits)) init(0)
  val bimodalTable = Vec.fill(cfg.gshareEntries)(Reg(UInt(2 bits)) init(1))
  val tageTables = Vec.fill(cfg.tageTableCount)(Vec.fill(cfg.tageTableEntries)(Reg(TageEntry()) init(TageEntry().getZero)))
  val nanoBtb = Vec.fill(cfg.nanoBtbEntries)(Reg(BtbEntry()) init(BtbEntry().getZero))
  val ftb = Vec.fill(cfg.ftbSets)(Vec.fill(cfg.ftbWays)(Reg(BtbEntry()) init(BtbEntry().getZero)))
  val ftbReplace = Vec.fill(cfg.ftbSets)(Reg(UInt(log2Up(cfg.ftbWays max 2) bits)) init(0))
  val indirectTable = Vec.fill(cfg.indirectSets)(Vec.fill(cfg.indirectWays)(Reg(IndirectEntry()) init(IndirectEntry().getZero)))
  val indirectReplace = Vec.fill(cfg.indirectSets)(Reg(UInt(log2Up(cfg.indirectWays max 2) bits)) init(0))
  val loopTable = Vec.fill(cfg.loopPredictorEntries)(Reg(LoopEntry()) init(LoopEntry().getZero))

  val inflight = UInt(4 bits)
  inflight := pendingReqValid.asUInt.resize(4)
  val beatValid = queueCount =/= 0
  val perfPendingReq = Bool()
  val perfBeat0Valid = Bool()
  val perfBeat1Valid = Bool()
  val perfReqIssued = Bool()
  val perfRspAccepted = Bool()
  val perfNeedCurrentReq = Bool()
  val perfNeedNextReq = Bool()
  val perfPrefetchReq = Bool()
  val perfWaitCurBeat = Bool()
  val perfWaitNextBeat = Bool()
  val perfTakeInsn = Bool()
  val perfCurBeatHit = Bool()
  val perfNextBeatHit = Bool()
  val perfCmdValid = Bool()
  val perfPrefetchWindow = Bool()
  val perfPrefetchBlockedNoCmd = Bool()
  val perfPrefetchBlockedPending = Bool()
  val perfPrefetchBlockedNextHit = Bool()
  val perfLoopPredictUsed = Bool()
  val perfLoopPredictHit = Bool()
  val perfFastPredictHit = Bool()
  val perfMainPredictHit = Bool()
  val perfIndirectPredictHit = Bool()
  val perfRasUse = Bool()
  val perfRasRepair = Bool()
  val perfFtqAlloc = Bool()
  val perfFtqRestore = Bool()
  val perfPredictedRedirect = Bool()

  perfPendingReq := pendingReqValid
  perfBeat0Valid := queueCount =/= 0
  perfBeat1Valid := queueCount > 1
  perfReqIssued := False
  perfRspAccepted := False
  perfNeedCurrentReq := False
  perfNeedNextReq := False
  perfPrefetchReq := False
  perfWaitCurBeat := False
  perfWaitNextBeat := False
  perfTakeInsn := False
  perfCurBeatHit := False
  perfNextBeatHit := False
  perfCmdValid := False
  perfPrefetchWindow := False
  perfPrefetchBlockedNoCmd := False
  perfPrefetchBlockedPending := False
  perfPrefetchBlockedNextHit := False
  perfLoopPredictUsed := False
  perfLoopPredictHit := False
  perfFastPredictHit := False
  perfMainPredictHit := False
  perfIndirectPredictHit := False
  perfRasUse := False
  perfRasRepair := False
  perfFtqAlloc := False
  perfFtqRestore := False
  perfPredictedRedirect := False

  io.pcAdvance := False
  io.pcStep := U(4, 3 bits)
  io.vmTranslateVirt.allowOverride := 0
  io.predictedJump.valid := False
  io.predictedJump.payload.target := 0
  io.predictedJump.payload.is_jump := False
  io.predictedJump.payload.is_branch := False

  io.iAxi.arw.valid := False
  io.iAxi.arw.addr := 0
  io.iAxi.arw.id := 0
  io.iAxi.arw.len := 0
  io.iAxi.arw.size := log2Up(dataWidth / 8)
  io.iAxi.arw.burst := Axi4.burst.INCR
  io.iAxi.arw.write := False
  io.iAxi.w.valid := False
  io.iAxi.w.data := 0
  io.iAxi.w.strb := 0
  io.iAxi.w.last := False
  io.iAxi.b.ready := True
  io.iAxi.r.ready.allowOverride := False

  val epoch = UInt(16 bits)
  epoch := io.currentEpoch + io.flush.asUInt.resize(16)
  val activeEpoch = epoch
  val frontendTrainingEnabled = cfg.predictorTrainingEnabled
  val predictedRedirectEnabled = cfg.predictedRedirectEnabled

  def satInc(value: UInt): UInt = {
    val maxValue = U((BigInt(1) << value.getWidth) - 1, value.getWidth bits)
    Mux(value === maxValue, value, value + 1)
  }
  def satDec(value: UInt): UInt = Mux(value === 0, value, value - 1)
  def satInc2(value: UInt): UInt = satInc(value)
  def satDec2(value: UInt): UInt = satDec(value)
  def blockPc(pc: UInt): UInt = {
    val ret = UInt(addressWidth bits)
    ret := pc
    if(cfg.fetchBlockBytes > 1) ret(log2Up(cfg.fetchBlockBytes) - 1 downto 0) := 0
    ret
  }
  def nextHistory(history: UInt, taken: Bool): UInt = {
    if(cfg.gshareHistoryWidth == 1) taken.asUInt.resized
    else (history(cfg.gshareHistoryWidth - 2 downto 0).asBits ## taken.asBits).asUInt
  }
  def branchImm(inst: Bits): UInt = (inst(31) ## inst(7) ## inst(30 downto 25) ## inst(11 downto 8) ## B"0").asSInt.resize(addressWidth).asUInt
  def jalImm(inst: Bits): UInt = (inst(31) ## inst(19 downto 12) ## inst(20) ## inst(30 downto 21) ## B"0").asSInt.resize(addressWidth).asUInt
  def blockOffset(pc: UInt): UInt = (pc & U(cfg.fetchBlockBytes - 1, addressWidth bits)).resized
  def historyLengthForTable(table: Int): Int = {
    if(cfg.tageTableCount <= 1) 4
    else {
      val minHist = 4
      val maxHist = cfg.globalHistoryWidth max minHist
      minHist + (((maxHist - minHist) * table) / ((cfg.tageTableCount - 1) max 1))
    }
  }
  def historySlice(history: UInt, length: Int, width: Int): UInt = {
    val truncated = UInt(width bits)
    truncated := history(width - 1 downto 0)
    if(length <= width) truncated
    else {
      val extra = UInt(width bits)
      extra := history((length min cfg.gshareHistoryWidth) - 1 downto ((length min cfg.gshareHistoryWidth) - width max 0)).resized
      truncated ^ extra
    }
  }
  def bimodalIndex(pc: UInt): UInt = pc(cfg.bimodalIndexWidth + 1 downto 2).resized
  def tageIndex(pc: UInt, history: UInt, table: Int): UInt = {
    val width = log2Up(cfg.tageTableEntries max 2)
    val slice = historySlice(history, historyLengthForTable(table), width)
    (pc(width + 1 downto 2) ^ slice).resized
  }
  def tageTag(pc: UInt, history: UInt, table: Int): UInt = {
    val sliceA = historySlice(history, historyLengthForTable(table), cfg.tageTagWidth)
    val sliceB = historySlice(history, (historyLengthForTable(table) / 2) max 1, cfg.tageTagWidth)
    (pc(cfg.tageTagWidth + 1 downto 2) ^ sliceA ^ (sliceB |<< 1).resized).resized
  }
  def btbIndex(pc: UInt, entries: Int): UInt = pc(cfg.fetchBlockOffsetWidth + log2Up(entries max 2) - 1 downto cfg.fetchBlockOffsetWidth)
  def ftbSetIndex(pc: UInt): UInt = pc(cfg.fetchBlockOffsetWidth + cfg.ftbSetIndexWidth - 1 downto cfg.fetchBlockOffsetWidth)
  def indirectSetIndex(pc: UInt, history: UInt): UInt = {
    val width = cfg.indirectSetIndexWidth
    val hist = historySlice(history, cfg.indirectHistoryWidth, width)
    (pc(cfg.fetchBlockOffsetWidth + width - 1 downto cfg.fetchBlockOffsetWidth) ^ hist).resized
  }
  def indirectTag(pc: UInt, history: UInt): UInt = {
    val folded = historySlice(history, cfg.indirectHistoryWidth, cfg.indirectTagWidth)
    (pc(cfg.indirectTagWidth + 1 downto 2) ^ folded).resized
  }

  val replayGuardValid = RegInit(False)
  val lastTakenPc = Reg(UInt(addressWidth bits)) init (0)
  val lastTakenEpoch = Reg(UInt(16 bits)) init (0)
  val lastTakenBeatAddr = Reg(UInt(addressWidth bits)) init (0)
  when(io.flush) {
    for (slot <- beats) {
      slot.valid := False
    }
    packet.valid := False
    pendingReqValid := False
    replayGuardValid := False
    queueHead := 0
    queueCount := 0
    streamNextValid := False
    streamNextAddr := 0
    compressedNextReqValid := False
    compressedNextReqAddr := 0
    if(frontendTrainingEnabled) {
      when(io.learn.valid && io.learn.redirect.mispredict) {
        val snapshot = ftq(io.learn.redirect.ftqIndex)
        perfFtqRestore := snapshot.valid
        speculativeHistory := nextHistory(snapshot.history, io.learn.redirect.taken && io.learn.redirect.isConditional)
        rasSpecSp := snapshot.ras.sp
        rasSpecCount := snapshot.ras.count
        perfRasRepair := io.learn.redirect.isCall || io.learn.redirect.isReturn
        when(io.learn.redirect.isCall) {
          val nextSp = UInt(log2Up(cfg.rasDepth max 2) bits)
          nextSp := (snapshot.ras.sp + 1).resized
          rasStack(nextSp) := io.learn.redirect.fallthrough
          rasSpecSp := nextSp
          when(snapshot.ras.count =/= cfg.rasDepth) {
            rasSpecCount := snapshot.ras.count + 1
          }
        } elsewhen(io.learn.redirect.isReturn && (snapshot.ras.count =/= 0)) {
          rasSpecSp := (snapshot.ras.sp - 1).resized
          rasSpecCount := snapshot.ras.count - 1
        }
      } otherwise {
        speculativeHistory := committedHistory
        rasSpecSp := rasArchSp
        rasSpecCount := rasArchCount
      }
    } else {
      speculativeHistory := committedHistory
      rasSpecSp := rasArchSp
      rasSpecCount := rasArchCount
    }
    for(entry <- ftq) {
      entry.valid := False
    }
  }

  if(frontendTrainingEnabled) when(io.learn.valid) {
    val learn = io.learn.redirect
    val snapshot = ftq(learn.ftqIndex)
    val trainHistory = UInt(cfg.gshareHistoryWidth bits)
    trainHistory := Mux(snapshot.valid, snapshot.history, committedHistory)
    when(learn.isConditional) {
      val idx = bimodalIndex(learn.branchPc)
      bimodalTable(idx) := Mux(learn.taken, satInc2(bimodalTable(idx)), satDec2(bimodalTable(idx)))
      val providerHit = Bool()
      providerHit := False
      for(table <- 0 until cfg.tageTableCount) {
        val idxT = tageIndex(learn.branchPc, trainHistory, table)
        val tagT = tageTag(learn.branchPc, trainHistory, table)
        when(tageTables(table)(idxT).valid && (tageTables(table)(idxT).tag === tagT)) {
          providerHit := True
        }
      }
      for(table <- 0 until cfg.tageTableCount) {
        val idxT = tageIndex(learn.branchPc, trainHistory, table)
        val tagT = tageTag(learn.branchPc, trainHistory, table)
        when(tageTables(table)(idxT).valid && (tageTables(table)(idxT).tag === tagT)) {
          tageTables(table)(idxT).ctr := Mux(learn.taken, satInc(tageTables(table)(idxT).ctr), satDec(tageTables(table)(idxT).ctr))
          when(learn.taken === tageTables(table)(idxT).ctr.msb) {
            tageTables(table)(idxT).useful := satInc(tageTables(table)(idxT).useful)
          } otherwise {
            tageTables(table)(idxT).useful := satDec(tageTables(table)(idxT).useful)
          }
        }
      }
      when(learn.mispredict || !providerHit) {
        for(table <- 0 until cfg.tageTableCount) {
          val idxT = tageIndex(learn.branchPc, trainHistory, table)
          val tagT = tageTag(learn.branchPc, trainHistory, table)
          when(!tageTables(table)(idxT).valid || (tageTables(table)(idxT).useful === 0)) {
            tageTables(table)(idxT).valid := True
            tageTables(table)(idxT).tag := tagT
            tageTables(table)(idxT).ctr := Mux(learn.taken, U(1 << (cfg.tageCtrBits - 1), cfg.tageCtrBits bits), U((1 << (cfg.tageCtrBits - 1)) - 1, cfg.tageCtrBits bits))
            tageTables(table)(idxT).useful := U(0, cfg.tageUsefulBits bits)
          }
        }
      }
      committedHistory := nextHistory(committedHistory, learn.taken)
    }

    val mainSet = ftbSetIndex(learn.blockPc)
    val ftbHit = Bool()
    ftbHit := False
    val ftbHitWay = UInt(log2Up(cfg.ftbWays max 2) bits)
    ftbHitWay := 0
    for(way <- 0 until cfg.ftbWays) {
      when(ftb(mainSet)(way).valid && (ftb(mainSet)(way).tag === learn.blockPc)) {
        ftbHit := True
        ftbHitWay := way
      }
    }
    val ftbWriteWay = UInt(log2Up(cfg.ftbWays max 2) bits)
    ftbWriteWay := ftbReplace(mainSet)
    when(ftbHit) {
      ftbWriteWay := ftbHitWay
    }
    ftb(mainSet)(ftbWriteWay).valid := True
    ftb(mainSet)(ftbWriteWay).tag := learn.blockPc
    ftb(mainSet)(ftbWriteWay).target := learn.target
    ftb(mainSet)(ftbWriteWay).takenByteOffset := learn.takenByteOffset
    ftb(mainSet)(ftbWriteWay).isConditional := learn.isConditional
    ftb(mainSet)(ftbWriteWay).isReturn := learn.isReturn
    ftb(mainSet)(ftbWriteWay).isIndirect := learn.isIndirect
    ftb(mainSet)(ftbWriteWay).isCall := learn.isCall
    ftb(mainSet)(ftbWriteWay).quality := Mux(learn.mispredict, U(1, 2 bits), satInc2(ftb(mainSet)(ftbWriteWay).quality))
    when(!ftbHit) {
      ftbReplace(mainSet) := ftbReplace(mainSet) + 1
    }

    when(learn.taken || learn.isConditional) {
      val l0Idx = btbIndex(learn.blockPc, cfg.nanoBtbEntries)
      nanoBtb(l0Idx).valid := True
      nanoBtb(l0Idx).tag := learn.blockPc
      nanoBtb(l0Idx).target := learn.target
      nanoBtb(l0Idx).takenByteOffset := learn.takenByteOffset
      nanoBtb(l0Idx).isConditional := learn.isConditional
      nanoBtb(l0Idx).isReturn := learn.isReturn
      nanoBtb(l0Idx).isIndirect := learn.isIndirect
      nanoBtb(l0Idx).isCall := learn.isCall
      nanoBtb(l0Idx).quality := Mux(learn.mispredict, U(1, 2 bits), satInc2(nanoBtb(l0Idx).quality))
    }

    when(learn.isIndirect && learn.taken) {
      val indSet = indirectSetIndex(learn.blockPc, trainHistory)
      val indTag = indirectTag(learn.blockPc, trainHistory)
      val indHit = Bool()
      indHit := False
      val indWay = UInt(log2Up(cfg.indirectWays max 2) bits)
      indWay := 0
      for(way <- 0 until cfg.indirectWays) {
        when(indirectTable(indSet)(way).valid && (indirectTable(indSet)(way).tag === indTag)) {
          indHit := True
          indWay := way
        }
      }
      val indWriteWay = UInt(log2Up(cfg.indirectWays max 2) bits)
      indWriteWay := indirectReplace(indSet)
      when(indHit) {
        indWriteWay := indWay
      }
      indirectTable(indSet)(indWriteWay).valid := True
      indirectTable(indSet)(indWriteWay).tag := indTag
      indirectTable(indSet)(indWriteWay).target := learn.target
      indirectTable(indSet)(indWriteWay).ctr := Mux(indHit && (indirectTable(indSet)(indWriteWay).target === learn.target), satInc(indirectTable(indSet)(indWriteWay).ctr), U(1, cfg.indirectCtrBits bits))
      when(!indHit) {
        indirectReplace(indSet) := indirectReplace(indSet) + 1
      }
    }

    if(cfg.loopPredictorActive) when(learn.isConditional && (learn.target < learn.fallthrough)) {
      val loopIdx = btbIndex(learn.blockPc, cfg.loopPredictorEntries)
      val loopTagValue = learn.blockPc
      when(!loopTable(loopIdx).valid || (loopTable(loopIdx).tag =/= loopTagValue)) {
        loopTable(loopIdx).valid := True
        loopTable(loopIdx).tag := loopTagValue
        loopTable(loopIdx).target := learn.target
        loopTable(loopIdx).fallthrough := learn.fallthrough
        loopTable(loopIdx).tripCount := U(0, 8 bits)
        loopTable(loopIdx).iterCount := U(0, 8 bits)
        loopTable(loopIdx).confidence := U(0, 2 bits)
      }
      loopTable(loopIdx).target := learn.target
      loopTable(loopIdx).fallthrough := learn.fallthrough
      when(learn.taken) {
        when(loopTable(loopIdx).iterCount =/= U(255, 8 bits)) {
          loopTable(loopIdx).iterCount := loopTable(loopIdx).iterCount + 1
        }
      } otherwise {
        when(loopTable(loopIdx).iterCount =/= 0) {
          when(loopTable(loopIdx).tripCount === loopTable(loopIdx).iterCount) {
            loopTable(loopIdx).confidence := satInc2(loopTable(loopIdx).confidence)
          } otherwise {
            loopTable(loopIdx).tripCount := loopTable(loopIdx).iterCount
            loopTable(loopIdx).confidence := U(1, 2 bits)
          }
        }
        loopTable(loopIdx).iterCount := U(0, 8 bits)
      }
    }

    when(learn.isCall) {
      val nextSp = UInt(log2Up(cfg.rasDepth max 2) bits)
      nextSp := (rasArchSp + 1).resized
      rasStack(nextSp) := learn.fallthrough
      rasArchSp := nextSp
      when(rasArchCount =/= cfg.rasDepth) {
        rasArchCount := rasArchCount + 1
      }
    } elsewhen(learn.isReturn && (rasArchCount =/= 0)) {
      rasArchSp := (rasArchSp - 1).resized
      rasArchCount := rasArchCount - 1
    }
  }

  def beatHit(slot: FetchBeat, addr: UInt): Bool = {
    slot.valid && (slot.epoch === activeEpoch) && (slot.beatAddr === addr)
  }

  def hitVec(addr: UInt): Bits = {
    val hits = Bits(fetchBufferDepth bits)
    for (i <- 0 until fetchBufferDepth) {
      hits(i) := beatHit(beats(i), addr)
    }
    hits
  }

  def wrapIndex(base: UInt, offset: UInt): UInt = {
    val sum = UInt((log2Up(fetchBufferDepth) + 1) bits)
    sum := base.resize(sum.getWidth) + offset.resize(sum.getWidth)
    val wrapped = UInt(log2Up(fetchBufferDepth) bits)
    if (isPow2(fetchBufferDepth)) {
      wrapped := sum(log2Up(fetchBufferDepth) - 1 downto 0)
    } else {
      wrapped := (sum >= fetchBufferDepth) ? (sum - fetchBufferDepth).resized | sum.resized
    }
    wrapped
  }

  def selectBeatData(hits: Bits): Bits = {
    val data = Bits(dataWidth bits)
    data := beats(0).data
    for (i <- 0 until fetchBufferDepth) {
      when(hits(i)) {
        data := beats(i).data
      }
    }
    data
  }

  def selectBeatEpoch(hits: Bits): UInt = {
    val value = UInt(16 bits)
    value := beats(0).epoch
    for (i <- 0 until fetchBufferDepth) {
      when(hits(i)) {
        value := beats(i).epoch
      }
    }
    value
  }

  def selectBeatAddr(hits: Bits): UInt = {
    val value = UInt(addressWidth bits)
    value := beats(0).beatAddr
    for (i <- 0 until fetchBufferDepth) {
      when(hits(i)) {
        value := beats(i).beatAddr
      }
    }
    value
  }

  def anyResident(addr: UInt): Bool = hitVec(addr).orR

  def selectHalfword(data: Bits, index: UInt): Bits = {
    index.mux(
      U(0) -> data(15 downto 0),
      U(1) -> data(31 downto 16),
      U(2) -> data(47 downto 32),
      default -> data(63 downto 48)
    )
  }

  val cmdArea = new cmdStage.Area {
    val bootSeedActive = (nextPacketSeq === 0) && !packetValid && !pendingReqValid && (queueCount === 0)
    val cmdPcRaw = UInt(addressWidth bits)
    cmdPcRaw := bootSeedActive ? ARCH_BASE | cmdStage(PC.PC)
    val pcBeatAddr = UInt(addressWidth bits)
    pcBeatAddr := cmdPcRaw
    pcBeatAddr(2 downto 0) := 0

    val curHits = hitVec(pcBeatAddr)
    val curHit = curHits.orR
    val curData = selectBeatData(curHits)

    val nextBeatAddr = pcBeatAddr + U(8, addressWidth bits)
    val nextHits = hitVec(nextBeatAddr)
    val nextHit = nextHits.orR

    val hwIndex = cmdPcRaw(2 downto 1)
    val first16 = selectHalfword(curData, hwIndex)
    val curRvc = RVC(first16, xlen = xlen)
    val curIsCompressed = if (withCompressed) {
      curHit && (first16(1 downto 0) =/= B"11")
    } else {
      False
    }
    val curDecodedOpcode = curRvc.inst(6 downto 0)
    val curCompressedControl = if (withCompressed) {
      curIsCompressed &&
      !curRvc.illegal &&
      ((curDecodedOpcode === B"7'b1101111") ||
        (curDecodedOpcode === B"7'b1100111") ||
        (curDecodedOpcode === B"7'b1100011") ||
        (curDecodedOpcode === B"7'b1110011"))
    } else {
      False
    }
    val needStraddleBeat = if (withCompressed) {
      curHit && (hwIndex === U(3)) && (first16(1 downto 0) === B"11")
    } else {
      False
    }

    val assembleHits = curHits
    val assembleCurValid = curHit
    val assembleCurData = curData
    val assembleSrcEpoch = selectBeatEpoch(assembleHits)
    val assembleSrcBeatAddr = selectBeatAddr(assembleHits)
    val assembleNextHits = nextHits
    val assembleNextValid = nextHit
    val assembleNextData = selectBeatData(assembleNextHits)
    val assembleHwIndex = hwIndex
    val assembleFirst16 = first16
    val assembleNeeds32 = if (withCompressed) assembleFirst16(1 downto 0) === B"11" else True
    val assembleStraddle = assembleNeeds32 && (assembleHwIndex === U(3))
    val waitingSecond = if (withCompressed) assembleCurValid && assembleStraddle && !assembleNextValid else False

    val assembledInsn = Bits(32 bits)
    assembledInsn := B"32'h00000013"
    if (withCompressed) {
      when(!assembleNeeds32) {
        assembledInsn := B"16'h0000" ## assembleFirst16
      } otherwise {
        switch(assembleHwIndex) {
          is(U(0)) { assembledInsn := assembleCurData(31 downto 16) ## assembleCurData(15 downto 0) }
          is(U(1)) { assembledInsn := assembleCurData(47 downto 32) ## assembleCurData(31 downto 16) }
          is(U(2)) { assembledInsn := assembleCurData(63 downto 48) ## assembleCurData(47 downto 32) }
          default { assembledInsn := assembleNextData(15 downto 0) ## assembleCurData(63 downto 48) }
        }
      }
    } else {
      assembledInsn := Mux(cmdStage(PC.PC)(2), assembleCurData(63 downto 32), assembleCurData(31 downto 0))
    }

    val packetHasSpace = !packetValid || packetAccepted
    val duplicatePc = assembleCurValid &&
      replayGuardValid &&
      (cmdPcRaw === lastTakenPc) &&
      (assembleSrcEpoch === lastTakenEpoch) &&
      (assembleSrcBeatAddr === lastTakenBeatAddr)
    when(replayGuardValid && (cmdPcRaw =/= lastTakenPc)) {
      replayGuardValid := False
    }

    val takenStep = UInt(addressWidth bits)
    if (withCompressed) {
      takenStep := assembleNeeds32 ? U(4, addressWidth bits) | U(2, addressWidth bits)
    } else {
      takenStep := U(4, addressWidth bits)
    }
    val packetReady = assembleCurValid && !waitingSecond
    val canEnqueuePacket = packetReady && !duplicatePc && packetHasSpace
    val decodedForPredict = Bits(32 bits)
    decodedForPredict := assembledInsn
    if(withCompressed) {
      when(!assembleNeeds32) {
        decodedForPredict := curRvc.inst
      }
    }
    val opcode = decodedForPredict(6 downto 0)
    val isConditional = opcode === B"7'b1100011"
    val isJal = opcode === B"7'b1101111"
    val isJalr = opcode === B"7'b1100111"
    val rdAddr = decodedForPredict(11 downto 7)
    val rs1Addr = decodedForPredict(19 downto 15)
    val isCall = (isJal || isJalr) && ((rdAddr === B"5'b00001") || (rdAddr === B"5'b00101"))
    val isReturn = isJalr && (rdAddr === B"5'b00000") && ((rs1Addr === B"5'b00001") || (rs1Addr === B"5'b00101"))
    val isIndirect = isJalr && !isReturn
    val thisBlockPc = blockPc(cmdPcRaw)
    val thisBlockOffset = blockOffset(cmdPcRaw)
    val bimodalIdx = bimodalIndex(cmdPcRaw)
    val baseTaken = bimodalTable(bimodalIdx) >= 2
    val tageProviderValid = Bool()
    val tageProviderCtr = UInt(cfg.tageCtrBits bits)
    tageProviderValid := False
    tageProviderCtr := U(1 << (cfg.tageCtrBits - 1), cfg.tageCtrBits bits)
    for(table <- 0 until cfg.tageTableCount) {
      val idxT = tageIndex(cmdPcRaw, speculativeHistory, table)
      val tagT = tageTag(cmdPcRaw, speculativeHistory, table)
      when(tageTables(table)(idxT).valid && (tageTables(table)(idxT).tag === tagT)) {
        tageProviderValid := True
        tageProviderCtr := tageTables(table)(idxT).ctr
      }
    }
    val directionTaken = Bool()
    directionTaken := Mux(tageProviderValid, tageProviderCtr.msb, baseTaken)
    val l0Idx = btbIndex(thisBlockPc, cfg.nanoBtbEntries)
    val l0Hit = nanoBtb(l0Idx).valid && (nanoBtb(l0Idx).tag === thisBlockPc)
    val mainSet = ftbSetIndex(thisBlockPc)
    val mainHit = Bool()
    val mainTarget = UInt(addressWidth bits)
    val mainTakenOffset = UInt(cfg.fetchBlockOffsetWidth bits)
    val mainIsReturn = Bool()
    val mainIsConditional = Bool()
    mainHit := False
    mainTarget := 0
    mainTakenOffset := 0
    mainIsReturn := False
    mainIsConditional := False
    for(way <- 0 until cfg.ftbWays) {
      when(ftb(mainSet)(way).valid && (ftb(mainSet)(way).tag === thisBlockPc)) {
        mainHit := True
        mainTarget := ftb(mainSet)(way).target
        mainTakenOffset := ftb(mainSet)(way).takenByteOffset
        mainIsReturn := ftb(mainSet)(way).isReturn
        mainIsConditional := ftb(mainSet)(way).isConditional
      }
    }
    val loopIdx = btbIndex(thisBlockPc, cfg.loopPredictorEntries)
    val loopHit = if(cfg.loopPredictorActive) (loopTable(loopIdx).valid && (loopTable(loopIdx).tag === thisBlockPc)) else False
    val indSet = indirectSetIndex(thisBlockPc, speculativeHistory)
    val indTag = indirectTag(thisBlockPc, speculativeHistory)
    val indHit = Bool()
    val indTarget = UInt(addressWidth bits)
    val indStrong = Bool()
    indHit := False
    indTarget := 0
    indStrong := False
    for(way <- 0 until cfg.indirectWays) {
      when(indirectTable(indSet)(way).valid && (indirectTable(indSet)(way).tag === indTag)) {
        indHit := True
        indTarget := indirectTable(indSet)(way).target
        indStrong := indirectTable(indSet)(way).ctr.msb
      }
    }
    val directTarget = UInt(addressWidth bits)
    directTarget := cmdPcRaw + branchImm(decodedForPredict).asSInt.asUInt
    when(isJal) {
      directTarget := cmdPcRaw + jalImm(decodedForPredict).asSInt.asUInt
    }
    val predictedValid = Bool()
    val predictedTaken = Bool()
    val predictedTarget = UInt(addressWidth bits)
    val loopPredicted = Bool()
    val indirectProvided = Bool()
    val fastPredictHit = Bool()
    val mainPredictHit = Bool()
    val rasUsed = Bool()
    predictedValid := False
    predictedTaken := False
    predictedTarget := 0
    loopPredicted := False
    indirectProvided := False
    fastPredictHit := False
    mainPredictHit := False
    rasUsed := False

    if(predictedRedirectEnabled) when(assembleCurValid && !waitingSecond) {
      when(loopHit && isConditional && (loopTable(loopIdx).target < loopTable(loopIdx).fallthrough) && (loopTable(loopIdx).confidence =/= 0) && (loopTable(loopIdx).tripCount =/= 0)) {
        predictedValid := True
        loopPredicted := True
        mainPredictHit := True
        when((loopTable(loopIdx).iterCount + 1) < loopTable(loopIdx).tripCount) {
          predictedTaken := True
          predictedTarget := loopTable(loopIdx).target
        } otherwise {
          predictedTaken := False
          predictedTarget := loopTable(loopIdx).fallthrough
        }
      } elsewhen(isConditional && mainHit && (mainTakenOffset === thisBlockOffset)) {
        predictedValid := True
        predictedTaken := directionTaken
        predictedTarget := mainTarget
        mainPredictHit := True
      } elsewhen(isJal) {
        predictedValid := True
        predictedTaken := True
        predictedTarget := directTarget
      } elsewhen(isReturn && (rasSpecCount =/= 0)) {
        predictedValid := True
        predictedTaken := True
        predictedTarget := rasStack(rasSpecSp)
        fastPredictHit := True
        rasUsed := True
      } elsewhen(isIndirect && indHit && indStrong) {
        predictedValid := True
        predictedTaken := True
        predictedTarget := indTarget
        indirectProvided := True
        mainPredictHit := True
      } elsewhen(l0Hit && (nanoBtb(l0Idx).takenByteOffset === thisBlockOffset)) {
        predictedValid := True
        predictedTaken := !nanoBtb(l0Idx).isConditional || directionTaken
        predictedTarget := Mux(nanoBtb(l0Idx).isReturn && (rasSpecCount =/= 0), rasStack(rasSpecSp), nanoBtb(l0Idx).target)
        fastPredictHit := True
        rasUsed := nanoBtb(l0Idx).isReturn && (rasSpecCount =/= 0)
      }
    }

    val issueAddr = UInt(addressWidth bits)
    issueAddr := pcBeatAddr
    val hasFreeSlot = queueCount =/= fetchBufferDepth
    val queueTailIndex = wrapIndex(queueHead, queueCount.resized)
    val queueHeadBeatAddr = UInt(addressWidth bits)
    queueHeadBeatAddr := pcBeatAddr
    when(queueCount =/= 0) {
      queueHeadBeatAddr := beats(queueHead).beatAddr
    }
    val streamHits = hitVec(streamNextAddr)
    val streamHit = streamHits.orR
    val issueSlotIndex = UInt(log2Up(fetchBufferDepth) bits)
    issueSlotIndex := 0
    val resetQueueOnRsp = Bool()
    resetQueueOnRsp := False

    val needCurrentRequest = cmdStage.up.isValid && !curHit && !pendingReqValid && !io.flush
    val needNextRequest = curHit && needStraddleBeat && !nextHit && !pendingReqValid && !io.flush && hasFreeSlot
    val compressedPrefetchWindow = curIsCompressed &&
      !curRvc.illegal &&
      !curCompressedControl &&
      (hwIndex === U(2) || hwIndex === U(3)) &&
      !nextHit &&
      !needStraddleBeat &&
      hasFreeSlot &&
      !io.flush
    val compressedPrefetchTracked = compressedNextReqValid && (compressedNextReqAddr === nextBeatAddr)
    val streamPrefetchWindow = streamNextValid &&
      hasFreeSlot &&
      curHit &&
      !curIsCompressed &&
      !io.flush
    val useCompressedPrefetch = compressedPrefetchWindow &&
      !pendingReqValid &&
      !needCurrentRequest &&
      !needNextRequest &&
      !compressedPrefetchTracked
    val useStreamPrefetch = streamPrefetchWindow &&
      !pendingReqValid &&
      !needCurrentRequest &&
      !needNextRequest &&
      !streamHit
    val prefetchWindow = compressedPrefetchWindow || streamPrefetchWindow
    val usePrefetch = useCompressedPrefetch || useStreamPrefetch

    when(needCurrentRequest) {
      issueAddr := pcBeatAddr
      issueSlotIndex := 0
      resetQueueOnRsp := True
    }

    when(needNextRequest || usePrefetch) {
      issueAddr := useCompressedPrefetch ? nextBeatAddr | streamNextAddr
      issueSlotIndex := queueTailIndex
    }

    val issueResident = anyResident(issueAddr)
    val issuePending = pendingReqValid && (pendingReq.baseAddr === issueAddr)
    val needRequest = (needCurrentRequest || needNextRequest || usePrefetch) && !issueResident && !issuePending
    val translatedIssueAddr = UInt(addressWidth bits)
    translatedIssueAddr := issueAddr
    when(io.vmTranslateEnable) {
      translatedIssueAddr := io.vmTranslatePhys
    }
    io.vmTranslateVirt.allowOverride := issueAddr
    perfNeedCurrentReq.allowOverride := needCurrentRequest
    perfNeedNextReq.allowOverride := needNextRequest
    perfPrefetchReq.allowOverride := usePrefetch
    perfCurBeatHit.allowOverride := curHit
    perfNextBeatHit.allowOverride := nextHit
    perfWaitCurBeat.allowOverride := !assembleCurValid
    perfWaitNextBeat.allowOverride := waitingSecond
    perfCmdValid.allowOverride := cmdStage.up.isValid
    perfPrefetchWindow.allowOverride := prefetchWindow
    perfPrefetchBlockedNoCmd.allowOverride := False
    perfPrefetchBlockedPending.allowOverride := prefetchWindow && pendingReqValid
    perfPrefetchBlockedNextHit.allowOverride := streamPrefetchWindow && streamHit && !io.flush

    io.iAxi.arw.valid.allowOverride := needRequest
    io.iAxi.arw.addr.allowOverride := translatedIssueAddr
    io.iAxi.arw.id.allowOverride := activeEpoch.resized
    io.iAxi.arw.len.allowOverride := 0

    val packetQueueBlocks = packetReady && !duplicatePc && !packetHasSpace && !io.flush
    haltWhen((needCurrentRequest || needNextRequest) && !io.iAxi.arw.fire)
    haltWhen(packetQueueBlocks)

    when(io.iAxi.arw.fire) {
      perfReqIssued := True
      pendingReqValid := True
      pendingReq.baseAddr := issueAddr
      pendingReq.epoch := activeEpoch
      pendingReq.slotIndex := issueSlotIndex
      pendingReq.resetQueue := resetQueueOnRsp
      when(useCompressedPrefetch) {
        compressedNextReqValid := True
        compressedNextReqAddr := nextBeatAddr
      }
      when(useStreamPrefetch) {
        streamNextAddr := streamNextAddr + U(8, addressWidth bits)
      }
    }

    when(canEnqueuePacket && !io.flush) {
      val ftqIdx = ftqAllocPtr
      packetEnqueue.allowOverride := True
      packetEnqueuePc.allowOverride := cmdPcRaw
      packetEnqueueInsn.allowOverride := assembledInsn
      packetEnqueueEpoch.allowOverride := assembleSrcEpoch
      packetEnqueueBeatAddr.allowOverride := assembleSrcBeatAddr
      packetEnqueueStep.allowOverride := takenStep.resize(3)
      packetEnqueueSeq.allowOverride := nextPacketSeq
      packetEnqueueFtqIndex.allowOverride := ftqIdx
      packetEnqueuePredictedValid.allowOverride := predictedValid
      packetEnqueuePredictedTaken.allowOverride := predictedTaken
      packetEnqueuePredictedTarget.allowOverride := predictedTarget

      perfTakeInsn := True
      perfLoopPredictUsed := loopPredicted
      perfLoopPredictHit := loopPredicted && predictedTaken
      perfFastPredictHit := fastPredictHit
      perfMainPredictHit := mainPredictHit
      perfIndirectPredictHit := indirectProvided
      perfRasUse := rasUsed
      perfFtqAlloc := True
      replayGuardValid := True
      lastTakenPc := cmdPcRaw
      lastTakenEpoch := assembleSrcEpoch
      lastTakenBeatAddr := assembleSrcBeatAddr
      io.pcAdvance := True
      io.pcStep := takenStep.resize(3)
      nextPacketSeq := nextPacketSeq + 1
      ftq(ftqIdx).valid := True
      ftq(ftqIdx).history := speculativeHistory
      ftq(ftqIdx).ras.sp := rasSpecSp
      ftq(ftqIdx).ras.count := rasSpecCount
      ftq(ftqIdx).blockPc := thisBlockPc
      ftqAllocPtr := ftqAllocPtr + 1

      if(predictedRedirectEnabled) when(predictedValid && predictedTaken) {
        io.predictedJump.valid := True
        io.predictedJump.payload.target := predictedTarget
        io.predictedJump.payload.is_jump := !isConditional
        io.predictedJump.payload.is_branch := isConditional
        perfPredictedRedirect := True
        when(isConditional) {
          speculativeHistory := nextHistory(speculativeHistory, True)
        }
        when(isCall) {
          val nextSp = UInt(log2Up(cfg.rasDepth max 2) bits)
          nextSp := (rasSpecSp + 1).resized
          rasStack(nextSp) := cmdPcRaw + takenStep
          rasSpecSp := nextSp
          when(rasSpecCount =/= cfg.rasDepth) {
            rasSpecCount := rasSpecCount + 1
          }
        } elsewhen(isReturn && (rasSpecCount =/= 0)) {
          rasSpecSp := (rasSpecSp - 1).resized
          rasSpecCount := rasSpecCount - 1
        }
      } elsewhen(predictedValid && !predictedTaken && isConditional) {
        speculativeHistory := nextHistory(speculativeHistory, False)
      }

      val nextPc = cmdPcRaw + takenStep
      val nextPcBeatAddr = UInt(addressWidth bits)
      nextPcBeatAddr := nextPc
      nextPcBeatAddr(2 downto 0) := 0
      when(compressedNextReqValid && (compressedNextReqAddr < nextPcBeatAddr)) {
        compressedNextReqValid := False
      }
      when(queueCount =/= 0 && beats(queueHead).valid && (beats(queueHead).epoch === assembleSrcEpoch) && (beats(queueHead).beatAddr < nextPcBeatAddr)) {
        beats(queueHead).valid := False
        queueHead := wrapIndex(queueHead, U(1))
        queueCount := queueCount - 1
      }
    }
  }

  val rspArea = new rspStage.Area {
    for (i <- 0 until fetchBufferDepth) {
      when(beats(i).valid && (beats(i).epoch =/= activeEpoch)) {
        beats(i).valid := False
      }
    }
    val headPacket = packet

    rspStage.up.valid := packetValid
    rspStage.up(PC.PC).allowOverride := headPacket.pc
    rspStage.up(INSTRUCTION).allowOverride := headPacket.insn
    rspStage.up(SPEC_EPOCH).allowOverride := headPacket.epoch
    rspStage.up(Fetch.FETCH_SEQ).allowOverride := headPacket.seq
    rspStage.up(FETCH_FTQ_IDX).allowOverride := headPacket.ftqIndex.resized
    rspStage.up(FETCH_PREDICTED_VALID).allowOverride := headPacket.predictedValid
    rspStage.up(FETCH_PREDICTED_TAKEN).allowOverride := headPacket.predictedTaken
    rspStage.up(FETCH_PREDICTED_TARGET).allowOverride := headPacket.predictedTarget
    packetAccepted.allowOverride := packetValid && rspStage.up.isFiring
    packetPop.allowOverride := packetAccepted
  }
  when(packetEnqueue) {
    packet.valid := True
    packet.pc := packetEnqueuePc
    packet.insn := packetEnqueueInsn
    packet.epoch := packetEnqueueEpoch
    packet.beatAddr := packetEnqueueBeatAddr
    packet.step := packetEnqueueStep
    packet.seq := packetEnqueueSeq
    packet.ftqIndex := packetEnqueueFtqIndex
    packet.predictedValid := packetEnqueuePredictedValid
    packet.predictedTaken := packetEnqueuePredictedTaken
    packet.predictedTarget := packetEnqueuePredictedTarget
  }
  packetValid := Mux(io.flush, False, (packetValid && !packetPop) || packetEnqueue)

  when(io.iAxi.r.fire) {
    perfRspAccepted := True
    val rspResident = anyResident(pendingReq.baseAddr)
    when(pendingReq.resetQueue) {
      for (slot <- beats) {
        slot.valid := False
      }
      queueHead := 0
      queueCount := 1
      streamNextValid := True
      streamNextAddr := pendingReq.baseAddr + U(8, addressWidth bits)
      compressedNextReqValid := False
    } otherwise {
      when(!rspResident && queueCount =/= fetchBufferDepth) {
        queueCount := queueCount + 1
      }
      streamNextValid := True
      streamNextAddr := pendingReq.baseAddr + U(8, addressWidth bits)
    }
    when(compressedNextReqValid && (compressedNextReqAddr === pendingReq.baseAddr)) {
      compressedNextReqValid := False
    }
    when(!rspResident || pendingReq.resetQueue) {
      beats(pendingReq.slotIndex).valid := True
      beats(pendingReq.slotIndex).data := io.iAxi.r.data
      beats(pendingReq.slotIndex).epoch := pendingReq.epoch
      beats(pendingReq.slotIndex).beatAddr := pendingReq.baseAddr
    }
    pendingReqValid := False
  }

  io.iAxi.r.ready.allowOverride := pendingReqValid
}
