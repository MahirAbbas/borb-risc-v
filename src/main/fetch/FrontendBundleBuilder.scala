package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendBundleBuilder(config: FrontendConfig) extends Component {
  val io = new Bundle {
    val traversal = in(TraversalRsp(config))
    val lookupRsps = in Vec(ICacheLookupRsp(config), config.lookupLanes)
    val bundleSeqBase = in UInt(32 bits)
    val fetchSeqBase = in UInt(32 bits)
    val bundle = master(Stream(FetchBundle(config)))
  }

  case class AssembleResult() extends Bundle {
    val valid = Bool()
    val insn = Bits(32 bits)
    val isCompressed = Bool()
    val step = UInt(4 bits)
    val usedNextBlock = Bool()
  }

  case class LocalControlResult() extends Bundle {
    val stop = Bool()
    val redirectValid = Bool()
    val redirectTarget = UInt(config.addressWidth bits)
  }

  def alignedBlock(address: UInt): UInt = {
    val ret = UInt(config.addressWidth bits)
    ret := address
    if(config.fetchBlockBytes > 1) {
      ret(config.fetchBlockOffsetWidth - 1 downto 0) := 0
    }
    ret
  }

  def alignedLine(address: UInt): UInt = {
    val ret = UInt(config.addressWidth bits)
    ret := address
    if(config.lineBytes > 1) {
      ret(config.lineOffsetWidth - 1 downto 0) := 0
    }
    ret
  }

  def blockDataFromLine(blockAddr: UInt, lineData: Bits): Bits = {
    val ret = Bits(config.fetchBlockBytes * 8 bits)
    ret := lineData(config.fetchBlockBytes * 8 - 1 downto 0)
    if(config.blocksPerLine > 1) {
      val blockSel = blockAddr(config.lineOffsetWidth - 1 downto config.fetchBlockOffsetWidth)
      for(block <- 1 until config.blocksPerLine) {
        when(blockSel === U(block, blockSel.getWidth bits)) {
          ret := lineData(((block + 1) * config.fetchBlockBytes * 8) - 1 downto block * config.fetchBlockBytes * 8)
        }
      }
    }
    ret
  }

  def selectHalfword(data: Bits, index: UInt): Bits = {
    index.mux(
      U(0) -> data(15 downto 0),
      U(1) -> data(31 downto 16),
      U(2) -> data(47 downto 32),
      default -> data(63 downto 48)
    )
  }

  def assembleInstruction(
      pc: UInt,
      block0Addr: UInt,
      block0Hit: Bool,
      block0Data: Bits,
      block1Addr: UInt,
      block1Hit: Bool,
      block1Data: Bits
  ): AssembleResult = {
    val result = AssembleResult()
    val secondBlock = alignedBlock(pc) === block1Addr
    val currentHit = Bool()
    val currentData = Bits(config.fetchBlockBytes * 8 bits)
    currentHit := Mux(secondBlock, block1Hit, block0Hit)
    currentData := Mux(secondBlock, block1Data, block0Data)
    val hwIndex = pc(2 downto 1)
    val first16 = selectHalfword(currentData, hwIndex)
    val isCompressed = if(config.withCompressed) first16(1 downto 0) =/= B"11" else False
    val needsSecond = !isCompressed && (hwIndex === U(3))
    val secondAvailable = !secondBlock && block1Hit

    result.valid := currentHit && (!needsSecond || secondAvailable)
    result.insn := B"32'h00000013"
    result.isCompressed := isCompressed
    result.step := Mux(isCompressed, U(2, 4 bits), U(4, 4 bits))
    result.usedNextBlock := needsSecond

    if(config.withCompressed) {
      when(isCompressed) {
        result.insn := B"16'h0000" ## first16
      } otherwise {
        switch(hwIndex) {
          is(U(0)) { result.insn := currentData(31 downto 16) ## currentData(15 downto 0) }
          is(U(1)) { result.insn := currentData(47 downto 32) ## currentData(31 downto 16) }
          is(U(2)) { result.insn := currentData(63 downto 48) ## currentData(47 downto 32) }
          default { result.insn := block1Data(15 downto 0) ## currentData(63 downto 48) }
        }
      }
    } else {
      result.insn := Mux(pc(2), currentData(63 downto 32), currentData(31 downto 0))
      result.isCompressed := False
    }

    result
  }

  def blockHit(blockAddr: UInt): Bool = {
    val hit = Bool()
    hit := False
    val wantedLine = alignedLine(blockAddr)
    for(lane <- 0 until config.lookupLanes) {
      when(io.lookupRsps(lane).valid && io.lookupRsps(lane).accepted && io.lookupRsps(lane).hit && (io.lookupRsps(lane).lineAddr === wantedLine)) {
        hit := True
      }
    }
    hit
  }

  def blockData(blockAddr: UInt): Bits = {
    val data = Bits(config.fetchBlockBytes * 8 bits)
    data := B(0, config.fetchBlockBytes * 8 bits)
    val wantedLine = alignedLine(blockAddr)
    for(lane <- 0 until config.lookupLanes) {
      when(io.lookupRsps(lane).valid && io.lookupRsps(lane).accepted && io.lookupRsps(lane).hit && (io.lookupRsps(lane).lineAddr === wantedLine)) {
        data := blockDataFromLine(blockAddr, io.lookupRsps(lane).lineData)
      }
    }
    data
  }

  def descForBlock(blockPc: UInt): TraversalBlockDescriptor = {
    val ret = TraversalBlockDescriptor(config)
    ret := io.traversal.blocks(0)
    if(config.maxBlocksPerCycle > 1) {
      when(io.traversal.blocks(1).valid && (io.traversal.blocks(1).blockPc === blockPc)) {
        ret := io.traversal.blocks(1)
      }
    }
    ret
  }

  def predictionFor(pc: UInt, desc: TraversalBlockDescriptor): FetchSlotPredictionMeta = {
    val meta = FetchSlotPredictionMeta(config)
    meta.valid := False
    meta.ftqIndex := desc.ftqIndex
    meta.predictedTaken := False
    meta.predictedTarget := desc.fallthrough
    meta.targetKind := FrontendTargetKind.none
    meta.blockStop := False
    meta
  }

  def isControlInstruction(insn: Bits, isCompressed: Bool): Bool = {
    val control = Bool()
    control := False

    when(!isCompressed) {
      val opcode = insn(6 downto 0)
      control := (opcode === B"7'b1100011") || // branch
        (opcode === B"7'b1101111") || // jal
        (opcode === B"7'b1100111")    // jalr
    } otherwise {
      val funct3 = insn(15 downto 13)
      val quadrant = insn(1 downto 0)
      val rs1 = insn(11 downto 7)
      val rs2 = insn(6 downto 2)
      val cJump = (quadrant === B"2'b01") && (funct3 === B"3'b101")
      val cBranch = (quadrant === B"2'b01") && ((funct3 === B"3'b110") || (funct3 === B"3'b111"))
      val cJrJalr = (quadrant === B"2'b10") &&
        (funct3 === B"3'b100") &&
        (rs2 === B"5'b00000") &&
        (rs1 =/= B"5'b00000")
      control := cJump || cBranch || cJrJalr
    }

    control
  }

  def localControlFor(pc: UInt, insn: Bits, isCompressed: Bool): LocalControlResult = {
    val local = LocalControlResult()
    local.stop := False
    local.redirectValid := False
    local.redirectTarget := pc

    when(!isCompressed) {
      val opcode = insn(6 downto 0)
      val jalImm = (insn(31) ## insn(19 downto 12) ## insn(20) ## insn(30 downto 21) ## B"1'b0").asSInt.resize(config.addressWidth)
      when(opcode === B"7'b1101111") {
        local.stop := True
        local.redirectValid := True
        local.redirectTarget := (pc.asSInt + jalImm).asUInt
      } elsewhen(opcode === B"7'b1100111") {
        local.stop := True
      } elsewhen(opcode === B"7'b1100011") {
        local.stop := True
      }
    } otherwise {
      val funct3 = insn(15 downto 13)
      val quadrant = insn(1 downto 0)
      val rs1 = insn(11 downto 7)
      val rs2 = insn(6 downto 2)
      val cJump = (quadrant === B"2'b01") && (funct3 === B"3'b101")
      val cBranch = (quadrant === B"2'b01") && ((funct3 === B"3'b110") || (funct3 === B"3'b111"))
      val cJrJalr = (quadrant === B"2'b10") &&
        (funct3 === B"3'b100") &&
        (rs2 === B"5'b00000") &&
        (rs1 =/= B"5'b00000")
      val cJImm = (insn(12) ## insn(8) ## insn(10) ## insn(9) ## insn(6) ## insn(7) ## insn(2) ## insn(11) ## insn(5 downto 3) ## B"1'b0").asSInt.resize(config.addressWidth)

      when(cJump) {
        local.stop := True
        local.redirectValid := True
        local.redirectTarget := (pc.asSInt + cJImm).asUInt
      } elsewhen(cBranch || cJrJalr) {
        local.stop := True
      }
    }

    local
  }

  def predictionFor(pc: UInt, desc: TraversalBlockDescriptor, insn: Bits, isCompressed: Bool): FetchSlotPredictionMeta = {
    val meta = FetchSlotPredictionMeta(config)
    if(config.predictedRedirectEnabled) {
      val slotOffset = pc(config.fetchBlockOffsetWidth - 1 downto 0).resized
      val slotIsControl = isControlInstruction(insn, isCompressed)
      val controlSlot = desc.valid &&
        slotIsControl &&
        (
          desc.isConditional ||
          desc.isReturn ||
          desc.isIndirect ||
          (desc.targetKind =/= FrontendTargetKind.none)
        ) &&
        (slotOffset === desc.takenByteOffset)

      meta.ftqIndex := desc.ftqIndex
      meta.valid := controlSlot
      meta.predictedTaken := controlSlot && desc.predictedTaken
      meta.predictedTarget := Mux(desc.predictedTaken, desc.target, desc.fallthrough)
      meta.targetKind := desc.targetKind
      meta.blockStop := controlSlot && desc.predictedTaken
    } else {
      meta.ftqIndex := desc.ftqIndex
      meta.valid := False
      meta.predictedTaken := False
      meta.predictedTarget := desc.fallthrough
      meta.targetKind := FrontendTargetKind.none
      meta.blockStop := False
    }
    meta
  }

  val block0Desc = io.traversal.blocks(0)
  val block1Desc = if(config.maxBlocksPerCycle > 1) io.traversal.blocks(1) else TraversalBlockDescriptor(config).getZero
  val block0Addr = alignedBlock(io.traversal.startPc)
  val block1Addr = block0Addr + U(config.fetchBlockBytes, config.addressWidth bits)
  val block0Hit = blockHit(block0Addr)
  val block1Hit = blockHit(block1Addr)
  val block0Data = blockData(block0Addr)
  val block1Data = blockData(block1Addr)

  val asm0 = assembleInstruction(io.traversal.startPc, block0Addr, block0Hit, block0Data, block1Addr, block1Hit, block1Data)
  val slot0NextPc = io.traversal.startPc + asm0.step.resize(config.addressWidth bits)
  val slot0Block = alignedBlock(io.traversal.startPc)
  val slot0Desc = descForBlock(slot0Block)
  val pred0 = predictionFor(io.traversal.startPc, slot0Desc, asm0.insn, asm0.isCompressed)
  val local0 = localControlFor(io.traversal.startPc, asm0.insn, asm0.isCompressed)

  val asm1 = assembleInstruction(slot0NextPc, block0Addr, block0Hit, block0Data, block1Addr, block1Hit, block1Data)
  val slot1NextPc = slot0NextPc + asm1.step.resize(config.addressWidth bits)
  val slot1Block = alignedBlock(slot0NextPc)
  val slot1Desc = descForBlock(slot1Block)
  val pred1 = predictionFor(slot0NextPc, slot1Desc, asm1.insn, asm1.isCompressed)
  val local1 = localControlFor(slot0NextPc, asm1.insn, asm1.isCompressed)

  val useSlot0 = io.traversal.valid && asm0.valid
  val slot0Stop = pred0.blockStop || local0.stop
  val slot1Stop = pred1.blockStop || local1.stop
  val useSlot1 = useSlot0 && !slot0Stop && asm1.valid
  val predictedRedirectValid = (useSlot0 && (pred0.blockStop || local0.redirectValid)) || (useSlot1 && (pred1.blockStop || local1.redirectValid))
  val predictedRedirectTarget = UInt(config.addressWidth bits)
  predictedRedirectTarget := slot0NextPc
  when(useSlot0 && pred0.blockStop) {
    predictedRedirectTarget := pred0.predictedTarget
  } elsewhen(useSlot0 && local0.redirectValid) {
    predictedRedirectTarget := local0.redirectTarget
  } elsewhen(useSlot1 && pred1.blockStop) {
    predictedRedirectTarget := pred1.predictedTarget
  } elsewhen(useSlot1 && local1.redirectValid) {
    predictedRedirectTarget := local1.redirectTarget
  }

  val slotCount = UInt(log2Up(config.bundleSlots + 1) bits)
  slotCount := useSlot0.asUInt.resize(slotCount.getWidth) + useSlot1.asUInt.resize(slotCount.getWidth)

  val actualTraversedBlocks = Vec(TraversalBlockDescriptor(config), config.maxBlocksPerCycle)
  actualTraversedBlocks(0) := TraversalBlockDescriptor(config).getZero
  if(config.maxBlocksPerCycle > 1) {
    actualTraversedBlocks(1) := TraversalBlockDescriptor(config).getZero
  }
  when(useSlot0) {
    actualTraversedBlocks(0) := slot0Desc
  }
  if(config.maxBlocksPerCycle > 1) {
    when(useSlot1 && (slot1Block =/= slot0Block)) {
      actualTraversedBlocks(1) := slot1Desc
    }
  }

  val actualTraversedBlockCount = UInt(log2Up(config.maxBlocksPerCycle + 1) bits)
  actualTraversedBlockCount := 0
  when(useSlot0) {
    actualTraversedBlockCount := 1
  }
  if(config.maxBlocksPerCycle > 1) {
    when(useSlot1 && (slot1Block =/= slot0Block)) {
      actualTraversedBlockCount := 2
    }
  }

  val payload = FetchBundle(config)
  payload.valid := useSlot0
  payload.bundleSeq := io.bundleSeqBase
  payload.ftqIndexBase := block0Desc.ftqIndex
  payload.epoch := io.traversal.epoch
  payload.startPc := io.traversal.startPc
  payload.slotCount := slotCount
  payload.bundleMeta.traversedBlockCount := actualTraversedBlockCount
  payload.bundleMeta.traversedBlocks := actualTraversedBlocks
  payload.bundleMeta.predictedStopReason := FrontendStopReason.none
  when(predictedRedirectValid) {
    payload.bundleMeta.predictedStopReason := FrontendStopReason.predictedTaken
  } elsewhen(useSlot0 && !useSlot1 && io.traversal.valid) {
    payload.bundleMeta.predictedStopReason := FrontendStopReason.slotLimit
  }
  payload.bundleMeta.predictedRedirectValid := predictedRedirectValid
  payload.bundleMeta.predictedRedirectTarget := predictedRedirectTarget
  payload.bundleMeta.nextStartPc := Mux(predictedRedirectValid, predictedRedirectTarget, Mux(useSlot1, slot1NextPc, slot0NextPc))
  payload.bundleMeta.recovery.valid := useSlot0
  payload.bundleMeta.recovery.ftqIndex := block0Desc.ftqIndex
  payload.bundleMeta.recovery.bundleSeq := io.bundleSeqBase
  payload.bundleMeta.recovery.slotIdx := 0
  payload.bundleMeta.recovery.blockPc := slot0Block
  payload.bundleMeta.recovery.byteOffsetInBlock := io.traversal.startPc(config.fetchBlockOffsetWidth - 1 downto 0).resized
  payload.bundleMeta.fetchSeqBase := io.fetchSeqBase

  val slot0Payload = FetchSlot(config)
  slot0Payload.valid := useSlot0
  slot0Payload.pc := io.traversal.startPc
  slot0Payload.insn := asm0.insn
  slot0Payload.isCompressed := asm0.isCompressed
  slot0Payload.nextPc := slot0NextPc
  slot0Payload.slotIdx := 0
  slot0Payload.ftqIndex := slot0Desc.ftqIndex
  slot0Payload.blockPc := slot0Block
  slot0Payload.byteOffsetInBlock := io.traversal.startPc(config.fetchBlockOffsetWidth - 1 downto 0).resized
  slot0Payload.predictionMeta := pred0
  slot0Payload.illegal := False
  slot0Payload.fetchFault := False
  payload.slots(0) := slot0Payload

  if(config.bundleSlots > 1) {
    val slot1Payload = FetchSlot(config)
    slot1Payload.valid := useSlot1
    slot1Payload.pc := slot0NextPc
    slot1Payload.insn := asm1.insn
    slot1Payload.isCompressed := asm1.isCompressed
    slot1Payload.nextPc := slot1NextPc
    slot1Payload.slotIdx := 1
    slot1Payload.ftqIndex := slot1Desc.ftqIndex
    slot1Payload.blockPc := slot1Block
    slot1Payload.byteOffsetInBlock := slot0NextPc(config.fetchBlockOffsetWidth - 1 downto 0).resized
    slot1Payload.predictionMeta := pred1
    slot1Payload.illegal := False
    slot1Payload.fetchFault := False
    payload.slots(1) := slot1Payload
  }

  io.bundle.valid := useSlot0
  io.bundle.payload := payload
}
