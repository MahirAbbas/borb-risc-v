package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendL1I(config: FrontendConfig) extends Component {
  private val bankIndexWidth = log2Up(config.icacheBanks max 2)
  private val setIndexWidth = log2Up(config.icacheSets max 2)
  private val tagWidth = config.addressWidth - config.lineOffsetWidth - bankIndexWidth - setIndexWidth

  case class CacheEntry() extends Bundle {
    val valid = Bool()
    val tag = UInt(tagWidth bits)
    val data = Bits(config.lineDataWidth bits)
  }

  case class MissEntry() extends Bundle {
    val valid = Bool()
    val issued = Bool()
    val lineAddr = UInt(config.addressWidth bits)
    val epoch = UInt(config.epochWidth bits)
    val reason = FrontendMissReason()
  }

  val io = new Bundle {
    val flush = in Bool()
    val invalidate = in Bool()
    val currentEpoch = in UInt(config.epochWidth bits)
    val lookupReq = in Vec(ICacheLookupReq(config), config.lookupLanes)
    val lookupRsp = out Vec(ICacheLookupRsp(config), config.lookupLanes)
    val missReq = master(Stream(ICacheMissReq(config)))
    val missRsp = slave(Flow(ICacheMissRsp(config)))
    val hasPendingMiss = out Bool()
    val reqBlockedOutstanding = out Bool()
    val bankConflictCycle = out Bool()
    val bankBusyCycle = out Bool()
    val dualLookupSuccess = out Bool()
    val staleRspDropped = out Bool()
  }

  val cache = Vec.fill(config.icacheBanks)(
    Vec.fill(config.icacheSets)(
      Vec.fill(config.icacheWays)(Reg(CacheEntry()) init(CacheEntry().getZero))
    )
  )
  val replacePtr = Vec.fill(config.icacheBanks)(
    Vec.fill(config.icacheSets)(Reg(UInt(log2Up(config.icacheWays max 2) bits)) init(0))
  )
  val misses = Vec.fill(config.maxOutstandingMisses)(Reg(MissEntry()) init(MissEntry().getZero))

  def alignedLine(address: UInt): UInt = {
    val ret = UInt(config.addressWidth bits)
    ret := address
    if(config.lineBytes > 1) {
      ret(config.lineOffsetWidth - 1 downto 0) := 0
    }
    ret
  }

  def bankIndex(lineAddr: UInt): UInt = {
    if(config.icacheBanks > 1) {
      lineAddr(config.lineOffsetWidth + bankIndexWidth - 1 downto config.lineOffsetWidth).resized
    } else {
      U(0, bankIndexWidth bits)
    }
  }

  def setIndex(lineAddr: UInt): UInt = {
    val low = config.lineOffsetWidth + bankIndexWidth
    lineAddr(low + setIndexWidth - 1 downto low).resized
  }

  def tagValue(lineAddr: UInt): UInt = {
    val low = config.lineOffsetWidth + bankIndexWidth + setIndexWidth
    lineAddr(config.addressWidth - 1 downto low).resized
  }

  def pendingForLine(lineAddr: UInt): Bool = {
    val pending = Bool()
    pending := False
    for(slot <- 0 until config.maxOutstandingMisses) {
      when(misses(slot).valid && (misses(slot).lineAddr === lineAddr)) {
        pending := True
      }
    }
    pending
  }

  def rawLookup(req: ICacheLookupReq): ICacheLookupRsp = {
    val rsp = ICacheLookupRsp(config)
    val lineAddr = alignedLine(req.blockAddr)
    val bank = bankIndex(lineAddr)
    val set = setIndex(lineAddr)
    val tag = tagValue(lineAddr)
    rsp.valid := req.valid
    rsp.accepted := req.valid
    rsp.hit := False
    rsp.blockAddr := req.blockAddr
    rsp.lineAddr := lineAddr
    rsp.lineData := B(0, config.lineDataWidth bits)
    rsp.missPending := pendingForLine(lineAddr)
    rsp.bankConflict := False
    for(way <- 0 until config.icacheWays) {
      when(cache(bank)(set)(way).valid && (cache(bank)(set)(way).tag === tag)) {
        rsp.hit := True
        rsp.lineData := cache(bank)(set)(way).data
      }
    }
    rsp
  }

  val rawRsp = Vec(ICacheLookupRsp(config), config.lookupLanes)
  val lineAddr = Vec(UInt(config.addressWidth bits), config.lookupLanes)
  val bank = Vec(UInt(bankIndexWidth bits), config.lookupLanes)
  for(lane <- 0 until config.lookupLanes) {
    rawRsp(lane) := rawLookup(io.lookupReq(lane))
    lineAddr(lane) := alignedLine(io.lookupReq(lane).blockAddr)
    bank(lane) := bankIndex(lineAddr(lane))
  }

  val sameLine01 =
    (config.lookupLanes > 1) generate (
      io.lookupReq(0).valid &&
      io.lookupReq(1).valid &&
      (lineAddr(0) === lineAddr(1))
    )
  val bankConflict01 =
    (config.lookupLanes > 1) generate (
      io.lookupReq(0).valid &&
      io.lookupReq(1).valid &&
      !sameLine01 &&
      (bank(0) === bank(1))
    )

  for(lane <- 0 until config.lookupLanes) {
    val rsp = ICacheLookupRsp(config)
    rsp.valid := rawRsp(lane).valid
    rsp.blockAddr := rawRsp(lane).blockAddr
    rsp.lineAddr := rawRsp(lane).lineAddr
    rsp.lineData := rawRsp(lane).lineData
    rsp.missPending := rawRsp(lane).missPending
    if((lane == 1) && (config.lookupLanes > 1)) {
      rsp.accepted := io.lookupReq(1).valid && !bankConflict01
      rsp.hit := rawRsp(1).hit && !bankConflict01
      rsp.bankConflict := bankConflict01
    } else {
      rsp.accepted := rawRsp(lane).accepted
      rsp.hit := rawRsp(lane).hit
      rsp.bankConflict := False
    }
    io.lookupRsp(lane) := rsp
  }

  val lane0Needs = io.lookupReq(0).valid && io.lookupRsp(0).accepted && !io.lookupRsp(0).hit && !io.lookupRsp(0).missPending
  val lane1Needs = if(config.lookupLanes > 1) {
    io.lookupReq(1).valid && io.lookupRsp(1).accepted && !io.lookupRsp(1).hit && !io.lookupRsp(1).missPending && (lineAddr(1) =/= lineAddr(0))
  } else {
    False
  }

  val freeMask = Bits(config.maxOutstandingMisses bits)
  for(slot <- 0 until config.maxOutstandingMisses) {
    freeMask(slot) := !misses(slot).valid
  }
  val freeCount = UInt(log2Up(config.maxOutstandingMisses + 1) bits)
  freeCount := freeMask.asBools.map(_.asUInt.resize(freeCount.getWidth)).reduce(_ + _)

  val alloc0Valid = lane0Needs || lane1Needs
  val alloc0Slot = UInt(config.requestTagWidth bits)
  alloc0Slot := OHToUInt(freeMask)
  val freeMaskAfter0 = Bits(config.maxOutstandingMisses bits)
  freeMaskAfter0 := freeMask
  when(alloc0Valid) {
    freeMaskAfter0(alloc0Slot) := False
  }
  val alloc1Valid = lane0Needs && lane1Needs
  val alloc1Slot = UInt(config.requestTagWidth bits)
  alloc1Slot := OHToUInt(freeMaskAfter0)

  io.hasPendingMiss := misses.map(_.valid).foldLeft(False)(_ || _)
  io.reqBlockedOutstanding := (lane0Needs.asUInt + lane1Needs.asUInt) > freeCount
  io.bankConflictCycle := (if(config.lookupLanes > 1) bankConflict01 else False)
  io.bankBusyCycle := io.bankConflictCycle
  io.dualLookupSuccess := (if(config.lookupLanes > 1) (io.lookupRsp(0).hit && io.lookupRsp(1).hit && !bankConflict01) else io.lookupRsp(0).hit)
  io.staleRspDropped := False

  when(!io.flush && !io.invalidate) {
    when(lane0Needs && (freeCount =/= 0)) {
      misses(alloc0Slot).valid := True
      misses(alloc0Slot).issued := False
      misses(alloc0Slot).lineAddr := lineAddr(0)
      misses(alloc0Slot).epoch := io.lookupReq(0).epoch
      misses(alloc0Slot).reason := Mux(io.lookupReq(0).kind === ICacheLookupKind.prefetch, FrontendMissReason.prefetch, FrontendMissReason.demand)
    }
    when(lane1Needs && (freeCount > lane0Needs.asUInt.resize(freeCount.getWidth))) {
      val slot = Mux(lane0Needs, alloc1Slot, alloc0Slot)
      misses(slot).valid := True
      misses(slot).issued := False
      misses(slot).lineAddr := lineAddr(1)
      misses(slot).epoch := io.lookupReq(1).epoch
      misses(slot).reason := Mux(io.lookupReq(1).kind === ICacheLookupKind.prefetch, FrontendMissReason.prefetch, FrontendMissReason.demand)
    }
  }

  val unissuedMask = Bits(config.maxOutstandingMisses bits)
  for(slot <- 0 until config.maxOutstandingMisses) {
    unissuedMask(slot) := misses(slot).valid && !misses(slot).issued
  }
  val issueValid = unissuedMask.orR
  val issueSlot = UInt(config.requestTagWidth bits)
  issueSlot := OHToUInt(unissuedMask)
  io.missReq.valid := issueValid && !io.flush && !io.invalidate
  io.missReq.payload.lineAddr := misses(issueSlot).lineAddr
  io.missReq.payload.tag := issueSlot
  io.missReq.payload.epoch := misses(issueSlot).epoch
  io.missReq.payload.reason := misses(issueSlot).reason

  when(io.missReq.fire) {
    misses(issueSlot).issued := True
  }

  when(io.missRsp.valid) {
    val rspSlot = io.missRsp.tag
    val rspLine = alignedLine(io.missRsp.lineAddr)
    val rspBank = bankIndex(rspLine)
    val rspSet = setIndex(rspLine)
    val rspTag = tagValue(rspLine)
    val hitWay = UInt(log2Up(config.icacheWays max 2) bits)
    val hitValid = Bool()
    hitWay := 0
    hitValid := False
    for(way <- 0 until config.icacheWays) {
      when(cache(rspBank)(rspSet)(way).valid && (cache(rspBank)(rspSet)(way).tag === rspTag)) {
        hitValid := True
        hitWay := way
      }
    }

    val writeWay = UInt(log2Up(config.icacheWays max 2) bits)
    writeWay := replacePtr(rspBank)(rspSet)
    when(hitValid) {
      writeWay := hitWay
    }

    val rspMatchesSlot =
      misses(rspSlot).valid &&
      misses(rspSlot).issued &&
      (misses(rspSlot).lineAddr === rspLine) &&
      (misses(rspSlot).epoch === io.missRsp.epoch) &&
      (io.missRsp.epoch === io.currentEpoch)

    when(rspMatchesSlot) {
      cache(rspBank)(rspSet)(writeWay).valid := True
      cache(rspBank)(rspSet)(writeWay).tag := rspTag
      cache(rspBank)(rspSet)(writeWay).data := io.missRsp.data
      when(!hitValid) {
        replacePtr(rspBank)(rspSet) := replacePtr(rspBank)(rspSet) + 1
      }
      misses(rspSlot).valid := False
      misses(rspSlot).issued := False
    } otherwise {
      io.staleRspDropped := True
    }
  }

  when(io.flush || io.invalidate) {
    for(slot <- 0 until config.maxOutstandingMisses) {
      misses(slot).valid := False
      misses(slot).issued := False
    }
  }

  when(io.invalidate) {
    for(bankIdx <- 0 until config.icacheBanks) {
      for(setIdx <- 0 until config.icacheSets) {
        for(way <- 0 until config.icacheWays) {
          cache(bankIdx)(setIdx)(way).valid := False
        }
      }
    }
  }
}
