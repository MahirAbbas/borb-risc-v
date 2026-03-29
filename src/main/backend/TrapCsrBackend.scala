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
import borb.frontend.DecodeTable
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.frontend.RVC
import borb.common.Common._
import borb.common.MicroCode._
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
    val csrMip = Reg(Bits(64 bits)) init 0
    val csrSie = Reg(Bits(64 bits)) init 0
    val csrSip = Reg(Bits(64 bits)) init 0
    val csrStvec = Reg(Bits(64 bits)) init 0
    val csrSscratch = Reg(Bits(64 bits)) init 0
    val csrSepc = Reg(Bits(64 bits)) init 0
    val csrScause = Reg(Bits(64 bits)) init 0
    val csrStval = Reg(Bits(64 bits)) init 0
    val csrFflags = Reg(Bits(5 bits)) init 0
    val csrFrm = Reg(Bits(3 bits)) init 0
    val csrSatp = Reg(Bits(64 bits)) init 0
    val pmpCfgBytes = Vec.fill(64)(Reg(Bits(8 bits)) init 0)
    val pmpAddrRegs = Vec.fill(64)(Reg(Bits(64 bits)) init B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
    val pmpImplementedEntries = 16
    val vmShadowEntries = 512
    val vmPteValid = Vec.fill(vmShadowEntries)(Reg(Bool()) init False)
    val vmPteAddr = Vec.fill(vmShadowEntries)(Reg(UInt(64 bits)) init 0)
    val vmPteData = Vec.fill(vmShadowEntries)(Reg(Bits(64 bits)) init 0)
    val vmPteBigEndian = Vec.fill(vmShadowEntries)(Reg(Bool()) init False)
    val vmPteReplace = Reg(UInt(log2Up(vmShadowEntries) bits)) init 0
    val vmTablePageEntries = 64
    val vmTablePageValid = Vec.fill(vmTablePageEntries)(Reg(Bool()) init False)
    val vmTablePageBase = Vec.fill(vmTablePageEntries)(Reg(UInt(64 bits)) init 0)
    val vmTablePageReplace = Reg(UInt(log2Up(vmTablePageEntries) bits)) init 0
    val currentPriv = Reg(UInt(2 bits)) init PRV_M
    val sawNonZeroPc = Reg(Bool) init False

    frm := csrFrm
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
      pte(0) && !(!pte(1) && pte(2)) && !pte(63 downto 54).orR
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

    def sstatusRead(): Bits = {
      val out = Bits(64 bits)
      out := 0
      out(63) := csrMstatus(63)
      out(36) := csrMstatus(36)
      out(33 downto 32) := csrMstatus(33 downto 32)
      out(19 downto 18) := csrMstatus(19 downto 18)
      out(16 downto 13) := csrMstatus(16 downto 13)
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

    def csrRead(addr: UInt): Bits = {
      val out = Bits(64 bits)
      out := 0
      switch(addr) {
        is(U"12'h300") { out := csrMstatus }
        is(U"12'h100") { out := sstatusRead() }
        is(U"12'h104") { out := csrSie }
        is(U"12'h105") { out := csrStvec }
        is(U"12'h140") { out := csrSscratch }
        is(U"12'h141") { out := sepcMasked(csrSepc) }
        is(U"12'h142") { out := csrScause }
        is(U"12'h143") { out := csrStval }
        is(U"12'h144") { out := csrSip }
        is(U"12'h301") { out := csrMisa }
        is(U"12'h302") { out := csrMedeleg }
        is(U"12'h305") { out := csrMtvec }
        is(U"12'h340") { out := csrMscratch }
        is(U"12'h341") { out := mepcMasked(csrMepc) }
        is(U"12'h342") { out := csrMcause }
        is(U"12'h343") { out := csrMtval }
        is(U"12'h344") { out := csrMip }
        is(U"12'h001") { out := B(0, 59 bits) ## csrFflags }
        is(U"12'h002") { out := B(0, 61 bits) ## csrFrm }
        is(U"12'h003") { out := B(0, 56 bits) ## csrFrm ## csrFflags }
        is(U"12'h180") { out := csrSatp }
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
      out
    }

    def csrSupported(addr: UInt): Bool = {
      val ok = Bool()
      ok := False
      switch(addr) {
        is(U"12'h100", U"12'h104", U"12'h105", U"12'h140", U"12'h141", U"12'h142", U"12'h143", U"12'h144") { ok := True }
        is(U"12'h300", U"12'h301", U"12'h302", U"12'h305", U"12'h340", U"12'h341", U"12'h342", U"12'h343", U"12'h344") { ok := True }
        is(U"12'h001", U"12'h002", U"12'h003", U"12'h180") { ok := True }
        is(U"12'h3A0", U"12'h3A2", U"12'h3A4", U"12'h3A6", U"12'h3A8", U"12'h3AA", U"12'h3AC", U"12'h3AE") { ok := True }
        is(U"12'hB00", U"12'hB02", U"12'hB03", U"12'hB04", U"12'hB05", U"12'hB06", U"12'hB07", U"12'hB08", U"12'hB09", U"12'hB0A", U"12'hB0B", U"12'hB0C", U"12'hB0D", U"12'hB0E", U"12'hB0F", U"12'hB10", U"12'hB11", U"12'hB12", U"12'hB13", U"12'hB14", U"12'hB15", U"12'hB16", U"12'hB17", U"12'hB18", U"12'hB19", U"12'hB1A", U"12'hB1B", U"12'hB1C", U"12'hB1D", U"12'hB1E", U"12'hB1F", U"12'hB20", U"12'hB21", U"12'hB22", U"12'hB23", U"12'hB24", U"12'hB25", U"12'hB26", U"12'hB27", U"12'hB28", U"12'hB29", U"12'hB2A", U"12'hB2B", U"12'hB2C", U"12'hB2D", U"12'hB2E", U"12'hB2F", U"12'hB30", U"12'hB31", U"12'hB32", U"12'hB33") { ok := True }
        is(U"12'hB80", U"12'hB82", U"12'hB83", U"12'hB84", U"12'hB85", U"12'hB86", U"12'hB87", U"12'hB88", U"12'hB89", U"12'hB8A", U"12'hB8B", U"12'hB8C", U"12'hB8D", U"12'hB8E", U"12'hB8F", U"12'hB90", U"12'hB91", U"12'hB92", U"12'hB93", U"12'hB94", U"12'hB95", U"12'hB96", U"12'hB97", U"12'hB98", U"12'hB99", U"12'hB9A", U"12'hB9B", U"12'hB9C", U"12'hB9D", U"12'hB9E", U"12'hB9F", U"12'hBA0", U"12'hBA1", U"12'hBA2", U"12'hBA3", U"12'hBA4", U"12'hBA5", U"12'hBA6", U"12'hBA7", U"12'hBA8", U"12'hBA9", U"12'hBAA", U"12'hBAB", U"12'hBAC", U"12'hBAD", U"12'hBAE", U"12'hBAF", U"12'hBB0", U"12'hBB1", U"12'hBB2", U"12'hBB3") { ok := True }
        is(U"12'hC00", U"12'hC02", U"12'hC80", U"12'hC82") { ok := True }
        for (i <- 0 until 64) {
          is(U(0x3B0 + i, 12 bits)) { ok := True }
        }
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
    // Raw 0x00000000 is a real architectural illegal instruction in tests like
    // the PMP TOR zero-address execution case, but the pipeline can also expose
    // startup garbage before the first real fetch packet advances. Use decode
    // validity or a nonzero fetch sequence to distinguish a real arrived
    // instruction from bootstrap junk.
    val trapInsnArrived = up.isValid && (up(Decoder.VALID) || (up(Fetch.FETCH_SEQ) =/= 0))
    val trapInsnDecodeIllegal = if (config.cExtensionEnabled) rawIsCompressed && rawRvc.illegal else False
    val trapInsnSupported = Symplify(trapInsn, decodeMasks)
    val trapInsnValid = trapInsnSupported && !trapInsnDecodeIllegal

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
    val csrIllegal = trapInsnValid && isCsrOp && (!csrSupported(csrAddr) || (currentPriv < csrPrivReq) || (csrWriteEn && csrReadOnly) || csrSatpIllegal)
    val csrFire = up.isFiring && epochMatches && up(Decoder.VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOALU) && isCsrOp

    when(csrFire && !csrIllegal && csrWriteEn) {
      switch(csrAddr) {
        is(U"12'h100") { writeSstatus(csrWriteData) }
        is(U"12'h104") { csrSie := csrWriteData }
        is(U"12'h105") { csrStvec := csrWriteData }
        is(U"12'h140") { csrSscratch := csrWriteData }
        is(U"12'h141") { csrSepc := csrWriteData }
        is(U"12'h142") { csrScause := csrWriteData }
        is(U"12'h143") { csrStval := csrWriteData }
        is(U"12'h144") { csrSip := csrWriteData }
        is(U"12'h300") { csrMstatus := sanitizeMstatus(csrWriteData) }
        is(U"12'h301") { csrMisa := csrWriteData }
        is(U"12'h302") { csrMedeleg := csrWriteData }
        is(U"12'h305") { csrMtvec := csrWriteData }
        is(U"12'h340") { csrMscratch := csrWriteData }
        is(U"12'h341") { csrMepc := csrWriteData }
        is(U"12'h342") { csrMcause := csrWriteData }
        is(U"12'h343") { csrMtval := csrWriteData }
        is(U"12'h344") { csrMip := csrWriteData }
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
        is(U"12'h180") { csrSatp := csrWriteData }
        is(U"12'h3A0") {
          for (i <- 0 until 8) {
            when(!pmpCfgBytes(i)(7)) {
              pmpCfgBytes(i) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A2") {
          for (i <- 0 until 8) {
            val idx = 8 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A4") {
          for (i <- 0 until 8) {
            val idx = 16 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A6") {
          for (i <- 0 until 8) {
            val idx = 24 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3A8") {
          for (i <- 0 until 8) {
            val idx = 32 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3AA") {
          for (i <- 0 until 8) {
            val idx = 40 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3AC") {
          for (i <- 0 until 8) {
            val idx = 48 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        is(U"12'h3AE") {
          for (i <- 0 until 8) {
            val idx = 56 + i
            when(!pmpCfgBytes(idx)(7)) {
              pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
            }
          }
        }
        for (i <- 0 until 64) {
          is(U(0x3B0 + i, 12 bits)) {
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
    val aguFire = up(Decoder.VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOAGU) && !duplicateInWb

    when(up.isFiring && up(LANE_SEL) && (up(PC.PC) =/= U(0, 64 bits))) {
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

    def vmTranslateSv39(vaddr: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, accessBytes: UInt): VmAccessResult = {
      val res = VmAccessResult()
      res.active := vmSv39Active(priv)
      res.pageFault := False
      res.accessFault := False
      res.physAddr := vaddr

      when(res.active) {
        when(!vmSv39Canonical(vaddr)) {
          res.pageFault := True
        } otherwise {
          val vpn2 = vaddr(38 downto 30)
          val vpn1 = vaddr(29 downto 21)
          val vpn0 = vaddr(20 downto 12)

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
                    val invalid0 = !pte0(0) || (!pte0(1) && pte0(2)) || pte0(63 downto 54).orR
                    val leaf0 = pte0(1) || pte0(3)
                    when(invalid0 || !leaf0) {
                      res.pageFault := True
                    } elsewhen(!vmAccessPermitted(pte0, priv, needX, needR, needW)) {
                      res.pageFault := True
                    } otherwise {
                      val phys = vmComposePhysAddr(0, vaddr, pte0)
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

      res
    }

    val loadBytes = UInt(64 bits)
    loadBytes := U(1, 64 bits)
    switch(up(Decoder.MicroCode)) {
      is(uopLH, uopLHU) { loadBytes := U(2, 64 bits) }
      is(uopLW, uopLWU) { loadBytes := U(4, 64 bits) }
      is(uopLD) { loadBytes := U(8, 64 bits) }
    }

    val storeBytes = UInt(64 bits)
    storeBytes := U(1, 64 bits)
    switch(up(Decoder.MicroCode)) {
      is(uopSH) { storeBytes := U(2, 64 bits) }
      is(uopSW) { storeBytes := U(4, 64 bits) }
      is(uopSD) { storeBytes := U(8, 64 bits) }
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
    val vmFetch = vmTranslateSv39(up(PC.PC), instPriv, needX = True, needR = False, needW = False, accessBytes = fetchBytes)
    val vmFetchBeat = vmTranslateSv39(fetch.io.vmTranslateVirt, fetchTranslatePriv, needX = True, needR = False, needW = False, accessBytes = U(8, 64 bits))
    val vmBranchFetch = vmTranslateSv39(branch.logic.target, instPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
    val vmLoad = vmTranslateSv39(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = True, needW = False, accessBytes = loadBytes)
    val vmStore = vmTranslateSv39(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = False, needW = True, accessBytes = storeBytes)

    fetch.io.vmTranslateEnable := vmFetchBeat.active && !vmFetchBeat.pageFault && !vmFetchBeat.accessFault
    fetch.io.vmTranslatePhys := vmFetchBeat.physAddr

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
    val lowExecAccessFault = !physAddrLegal(fetchAddrForPerms)
    val lowLoadAccessFault = !physAddrLegal(loadAddrForPerms)
    val lowStoreAccessFault = !physAddrLegal(storeAddrForPerms)
    // Fetch permission faults must beat illegal-instruction classification, even
    // when the fetched bytes decode as garbage and never become a clean fire in
    // execute. The "none" PMP tests intentionally return to an unexecutable
    // lower-mode PC, so relying on up.isFiring misclassifies the re-faulting
    // fetch as mcause=2 instead of mcause=1.
    val pmpExecFault = trapInsnArrived && !vmFetch.pageFault && (vmFetch.accessFault || !pmpExecAllowed || lowExecAccessFault)
    val pmpLoadFault = aguFire && lsu.logic.isLoad && !isAmoOp && !vmLoad.pageFault && (vmLoad.accessFault || !pmpLoadAllowed || lowLoadAccessFault)
    val pmpStoreFault = aguFire && (
      (lsu.logic.isStore && !isAmoOp && !vmStore.pageFault && (vmStore.accessFault || !pmpStoreAllowed || lowStoreAccessFault)) ||
      (isAmoOp && !(vmLoad.pageFault || vmStore.pageFault) && (vmLoad.accessFault || vmStore.accessFault || !pmpLoadAllowed || !pmpStoreAllowed || lowLoadAccessFault || lowStoreAccessFault))
    )
    val pmpDataFault = pmpLoadFault || pmpStoreFault
    val trapFromBranchFetchPage = branch.logic.jumpCmd.valid && epochMatches && vmBranchFetch.active && vmBranchFetch.pageFault
    val trapFromBranchFetchAccess = branch.logic.jumpCmd.valid && epochMatches && vmBranchFetch.active && !vmBranchFetch.pageFault && vmBranchFetch.accessFault
    val trapFromFetchPage = trapInsnArrived && vmFetch.pageFault
    val trapFromLoadPage = aguFire && lsu.logic.isLoad && !isAmoOp && vmLoad.pageFault
    val trapFromStorePage = aguFire && ((lsu.logic.isStore && !isAmoOp && vmStore.pageFault) || (isAmoOp && (vmLoad.pageFault || vmStore.pageFault)))
    lsu.io.pmpFault := pmpDataFault || trapFromLoadPage || trapFromStorePage

    val trapFromLoadMisalign = lsu.logic.misaligned && lsu.logic.isLoad && !isAmoOp && aguFire
    val trapFromStoreMisalign = lsu.logic.misaligned && lsu.logic.isStore && aguFire
    val trapFromLoadAccess = pmpLoadFault
    val trapFromStoreAccess = pmpStoreFault
    val trapFromFetchAccess = pmpExecFault
    val sretInsn = insn === B"32'h10200073"
    val sfenceInsn = (insn(31 downto 25) === B"7'b0001001") && (insn(14 downto 12) === B"3'b000") && (insn(6 downto 0) === B"7'b1110011")
    val mretInsn = insn === B"32'h30200073"
    val mretIllegal = mretInsn && (currentPriv =/= PRV_M)
    val sretIllegal = sretInsn && (currentPriv =/= PRV_S)
    val sfenceIllegal = sfenceInsn && ((currentPriv === PRV_U) || ((currentPriv === PRV_S) && csrMstatus(20)))
    val trapFromEcall = up.isFiring && insn === B"32'h00000073"
    val trapFromEbreak = up.isFiring && insn === B"32'h00100073"
    val isHandledSystem = mretInsn || sretInsn || sfenceInsn || trapFromEcall || trapFromEbreak
    val atomicOpcode = insn(6 downto 0) === B"0101111"
    val trapFromAtomicDisabled = if (config.aExtensionEnabled) False else atomicOpcode
    val trapFromIllegal32 = (trapInsnArrived && !trapInsnValid && !isHandledSystem) || trapFromAtomicDisabled
    val trapFromIllegalInsn = up.isFiring && (trapFromIllegal32 || csrIllegal || mretIllegal || sretIllegal || sfenceIllegal)
    val mretFire = up.isFiring && epochMatches && mretInsn && (currentPriv === PRV_M)
    val sretFire = up.isFiring && epochMatches && sretInsn && (currentPriv === PRV_S)
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
    val vmReturnFetch = vmTranslateSv39(returnTarget, returnPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
    val returnAddrForPerms = UInt(64 bits)
    returnAddrForPerms := returnTarget
    when(vmReturnFetch.active && !vmReturnFetch.pageFault && !vmReturnFetch.accessFault) {
      returnAddrForPerms := vmReturnFetch.physAddr
    }
    val pmpReturnExecAllowed = pmpAllow(returnAddrForPerms, returnPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
    val lowReturnExecAccessFault = !physAddrLegal(returnAddrForPerms)
    val trapFromReturnFetchPage = returnFire && vmReturnFetch.active && vmReturnFetch.pageFault
    val trapFromReturnFetchAccess = returnFire && !vmReturnFetch.pageFault && (vmReturnFetch.accessFault || !pmpReturnExecAllowed || lowReturnExecAccessFault)
    val returnTrap = trapFromReturnFetchAccess || trapFromReturnFetchPage
    val mretComplete = mretFire && !returnTrap
    val sretComplete = sretFire && !returnTrap
    val trapEligible = up.isFiring && epochMatches
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
    when(trapSelBranch) {
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

    down(TRAP) := trapFromBranch || trapFromBranchFetchAccess || trapFromBranchFetchPage || trapFromReturnFetchAccess || trapFromReturnFetchPage || trapFromFetchAccess || trapFromFetchPage || trapFromLoadMisalign || trapFromStoreMisalign || trapFromLoadAccess || trapFromLoadPage || trapFromStoreAccess || trapFromStorePage || trapFromIllegalInsn || trapFromEcall || trapFromEbreak
  }
}
