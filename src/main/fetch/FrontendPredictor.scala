package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendPredictor(config: FrontendConfig) extends Component {
  val io = new Bundle {
    val active = slave(Flow(FrontendPcState(config.addressWidth, config.epochWidth)))
    val accepted = slave(Flow(FrontendAcceptedTraversal(config)))
    val branchResolve = slave(Flow(BranchResolveUpdate(config)))
    val indirectResolve = slave(Flow(IndirectResolveUpdate(config)))
    val recover = slave(Flow(FrontendRecoverUpdate(config)))
    val traversal = out(TraversalRsp(config))
  }

  case class BtbEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(config.addressWidth bits)
    val target = UInt(config.addressWidth bits)
    val takenByteOffset = UInt(config.fetchBlockOffsetWidth bits)
    val isConditional = Bool()
    val isReturn = Bool()
    val isIndirect = Bool()
    val isCall = Bool()
    val quality = UInt(2 bits)
  }

  case class IndirectEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(config.indirectTagWidth bits)
    val target = UInt(config.addressWidth bits)
    val ctr = UInt(config.indirectCtrBits bits)
  }

  case class LoopEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(config.addressWidth bits)
    val target = UInt(config.addressWidth bits)
    val fallthrough = UInt(config.addressWidth bits)
    val tripCount = UInt(8 bits)
    val iterCount = UInt(8 bits)
    val confidence = UInt(2 bits)
  }

  val speculativeHistory = Reg(UInt(config.gshareHistoryWidth bits)) init(0)
  val committedHistory = Reg(UInt(config.gshareHistoryWidth bits)) init(0)
  val rasSpecSp = Reg(UInt(log2Up(config.rasDepth max 2) bits)) init(0)
  val rasSpecCount = Reg(UInt(log2Up(config.rasDepth + 1) bits)) init(0)
  val rasArchSp = Reg(UInt(log2Up(config.rasDepth max 2) bits)) init(0)
  val rasArchCount = Reg(UInt(log2Up(config.rasDepth + 1) bits)) init(0)
  val rasStack = Vec.fill(config.rasDepth)(Reg(UInt(config.addressWidth bits)) init(0))
  val ftq = Vec.fill(config.ftqDepth)(Reg(FtqEntry(config)) init(FtqEntry(config).getZero))
  val ftqAllocPtr = Reg(UInt(config.ftqIndexWidth bits)) init(0)
  val bimodalTable = Vec.fill(config.gshareEntries)(Reg(UInt(2 bits)) init(1))
  val nanoBtb = Vec.fill(config.nanoBtbEntries)(Reg(BtbEntry()) init(BtbEntry().getZero))
  val ftb = Vec.fill(config.ftbSets)(Vec.fill(config.ftbWays)(Reg(BtbEntry()) init(BtbEntry().getZero)))
  val ftbReplace = Vec.fill(config.ftbSets)(Reg(UInt(log2Up(config.ftbWays max 2) bits)) init(0))
  val indirectTable = Vec.fill(config.indirectSets)(Vec.fill(config.indirectWays)(Reg(IndirectEntry()) init(IndirectEntry().getZero)))
  val indirectReplace = Vec.fill(config.indirectSets)(Reg(UInt(log2Up(config.indirectWays max 2) bits)) init(0))
  val loopTable = Vec.fill(config.loopPredictorEntries)(Reg(LoopEntry()) init(LoopEntry().getZero))

  def satInc(value: UInt): UInt = {
    val maxValue = U((BigInt(1) << value.getWidth) - 1, value.getWidth bits)
    Mux(value === maxValue, value, value + 1)
  }

  def satDec(value: UInt): UInt = Mux(value === 0, value, value - 1)

  def alignedBlock(address: UInt): UInt = {
    val ret = UInt(config.addressWidth bits)
    ret := address
    if(config.fetchBlockBytes > 1) {
      ret(config.fetchBlockOffsetWidth - 1 downto 0) := 0
    }
    ret
  }

  def nextHistory(history: UInt, taken: Bool): UInt = {
    if(config.gshareHistoryWidth == 1) taken.asUInt.resized
    else (history(config.gshareHistoryWidth - 2 downto 0).asBits ## taken.asBits).asUInt
  }

  def bimodalIndex(pc: UInt): UInt = pc(config.bimodalIndexWidth + 1 downto 2).resized

  def btbIndex(pc: UInt, entries: Int): UInt = {
    pc(config.fetchBlockOffsetWidth + log2Up(entries max 2) - 1 downto config.fetchBlockOffsetWidth)
  }

  def ftbSetIndex(pc: UInt): UInt = {
    pc(config.fetchBlockOffsetWidth + config.ftbSetIndexWidth - 1 downto config.fetchBlockOffsetWidth)
  }

  def indirectSetIndex(pc: UInt, history: UInt): UInt = {
    val width = config.indirectSetIndexWidth
    val hist = history(width - 1 downto 0).resized
    (pc(config.fetchBlockOffsetWidth + width - 1 downto config.fetchBlockOffsetWidth) ^ hist).resized
  }

  def indirectTag(pc: UInt, history: UInt): UInt = {
    (pc(config.indirectTagWidth + 1 downto 2) ^ history(config.indirectTagWidth - 1 downto 0)).resized
  }

  def wrapFtq(base: UInt, offset: Int): UInt = {
    if(config.ftqDepth == 1) {
      U(0, config.ftqIndexWidth bits)
    } else {
      (base + U(offset, config.ftqIndexWidth bits)).resized
    }
  }

  def blockPrediction(blockPc: UInt, history: UInt, ras: RasCheckpoint, ftqIndex: UInt): TraversalBlockDescriptor = {
    val desc = TraversalBlockDescriptor(config)
    desc.valid := True
    desc.ftqIndex := ftqIndex
    desc.blockPc := blockPc
    desc.needLookup := True
    desc.predictedTaken := False
    desc.target := 0
    desc.targetKind := FrontendTargetKind.none
    desc.takenByteOffset := 0
    desc.fallthrough := blockPc + U(config.fetchBlockBytes, config.addressWidth bits)
    desc.checkpoint.history := history
    desc.checkpoint.ras := ras
    desc.isConditional := False
    desc.isCall := False
    desc.isReturn := False
    desc.isIndirect := False
    desc.loopPredicted := False
    desc.indirectProvided := False
    desc.rasUsed := False

    val l0Idx = btbIndex(blockPc, config.nanoBtbEntries)
    val l0Hit = nanoBtb(l0Idx).valid && (nanoBtb(l0Idx).tag === blockPc)
    val mainSet = ftbSetIndex(blockPc)
    val mainHit = Bool()
    val mainWay = UInt(log2Up(config.ftbWays max 2) bits)
    mainHit := False
    mainWay := 0
    for(way <- 0 until config.ftbWays) {
      when(ftb(mainSet)(way).valid && (ftb(mainSet)(way).tag === blockPc)) {
        mainHit := True
        mainWay := way
      }
    }

    val useMain = mainHit
    val baseEntry = BtbEntry()
    baseEntry := nanoBtb(l0Idx)
    when(useMain) {
      baseEntry := ftb(mainSet)(mainWay)
    }

    val branchPc = blockPc + baseEntry.takenByteOffset.resize(config.addressWidth bits)
    val directionTaken = bimodalTable(bimodalIndex(branchPc)) >= 2
    val loopIdx = btbIndex(blockPc, config.loopPredictorEntries)
    val loopHit = if(config.loopPredictorActive) {
      loopTable(loopIdx).valid && (loopTable(loopIdx).tag === blockPc)
    } else {
      False
    }

    val indSet = indirectSetIndex(blockPc, history)
    val indTag = indirectTag(blockPc, history)
    val indHit = Bool()
    val indTarget = UInt(config.addressWidth bits)
    val indStrong = Bool()
    indHit := False
    indTarget := 0
    indStrong := False
    for(way <- 0 until config.indirectWays) {
      when(indirectTable(indSet)(way).valid && (indirectTable(indSet)(way).tag === indTag)) {
        indHit := True
        indTarget := indirectTable(indSet)(way).target
        indStrong := indirectTable(indSet)(way).ctr.msb
      }
    }

    when(useMain || l0Hit) {
      desc.takenByteOffset := baseEntry.takenByteOffset
      desc.isConditional := baseEntry.isConditional
      desc.isCall := baseEntry.isCall
      desc.isReturn := baseEntry.isReturn
      desc.isIndirect := baseEntry.isIndirect
      desc.target := baseEntry.target
      desc.targetKind := FrontendTargetKind.direct

      when(loopHit && baseEntry.isConditional && (loopTable(loopIdx).confidence =/= 0) && (loopTable(loopIdx).tripCount =/= 0)) {
        desc.loopPredicted := True
        when((loopTable(loopIdx).iterCount + 1) < loopTable(loopIdx).tripCount) {
          desc.predictedTaken := True
          desc.target := loopTable(loopIdx).target
          desc.targetKind := FrontendTargetKind.direct
        } otherwise {
          desc.predictedTaken := False
          desc.target := loopTable(loopIdx).target
        }
      } elsewhen(baseEntry.isReturn) {
        desc.targetKind := FrontendTargetKind.ret
        when(ras.count =/= 0) {
          desc.predictedTaken := True
          desc.target := rasStack(ras.sp)
          desc.rasUsed := True
        }
      } elsewhen(baseEntry.isIndirect) {
        desc.targetKind := FrontendTargetKind.indirect
        when(indHit && indStrong) {
          desc.predictedTaken := True
          desc.target := indTarget
          desc.indirectProvided := True
        }
      } elsewhen(baseEntry.isConditional) {
        desc.predictedTaken := directionTaken
      } otherwise {
        desc.predictedTaken := True
      }
    }

    desc
  }

  val traversal = TraversalRsp(config)
  traversal.valid := io.active.valid
  traversal.startPc := io.active.payload.pc
  traversal.epoch := io.active.payload.epoch
  traversal.predictedStopReason := FrontendStopReason.none
  traversal.predictedRedirectValid := False
  traversal.predictedRedirectTarget := 0
  traversal.nextStartPc := io.active.payload.pc

  val activeValid = io.active.valid
  val activePc = io.active.payload.pc
  val block0Pc = alignedBlock(activePc)
  val block0 = blockPrediction(block0Pc, speculativeHistory, {
    val ras = RasCheckpoint(config)
    ras.sp := rasSpecSp
    ras.count := rasSpecCount
    ras
  }, ftqAllocPtr)
  traversal.blocks(0) := block0
  traversal.blockCount := io.active.valid.asUInt.resize(traversal.blockCount.getWidth)

  val histAfter0 = UInt(config.gshareHistoryWidth bits)
  histAfter0 := Mux(block0.valid && block0.isConditional, nextHistory(speculativeHistory, block0.predictedTaken), speculativeHistory)
  val ras0 = RasCheckpoint(config)
  ras0.sp := rasSpecSp
  ras0.count := rasSpecCount
  val block0CallPush = block0.valid && block0.predictedTaken && block0.isCall
  val block0CallSp = UInt(log2Up(config.rasDepth max 2) bits)
  block0CallSp := (rasSpecSp + 1).resized
  when(block0CallPush) {
    ras0.sp := block0CallSp
    when(rasSpecCount =/= config.rasDepth) {
      ras0.count := rasSpecCount + 1
    }
  } elsewhen(block0.valid && block0.predictedTaken && block0.isReturn && (rasSpecCount =/= 0)) {
    ras0.sp := (rasSpecSp - 1).resized
    ras0.count := rasSpecCount - 1
  }

  if(config.maxBlocksPerCycle > 1) {
    val block1Pc = block0.fallthrough
    val block1 = blockPrediction(block1Pc, histAfter0, ras0, wrapFtq(ftqAllocPtr, 1))
    val secondBlockTraversed = activeValid && !block0.predictedTaken
    val block1Out = TraversalBlockDescriptor(config)
    block1Out := TraversalBlockDescriptor(config).getZero
    when(secondBlockTraversed) {
      block1Out := block1
    }
    traversal.blocks(1) := block1Out
    when(secondBlockTraversed) {
      traversal.blockCount := U(2, traversal.blockCount.getWidth bits)
    }

    val histAfter1 = UInt(config.gshareHistoryWidth bits)
    histAfter1 := histAfter0
    when(block1.valid && !block0.predictedTaken && block1.isConditional) {
      histAfter1 := nextHistory(histAfter0, block1.predictedTaken)
    }
    val ras1 = RasCheckpoint(config)
    ras1 := ras0
    val block1CallPush = block1.valid && !block0.predictedTaken && block1.predictedTaken && block1.isCall
    val block1CallSp = UInt(log2Up(config.rasDepth max 2) bits)
    block1CallSp := (ras0.sp + 1).resized
    when(block1CallPush) {
      ras1.sp := block1CallSp
      when(ras0.count =/= config.rasDepth) {
        ras1.count := ras0.count + 1
      }
    } elsewhen(block1.valid && !block0.predictedTaken && block1.predictedTaken && block1.isReturn && (ras0.count =/= 0)) {
      ras1.sp := (ras0.sp - 1).resized
      ras1.count := ras0.count - 1
    }

    when(activeValid && block0.predictedTaken) {
      traversal.predictedStopReason := FrontendStopReason.predictedTaken
      traversal.predictedRedirectValid := True
      traversal.predictedRedirectTarget := block0.target
      traversal.nextStartPc := block0.target
    } elsewhen(activeValid && block1.valid && block1.predictedTaken) {
      traversal.predictedStopReason := FrontendStopReason.predictedTaken
      traversal.predictedRedirectValid := True
      traversal.predictedRedirectTarget := block1.target
      traversal.nextStartPc := block1.target
    } elsewhen(activeValid && block1.valid) {
      traversal.nextStartPc := block1.fallthrough
    } otherwise {
      traversal.nextStartPc := block0.fallthrough
    }

  } else {
    traversal.blocks(1) := TraversalBlockDescriptor(config).getZero
    when(activeValid && block0.predictedTaken) {
      traversal.predictedStopReason := FrontendStopReason.predictedTaken
      traversal.predictedRedirectValid := True
      traversal.predictedRedirectTarget := block0.target
      traversal.nextStartPc := block0.target
    } otherwise {
      traversal.nextStartPc := block0.fallthrough
    }

  }

  when(io.accepted.valid) {
    val accepted = io.accepted.payload.traversal
    val acceptedBundleSeq = io.accepted.payload.bundleSeq
    val acceptedBlock0 = accepted.blocks(0)
    val acceptedSecond = accepted.blockCount === U(2, accepted.blockCount.getWidth bits)
    ftq(acceptedBlock0.ftqIndex).valid := acceptedBlock0.valid
    ftq(acceptedBlock0.ftqIndex).blockPc := acceptedBlock0.blockPc
    ftq(acceptedBlock0.ftqIndex).epoch := accepted.epoch
    ftq(acceptedBlock0.ftqIndex).bundleSeq := acceptedBundleSeq
    ftq(acceptedBlock0.ftqIndex).checkpoint := acceptedBlock0.checkpoint

    val hist0 = UInt(config.gshareHistoryWidth bits)
    hist0 := Mux(acceptedBlock0.isConditional, nextHistory(acceptedBlock0.checkpoint.history, acceptedBlock0.predictedTaken), acceptedBlock0.checkpoint.history)
    val rasAfter0 = RasCheckpoint(config)
    rasAfter0 := acceptedBlock0.checkpoint.ras
    val block0Push = acceptedBlock0.valid && acceptedBlock0.predictedTaken && acceptedBlock0.isCall
    val block0Pop = acceptedBlock0.valid && acceptedBlock0.predictedTaken && acceptedBlock0.isReturn && (acceptedBlock0.checkpoint.ras.count =/= 0)
    val block0PushSp = UInt(log2Up(config.rasDepth max 2) bits)
    block0PushSp := (acceptedBlock0.checkpoint.ras.sp + 1).resized
    when(block0Push) {
      rasAfter0.sp := block0PushSp
      when(acceptedBlock0.checkpoint.ras.count =/= config.rasDepth) {
        rasAfter0.count := acceptedBlock0.checkpoint.ras.count + 1
      }
      rasStack(block0PushSp) := acceptedBlock0.fallthrough
    } elsewhen(block0Pop) {
      rasAfter0.sp := (acceptedBlock0.checkpoint.ras.sp - 1).resized
      rasAfter0.count := acceptedBlock0.checkpoint.ras.count - 1
    }

    val hist1 = UInt(config.gshareHistoryWidth bits)
    hist1 := hist0
    val rasAfter1 = RasCheckpoint(config)
    rasAfter1 := rasAfter0
    if(config.maxBlocksPerCycle > 1) {
      val acceptedBlock1 = accepted.blocks(1)
      when(acceptedSecond) {
        ftq(acceptedBlock1.ftqIndex).valid := acceptedBlock1.valid
        ftq(acceptedBlock1.ftqIndex).blockPc := acceptedBlock1.blockPc
        ftq(acceptedBlock1.ftqIndex).epoch := accepted.epoch
        ftq(acceptedBlock1.ftqIndex).bundleSeq := acceptedBundleSeq
        ftq(acceptedBlock1.ftqIndex).checkpoint := acceptedBlock1.checkpoint
        hist1 := Mux(acceptedBlock1.isConditional, nextHistory(acceptedBlock1.checkpoint.history, acceptedBlock1.predictedTaken), acceptedBlock1.checkpoint.history)
        val block1Push = acceptedBlock1.valid && acceptedBlock1.predictedTaken && acceptedBlock1.isCall
        val block1Pop = acceptedBlock1.valid && acceptedBlock1.predictedTaken && acceptedBlock1.isReturn && (acceptedBlock1.checkpoint.ras.count =/= 0)
        val block1PushSp = UInt(log2Up(config.rasDepth max 2) bits)
        block1PushSp := (acceptedBlock1.checkpoint.ras.sp + 1).resized
        rasAfter1 := acceptedBlock1.checkpoint.ras
        when(block1Push) {
          rasAfter1.sp := block1PushSp
          when(acceptedBlock1.checkpoint.ras.count =/= config.rasDepth) {
            rasAfter1.count := acceptedBlock1.checkpoint.ras.count + 1
          }
          rasStack(block1PushSp) := acceptedBlock1.fallthrough
        } elsewhen(block1Pop) {
          rasAfter1.sp := (acceptedBlock1.checkpoint.ras.sp - 1).resized
          rasAfter1.count := acceptedBlock1.checkpoint.ras.count - 1
        }
      }
    }
    ftqAllocPtr := ftqAllocPtr + accepted.blockCount.resized
    speculativeHistory := Mux(acceptedSecond, hist1, hist0)
    rasSpecSp := Mux(acceptedSecond, rasAfter1.sp, rasAfter0.sp)
    rasSpecCount := Mux(acceptedSecond, rasAfter1.count, rasAfter0.count)
  }

  when(io.branchResolve.valid) {
    val update = io.branchResolve.payload
    val branchPc = update.pc
    when(update.isConditional) {
      val idx = bimodalIndex(branchPc)
      bimodalTable(idx) := Mux(update.actualTaken, satInc(bimodalTable(idx)), satDec(bimodalTable(idx)))
      committedHistory := nextHistory(committedHistory, update.actualTaken)
    }

    val mainSet = ftbSetIndex(update.blockPc)
    val ftbHit = Bool()
    val ftbHitWay = UInt(log2Up(config.ftbWays max 2) bits)
    ftbHit := False
    ftbHitWay := 0
    for(way <- 0 until config.ftbWays) {
      when(ftb(mainSet)(way).valid && (ftb(mainSet)(way).tag === update.blockPc)) {
        ftbHit := True
        ftbHitWay := way
      }
    }
    val ftbWriteWay = UInt(log2Up(config.ftbWays max 2) bits)
    ftbWriteWay := ftbReplace(mainSet)
    when(ftbHit) {
      ftbWriteWay := ftbHitWay
    }
    ftb(mainSet)(ftbWriteWay).valid := True
    ftb(mainSet)(ftbWriteWay).tag := update.blockPc
    ftb(mainSet)(ftbWriteWay).target := update.actualTarget
    ftb(mainSet)(ftbWriteWay).takenByteOffset := update.byteOffsetInBlock
    ftb(mainSet)(ftbWriteWay).isConditional := update.isConditional
    ftb(mainSet)(ftbWriteWay).isReturn := update.isReturn
    ftb(mainSet)(ftbWriteWay).isIndirect := update.isIndirect
    ftb(mainSet)(ftbWriteWay).isCall := update.isCall
    ftb(mainSet)(ftbWriteWay).quality := Mux(update.mispredict, U(1, 2 bits), satInc(ftb(mainSet)(ftbWriteWay).quality))
    when(!ftbHit) {
      ftbReplace(mainSet) := ftbReplace(mainSet) + 1
    }

    when(update.actualTaken || update.isConditional) {
      val l0Idx = btbIndex(update.blockPc, config.nanoBtbEntries)
      nanoBtb(l0Idx).valid := True
      nanoBtb(l0Idx).tag := update.blockPc
      nanoBtb(l0Idx).target := update.actualTarget
      nanoBtb(l0Idx).takenByteOffset := update.byteOffsetInBlock
      nanoBtb(l0Idx).isConditional := update.isConditional
      nanoBtb(l0Idx).isReturn := update.isReturn
      nanoBtb(l0Idx).isIndirect := update.isIndirect
      nanoBtb(l0Idx).isCall := update.isCall
      nanoBtb(l0Idx).quality := Mux(update.mispredict, U(1, 2 bits), satInc(nanoBtb(l0Idx).quality))
    }

    when(update.isCall) {
      val nextSp = UInt(log2Up(config.rasDepth max 2) bits)
      nextSp := (rasArchSp + 1).resized
      rasStack(nextSp) := update.fallthrough
      rasArchSp := nextSp
      when(rasArchCount =/= config.rasDepth) {
        rasArchCount := rasArchCount + 1
      }
    } elsewhen(update.isReturn && (rasArchCount =/= 0)) {
      rasArchSp := (rasArchSp - 1).resized
      rasArchCount := rasArchCount - 1
    }

    if(config.loopPredictorActive) when(update.isConditional && (update.actualTarget < update.fallthrough)) {
      val loopIdx = btbIndex(update.blockPc, config.loopPredictorEntries)
      val loopEntryNext = LoopEntry()
      loopEntryNext.valid := loopTable(loopIdx).valid
      loopEntryNext.tag := loopTable(loopIdx).tag
      loopEntryNext.tripCount := loopTable(loopIdx).tripCount
      loopEntryNext.iterCount := loopTable(loopIdx).iterCount
      loopEntryNext.confidence := loopTable(loopIdx).confidence

      when(!loopTable(loopIdx).valid || (loopTable(loopIdx).tag =/= update.blockPc)) {
        loopEntryNext.valid := True
        loopEntryNext.tag := update.blockPc
        loopEntryNext.tripCount := U(0, 8 bits)
        loopEntryNext.iterCount := U(0, 8 bits)
        loopEntryNext.confidence := U(0, 2 bits)
      }

      loopEntryNext.target := update.actualTarget
      loopEntryNext.fallthrough := update.fallthrough
      when(update.actualTaken) {
        when(loopTable(loopIdx).iterCount =/= U(255, 8 bits)) {
          loopEntryNext.iterCount := loopTable(loopIdx).iterCount + 1
        }
      } otherwise {
        when(loopTable(loopIdx).iterCount =/= 0) {
          when(loopTable(loopIdx).tripCount === loopTable(loopIdx).iterCount) {
            loopEntryNext.confidence := satInc(loopTable(loopIdx).confidence)
          } otherwise {
            loopEntryNext.tripCount := loopTable(loopIdx).iterCount
            loopEntryNext.confidence := U(1, 2 bits)
          }
        }
        loopEntryNext.iterCount := U(0, 8 bits)
      }

      loopTable(loopIdx) := loopEntryNext
    }

    when(update.mispredict) {
      val snapshot = ftq(update.ftqIndex)
      val recoveredHistory = UInt(config.gshareHistoryWidth bits)
      recoveredHistory := committedHistory
      val recoveredRas = RasCheckpoint(config)
      recoveredRas.sp := rasArchSp
      recoveredRas.count := rasArchCount
      when(snapshot.valid && (snapshot.bundleSeq === update.bundleSeq) && (snapshot.blockPc === update.blockPc)) {
        recoveredHistory := snapshot.checkpoint.history
        recoveredRas := snapshot.checkpoint.ras
      }
      ftqAllocPtr := update.ftqIndex
      speculativeHistory := Mux(update.isConditional, nextHistory(recoveredHistory, update.actualTaken), recoveredHistory)
      rasSpecSp := recoveredRas.sp
      rasSpecCount := recoveredRas.count
      when(update.isCall) {
        val nextSp = UInt(log2Up(config.rasDepth max 2) bits)
        nextSp := (recoveredRas.sp + 1).resized
        rasStack(nextSp) := update.fallthrough
        rasSpecSp := nextSp
        when(recoveredRas.count =/= config.rasDepth) {
          rasSpecCount := recoveredRas.count + 1
        }
      } elsewhen(update.isReturn && (recoveredRas.count =/= 0)) {
        rasSpecSp := (recoveredRas.sp - 1).resized
        rasSpecCount := recoveredRas.count - 1
      }
    }
  }

  when(io.indirectResolve.valid) {
    val update = io.indirectResolve.payload
    val resolvedHistory = UInt(config.gshareHistoryWidth bits)
    resolvedHistory := update.history
    when(ftq(update.ftqIndex).valid && (ftq(update.ftqIndex).bundleSeq === update.bundleSeq) && (ftq(update.ftqIndex).blockPc === update.blockPc)) {
      resolvedHistory := ftq(update.ftqIndex).checkpoint.history
    }
    val indSet = indirectSetIndex(update.blockPc, resolvedHistory)
    val indTag = indirectTag(update.blockPc, resolvedHistory)
    val indHit = Bool()
    val indWay = UInt(log2Up(config.indirectWays max 2) bits)
    indHit := False
    indWay := 0
    for(way <- 0 until config.indirectWays) {
      when(indirectTable(indSet)(way).valid && (indirectTable(indSet)(way).tag === indTag)) {
        indHit := True
        indWay := way
      }
    }
    val writeWay = UInt(log2Up(config.indirectWays max 2) bits)
    writeWay := indirectReplace(indSet)
    when(indHit) {
      writeWay := indWay
    }
    indirectTable(indSet)(writeWay).valid := True
    indirectTable(indSet)(writeWay).tag := indTag
    indirectTable(indSet)(writeWay).target := update.target
    indirectTable(indSet)(writeWay).ctr := Mux(indHit, satInc(indirectTable(indSet)(writeWay).ctr), U(1, config.indirectCtrBits bits))
    when(!indHit) {
      indirectReplace(indSet) := indirectReplace(indSet) + 1
    }
  }

  when(io.recover.valid && !io.branchResolve.valid) {
    val recoverEntry = ftq(io.recover.payload.recovery.ftqIndex)
    when(
      io.recover.payload.recovery.valid &&
      recoverEntry.valid &&
      (recoverEntry.bundleSeq === io.recover.payload.recovery.bundleSeq) &&
      (recoverEntry.blockPc === io.recover.payload.recovery.blockPc)
    ) {
      ftqAllocPtr := io.recover.payload.recovery.ftqIndex
      speculativeHistory := recoverEntry.checkpoint.history
      rasSpecSp := recoverEntry.checkpoint.ras.sp
      rasSpecCount := recoverEntry.checkpoint.ras.count
    } otherwise {
      speculativeHistory := committedHistory
      rasSpecSp := rasArchSp
      rasSpecCount := rasArchCount
    }
  }

  io.traversal := traversal
}
