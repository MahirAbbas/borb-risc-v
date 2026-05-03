package borb.backend

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.core.CpuConfig
import borb.core.PerfCountersBundle
import borb.execute.{Branch, Lsu}
import borb.execute.WriteBack
import borb.fetch.Fetch
import borb.fetch.PC
import borb.dispatch.IssueSemantics
import borb.frontend.DecodeTable
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.frontend.RVC
import borb.common.Common._
import borb.common.MicroCode._
import borb.vector.VectorHartContext
import spinal.lib.logic.Masked
import spinal.lib.logic.Symplify

case class TrapCsrBackend(
    execStage: CtrlLink,
    wbStage: CtrlLink,
    config: CpuConfig,
    currentEpoch: UInt,
    pc: PC,
    fetch: Fetch,
    branch: Branch,
    lsu: Lsu,
    perfCounters: PerfCountersBundle
) extends Area {
  val fpFlagsSetValid = Bool()
  val fpFlagsSetBits = Bits(5 bits)
  val frm = Bits(3 bits)
  val vectorContext = VectorHartContext(config.vectorConfig.copy(xlen = config.xlen, addressWidth = config.physicalAddrWidth))
  val vectorMemoryComplete = Bool()
  val vectorMemoryTrapValid = Bool()
  val vectorMemoryTrapIsStore = Bool()
  val vectorMemoryTrapTval = Bits(config.xlen bits)
  val vectorMemoryTrapElement = UInt(config.xlen bits)
  val csrIntResult = IntResultIntent()
  val redirect = TrapRedirectOutcome()

  csrIntResult.valid := False
  csrIntResult.rd := 0
  csrIntResult.data := 0
  csrIntResult.writesRd := False
  csrIntResult.commitEligible := False
  csrIntResult.epoch := 0

  val logic = new execStage.Area {
    val epochMatches = up(SPEC_EPOCH) === currentEpoch
    val decodeMasks = collection.mutable.LinkedHashSet[Masked]()
    for ((instr, _) <- DecodeTable.X_table) {
      decodeMasks += Masked(instr)
    }

    val CAUSE_MISALIGNED_FETCH = U(0, 64 bits)
    val CAUSE_FETCH_ACCESS = U(1, 64 bits)
    val CAUSE_ILLEGAL_INSTRUCTION = U(2, 64 bits)
    val CAUSE_MISALIGNED_LOAD = U(4, 64 bits)
    val CAUSE_LOAD_ACCESS = U(5, 64 bits)
    val CAUSE_MISALIGNED_STORE = U(6, 64 bits)
    val CAUSE_STORE_ACCESS = U(7, 64 bits)
    val CAUSE_USER_ECALL = U(8, 64 bits)
    val CAUSE_SUPERVISOR_ECALL = U(9, 64 bits)
    val CAUSE_MACHINE_ECALL = U(11, 64 bits)
    val CAUSE_FETCH_PAGE = U(12, 64 bits)
    val CAUSE_LOAD_PAGE = U(13, 64 bits)
    val CAUSE_STORE_PAGE = U(15, 64 bits)
    val PRV_U = U(0, 2 bits)
    val PRV_S = U(1, 2 bits)
    val PRV_M = U(3, 2 bits)
    val ARCH_BASE = U(BigInt("80000000", 16), 64 bits)
    val IMPLEMENTED_PHYS_ADDR_WIDTH = 56

    val rv64MstatusWidthBits = BigInt("0000000A00000000", 16)
    val csrMstatus = Reg(Bits(64 bits)) init B(rv64MstatusWidthBits, 64 bits)
    val misaBase = BigInt("8000000000000100", 16)
    val misaA = if (config.aExtensionEnabled) BigInt("0000000000000001", 16) else BigInt(0)
    val misaC = if (config.cExtensionEnabled) BigInt("0000000000000004", 16) else BigInt(0)
    val misaF = if (config.fExtensionEnabled) BigInt("0000000000000020", 16) else BigInt(0)
    val misaM = if (config.mExtensionEnabled) BigInt("0000000000001000", 16) else BigInt(0)
    val misaD = if (config.dExtensionEnabled) BigInt("0000000000000008", 16) else BigInt(0)
    val csrMisa = Reg(Bits(64 bits)) init B(misaBase | misaA | misaC | misaF | misaM | misaD, 64 bits)
    val csrMedeleg = Reg(Bits(64 bits)) init 0
    val csrMtvec = Reg(Bits(64 bits)) init 0
    val csrMscratch = Reg(Bits(64 bits)) init 0
    val csrMepc = Reg(Bits(64 bits)) init 0
    val csrMcause = Reg(Bits(64 bits)) init 0
    val csrMtval = Reg(Bits(64 bits)) init 0
    val csrMie = Reg(Bits(64 bits)) init 0
    val csrMideleg = Reg(Bits(64 bits)) init 0
    val csrMip = Reg(Bits(64 bits)) init 0
    val csrMcounteren = Reg(Bits(64 bits)) init 0
    val csrMenvcfg = Reg(Bits(64 bits)) init 0
    val csrSie = Reg(Bits(64 bits)) init 0
    val csrSip = Reg(Bits(64 bits)) init 0
    val csrScounteren = Reg(Bits(64 bits)) init 0
    val csrSenvcfg = Reg(Bits(64 bits)) init 0
    val csrStimecmp = Reg(UInt(64 bits)) init U(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits)
    val csrStvec = Reg(Bits(64 bits)) init 0
    val csrSscratch = Reg(Bits(64 bits)) init 0
    val csrSepc = Reg(Bits(64 bits)) init 0
    val csrScause = Reg(Bits(64 bits)) init 0
    val csrStval = Reg(Bits(64 bits)) init 0
    val csrFflags = Reg(Bits(5 bits)) init 0
    val csrFrm = Reg(Bits(3 bits)) init 0
    val csrVl = Reg(UInt(64 bits)) init 0
    val csrVtype = Reg(Bits(64 bits)) init 0
    val csrVstart = Reg(UInt(64 bits)) init 0
    val csrVxrm = Reg(Bits(2 bits)) init 0
    val csrVxsat = Reg(Bool()) init False
    val csrSatp = Reg(Bits(64 bits)) init 0
    val pmpCfgBytes = Vec.fill(64)(Reg(Bits(8 bits)) init 0)
    val pmpAddrRegs = Vec.fill(64)(Reg(Bits(64 bits)) init B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
    val pmpImplementedEntries = config.pmpImplementedEntries
    val vmShadowEntries = config.vmShadowEntries
    val vmPteValid = Vec.fill(vmShadowEntries)(Reg(Bool()) init False)
    val vmPteAddr = Vec.fill(vmShadowEntries)(Reg(UInt(64 bits)) init 0)
    val vmPteData = Vec.fill(vmShadowEntries)(Reg(Bits(64 bits)) init 0)
    val vmPteBigEndian = Vec.fill(vmShadowEntries)(Reg(Bool()) init False)
    val vmPteReplace = Reg(UInt(log2Up(vmShadowEntries) bits)) init 0
    val vmTablePageEntries = config.vmTablePageEntries
    val vmTablePageValid = Vec.fill(vmTablePageEntries)(Reg(Bool()) init False)
    val vmTablePageBase = Vec.fill(vmTablePageEntries)(Reg(UInt(64 bits)) init 0)
    val vmTablePageReplace = Reg(UInt(log2Up(vmTablePageEntries) bits)) init 0
    val itlbEntries = config.itlbEntries
    val dtlbEntries = config.dtlbEntries
    val sharedTlbEntries = config.sharedTlbEntries
    val totalTlbEntries = itlbEntries + dtlbEntries + sharedTlbEntries
    val tlbKindWidth = 2
    val tlbKindI = U(0, tlbKindWidth bits)
    val tlbKindD = U(1, tlbKindWidth bits)
    val tlbKindShared = U(2, tlbKindWidth bits)
    val vmTlbValid = Vec.fill(totalTlbEntries)(Reg(Bool()) init False)
    val vmTlbKind = Vec.fill(totalTlbEntries)(Reg(UInt(tlbKindWidth bits)) init 0)
    val vmTlbVpn = Vec.fill(totalTlbEntries)(Reg(UInt(27 bits)) init 0)
    val vmTlbSatp = Vec.fill(totalTlbEntries)(Reg(Bits(44 bits)) init 0)
    val vmTlbPriv = Vec.fill(totalTlbEntries)(Reg(UInt(2 bits)) init 0)
    val vmTlbReq = Vec.fill(totalTlbEntries)(Reg(Bits(3 bits)) init 0)
    val vmTlbStatus = Vec.fill(totalTlbEntries)(Reg(Bits(2 bits)) init 0)
    val vmTlbPpn = Vec.fill(totalTlbEntries)(Reg(UInt(44 bits)) init 0)
    val vmItlbReplace = Reg(UInt(log2Up(itlbEntries) bits)) init 0
    val vmDtlbReplace = Reg(UInt(log2Up(dtlbEntries) bits)) init 0
    val vmSharedTlbReplace = Reg(UInt(log2Up(sharedTlbEntries) bits)) init 0
    val currentPriv = Reg(UInt(2 bits)) init PRV_M
    val sawNonZeroPc = Reg(Bool) init False
    val counterOffsets = Vec.fill(32)(Reg(UInt(64 bits)) init 0)
    val counterLastRaw = Vec.fill(32)(Reg(UInt(64 bits)) init 0)
    val counterLastArch = Vec.fill(32)(Reg(UInt(64 bits)) init 0)
    val mhpmEventRegs = Vec.fill(32)(Reg(Bits(64 bits)) init 0)
    counterOffsets(1) := 0

    frm := csrFrm
    vectorContext.valid := True
    vectorContext.hartId := 0
    vectorContext.vl := csrVl
    vectorContext.vtype := csrVtype
    vectorContext.vstart := csrVstart
    vectorContext.vxrm := csrVxrm
    vectorContext.vxsat := csrVxsat
    vectorContext.busy := False
    vectorContext.dirty := csrMstatus(10 downto 9) =/= B"00"
    when(fpFlagsSetValid) {
      csrFflags := csrFflags | fpFlagsSetBits
    }

    def pmpCfgSanitize(cfg: Bits): Bits = {
      val c = Bits(8 bits)
      c := cfg
      c(6 downto 5) := B"00"
      c(1) := cfg(1) & cfg(0)
      c
    }

    def pmpCfgReadWord(group: Int): Bits = {
      val out = Bits(64 bits)
      out := 0
      for (i <- 0 until 8) {
        val idx = group * 8 + i
        if (idx < pmpImplementedEntries) {
          out((i * 8 + 7) downto (i * 8)) := pmpCfgBytes(idx)
        } else {
          out((i * 8 + 7) downto (i * 8)) := 0
        }
      }
      out
    }

    def sanitizeEnvcfg(data: Bits): Bits = {
      data & B(BigInt("80000000000000F1", 16), 64 bits)
    }

    case class VmShadowLookup() extends Bundle {
      val hit = Bool()
      val data = Bits(64 bits)
      val bigEndian = Bool()
    }

    def shadowLookup(addr: UInt): VmShadowLookup = {
      val res = VmShadowLookup()
      res.hit := False
      res.data := 0
      res.bigEndian := False
      for (i <- (0 until vmShadowEntries).reverse) {
        when(vmPteValid(i) && (vmPteAddr(i) === addr)) {
          res.hit := True
          res.data := vmPteData(i)
          res.bigEndian := vmPteBigEndian(i)
        }
      }
      res
    }

    def byteSwap64(raw: Bits): Bits = {
      val swapped = Bits(64 bits)
      for (i <- 0 until 8) {
        swapped(i * 8 + 7 downto i * 8) := raw((7 - i) * 8 + 7 downto (7 - i) * 8)
      }
      swapped
    }

    def decodeVmPte(raw: Bits): Bits = {
      val pte = Bits(64 bits)
      pte := raw
      when(csrMstatus(36)) {
        pte := byteSwap64(raw)
      }
      pte
    }

    def decodeShadowStoredPte(raw: Bits, bigEndian: Bool): Bits = {
      val pte = Bits(64 bits)
      pte := raw
      when(bigEndian) {
        pte := byteSwap64(raw)
      }
      pte
    }

    def vmPteLooksTracked(pte: Bits): Bool = {
      val svnapot64k = pte(63) && pte(13) && !pte(12 downto 10).orR
      pte(0) && !(!pte(1) && pte(2)) && !pte(62 downto 54).orR && (!pte(63) || svnapot64k)
    }

    def mepcMasked(raw: Bits): Bits = {
      val out = Bits(64 bits)
      out := raw
      out(0) := False
      when(!csrMisa(2)) {
        out(1) := False
      }
      out
    }

    def sepcMasked(raw: Bits): Bits = {
      val out = Bits(64 bits)
      out := raw
      out(0) := False
      when(!csrMisa(2)) {
        out(1) := False
      }
      out
    }

    def trapVectorDirect(raw: Bits): Bits = {
      val out = Bits(64 bits)
      out := raw
      out(1 downto 0) := B"00"
      out
    }

    def sstatusRead(): Bits = {
      val out = Bits(64 bits)
      out := 0
      out(63) := csrMstatus(63)
      out(36) := csrMstatus(36)
      out(33 downto 32) := csrMstatus(33 downto 32)
      out(19 downto 18) := csrMstatus(19 downto 18)
      out(16 downto 13) := csrMstatus(16 downto 13)
      out(10 downto 9) := csrMstatus(10 downto 9)
      out(8) := csrMstatus(8)
      out(6 downto 5) := csrMstatus(6 downto 5)
      out(1) := csrMstatus(1)
      out
    }

    def writeSstatus(writeData: Bits): Unit = {
      val next = Bits(64 bits)
      next := csrMstatus
      next(63) := writeData(63)
      next(36) := writeData(36)
      next(33 downto 32) := B"2'b10"
      next(19 downto 18) := writeData(19 downto 18)
      next(16 downto 13) := writeData(16 downto 13)
      next(10 downto 9) := writeData(10 downto 9)
      next(8) := writeData(8)
      next(6 downto 5) := writeData(6 downto 5)
      next(1) := writeData(1)
      csrMstatus := next
    }

    def sanitizeMstatus(writeData: Bits): Bits = {
      val next = Bits(64 bits)
      next := writeData
      next(35 downto 34) := B"2'b10"
      next(33 downto 32) := B"2'b10"
      next
    }

    def rawCounter(index: Int): UInt = index match {
      case 0 => perfCounters.cycles
      case 1 => perfCounters.cycles
      case 2 => perfCounters.instret
      case 3 => perfCounters.stallsHazard
      case 4 => perfCounters.stallsFetch
      case 5 => perfCounters.stallsMem
      case 6 => perfCounters.stallsBackend
      case 7 => perfCounters.branches
      case 8 => perfCounters.branchesTaken
      case 9 => perfCounters.flushes
      case 10 => perfCounters.loads
      case 11 => perfCounters.stores
      case 12 => perfCounters.jumps
      case 13 => perfCounters.csrOps
      case 14 => perfCounters.mulDivOps
      case 15 => perfCounters.trapCommits
      case 16 => perfCounters.stallsWriteback
      case 17 => perfCounters.stallsCommit
      case 18 => perfCounters.stallsMulDivBusy
      case 19 => perfCounters.stallsLsuReplayOrWait
      case 20 => perfCounters.stallsDispatchToSrc
      case 21 => perfCounters.stallsSrcToExec
      case 22 => perfCounters.stallsExecToWrite
      case 23 => perfCounters.cyclesDispatchValid
      case 24 => perfCounters.cyclesSrcValid
      case 25 => perfCounters.cyclesExecValid
      case 26 => perfCounters.cyclesWriteValid
      case 27 => perfCounters.cyclesDispatchFire
      case 28 => perfCounters.cyclesSrcFire
      case 29 => perfCounters.cyclesExecFire
      case 30 => perfCounters.cyclesWriteFire
      case 31 => perfCounters.frontendPendingReqCycles
    }

    def archCounter(index: Int): UInt = rawCounter(index) + counterOffsets(index)

    for (i <- 3 until 32) {
      val raw = rawCounter(i)
      val delta = raw - counterLastRaw(i)
      val inhibitM = currentPriv === PRV_M && mhpmEventRegs(i)(62)
      val inhibitS = currentPriv === PRV_S && mhpmEventRegs(i)(61)
      val inhibitU = currentPriv === PRV_U && mhpmEventRegs(i)(60)
      val inhibited = inhibitM || inhibitS || inhibitU
      val nextArch = UInt(64 bits)
      nextArch := archCounter(i)
      when(inhibited) {
        counterOffsets(i) := counterOffsets(i) - delta
        nextArch := counterLastArch(i)
      }
      when(nextArch < counterLastArch(i)) {
        mhpmEventRegs(i)(63) := True
      }
      counterLastRaw(i) := raw
      counterLastArch(i) := nextArch
    }

    def timerPending(): Bool = archCounter(1) >= csrStimecmp

    def counterOverflowPending(): Bool = {
      val pending = Bool()
      pending := False
      for (i <- 3 until 32) {
        when(mhpmEventRegs(i)(63)) {
          pending := True
        }
      }
      pending
    }

    def mipRead(): Bits = {
      val out = Bits(64 bits)
      out := csrMip
      out(5) := timerPending()
      out(13) := counterOverflowPending()
      out
    }

    def sipRead(): Bits = {
      val out = Bits(64 bits)
      out := csrSip
      out(5) := timerPending()
      out(13) := counterOverflowPending()
      out
    }

    def scountovfRead(): Bits = {
      val out = Bits(64 bits)
      out := 0
      for (i <- 3 until 32) {
        out(i) := mhpmEventRegs(i)(63)
      }
      out
    }

    def writeCounter(index: Int, writeData: Bits): Unit = {
      counterOffsets(index) := writeData.asUInt - rawCounter(index)
      counterLastArch(index) := writeData.asUInt
    }

    def writeCounterHigh(index: Int, writeData: Bits): Unit = {
      val next = Bits(64 bits)
      next := archCounter(index).asBits
      next(63 downto 32) := writeData(31 downto 0)
      counterOffsets(index) := next.asUInt - rawCounter(index)
      counterLastArch(index) := next.asUInt
    }

    def csrRead(addr: UInt): Bits = {
      val out = Bits(64 bits)
      out := 0
      switch(addr) {
        is(U"12'h300") { out := csrMstatus }
        is(U"12'h100") { out := sstatusRead() }
        is(U"12'h104") { out := csrSie }
        is(U"12'h106") { out := csrScounteren }
        is(U"12'h105") { out := trapVectorDirect(csrStvec) }
        is(U"12'h10A") { out := csrSenvcfg }
        is(U"12'h140") { out := csrSscratch }
        is(U"12'h141") { out := sepcMasked(csrSepc) }
        is(U"12'h142") { out := csrScause }
        is(U"12'h143") { out := csrStval }
        is(U"12'h144") { out := sipRead() }
        is(U"12'h14D") { out := csrStimecmp.asBits }
        is(U"12'h301") { out := csrMisa }
        is(U"12'h302") { out := csrMedeleg }
        is(U"12'h303") { out := csrMideleg }
        is(U"12'h304") { out := csrMie }
        is(U"12'h305") { out := trapVectorDirect(csrMtvec) }
        is(U"12'h30A") { out := csrMenvcfg }
        is(U"12'h340") { out := csrMscratch }
        is(U"12'h341") { out := mepcMasked(csrMepc) }
        is(U"12'h342") { out := csrMcause }
        is(U"12'h343") { out := csrMtval }
        is(U"12'h344") { out := mipRead() }
        is(U"12'h306") { out := csrMcounteren }
        is(U"12'h001") { out := B(0, 59 bits) ## csrFflags }
        is(U"12'h002") { out := B(0, 61 bits) ## csrFrm }
        is(U"12'h003") { out := B(0, 56 bits) ## csrFrm ## csrFflags }
        is(U"12'h008") { out := csrVstart.asBits }
        is(U"12'h009") { out := B(0, 63 bits) ## csrVxsat }
        is(U"12'h00A") { out := B(0, 62 bits) ## csrVxrm }
        is(U"12'h00F") { out := B(0, 61 bits) ## csrVxrm ## csrVxsat }
        is(U"12'h180") { out := csrSatp }
        is(U"12'hC20") { out := csrVl.asBits }
        is(U"12'hC21") { out := csrVtype }
        is(U"12'hC22") { out := U(config.vectorConfig.vectorBytes, 64 bits).asBits }
        is(U"12'h3A0") { out := pmpCfgReadWord(0) }
        is(U"12'h3A2") { out := pmpCfgReadWord(1) }
        is(U"12'h3A4") { out := pmpCfgReadWord(2) }
        is(U"12'h3A6") { out := pmpCfgReadWord(3) }
        is(U"12'h3A8") { out := pmpCfgReadWord(4) }
        is(U"12'h3AA") { out := pmpCfgReadWord(5) }
        is(U"12'h3AC") { out := pmpCfgReadWord(6) }
        is(U"12'h3AE") { out := pmpCfgReadWord(7) }
        is(U"12'hB00") { out := perfCounters.cycles.asBits }
        is(U"12'hB02") { out := perfCounters.instret.asBits }
        is(U"12'hB03") { out := perfCounters.stallsHazard.asBits }
        is(U"12'hB04") { out := perfCounters.stallsFetch.asBits }
        is(U"12'hB05") { out := perfCounters.stallsMem.asBits }
        is(U"12'hB06") { out := perfCounters.stallsBackend.asBits }
        is(U"12'hB07") { out := perfCounters.branches.asBits }
        is(U"12'hB08") { out := perfCounters.branchesTaken.asBits }
        is(U"12'hB09") { out := perfCounters.flushes.asBits }
        is(U"12'hB0A") { out := perfCounters.loads.asBits }
        is(U"12'hB0B") { out := perfCounters.stores.asBits }
        is(U"12'hB0C") { out := perfCounters.jumps.asBits }
        is(U"12'hB0D") { out := perfCounters.csrOps.asBits }
        is(U"12'hB0E") { out := perfCounters.mulDivOps.asBits }
        is(U"12'hB0F") { out := perfCounters.trapCommits.asBits }
        is(U"12'hB10") { out := perfCounters.stallsWriteback.asBits }
        is(U"12'hB11") { out := perfCounters.stallsCommit.asBits }
        is(U"12'hB12") { out := perfCounters.stallsMulDivBusy.asBits }
        is(U"12'hB13") { out := perfCounters.stallsLsuReplayOrWait.asBits }
        is(U"12'hB14") { out := perfCounters.stallsDispatchToSrc.asBits }
        is(U"12'hB15") { out := perfCounters.stallsSrcToExec.asBits }
        is(U"12'hB16") { out := perfCounters.stallsExecToWrite.asBits }
        is(U"12'hB17") { out := perfCounters.cyclesDispatchValid.asBits }
        is(U"12'hB18") { out := perfCounters.cyclesSrcValid.asBits }
        is(U"12'hB19") { out := perfCounters.cyclesExecValid.asBits }
        is(U"12'hB1A") { out := perfCounters.cyclesWriteValid.asBits }
        is(U"12'hB1B") { out := perfCounters.cyclesDispatchFire.asBits }
        is(U"12'hB1C") { out := perfCounters.cyclesSrcFire.asBits }
        is(U"12'hB1D") { out := perfCounters.cyclesExecFire.asBits }
        is(U"12'hB1E") { out := perfCounters.cyclesWriteFire.asBits }
        is(U"12'hB1F") { out := perfCounters.frontendPendingReqCycles.asBits }
        is(U"12'hB20") { out := perfCounters.frontendBeat0ValidCycles.asBits }
        is(U"12'hB21") { out := perfCounters.frontendBeat1ValidCycles.asBits }
        is(U"12'hB22") { out := perfCounters.frontendReqIssued.asBits }
        is(U"12'hB23") { out := perfCounters.frontendRspAccepted.asBits }
        is(U"12'hB24") { out := perfCounters.frontendNeedCurrentReq.asBits }
        is(U"12'hB25") { out := perfCounters.frontendNeedNextReq.asBits }
        is(U"12'hB26") { out := perfCounters.frontendPrefetchReq.asBits }
        is(U"12'hB27") { out := perfCounters.frontendWaitCurBeat.asBits }
        is(U"12'hB28") { out := perfCounters.frontendWaitNextBeat.asBits }
        is(U"12'hB29") { out := perfCounters.frontendTakeInsn.asBits }
        is(U"12'hB2A") { out := perfCounters.frontendCurBeatHit.asBits }
        is(U"12'hB2B") { out := perfCounters.frontendNextBeatHit.asBits }
        is(U"12'hB2C") { out := perfCounters.backendOccupancy0.asBits }
        is(U"12'hB2D") { out := perfCounters.backendOccupancy1.asBits }
        is(U"12'hB2E") { out := perfCounters.backendOccupancy2.asBits }
        is(U"12'hB2F") { out := perfCounters.backendOccupancy3.asBits }
        is(U"12'hB30") { out := perfCounters.backendOccupancy4.asBits }
        is(U"12'hB31") { out := perfCounters.backendOverlapDispatchSrc.asBits }
        is(U"12'hB32") { out := perfCounters.backendOverlapSrcExec.asBits }
        is(U"12'hB33") { out := perfCounters.backendOverlapExecWrite.asBits }
        is(U"12'hB80") { out := perfCounters.cycles(63 downto 32).asBits.resized }
        is(U"12'hB82") { out := perfCounters.instret(63 downto 32).asBits.resized }
        is(U"12'hB83") { out := perfCounters.stallsHazard(63 downto 32).asBits.resized }
        is(U"12'hB84") { out := perfCounters.stallsFetch(63 downto 32).asBits.resized }
        is(U"12'hB85") { out := perfCounters.stallsMem(63 downto 32).asBits.resized }
        is(U"12'hB86") { out := perfCounters.stallsBackend(63 downto 32).asBits.resized }
        is(U"12'hB87") { out := perfCounters.branches(63 downto 32).asBits.resized }
        is(U"12'hB88") { out := perfCounters.branchesTaken(63 downto 32).asBits.resized }
        is(U"12'hB89") { out := perfCounters.flushes(63 downto 32).asBits.resized }
        is(U"12'hB8A") { out := perfCounters.loads(63 downto 32).asBits.resized }
        is(U"12'hB8B") { out := perfCounters.stores(63 downto 32).asBits.resized }
        is(U"12'hB8C") { out := perfCounters.jumps(63 downto 32).asBits.resized }
        is(U"12'hB8D") { out := perfCounters.csrOps(63 downto 32).asBits.resized }
        is(U"12'hB8E") { out := perfCounters.mulDivOps(63 downto 32).asBits.resized }
        is(U"12'hB8F") { out := perfCounters.trapCommits(63 downto 32).asBits.resized }
        is(U"12'hB90") { out := perfCounters.stallsWriteback(63 downto 32).asBits.resized }
        is(U"12'hB91") { out := perfCounters.stallsCommit(63 downto 32).asBits.resized }
        is(U"12'hB92") { out := perfCounters.stallsMulDivBusy(63 downto 32).asBits.resized }
        is(U"12'hB93") { out := perfCounters.stallsLsuReplayOrWait(63 downto 32).asBits.resized }
        is(U"12'hB94") { out := perfCounters.stallsDispatchToSrc(63 downto 32).asBits.resized }
        is(U"12'hB95") { out := perfCounters.stallsSrcToExec(63 downto 32).asBits.resized }
        is(U"12'hB96") { out := perfCounters.stallsExecToWrite(63 downto 32).asBits.resized }
        is(U"12'hB97") { out := perfCounters.cyclesDispatchValid(63 downto 32).asBits.resized }
        is(U"12'hB98") { out := perfCounters.cyclesSrcValid(63 downto 32).asBits.resized }
        is(U"12'hB99") { out := perfCounters.cyclesExecValid(63 downto 32).asBits.resized }
        is(U"12'hB9A") { out := perfCounters.cyclesWriteValid(63 downto 32).asBits.resized }
        is(U"12'hB9B") { out := perfCounters.cyclesDispatchFire(63 downto 32).asBits.resized }
        is(U"12'hB9C") { out := perfCounters.cyclesSrcFire(63 downto 32).asBits.resized }
        is(U"12'hB9D") { out := perfCounters.cyclesExecFire(63 downto 32).asBits.resized }
        is(U"12'hB9E") { out := perfCounters.cyclesWriteFire(63 downto 32).asBits.resized }
        is(U"12'hB9F") { out := perfCounters.frontendPendingReqCycles(63 downto 32).asBits.resized }
        is(U"12'hBA0") { out := perfCounters.frontendBeat0ValidCycles(63 downto 32).asBits.resized }
        is(U"12'hBA1") { out := perfCounters.frontendBeat1ValidCycles(63 downto 32).asBits.resized }
        is(U"12'hBA2") { out := perfCounters.frontendReqIssued(63 downto 32).asBits.resized }
        is(U"12'hBA3") { out := perfCounters.frontendRspAccepted(63 downto 32).asBits.resized }
        is(U"12'hBA4") { out := perfCounters.frontendNeedCurrentReq(63 downto 32).asBits.resized }
        is(U"12'hBA5") { out := perfCounters.frontendNeedNextReq(63 downto 32).asBits.resized }
        is(U"12'hBA6") { out := perfCounters.frontendPrefetchReq(63 downto 32).asBits.resized }
        is(U"12'hBA7") { out := perfCounters.frontendWaitCurBeat(63 downto 32).asBits.resized }
        is(U"12'hBA8") { out := perfCounters.frontendWaitNextBeat(63 downto 32).asBits.resized }
        is(U"12'hBA9") { out := perfCounters.frontendTakeInsn(63 downto 32).asBits.resized }
        is(U"12'hBAA") { out := perfCounters.frontendCurBeatHit(63 downto 32).asBits.resized }
        is(U"12'hBAB") { out := perfCounters.frontendNextBeatHit(63 downto 32).asBits.resized }
        is(U"12'hBAC") { out := perfCounters.backendOccupancy0(63 downto 32).asBits.resized }
        is(U"12'hBAD") { out := perfCounters.backendOccupancy1(63 downto 32).asBits.resized }
        is(U"12'hBAE") { out := perfCounters.backendOccupancy2(63 downto 32).asBits.resized }
        is(U"12'hBAF") { out := perfCounters.backendOccupancy3(63 downto 32).asBits.resized }
        is(U"12'hBB0") { out := perfCounters.backendOccupancy4(63 downto 32).asBits.resized }
        is(U"12'hBB1") { out := perfCounters.backendOverlapDispatchSrc(63 downto 32).asBits.resized }
        is(U"12'hBB2") { out := perfCounters.backendOverlapSrcExec(63 downto 32).asBits.resized }
        is(U"12'hBB3") { out := perfCounters.backendOverlapExecWrite(63 downto 32).asBits.resized }
        is(U"12'hC00") { out := perfCounters.cycles.asBits }
        is(U"12'hC02") { out := perfCounters.instret.asBits }
        is(U"12'hC80") { out := perfCounters.cycles(63 downto 32).asBits.resized }
        is(U"12'hC82") { out := perfCounters.instret(63 downto 32).asBits.resized }
        for (i <- 0 until 64) {
          is(U(0x3B0 + i, 12 bits)) { out := (if (i < pmpImplementedEntries) pmpAddrRegs(i) else B(0, 64 bits)) }
        }
      }
      for (i <- 0 until 32) {
        if (i != 1) {
          when(addr === U(0xB00 + i, 12 bits)) { out := archCounter(i).asBits }
          when(addr === U(0xB80 + i, 12 bits)) { out := archCounter(i)(63 downto 32).asBits.resized }
        }
        when(addr === U(0xC00 + i, 12 bits)) { out := archCounter(i).asBits }
        when(addr === U(0xC80 + i, 12 bits)) { out := archCounter(i)(63 downto 32).asBits.resized }
        if (i >= 3) {
          when(addr === U(0x320 + i, 12 bits)) { out := mhpmEventRegs(i) }
        }
      }
      when(addr === U"12'hDA0") { out := scountovfRead() }
      out
    }

    def csrSupported(addr: UInt): Bool = {
      val ok = Bool()
      ok := False
      switch(addr) {
        is(U"12'h100", U"12'h104", U"12'h105", U"12'h106", U"12'h10A", U"12'h140", U"12'h141", U"12'h142", U"12'h143", U"12'h144", U"12'h14D") { ok := True }
        is(U"12'h300", U"12'h301", U"12'h302", U"12'h303", U"12'h304", U"12'h305", U"12'h306", U"12'h30A", U"12'h340", U"12'h341", U"12'h342", U"12'h343", U"12'h344") { ok := True }
        is(U"12'h001", U"12'h002", U"12'h003", U"12'h008", U"12'h009", U"12'h00A", U"12'h00F", U"12'h180") { ok := True }
        is(U"12'hC20", U"12'hC21", U"12'hC22") { ok := True }
        is(U"12'h3A0", U"12'h3A2", U"12'h3A4", U"12'h3A6", U"12'h3A8", U"12'h3AA", U"12'h3AC", U"12'h3AE") { ok := True }
        is(U"12'hB00", U"12'hB02", U"12'hB03", U"12'hB04", U"12'hB05", U"12'hB06", U"12'hB07", U"12'hB08", U"12'hB09", U"12'hB0A", U"12'hB0B", U"12'hB0C", U"12'hB0D", U"12'hB0E", U"12'hB0F", U"12'hB10", U"12'hB11", U"12'hB12", U"12'hB13", U"12'hB14", U"12'hB15", U"12'hB16", U"12'hB17", U"12'hB18", U"12'hB19", U"12'hB1A", U"12'hB1B", U"12'hB1C", U"12'hB1D", U"12'hB1E", U"12'hB1F", U"12'hB20", U"12'hB21", U"12'hB22", U"12'hB23", U"12'hB24", U"12'hB25", U"12'hB26", U"12'hB27", U"12'hB28", U"12'hB29", U"12'hB2A", U"12'hB2B", U"12'hB2C", U"12'hB2D", U"12'hB2E", U"12'hB2F", U"12'hB30", U"12'hB31", U"12'hB32", U"12'hB33") { ok := True }
        is(U"12'hB80", U"12'hB82", U"12'hB83", U"12'hB84", U"12'hB85", U"12'hB86", U"12'hB87", U"12'hB88", U"12'hB89", U"12'hB8A", U"12'hB8B", U"12'hB8C", U"12'hB8D", U"12'hB8E", U"12'hB8F", U"12'hB90", U"12'hB91", U"12'hB92", U"12'hB93", U"12'hB94", U"12'hB95", U"12'hB96", U"12'hB97", U"12'hB98", U"12'hB99", U"12'hB9A", U"12'hB9B", U"12'hB9C", U"12'hB9D", U"12'hB9E", U"12'hB9F", U"12'hBA0", U"12'hBA1", U"12'hBA2", U"12'hBA3", U"12'hBA4", U"12'hBA5", U"12'hBA6", U"12'hBA7", U"12'hBA8", U"12'hBA9", U"12'hBAA", U"12'hBAB", U"12'hBAC", U"12'hBAD", U"12'hBAE", U"12'hBAF", U"12'hBB0", U"12'hBB1", U"12'hBB2", U"12'hBB3") { ok := True }
        is(U"12'hC00", U"12'hC01", U"12'hC02", U"12'hC80", U"12'hC81", U"12'hC82") { ok := True }
        for (i <- 0 until 64) {
          is(U(0x3B0 + i, 12 bits)) { ok := True }
        }
        for (i <- 3 until 32) {
          is(U(0x323 + (i - 3), 12 bits)) { ok := True }
          is(U(0xC00 + i, 12 bits)) { ok := True }
          is(U(0xC80 + i, 12 bits)) { ok := True }
        }
        is(U"12'hDA0") { ok := True }
      }
      ok
    }

    val rawInsn = up(Decoder.INSTRUCTION)
    val rawIsCompressed = if (config.cExtensionEnabled) rawInsn(1 downto 0) =/= B"11" else False
    val rawRvc = if (config.cExtensionEnabled) RVC(rawInsn(15 downto 0), xlen = config.xlen) else null
    val trapInsn = Bits(32 bits)
    trapInsn := rawInsn
    if (config.cExtensionEnabled) {
      when(rawIsCompressed) {
        trapInsn := rawRvc.inst
      }
    }
    val trapPcInRam = up(PC.PC) >= ARCH_BASE
    val compressedIllegalInRam = if (config.cExtensionEnabled) rawIsCompressed && rawRvc.illegal && trapPcInRam else False
    val fetchedPacketObserved = (up(Fetch.FETCH_SEQ) =/= 0) || (up(SPEC_EPOCH) =/= 0)
    val decodedIllegalInRam = up(Decoder.DECODE_ILLEGAL) && trapPcInRam && fetchedPacketObserved
    val decodedUnsupportedInRam = up.isValid && !up(Decoder.VALID) && trapPcInRam && fetchedPacketObserved
    val trapInsnDecodeIllegal = decodedIllegalInRam || (compressedIllegalInRam && fetchedPacketObserved)
    val trapInsnSupported = Symplify(trapInsn, decodeMasks)
    val trapInsnValid = trapInsnSupported && !trapInsnDecodeIllegal
    val illegalTrapPacket = decodedIllegalInRam || decodedUnsupportedInRam
    // Legal instructions follow decoded packet validity, but architecturally
    // illegal encodings must still reach the trap path instead of being
    // silently converted into bubbles.
    val trapInsnArrived = up.isValid && (up(Decoder.VALID) || illegalTrapPacket)

    val csrAddr = trapInsn(31 downto 20).asUInt
    val csrOld = csrRead(csrAddr)
    val csrRs1 = up(borb.dispatch.SrcPlugin.RS1)
    val csrZimm = B(59 bits, default -> False) ## trapInsn(19 downto 15)
    val csrWriteData = Bits(64 bits)
    csrWriteData := csrOld
    val csrWriteEn = Bool()
    csrWriteEn := False

    val isCsrOp = up(Decoder.MicroCode) === uopCSRRW || up(Decoder.MicroCode) === uopCSRRS || up(Decoder.MicroCode) === uopCSRRC ||
      up(Decoder.MicroCode) === uopCSRRWI || up(Decoder.MicroCode) === uopCSRRSI || up(Decoder.MicroCode) === uopCSRRCI
    val csrPrivReq = csrAddr(9 downto 8)
    val csrReadOnly = csrAddr(11 downto 10) === U"2'b11"
    val counterShadowRead = (csrAddr(11 downto 8) === U"4'hC") &&
      ((csrAddr(7 downto 5) === U"3'b000") || (csrAddr(7 downto 5) === U"3'b100"))
    val counterShadowIndex = csrAddr(4 downto 0).resize(6)
    val counterDisabledForS = currentPriv === PRV_S && !csrMcounteren(counterShadowIndex)
    val counterDisabledForU = currentPriv === PRV_U && (!csrMcounteren(counterShadowIndex) || !csrScounteren(counterShadowIndex))
    val vectorStatusOff = csrMstatus(10 downto 9) === B"00"

    def markVectorDirty(): Unit = {
      csrMstatus(10 downto 9) := B"11"
    }

    def vectorVtypeLegal(vtype: Bits): Bool = {
      val reservedClear = !vtype(62 downto 8).orR && !vtype(63)
      val sewSupported = vtype(5 downto 3).asUInt <= U(3, 3 bits)
      val lmulSupported = !vtype(2)
      reservedClear && sewSupported && lmulSupported
    }

    def vectorVlMax(vtype: Bits): UInt = {
      val elementsAtM1 = U(config.vectorConfig.vectorBytes, 64 bits) |>> vtype(5 downto 3).asUInt
      elementsAtM1 |<< vtype(1 downto 0).asUInt
    }

    switch(up(Decoder.MicroCode)) {
      is(uopCSRRW) { csrWriteData := csrRs1; csrWriteEn := True }
      is(uopCSRRS) { csrWriteData := csrOld | csrRs1; csrWriteEn := csrRs1 =/= 0 }
      is(uopCSRRC) { csrWriteData := csrOld & ~csrRs1; csrWriteEn := csrRs1 =/= 0 }
      is(uopCSRRWI) { csrWriteData := csrZimm; csrWriteEn := True }
      is(uopCSRRSI) { csrWriteData := csrOld | csrZimm; csrWriteEn := csrZimm =/= 0 }
      is(uopCSRRCI) { csrWriteData := csrOld & ~csrZimm; csrWriteEn := csrZimm =/= 0 }
    }

    val csrSatpIllegal = trapInsnValid && isCsrOp && (csrAddr === U"12'h180") &&
      (currentPriv === PRV_U || ((currentPriv === PRV_S) && csrMstatus(20)))
    val csrIllegal = trapInsnValid && isCsrOp && (!csrSupported(csrAddr) || (currentPriv < csrPrivReq) || (csrWriteEn && csrReadOnly) ||
      (counterShadowRead && (counterDisabledForS || counterDisabledForU)) || csrSatpIllegal)
    val csrFire = up.isFiring && epochMatches && up(Decoder.VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOALU) && isCsrOp

    when(csrFire && !csrIllegal && csrWriteEn) {
      switch(csrAddr) {
        is(U"12'h100") {
          writeSstatus(csrWriteData)
          vmTlbInvalidateAll()
        }
        is(U"12'h104") { csrSie := csrWriteData }
        is(U"12'h105") { csrStvec := trapVectorDirect(csrWriteData) }
        is(U"12'h106") { csrScounteren := csrWriteData(31 downto 0).resize(64) }
        is(U"12'h10A") { csrSenvcfg := sanitizeEnvcfg(csrWriteData) }
        is(U"12'h140") { csrSscratch := csrWriteData }
        is(U"12'h141") { csrSepc := csrWriteData }
        is(U"12'h142") { csrScause := csrWriteData }
        is(U"12'h143") { csrStval := csrWriteData }
        is(U"12'h144") {
          csrSip := csrWriteData
          csrSip(5) := False
          csrSip(13) := False
        }
        is(U"12'h14D") { csrStimecmp := csrWriteData.asUInt }
        is(U"12'h300") {
          csrMstatus := sanitizeMstatus(csrWriteData)
          vmTlbInvalidateAll()
        }
        is(U"12'h301") { csrMisa := csrWriteData }
        is(U"12'h302") { csrMedeleg := csrWriteData }
        is(U"12'h303") { csrMideleg := csrWriteData }
        is(U"12'h304") { csrMie := csrWriteData }
        is(U"12'h305") { csrMtvec := trapVectorDirect(csrWriteData) }
        is(U"12'h30A") { csrMenvcfg := sanitizeEnvcfg(csrWriteData) }
        is(U"12'h340") { csrMscratch := csrWriteData }
        is(U"12'h341") { csrMepc := csrWriteData }
        is(U"12'h342") { csrMcause := csrWriteData }
        is(U"12'h343") { csrMtval := csrWriteData }
        is(U"12'h344") {
          csrMip := csrWriteData
          csrMip(5) := False
          csrMip(13) := False
        }
        is(U"12'h306") { csrMcounteren := csrWriteData(31 downto 0).resize(64) }
        is(U"12'h001") { csrFflags := csrWriteData(4 downto 0) }
        is(U"12'h002") {
          when(csrWriteData(2 downto 0) =/= B"101" && csrWriteData(2 downto 0) =/= B"110") {
            csrFrm := csrWriteData(2 downto 0)
          }
        }
        is(U"12'h003") {
          csrFflags := csrWriteData(4 downto 0)
          when(csrWriteData(7 downto 5) =/= B"101" && csrWriteData(7 downto 5) =/= B"110") {
            csrFrm := csrWriteData(7 downto 5)
          }
        }
        is(U"12'h008") {
          csrVstart := csrWriteData.asUInt
          markVectorDirty()
        }
        is(U"12'h009") {
          csrVxsat := csrWriteData(0)
          markVectorDirty()
        }
        is(U"12'h00A") {
          csrVxrm := csrWriteData(1 downto 0)
          markVectorDirty()
        }
        is(U"12'h00F") {
          csrVxsat := csrWriteData(0)
          csrVxrm := csrWriteData(2 downto 1)
          markVectorDirty()
        }
        is(U"12'h180") {
          csrSatp := csrWriteData
          vmTlbInvalidateAll()
        }
        for (i <- 0 until 32) {
          if (i != 1) {
            is(U(0xB00 + i, 12 bits)) { writeCounter(i, csrWriteData) }
            is(U(0xB80 + i, 12 bits)) { writeCounterHigh(i, csrWriteData) }
          }
          if (i >= 3) {
            is(U(0x320 + i, 12 bits)) { mhpmEventRegs(i) := csrWriteData }
          }
        }
        is(U"12'h3A0") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            when(!pmpCfgBytes(i)(7)) {
              pmpCfgBytes(i) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A2") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            val idx = 8 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A4") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            val idx = 16 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A6") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            val idx = 24 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A8") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            val idx = 32 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3AA") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            val idx = 40 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3AC") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            val idx = 48 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3AE") {
          vmTlbInvalidateAll()
          for (i <- 0 until 8) {
            val idx = 56 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        for (i <- 0 until 64) {
          is(U(0x3B0 + i, 12 bits)) {
            vmTlbInvalidateAll()
            val selfLocked = pmpCfgBytes(i)(7)
            val nextTorLocked = if (i < 63) (pmpCfgBytes(i + 1)(7) && (pmpCfgBytes(i + 1)(4 downto 3) === B"01")) else False
            if (i < pmpImplementedEntries) {
              when(!(selfLocked || nextTorLocked)) {
                pmpAddrRegs(i) := csrWriteData
              }
            }
          }
        }
      }
    }

    val isVsetvli = up(Decoder.MicroCode) === uopVSETVLI
    val isVsetivli = up(Decoder.MicroCode) === uopVSETIVLI
    val isVsetvl = up(Decoder.MicroCode) === uopVSETVL
    val isVectorConfigOp = isVsetvli || isVsetivli || isVsetvl
    val vectorConfigFire = up.isFiring && epochMatches && up(Decoder.VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOALU) && isVectorConfigOp
    val vectorConfigImmediateVtype = Bits(64 bits)
    vectorConfigImmediateVtype := 0
    when(isVsetvli) {
      vectorConfigImmediateVtype(10 downto 0) := trapInsn(30 downto 20)
    } elsewhen(isVsetivli) {
      vectorConfigImmediateVtype(9 downto 0) := trapInsn(29 downto 20)
    }
    val vectorConfigRequestedVtype = Bits(64 bits)
    vectorConfigRequestedVtype := vectorConfigImmediateVtype
    when(isVsetvl) {
      vectorConfigRequestedVtype := up(borb.dispatch.SrcPlugin.RS2)
    }
    val vectorConfigLegalVtype = vectorVtypeLegal(vectorConfigRequestedVtype)
    val vectorConfigVlmax = vectorVlMax(vectorConfigRequestedVtype)
    val vectorConfigAvl = UInt(64 bits)
    vectorConfigAvl := up(borb.dispatch.SrcPlugin.RS1).asUInt
    when(isVsetivli) {
      vectorConfigAvl := trapInsn(19 downto 15).asUInt.resized
    } elsewhen(isVsetvli && (up(Decoder.RS1_ADDR) === B"00000")) {
      vectorConfigAvl := vectorConfigVlmax
    } elsewhen(isVsetvl && (up(Decoder.RS1_ADDR) === B"00000")) {
      vectorConfigAvl := vectorConfigVlmax
    }
    val vectorConfigNextVl = UInt(64 bits)
    vectorConfigNextVl := Mux(vectorConfigAvl < vectorConfigVlmax, vectorConfigAvl, vectorConfigVlmax)
    val vectorConfigNextVtype = Bits(64 bits)
    vectorConfigNextVtype := vectorConfigRequestedVtype
    when(!vectorConfigLegalVtype) {
      vectorConfigNextVtype := 0
      vectorConfigNextVtype(63) := True
      vectorConfigNextVl := 0
    }
    when(vectorConfigFire) {
      csrVl := vectorConfigNextVl
      csrVtype := vectorConfigNextVtype
      csrVstart := 0
      markVectorDirty()

      csrIntResult.valid := True
      csrIntResult.rd := up(Decoder.RD_ADDR).asUInt
      csrIntResult.data := vectorConfigNextVl.asBits
      csrIntResult.writesRd := True
      csrIntResult.commitEligible := epochMatches
      csrIntResult.epoch := up(SPEC_EPOCH)

      down(WriteBack.RESULT).address.allowOverride := up(Decoder.RD_ADDR).asUInt
      down(WriteBack.RESULT).data.allowOverride := Mux(up(Decoder.RD_ADDR).asUInt === 0, B(0, 64 bits), vectorConfigNextVl.asBits)
      down(WriteBack.RESULT).valid.allowOverride := True
    }
    when(vectorMemoryComplete && !vectorMemoryTrapValid) {
      csrVstart := 0
    }

    when(csrFire && !csrIllegal && csrWriteEn && (csrAddr === U"12'h180")) {
      val satpModeSv39 = csrWriteData(63 downto 60) === B"4'b1000"
      val satpRootBase = csrWriteData(43 downto 0).asUInt.resize(64) |<< 12
      val rootHit = Bool()
      val rootFree = Bool()
      val rootHitIndex = UInt(log2Up(vmTablePageEntries) bits)
      val rootFreeIndex = UInt(log2Up(vmTablePageEntries) bits)
      rootHit := False
      rootFree := False
      rootHitIndex := 0
      rootFreeIndex := 0
      for (i <- (0 until vmTablePageEntries).reverse) {
        when(vmTablePageValid(i) && (vmTablePageBase(i) === satpRootBase)) {
          rootHit := True
          rootHitIndex := i
        }
        when(!vmTablePageValid(i)) {
          rootFree := True
          rootFreeIndex := i
        }
      }
      when(satpModeSv39 && !rootHit) {
        val rootAllocIndex = UInt(log2Up(vmTablePageEntries) bits)
        rootAllocIndex := rootFree ? rootFreeIndex | vmTablePageReplace
        vmTablePageValid(rootAllocIndex) := True
        vmTablePageBase(rootAllocIndex) := satpRootBase
        when(!rootFree) {
          vmTablePageReplace := vmTablePageReplace + 1
        }
      }
    }

    when(csrFire && !csrIllegal) {
      csrIntResult.valid := True
      csrIntResult.rd := up(Decoder.RD_ADDR).asUInt
      csrIntResult.data := csrOld
      csrIntResult.writesRd := True
      csrIntResult.commitEligible := epochMatches
      csrIntResult.epoch := up(SPEC_EPOCH)

      down(WriteBack.RESULT).address.allowOverride := up(Decoder.RD_ADDR).asUInt
      down(WriteBack.RESULT).data.allowOverride := Mux(up(Decoder.RD_ADDR).asUInt === 0, B(0, 64 bits), csrOld)
      down(WriteBack.RESULT).valid.allowOverride := True
    }

    val trapFromBranch = branch.logic.willTrap
    val insn = trapInsn
    val duplicateInWb = wbStage.up.isValid &&
      wbStage(Decoder.VALID) &&
      wbStage(LANE_SEL) &&
      (wbStage(Fetch.FETCH_SEQ) === up(Fetch.FETCH_SEQ))
    val aguFire = sawNonZeroPc && up.isValid && up(Decoder.VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOAGU) && !duplicateInWb
    val usesFpState = up(IssueSemantics.PROPS).readsFpRs1 || up(IssueSemantics.PROPS).readsFpRs2 ||
      up(IssueSemantics.PROPS).readsFpRs3 || up(IssueSemantics.PROPS).writesFpRd
    val fpStateDisabledIllegal = usesFpState && (csrMstatus(14 downto 13) === B"00")

    val stageExecFire = up.isValid && up.isFiring && ((up(LANE_SEL) && up(Decoder.VALID)) || illegalTrapPacket)
    when(illegalTrapPacket) {
      up(Decoder.VALID).allowOverride := True
      up(LANE_SEL).allowOverride := True
    }

    when(stageExecFire && (up(PC.PC) >= ARCH_BASE)) {
      sawNonZeroPc := True
    }

    val mstatusMprv = csrMstatus(17)
    val mstatusMpp = csrMstatus(12 downto 11).asUInt
    val dataPriv = Mux((currentPriv === PRV_M) && mstatusMprv, mstatusMpp, currentPriv)
    val instPriv = currentPriv

    def pmpAllow(addrRaw: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, accessBytes: UInt): Bool = {
      val addrLo = addrRaw
      val bytes = accessBytes.max(U(1, 64 bits))
      val addrHi = addrLo + (bytes - U(1, 64 bits))
      val hitVec = Vec(Bool(), pmpImplementedEntries)
      val permVec = Vec(Bool(), pmpImplementedEntries)

      for (i <- 0 until pmpImplementedEntries) {
        val cfg = pmpCfgBytes(i)
        val l = cfg(7)
        val a = cfg(4 downto 3)
        val r = cfg(0)
        val w = cfg(1)
        val x = cfg(2)
        val entry = pmpAddrRegs(i).asUInt
        val prev = if (i == 0) U(0, 64 bits) else pmpAddrRegs(i - 1).asUInt

        val torLo = prev |<< 2
        val torHiExcl = entry |<< 2
        val na4Lo = entry |<< 2
        val na4Hi = (entry |<< 2) + U(3, 64 bits)
        val lowestZero = ((~entry) & (entry + U(1, 64 bits)))
        val napotMask = lowestZero - U(1, 64 bits)
        val napotBase = (entry & ~napotMask) |<< 2
        val napotSpan = (napotMask |<< 3) | U(7, 64 bits)
        val napotTop = napotBase + napotSpan

        val hitAny = Bool()
        val fullMatch = Bool()
        hitAny := False
        fullMatch := False
        switch(a) {
          is(B"00") { hitAny := False }
          is(B"01") {
            val torNonEmpty = torHiExcl =/= U(0, 64 bits)
            val torHi = torHiExcl - U(1, 64 bits)
            hitAny := torNonEmpty && (addrLo <= torHi) && (addrHi >= torLo)
            fullMatch := torNonEmpty && (addrLo >= torLo) && (addrHi <= torHi)
          }
          is(B"10") {
            hitAny := (addrLo <= na4Hi) && (addrHi >= na4Lo)
            fullMatch := (addrLo >= na4Lo) && (addrHi <= na4Hi)
          }
          default {
            hitAny := (addrLo <= napotTop) && (addrHi >= napotBase)
            fullMatch := (addrLo >= napotBase) && (addrHi <= napotTop)
          }
        }

        val reqPerm = (!needX || x) && (!needR || r) && (!needW || w)
        val accessOk = fullMatch && reqPerm
        val mPerm = l ? accessOk | True
        val suPerm = accessOk
        val perm = (priv === PRV_M) ? mPerm | suPerm

        hitVec(i) := hitAny
        permVec(i) := perm
      }

      var allowExpr: Bool = (priv === PRV_M)
      for (i <- (pmpImplementedEntries - 1) downto 0) {
        allowExpr = Mux(hitVec(i), permVec(i), allowExpr)
      }
      allowExpr
    }

    case class VmTlbLookup() extends Bundle {
      val hit = Bool()
      val physPage = UInt(44 bits)
    }

    def vmReqMask(needX: Bool, needR: Bool, needW: Bool): Bits = {
      val mask = Bits(3 bits)
      mask(0) := needX
      mask(1) := needR
      mask(2) := needW
      mask
    }

    def vmStatusMask(): Bits = csrMstatus(19) ## csrMstatus(18)

    def vmTlbLookup(vaddr: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, kind: UInt): VmTlbLookup = {
      val res = VmTlbLookup()
      val vpn = vaddr(38 downto 12)
      val satpRoot = csrSatp(43 downto 0)
      val req = vmReqMask(needX, needR, needW)
      val status = vmStatusMask()
      res.hit := False
      res.physPage := 0
      for (i <- (0 until totalTlbEntries).reverse) {
        val kindMatches = (vmTlbKind(i) === kind) || (vmTlbKind(i) === tlbKindShared)
        when(
          vmTlbValid(i) &&
          kindMatches &&
          (vmTlbVpn(i) === vpn) &&
          (vmTlbSatp(i) === satpRoot) &&
          (vmTlbPriv(i) === priv) &&
          (vmTlbReq(i) === req) &&
          (vmTlbStatus(i) === status)
        ) {
          res.hit := True
          res.physPage := vmTlbPpn(i)
        }
      }
      res
    }

    def vmTlbFill(index: UInt, kind: UInt, vaddr: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, physAddr: UInt): Unit = {
      vmTlbValid(index) := True
      vmTlbKind(index) := kind
      vmTlbVpn(index) := vaddr(38 downto 12)
      vmTlbSatp(index) := csrSatp(43 downto 0)
      vmTlbPriv(index) := priv
      vmTlbReq(index) := vmReqMask(needX, needR, needW)
      vmTlbStatus(index) := vmStatusMask()
      vmTlbPpn(index) := physAddr(55 downto 12)
    }

    def vmTlbInvalidateAll(): Unit = {
      for (i <- 0 until totalTlbEntries) {
        vmTlbValid(i) := False
      }
    }

    // The software page-table shadow is only valid for real RV64 PTE writes.
    // Mirroring every committed store pollutes the shadow with test-data stores,
    // which eventually evicts or corrupts live PTEs and causes spurious VM faults.
    val shadowPteWrite = lsu.io.storeCommit &&
      (lsu.io.storeMask === B"8'hFF") &&
      ((lsu.io.storeAddr(2 downto 0)) === U(0, 3 bits))
    val shadowWriteData = lsu.io.storeData
    val shadowStorePage = lsu.io.storeAddr & U(BigInt("FFFFFFFFFFFFF000", 16), 64 bits)

    val trackedTablePageHit = Bool()
    val trackedTablePageIndex = UInt(log2Up(vmTablePageEntries) bits)
    val trackedTablePageFree = Bool()
    val trackedTablePageFreeIndex = UInt(log2Up(vmTablePageEntries) bits)
    trackedTablePageHit := False
    trackedTablePageIndex := 0
    trackedTablePageFree := False
    trackedTablePageFreeIndex := 0
    for (i <- (0 until vmTablePageEntries).reverse) {
      when(vmTablePageValid(i) && (vmTablePageBase(i) === shadowStorePage)) {
        trackedTablePageHit := True
        trackedTablePageIndex := i
      }
      when(!vmTablePageValid(i)) {
        trackedTablePageFree := True
        trackedTablePageFreeIndex := i
      }
    }

    val shadowDecodedWriteData = decodeShadowStoredPte(shadowWriteData, lsu.io.bigEndian)
    val seedLooksLikePte = shadowPteWrite && vmPteLooksTracked(shadowDecodedWriteData)
    val shadowPageTableWrite = shadowPteWrite && (trackedTablePageHit || seedLooksLikePte)

    when(shadowPageTableWrite) {
      vmTlbInvalidateAll()
      when(!trackedTablePageHit) {
        val allocIndex = UInt(log2Up(vmTablePageEntries) bits)
        allocIndex := trackedTablePageFree ? trackedTablePageFreeIndex | vmTablePageReplace
        vmTablePageValid(allocIndex) := True
        vmTablePageBase(allocIndex) := shadowStorePage
        when(!trackedTablePageFree) {
          vmTablePageReplace := vmTablePageReplace + 1
        }
      }

      val alignedAddr = lsu.io.storeAddr & U(BigInt("FFFFFFFFFFFFFFF8", 16), 64 bits)
      val hitAny = Bool()
      val hitIndex = UInt(log2Up(vmShadowEntries) bits)
      val freeAny = Bool()
      val freeIndex = UInt(log2Up(vmShadowEntries) bits)
      hitAny := False
      hitIndex := 0
      freeAny := False
      freeIndex := 0
      for (i <- (0 until vmShadowEntries).reverse) {
        when(vmPteValid(i) && (vmPteAddr(i) === alignedAddr)) {
          hitAny := True
          hitIndex := i
        }
        when(!vmPteValid(i)) {
          freeAny := True
          freeIndex := i
        }
      }

      val writeIndex = UInt(log2Up(vmShadowEntries) bits)
      writeIndex := hitIndex
      when(!hitAny) {
        writeIndex := freeAny ? freeIndex | vmPteReplace
      }

      val merged = Bits(64 bits)
      merged := hitAny ? vmPteData(hitIndex) | B(0, 64 bits)
      for (byte <- 0 until 8) {
        when(lsu.io.storeMask(byte)) {
          merged(byte * 8 + 7 downto byte * 8) := lsu.io.storeData(byte * 8 + 7 downto byte * 8)
        }
      }
      val mergedBigEndian = lsu.io.bigEndian
      val mergedDecoded = decodeShadowStoredPte(merged, mergedBigEndian)

      vmPteValid(writeIndex) := True
      vmPteAddr(writeIndex) := alignedAddr
      vmPteData(writeIndex) := merged
      vmPteBigEndian(writeIndex) := mergedBigEndian
      when(!hitAny && !freeAny) {
        vmPteReplace := vmPteReplace + 1
      }

      val childPageBase = (mergedDecoded(53 downto 10).asUInt.resize(64) |<< 12)
      val childTablePtr =
        mergedDecoded(0) && !mergedDecoded(1) && !mergedDecoded(2) && !mergedDecoded(3) &&
        mergedDecoded(53 downto 10).orR && !mergedDecoded(63 downto 54).orR
      val childTableHit = Bool()
      val childTableFree = Bool()
      val childTableIndex = UInt(log2Up(vmTablePageEntries) bits)
      val childTableFreeIndex = UInt(log2Up(vmTablePageEntries) bits)
      childTableHit := False
      childTableFree := False
      childTableIndex := 0
      childTableFreeIndex := 0
      for (i <- (0 until vmTablePageEntries).reverse) {
        when(vmTablePageValid(i) && (vmTablePageBase(i) === childPageBase)) {
          childTableHit := True
          childTableIndex := i
        }
        when(!vmTablePageValid(i)) {
          childTableFree := True
          childTableFreeIndex := i
        }
      }
      when(childTablePtr && !childTableHit) {
        val childAllocIndex = UInt(log2Up(vmTablePageEntries) bits)
        childAllocIndex := childTableFree ? childTableFreeIndex | vmTablePageReplace
        vmTablePageValid(childAllocIndex) := True
        vmTablePageBase(childAllocIndex) := childPageBase
        when(!childTableFree) {
          vmTablePageReplace := vmTablePageReplace + 1
        }
      }
    }

    case class VmAccessResult() extends Bundle {
      val active = Bool()
      val pageFault = Bool()
      val accessFault = Bool()
      val physAddr = UInt(64 bits)
    }

    def vmSv39Active(priv: UInt): Bool = {
      (csrSatp(63 downto 60) === B"4'b1000") && (priv =/= PRV_M)
    }

    def vmSv39Canonical(addr: UInt): Bool = {
      val upper = addr(63 downto 39)
      val signFill = Bits(25 bits)
      signFill := addr(38) ? B(25 bits, default -> True) | B(0, 25 bits)
      upper.asBits === signFill
    }

    def physAddrLegal(addr: UInt): Bool = {
      if (IMPLEMENTED_PHYS_ADDR_WIDTH < 64) {
        !addr(63 downto IMPLEMENTED_PHYS_ADDR_WIDTH).orR && !(addr < ARCH_BASE)
      } else {
        !(addr < ARCH_BASE)
      }
    }

    def vmAccessPermitted(pte: Bits, priv: UInt, needX: Bool, needR: Bool, needW: Bool): Bool = {
      val pteR = pte(1)
      val pteW = pte(2)
      val pteX = pte(3)
      val pteU = pte(4)
      val pteA = pte(6)
      val pteD = pte(7)
      val mxr = csrMstatus(19)
      val sum = csrMstatus(18)
      val readable = pteR || (mxr && pteX)
      val privOk = Bool()
      privOk := False
      when(priv === PRV_U) {
        privOk := pteU
      } elsewhen(priv === PRV_S) {
        privOk := !pteU || ((!needX) && sum)
      }
      privOk &&
      (!needX || pteX) &&
      (!needR || readable) &&
      (!needW || pteW) &&
      pteA &&
      (!needW || pteD)
    }

    def vmComposePhysAddr(level: Int, vaddr: UInt, pte: Bits): UInt = {
      val phys = UInt(64 bits)
      phys := 0
      phys(11 downto 0) := vaddr(11 downto 0)
      level match {
        case 2 =>
          phys(20 downto 12) := vaddr(20 downto 12)
          phys(29 downto 21) := vaddr(29 downto 21)
          phys(55 downto 30) := pte(53 downto 28).asUInt
        case 1 =>
          phys(20 downto 12) := vaddr(20 downto 12)
          phys(29 downto 21) := pte(27 downto 19).asUInt
          phys(55 downto 30) := pte(53 downto 28).asUInt
        case _ =>
          phys(20 downto 12) := pte(18 downto 10).asUInt
          phys(29 downto 21) := pte(27 downto 19).asUInt
          phys(55 downto 30) := pte(53 downto 28).asUInt
      }
      phys
    }

    def vmComposeSvnapot64kAddr(vaddr: UInt, pte: Bits): UInt = {
      val phys = vmComposePhysAddr(0, vaddr, pte)
      phys(15 downto 12) := vaddr(15 downto 12)
      phys
    }

    def vmTranslateSv39(vaddr: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, accessBytes: UInt, tlbKind: UInt): VmAccessResult = {
      val res = VmAccessResult()
      res.active := vmSv39Active(priv)
      res.pageFault := False
      res.accessFault := False
      res.physAddr := vaddr

      when(res.active) {
        when(!vmSv39Canonical(vaddr)) {
          res.pageFault := True
        } otherwise {
          val tlb = vmTlbLookup(vaddr, priv, needX, needR, needW, tlbKind)
          val vpn2 = vaddr(38 downto 30)
          val vpn1 = vaddr(29 downto 21)
          val vpn0 = vaddr(20 downto 12)

          when(tlb.hit) {
            val phys = UInt(64 bits)
            phys := (tlb.physPage.resize(64) |<< 12) | vaddr(11 downto 0).resize(64)
            res.physAddr := phys
            when(!pmpAllow(phys, priv, needX, needR, needW, accessBytes) || !physAddrLegal(phys)) {
              res.accessFault := True
            }
          } otherwise {
            val l2Base = csrSatp(43 downto 0).asUInt.resize(64) |<< 12
            val l2PteAddr = l2Base + (vpn2.resize(64) |<< 3)
            val l2Pte = shadowLookup(l2PteAddr)
            val l2PmpOk = pmpAllow(l2PteAddr, PRV_S, needX = False, needR = True, needW = False, accessBytes = U(8, 64 bits)) && physAddrLegal(l2PteAddr)

            when(!l2PmpOk) {
              res.accessFault := True
            } elsewhen(!l2Pte.hit) {
              res.pageFault := True
            } otherwise {
              val pte = decodeShadowStoredPte(l2Pte.data, l2Pte.bigEndian)
              val invalid = !pte(0) || (!pte(1) && pte(2)) || pte(63 downto 54).orR
              val leaf = pte(1) || pte(3)
              when(invalid) {
                res.pageFault := True
              } elsewhen(leaf) {
                when(pte(27 downto 19).orR || pte(18 downto 10).orR) {
                  res.pageFault := True
                } elsewhen(!vmAccessPermitted(pte, priv, needX, needR, needW)) {
                  res.pageFault := True
                } otherwise {
                  val phys = vmComposePhysAddr(2, vaddr, pte)
                  res.physAddr := phys
                  when(!pmpAllow(phys, priv, needX, needR, needW, accessBytes) || !physAddrLegal(phys)) {
                    res.accessFault := True
                  }
                }
              } otherwise {
                val l1Base = pte(53 downto 10).asUInt.resize(64) |<< 12
                val l1PteAddr = l1Base + (vpn1.resize(64) |<< 3)
                val l1Pte = shadowLookup(l1PteAddr)
                val l1PmpOk = pmpAllow(l1PteAddr, PRV_S, needX = False, needR = True, needW = False, accessBytes = U(8, 64 bits)) && physAddrLegal(l1PteAddr)

                when(!l1PmpOk) {
                  res.accessFault := True
                } elsewhen(!l1Pte.hit) {
                  res.pageFault := True
                } otherwise {
                  val pte1 = decodeShadowStoredPte(l1Pte.data, l1Pte.bigEndian)
                  val invalid1 = !pte1(0) || (!pte1(1) && pte1(2)) || pte1(63 downto 54).orR
                  val leaf1 = pte1(1) || pte1(3)
                  when(invalid1) {
                    res.pageFault := True
                  } elsewhen(leaf1) {
                    when(pte1(18 downto 10).orR) {
                      res.pageFault := True
                    } elsewhen(!vmAccessPermitted(pte1, priv, needX, needR, needW)) {
                      res.pageFault := True
                    } otherwise {
                      val phys = vmComposePhysAddr(1, vaddr, pte1)
                      res.physAddr := phys
                      when(!pmpAllow(phys, priv, needX, needR, needW, accessBytes) || !physAddrLegal(phys)) {
                        res.accessFault := True
                      }
                    }
                  } otherwise {
                    val l0Base = pte1(53 downto 10).asUInt.resize(64) |<< 12
                    val l0PteAddr = l0Base + (vpn0.resize(64) |<< 3)
                    val l0Pte = shadowLookup(l0PteAddr)
                    val l0PmpOk = pmpAllow(l0PteAddr, PRV_S, needX = False, needR = True, needW = False, accessBytes = U(8, 64 bits)) && physAddrLegal(l0PteAddr)

                    when(!l0PmpOk) {
                      res.accessFault := True
                    } elsewhen(!l0Pte.hit) {
                      res.pageFault := True
                    } otherwise {
                      val pte0 = decodeShadowStoredPte(l0Pte.data, l0Pte.bigEndian)
                      val svnapot64k = pte0(63) && pte0(13) && !pte0(12 downto 10).orR
                      val invalid0 = !pte0(0) || (!pte0(1) && pte0(2)) || pte0(62 downto 54).orR || (pte0(63) && !svnapot64k)
                      val leaf0 = pte0(1) || pte0(3)
                      when(invalid0 || !leaf0) {
                        res.pageFault := True
                      } elsewhen(!vmAccessPermitted(pte0, priv, needX, needR, needW)) {
                        res.pageFault := True
                      } otherwise {
                        val phys = UInt(64 bits)
                        phys := vmComposePhysAddr(0, vaddr, pte0)
                        when(svnapot64k) {
                          phys := vmComposeSvnapot64kAddr(vaddr, pte0)
                        }
                        res.physAddr := phys
                        when(!pmpAllow(phys, priv, needX, needR, needW, accessBytes) || !physAddrLegal(phys)) {
                          res.accessFault := True
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      res
    }

    val loadBytes = UInt(64 bits)
    loadBytes := U(1, 64 bits)
    switch(up(Decoder.MicroCode)) {
      is(uopLH, uopLHU, uopFLH) { loadBytes := U(2, 64 bits) }
      is(uopLW, uopLWU, uopFLW) { loadBytes := U(4, 64 bits) }
      is(uopLD, uopFLD) { loadBytes := U(8, 64 bits) }
    }

    val storeBytes = UInt(64 bits)
    storeBytes := U(1, 64 bits)
    switch(up(Decoder.MicroCode)) {
      is(uopSH, uopFSH) { storeBytes := U(2, 64 bits) }
      is(uopSW, uopFSW) { storeBytes := U(4, 64 bits) }
      is(uopSD, uopFSD) { storeBytes := U(8, 64 bits) }
      is(uopCBOZERO) { storeBytes := U(64, 64 bits) }
    }

    val atomicWordAccess = up(Decoder.MicroCode).mux(
      uopAMOSWAPW -> True, uopAMOADDW -> True, uopAMOXORW -> True, uopAMOANDW -> True, uopAMOORW -> True,
      uopAMOMINW -> True, uopAMOMAXW -> True, uopAMOMINUW -> True, uopAMOMAXUW -> True, default -> False
    )
    val atomicDoubleAccess = up(Decoder.MicroCode).mux(
      uopAMOSWAPD -> True, uopAMOADDD -> True, uopAMOXORD -> True, uopAMOANDD -> True, uopAMOORD -> True,
      uopAMOMIND -> True, uopAMOMAXD -> True, uopAMOMINUD -> True, uopAMOMAXUD -> True, default -> False
    )
    when(atomicWordAccess) {
      loadBytes := U(4, 64 bits)
      storeBytes := U(4, 64 bits)
    }
    when(atomicDoubleAccess) {
      loadBytes := U(8, 64 bits)
      storeBytes := U(8, 64 bits)
    }

    val fetchBytes = UInt(64 bits)
    fetchBytes := U(4, 64 bits)
    if (config.cExtensionEnabled) {
      when(rawIsCompressed && !trapInsnDecodeIllegal) {
        fetchBytes := U(2, 64 bits)
      }
    }

    val fetchTranslatePriv = UInt(2 bits)
    fetchTranslatePriv := instPriv
    val vmFetch = vmTranslateSv39(up(PC.PC), instPriv, needX = True, needR = False, needW = False, accessBytes = fetchBytes, tlbKind = tlbKindI)
    val vmFetchBeat = vmTranslateSv39(fetch.io.vmTranslateVirt, fetchTranslatePriv, needX = True, needR = False, needW = False, accessBytes = U(8, 64 bits), tlbKind = tlbKindI)
    val vmBranchFetch = vmTranslateSv39(branch.logic.target, instPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits), tlbKind = tlbKindI)
    val vmLoad = vmTranslateSv39(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = True, needW = False, accessBytes = loadBytes, tlbKind = tlbKindD)
    val vmStore = vmTranslateSv39(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = False, needW = True, accessBytes = storeBytes, tlbKind = tlbKindD)

    fetch.io.vmTranslateEnable := vmFetchBeat.active && !vmFetchBeat.pageFault && !vmFetchBeat.accessFault
    fetch.io.vmTranslatePhys := vmFetchBeat.physAddr

    def fillSharedTlb(vaddr: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, physAddr: UInt): Unit = {
      val sharedIndex = U(itlbEntries + dtlbEntries, log2Up(totalTlbEntries) bits) + vmSharedTlbReplace.resize(log2Up(totalTlbEntries))
      vmTlbFill(sharedIndex, tlbKindShared, vaddr, priv, needX, needR, needW, physAddr)
      vmSharedTlbReplace := vmSharedTlbReplace + 1
    }

    lsu.io.useTranslatedAddr := False
    lsu.io.translatedAddr := lsu.logic.effectiveAddr
    lsu.io.bigEndian := False
    when(dataPriv === PRV_M) {
      lsu.io.bigEndian := csrMstatus(37)
    } elsewhen(dataPriv === PRV_S) {
      lsu.io.bigEndian := csrMstatus(36)
    }
    when(vmLoad.active && !vmLoad.pageFault && !vmLoad.accessFault) {
      lsu.io.useTranslatedAddr := True
      lsu.io.translatedAddr := vmLoad.physAddr
    }
    when(vmStore.active && !vmStore.pageFault && !vmStore.accessFault) {
      lsu.io.useTranslatedAddr := True
      lsu.io.translatedAddr := vmStore.physAddr
    }

    val fetchAddrForPerms = UInt(64 bits)
    fetchAddrForPerms := up(PC.PC)
    when(vmFetch.active && !vmFetch.pageFault && !vmFetch.accessFault) {
      fetchAddrForPerms := vmFetch.physAddr
    }

    val loadAddrForPerms = UInt(64 bits)
    loadAddrForPerms := lsu.logic.effectiveAddr
    when(vmLoad.active && !vmLoad.pageFault && !vmLoad.accessFault) {
      loadAddrForPerms := vmLoad.physAddr
    }

    val storeAddrForPerms = UInt(64 bits)
    storeAddrForPerms := lsu.logic.effectiveAddr
    when(vmStore.active && !vmStore.pageFault && !vmStore.accessFault) {
      storeAddrForPerms := vmStore.physAddr
    }

    val pmpExecAllowed = if (config.cExtensionEnabled) {
      val fetchPc = fetchAddrForPerms
      val isCompressed = rawIsCompressed && !trapInsnDecodeIllegal
      val loParcelExecAllowed = pmpAllow(fetchPc, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
      val hiParcelExecAllowed = pmpAllow(fetchPc + U(2, 64 bits), instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
      loParcelExecAllowed && (isCompressed || hiParcelExecAllowed)
    } else {
      pmpAllow(fetchAddrForPerms, instPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
    }

    val isAmoOp = up(Decoder.MicroCode).mux(
      uopAMOSWAPW -> True, uopAMOADDW -> True, uopAMOXORW -> True, uopAMOANDW -> True, uopAMOORW -> True,
      uopAMOMINW -> True, uopAMOMAXW -> True, uopAMOMINUW -> True, uopAMOMAXUW -> True,
      uopAMOSWAPD -> True, uopAMOADDD -> True, uopAMOXORD -> True, uopAMOANDD -> True, uopAMOORD -> True,
      uopAMOMIND -> True, uopAMOMAXD -> True, uopAMOMINUD -> True, uopAMOMAXUD -> True, default -> False
    )
    val pmpLoadAllowed = pmpAllow(loadAddrForPerms, dataPriv, needX = False, needR = True, needW = False, accessBytes = loadBytes)
    val pmpStoreAllowed = pmpAllow(storeAddrForPerms, dataPriv, needX = False, needR = False, needW = True, accessBytes = storeBytes)

    // Packetized fetch can legitimately have non-zero younger PCs in flight
    // before the oldest architectural PC=0 instruction retires, so treating a
    // later PC=0 observe as a stale-fetch fault is no longer sound here.
    val latePcZeroFetch = False
    val lowExecAccessFault = sawNonZeroPc && !physAddrLegal(fetchAddrForPerms)
    val lowLoadAccessFault = !physAddrLegal(loadAddrForPerms)
    val lowStoreAccessFault = !physAddrLegal(storeAddrForPerms)
    // Fetch permission faults must beat illegal-instruction classification, even
    // when the fetched bytes decode as garbage and never become a clean fire in
    // execute. The "none" PMP tests intentionally return to an unexecutable
    // lower-mode PC, so relying on up.isFiring misclassifies the re-faulting
    // fetch as mcause=2 instead of mcause=1.
    val pmpExecFault = trapInsnArrived && !vmFetch.pageFault && (vmFetch.accessFault || !pmpExecAllowed || lowExecAccessFault)
    val zfhMemLoadInsn = (insn(6 downto 0) === B"7'b0000111") && (insn(14 downto 12) === B"3'b001")
    val zfhMemStoreInsn = (insn(6 downto 0) === B"7'b0100111") && (insn(14 downto 12) === B"3'b001")
    val zfhMemAccessFire = sawNonZeroPc && up.isValid && up(Decoder.VALID) && up(LANE_SEL) &&
      !duplicateInWb && (zfhMemLoadInsn || zfhMemStoreInsn)
    val dataAccessFire = (aguFire || zfhMemAccessFire) && trapPcInRam && !fpStateDisabledIllegal
    val trapLoadAccess = lsu.logic.isLoad || (zfhMemAccessFire && zfhMemLoadInsn)
    val trapStoreAccess = lsu.logic.isStore || (zfhMemAccessFire && zfhMemStoreInsn)
    when(fetch.io.vmTranslateValid && vmFetchBeat.active && !vmFetchBeat.pageFault && !vmFetchBeat.accessFault) {
      val itlbIndex = vmItlbReplace.resize(log2Up(totalTlbEntries))
      vmTlbFill(itlbIndex, tlbKindI, fetch.io.vmTranslateVirt, fetchTranslatePriv, needX = True, needR = False, needW = False, vmFetchBeat.physAddr)
      vmItlbReplace := vmItlbReplace + 1
      fillSharedTlb(fetch.io.vmTranslateVirt, fetchTranslatePriv, needX = True, needR = False, needW = False, vmFetchBeat.physAddr)
    }
    when(dataAccessFire && trapLoadAccess && vmLoad.active && !vmLoad.pageFault && !vmLoad.accessFault) {
      val dtlbIndex = U(itlbEntries, log2Up(totalTlbEntries) bits) + vmDtlbReplace.resize(log2Up(totalTlbEntries))
      vmTlbFill(dtlbIndex, tlbKindD, lsu.logic.effectiveAddr, dataPriv, needX = False, needR = True, needW = False, vmLoad.physAddr)
      vmDtlbReplace := vmDtlbReplace + 1
      fillSharedTlb(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = True, needW = False, vmLoad.physAddr)
    } elsewhen(dataAccessFire && trapStoreAccess && vmStore.active && !vmStore.pageFault && !vmStore.accessFault) {
      val dtlbIndex = U(itlbEntries, log2Up(totalTlbEntries) bits) + vmDtlbReplace.resize(log2Up(totalTlbEntries))
      vmTlbFill(dtlbIndex, tlbKindD, lsu.logic.effectiveAddr, dataPriv, needX = False, needR = False, needW = True, vmStore.physAddr)
      vmDtlbReplace := vmDtlbReplace + 1
      fillSharedTlb(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = False, needW = True, vmStore.physAddr)
    }
    val pmpLoadFault = dataAccessFire && trapLoadAccess && !isAmoOp && !vmLoad.pageFault && (vmLoad.accessFault || !pmpLoadAllowed || lowLoadAccessFault)
    val pmpStoreFault = dataAccessFire && (
      (trapStoreAccess && !isAmoOp && !vmStore.pageFault && (vmStore.accessFault || !pmpStoreAllowed || lowStoreAccessFault)) ||
      (isAmoOp && !(vmLoad.pageFault || vmStore.pageFault) && (vmLoad.accessFault || vmStore.accessFault || !pmpLoadAllowed || !pmpStoreAllowed || lowLoadAccessFault || lowStoreAccessFault))
    )
    val pmpDataFault = pmpLoadFault || pmpStoreFault
    val trapFromBranchFetchPage = branch.logic.jumpCmd.valid && epochMatches && vmBranchFetch.active && vmBranchFetch.pageFault
    val trapFromBranchFetchAccess = branch.logic.jumpCmd.valid && epochMatches && vmBranchFetch.active && !vmBranchFetch.pageFault && vmBranchFetch.accessFault
    val trapFromFetchPage = trapInsnArrived && vmFetch.pageFault
    val trapFromLoadPage = dataAccessFire && trapLoadAccess && !isAmoOp && vmLoad.pageFault
    val trapFromStorePage = dataAccessFire && ((trapStoreAccess && !isAmoOp && vmStore.pageFault) || (isAmoOp && (vmLoad.pageFault || vmStore.pageFault)))
    lsu.io.pmpFault := pmpDataFault || trapFromLoadPage || trapFromStorePage

    val trapFromVectorLoadMisalign = vectorMemoryTrapValid && !vectorMemoryTrapIsStore
    val trapFromVectorStoreMisalign = vectorMemoryTrapValid && vectorMemoryTrapIsStore
    val trapFromLoadMisalign = (lsu.logic.misaligned && trapLoadAccess && isAmoOp && dataAccessFire) || trapFromVectorLoadMisalign
    val trapFromStoreMisalign = (lsu.logic.misaligned && trapStoreAccess && isAmoOp && dataAccessFire) || trapFromVectorStoreMisalign
    val trapFromLoadAccess = pmpLoadFault
    val trapFromStoreAccess = pmpStoreFault
    val trapFromFetchAccess = pmpExecFault
    val sretInsn = insn === B"32'h10200073"
    val sfenceVmaInsn = (insn(31 downto 25) === B"7'b0001001") && (insn(14 downto 12) === B"3'b000") && (insn(6 downto 0) === B"7'b1110011")
    val sinvalVmaInsn = (insn(31 downto 25) === B"7'b0001011") && (insn(14 downto 12) === B"3'b000") && (insn(6 downto 0) === B"7'b1110011")
    val sfenceWInvalInsn = insn === B"32'h18000073"
    val sfenceInvalIrInsn = insn === B"32'h18100073"
    val sfenceInsn = sfenceVmaInsn || sinvalVmaInsn || sfenceWInvalInsn || sfenceInvalIrInsn
    val mretInsn = insn === B"32'h30200073"
    val mretIllegal = mretInsn && (currentPriv =/= PRV_M)
    val sretIllegal = sretInsn && (currentPriv =/= PRV_S)
    val sfenceIllegal = sfenceInsn && ((currentPriv === PRV_U) || ((currentPriv === PRV_S) && csrMstatus(20)))
    when(stageExecFire && epochMatches && sfenceInsn && !sfenceIllegal) {
      vmTlbInvalidateAll()
    }
    val trapFromEcall = stageExecFire && insn === B"32'h00000073"
    val trapFromEbreak = stageExecFire && insn === B"32'h00100073"
    val isHandledSystem = mretInsn || sretInsn || sfenceInsn || trapFromEcall || trapFromEbreak
    val atomicOpcode = insn(6 downto 0) === B"0101111"
    val trapFromAtomicDisabled = if (config.aExtensionEnabled) False else atomicOpcode
    val trapFromIllegal32 = (trapInsnArrived && !trapInsnValid && !isHandledSystem) || trapFromAtomicDisabled
    val trapFromIllegalInsn = stageExecFire && (trapFromIllegal32 || csrIllegal || mretIllegal || sretIllegal || sfenceIllegal || fpStateDisabledIllegal)
    val mretFire = stageExecFire && epochMatches && mretInsn && (currentPriv === PRV_M)
    val sretFire = stageExecFire && epochMatches && sretInsn && (currentPriv === PRV_S)
    val returnFire = mretFire || sretFire
    val mretPriv = csrMstatus(12 downto 11).asUInt
    val sretPriv = UInt(2 bits)
    sretPriv := PRV_U
    when(csrMstatus(8)) {
      sretPriv := PRV_S
    }
    val mretNextMstatus = Bits(64 bits)
    mretNextMstatus := csrMstatus
    mretNextMstatus(3) := csrMstatus(7)
    mretNextMstatus(7) := True
    when(mretPriv =/= PRV_M) {
      mretNextMstatus(17) := False
    }
    mretNextMstatus(12 downto 11) := B"00"
    val sretNextMstatus = Bits(64 bits)
    sretNextMstatus := csrMstatus
    sretNextMstatus(1) := csrMstatus(5)
    sretNextMstatus(5) := True
    sretNextMstatus(8) := False
    val postReturnMstatus = Bits(64 bits)
    postReturnMstatus := csrMstatus
    when(mretFire) {
      postReturnMstatus := mretNextMstatus
    }
    when(sretFire) {
      postReturnMstatus := sretNextMstatus
    }
    val returnPriv = UInt(2 bits)
    returnPriv := mretPriv
    when(sretFire) {
      returnPriv := sretPriv
    }
    when(returnFire) {
      fetchTranslatePriv := returnPriv
    }
    val returnTarget = UInt(64 bits)
    returnTarget := mepcMasked(csrMepc).asUInt
    when(sretFire) {
      returnTarget := sepcMasked(csrSepc).asUInt
    }
    val vmReturnFetch = vmTranslateSv39(returnTarget, returnPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits), tlbKind = tlbKindI)
    val returnAddrForPerms = UInt(64 bits)
    returnAddrForPerms := returnTarget
    when(vmReturnFetch.active && !vmReturnFetch.pageFault && !vmReturnFetch.accessFault) {
      returnAddrForPerms := vmReturnFetch.physAddr
    }
    val pmpReturnExecAllowed = pmpAllow(returnAddrForPerms, returnPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
    val lowReturnExecAccessFault = sawNonZeroPc && !physAddrLegal(returnAddrForPerms)
    val trapFromReturnFetchPage = returnFire && vmReturnFetch.active && vmReturnFetch.pageFault
    val trapFromReturnFetchAccess = returnFire && !vmReturnFetch.pageFault && (vmReturnFetch.accessFault || !pmpReturnExecAllowed || lowReturnExecAccessFault)
    val returnTrap = trapFromReturnFetchAccess || trapFromReturnFetchPage
    val mretComplete = mretFire && !returnTrap
    val sretComplete = sretFire && !returnTrap
    val trapEligible = (stageExecFire || vectorMemoryTrapValid) && epochMatches
    val trapSelBranch = trapEligible && trapFromBranch
    val trapSelBranchFetchAccess = trapEligible && trapFromBranchFetchAccess
    val trapSelBranchFetchPage = trapEligible && trapFromBranchFetchPage
    val trapSelReturnFetchAccess = trapEligible && trapFromReturnFetchAccess
    val trapSelReturnFetchPage = trapEligible && trapFromReturnFetchPage
    val trapSelLoadMisalign = trapEligible && trapFromLoadMisalign
    val trapSelStoreMisalign = trapEligible && trapFromStoreMisalign
    val trapSelLoadAccess = trapEligible && trapFromLoadAccess
    val trapSelLoadPage = trapEligible && trapFromLoadPage
    val trapSelStoreAccess = trapEligible && trapFromStoreAccess
    val trapSelStorePage = trapEligible && trapFromStorePage
    val trapSelIllegalInsn = trapEligible && trapFromIllegalInsn
    val trapSelEcall = trapEligible && trapFromEcall
    val trapSelEbreak = trapEligible && trapFromEbreak
    val trapSelNonFetch =
      trapSelBranch || trapSelBranchFetchAccess || trapSelBranchFetchPage || trapSelReturnFetchAccess || trapSelReturnFetchPage ||
        trapSelLoadMisalign || trapSelStoreMisalign || trapSelLoadAccess || trapSelLoadPage ||
        trapSelStoreAccess || trapSelStorePage || trapSelIllegalInsn || trapSelEcall || trapSelEbreak
    val trapSelFetchAccess = trapEligible && trapFromFetchAccess && !trapSelNonFetch
    val trapSelFetchPage = trapEligible && trapFromFetchPage && !trapSelNonFetch && !trapFromFetchAccess
    val trapFire =
      trapSelBranch || trapSelBranchFetchAccess || trapSelBranchFetchPage || trapSelReturnFetchAccess || trapSelReturnFetchPage ||
        trapSelFetchAccess || trapSelFetchPage || trapSelLoadMisalign || trapSelStoreMisalign || trapSelLoadAccess ||
        trapSelLoadPage || trapSelStoreAccess || trapSelStorePage || trapSelIllegalInsn || trapSelEcall || trapSelEbreak

    val trapCause = Bits(64 bits)
    trapCause := CAUSE_MISALIGNED_STORE.asBits
    when(trapSelBranch) {
      trapCause := CAUSE_MISALIGNED_FETCH.asBits
    } elsewhen(trapSelBranchFetchAccess) {
      trapCause := CAUSE_FETCH_ACCESS.asBits
    } elsewhen(trapSelBranchFetchPage) {
      trapCause := CAUSE_FETCH_PAGE.asBits
    } elsewhen(trapSelReturnFetchAccess) {
      trapCause := CAUSE_FETCH_ACCESS.asBits
    } elsewhen(trapSelReturnFetchPage) {
      trapCause := CAUSE_FETCH_PAGE.asBits
    } elsewhen(trapSelFetchAccess) {
      trapCause := CAUSE_FETCH_ACCESS.asBits
    } elsewhen(trapSelFetchPage) {
      trapCause := CAUSE_FETCH_PAGE.asBits
    } elsewhen(trapSelLoadMisalign) {
      trapCause := CAUSE_MISALIGNED_LOAD.asBits
    } elsewhen(trapSelLoadAccess) {
      trapCause := CAUSE_LOAD_ACCESS.asBits
    } elsewhen(trapSelLoadPage) {
      trapCause := CAUSE_LOAD_PAGE.asBits
    } elsewhen(trapSelStoreAccess) {
      trapCause := CAUSE_STORE_ACCESS.asBits
    } elsewhen(trapSelStorePage) {
      trapCause := CAUSE_STORE_PAGE.asBits
    } elsewhen(trapSelIllegalInsn) {
      trapCause := CAUSE_ILLEGAL_INSTRUCTION.asBits
    } elsewhen(trapSelEbreak) {
      trapCause := U(3, 64 bits).asBits
    } elsewhen(trapSelEcall) {
      when(currentPriv === PRV_M) {
        trapCause := CAUSE_MACHINE_ECALL.asBits
      } elsewhen(currentPriv === PRV_S) {
        trapCause := CAUSE_SUPERVISOR_ECALL.asBits
      } otherwise {
        trapCause := CAUSE_USER_ECALL.asBits
      }
    }

    val pcRaw = up(PC.PC)
    val branchTargetRaw = branch.logic.target
    val memAddrRaw = lsu.logic.effectiveAddr
    val pcArch = pcRaw
    val branchTargetArch = branchTargetRaw
    val memAddrArch = memAddrRaw
    val secondParcelFetchFault = trapFromFetchAccess &&
      (if (config.cExtensionEnabled) True else False) &&
      !rawIsCompressed &&
      pmpAllow(fetchAddrForPerms, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits)) &&
      !pmpAllow(fetchAddrForPerms + U(2, 64 bits), instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))

    val trapTval = Bits(64 bits)
    trapTval := memAddrArch.asBits
    when((trapSelLoadAccess || trapSelLoadPage || trapSelStoreAccess || trapSelStorePage) && (memAddrRaw === U(0, 64 bits))) {
      trapTval := memAddrRaw.asBits
    }
    when(trapSelLoadMisalign && trapFromVectorLoadMisalign) {
      trapTval := vectorMemoryTrapTval
    } elsewhen(trapSelStoreMisalign && trapFromVectorStoreMisalign) {
      trapTval := vectorMemoryTrapTval
    } elsewhen(trapSelBranch) {
      trapTval := branchTargetArch.asBits
    } elsewhen(trapSelBranchFetchAccess || trapSelBranchFetchPage) {
      trapTval := branchTargetArch.asBits
    } elsewhen(trapSelReturnFetchAccess || trapSelReturnFetchPage) {
      trapTval := returnTarget.asBits
    } elsewhen(trapSelFetchAccess || trapSelFetchPage) {
      trapTval := Mux(secondParcelFetchFault, (pcArch + U(2, 64 bits)).asBits, (latePcZeroFetch ? pcRaw.asBits | pcArch.asBits))
    } elsewhen(trapSelIllegalInsn) {
      val illegalInsnBits = trapInsn
      trapTval := Mux(illegalInsnBits(1 downto 0) =/= B"11", illegalInsnBits(15 downto 0).asBits.resize(64), illegalInsnBits.resized)
    } elsewhen(trapSelEbreak) {
      trapTval := pcArch.asBits
    } elsewhen(trapSelEcall) {
      trapTval := B(0, 64 bits)
    }

    val trapEpc = Bits(64 bits)
    trapEpc := (latePcZeroFetch ? pcRaw.asBits | pcArch.asBits)
    when(trapSelBranchFetchAccess || trapSelBranchFetchPage) {
      trapEpc := branchTargetArch.asBits
    } elsewhen(trapSelReturnFetchAccess || trapSelReturnFetchPage) {
      trapEpc := returnTarget.asBits
    }

    val trapOriginPriv = UInt(2 bits)
    trapOriginPriv := currentPriv
    when(trapSelReturnFetchAccess || trapSelReturnFetchPage) {
      trapOriginPriv := returnPriv
    }

    when(trapFire) {
      when(vectorMemoryTrapValid) {
        csrVstart := vectorMemoryTrapElement
      }
      val delegatedToS = Bool()
      val trapMstatusBase = Bits(64 bits)
      delegatedToS := False
      trapMstatusBase := csrMstatus
      when(returnFire) {
        trapMstatusBase := postReturnMstatus
      }
      when(trapOriginPriv =/= PRV_M) {
        switch(trapCause(5 downto 0).asUInt) {
          for (i <- 0 until 64) {
            is(U(i, 6 bits)) { delegatedToS := csrMedeleg(i) }
          }
        }
      }
      when(delegatedToS) {
        val nextMstatus = Bits(64 bits)
        nextMstatus := trapMstatusBase
        nextMstatus(5) := trapMstatusBase(1)
        nextMstatus(1) := False
        nextMstatus(8) := trapOriginPriv === PRV_S
        csrMstatus := nextMstatus
        csrSepc := trapEpc
        csrScause := trapCause
        csrStval := trapTval
        currentPriv := PRV_S
      } otherwise {
        val nextMstatus = Bits(64 bits)
        nextMstatus := trapMstatusBase
        nextMstatus(7) := trapMstatusBase(3)
        nextMstatus(3) := False
        nextMstatus(12 downto 11) := trapOriginPriv.asBits
        csrMstatus := nextMstatus
        csrMepc := trapEpc
        csrMcause := trapCause
        csrMtval := trapTval
        currentPriv := PRV_M
      }
    }

    when(mretComplete) {
      currentPriv := mretPriv
      csrMstatus := mretNextMstatus
    }

    when(sretComplete) {
      currentPriv := sretPriv
      csrMstatus := sretNextMstatus
    }

    val mtvecBase = csrMtvec.asUInt & U(BigInt("FFFFFFFFFFFFFFFC", 16), 64 bits)
    val stvecBase = csrStvec.asUInt & U(BigInt("FFFFFFFFFFFFFFFC", 16), 64 bits)
    val delegatedTrap = Bool()
    delegatedTrap := False
    when(trapFire && (trapOriginPriv =/= PRV_M)) {
      switch(trapCause(5 downto 0).asUInt) {
        for (i <- 0 until 64) {
          is(U(i, 6 bits)) { delegatedTrap := csrMedeleg(i) }
        }
      }
    }
    when(trapFire) {
      fetchTranslatePriv := delegatedTrap ? PRV_S | PRV_M
    }
    pc.exception.valid.allowOverride := trapFire
    pc.exception.payload.vector.allowOverride := delegatedTrap ? stvecBase | mtvecBase

    redirect.trapFire := trapFire
    redirect.mretFire := mretComplete || sretComplete
    redirect.mretTarget := returnTarget
    redirect.trapCause := trapCause
    redirect.trapTval := trapTval

    // Keep trap ownership instruction-local. Raw trap-source unions can stay
    // high for non-firing or stale-epoch execute occupants, which incorrectly
    // tags adjacent instructions in writeback as traps.
    down(TRAP) := trapFire
  }
}
