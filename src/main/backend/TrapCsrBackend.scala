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
import borb.memory.{Pmp, Sv39, VmContext}
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
    branch: Branch,
    lsu: Lsu,
    perfCounters: PerfCountersBundle
) extends Area {
  val fpFlagsSetValid = Bool()
  val fpFlagsSetBits = Bits(5 bits)
  val frm = Bits(3 bits)
  val csrIntResult = IntResultIntent()
  val redirect = TrapRedirectOutcome()
  val vmContext = VmContext()

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

    val mstatusInit = if (config.xlen == 64) BigInt("0000000A00000000", 16) else BigInt(0)
    val csrMstatus = Reg(Bits(64 bits)) init B(mstatusInit, 64 bits)
    val misaBase = BigInt("8000000000000100", 16)
    val misaA = if (config.aExtensionEnabled) BigInt("0000000000000001", 16) else BigInt(0)
    val misaC = if (config.cExtensionEnabled) BigInt("0000000000000004", 16) else BigInt(0)
    val misaF = if (config.fExtensionEnabled) BigInt("0000000000000020", 16) else BigInt(0)
    val misaM = if (config.mExtensionEnabled) BigInt("0000000000001000", 16) else BigInt(0)
    val misaD = if (config.dExtensionEnabled) BigInt("0000000000000008", 16) else BigInt(0)
    val csrMisa = Reg(Bits(64 bits)) init B(misaBase | misaA | misaC | misaF | misaM | misaD, 64 bits)
    val csrMedeleg = Reg(Bits(64 bits)) init 0
    val csrMideleg = Reg(Bits(64 bits)) init 0
    val csrMie = Reg(Bits(64 bits)) init 0
    val csrMtvec = Reg(Bits(64 bits)) init 0
    val csrMscratch = Reg(Bits(64 bits)) init 0
    val csrMepc = Reg(Bits(64 bits)) init 0
    val csrMcause = Reg(Bits(64 bits)) init 0
    val csrMtval = Reg(Bits(64 bits)) init 0
    val csrMip = Reg(Bits(64 bits)) init 0
    val csrStvec = Reg(Bits(64 bits)) init 0
    val csrSscratch = Reg(Bits(64 bits)) init 0
    val csrSepc = Reg(Bits(64 bits)) init 0
    val csrScause = Reg(Bits(64 bits)) init 0
    val csrStval = Reg(Bits(64 bits)) init 0
    val csrScounteren = Reg(Bits(64 bits)) init 0
    val csrFflags = Reg(Bits(5 bits)) init 0
    val csrFrm = Reg(Bits(3 bits)) init 0
    val csrSatp = Reg(Bits(64 bits)) init 0
    val pmpCfgBytes = Vec.fill(64)(Reg(Bits(8 bits)) init 0)
    val pmpAddrRegs = Vec.fill(64)(Reg(Bits(64 bits)) init B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
    val pmpImplementedEntries = 16
    val currentPriv = Reg(UInt(2 bits)) init PRV_M
    val sawNonZeroPc = Reg(Bool) init False

    frm := csrFrm
    when(fpFlagsSetValid) {
      csrFflags := csrFflags | fpFlagsSetBits
    }
    val csrFflagsRead = Bits(5 bits)
    csrFflagsRead := csrFflags
    when(fpFlagsSetValid) {
      csrFflagsRead := csrFflags | fpFlagsSetBits
    }

    val sstatusMask = B(BigInt("0000000300066122", 16), 64 bits)
    val supervisorInterruptMask = B(BigInt("0000000000000222", 16), 64 bits)
    val satpModeBare = if (config.xlen == 64) U(0, 4 bits) else U(0, 1 bits)
    val satpModeSv39 = if (config.xlen == 64) U(8, 4 bits) else U(0, 1 bits)

    def applyMaskedWrite(oldValue: Bits, newValue: Bits, mask: Bits): Bits = {
      (oldValue & ~mask) | (newValue & mask)
    }

    def sstatusReadValue(): Bits = {
      val out = Bits(64 bits)
      out := csrMstatus & sstatusMask
      out
    }

    def sanitizeMstatusWrite(newValue: Bits): Bits = {
      val out = Bits(64 bits)
      out := newValue
      if (config.xlen == 64) {
        // RV64 width state is fixed in this core. Keep UXL/SXL at 64-bit even
        // when software writes mstatus/sstatus directly.
        out(35 downto 34) := B"10"
        out(33 downto 32) := B"10"
      }
      out
    }

    def satpWriteLegal(newValue: Bits): Bool = {
      if (config.xlen == 64) {
        val mode = newValue(63 downto 60).asUInt
        (mode === satpModeBare) || (mode === satpModeSv39)
      } else {
        True
      }
    }

    def satpSanitize(oldValue: Bits, newValue: Bits): Bits = {
      val out = Bits(64 bits)
      out := oldValue
      when(satpWriteLegal(newValue)) {
        out := newValue
      }
      out
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

    def mepcMasked(raw: Bits): Bits = {
      val out = Bits(64 bits)
      out := raw
      out(0) := False
      when(!csrMisa(2)) {
        out(1) := False
      }
      out
    }

    def csrRead(addr: UInt): Bits = {
      val out = Bits(64 bits)
      out := 0
      switch(addr) {
        is(U"12'h100") { out := sstatusReadValue() }
        is(U"12'h104") { out := csrMie & supervisorInterruptMask }
        is(U"12'h105") { out := csrStvec }
        is(U"12'h106") { out := csrScounteren }
        is(U"12'h140") { out := csrSscratch }
        is(U"12'h141") { out := mepcMasked(csrSepc) }
        is(U"12'h142") { out := csrScause }
        is(U"12'h143") { out := csrStval }
        is(U"12'h144") { out := csrMip & supervisorInterruptMask }
        is(U"12'h300") { out := csrMstatus }
        is(U"12'h301") { out := csrMisa }
        is(U"12'h302") { out := csrMedeleg }
        is(U"12'h303") { out := csrMideleg }
        is(U"12'h304") { out := csrMie }
        is(U"12'h305") { out := csrMtvec }
        is(U"12'h340") { out := csrMscratch }
        is(U"12'h341") { out := mepcMasked(csrMepc) }
        is(U"12'h342") { out := csrMcause }
        is(U"12'h343") { out := csrMtval }
        is(U"12'h344") { out := csrMip }
        is(U"12'h001") { out := B(0, 59 bits) ## csrFflagsRead }
        is(U"12'h002") { out := B(0, 61 bits) ## csrFrm }
        is(U"12'h003") { out := B(0, 56 bits) ## csrFrm ## csrFflagsRead }
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
        is(U"12'h100", U"12'h104", U"12'h105", U"12'h106", U"12'h140", U"12'h141", U"12'h142", U"12'h143", U"12'h144") { ok := True }
        is(U"12'h300", U"12'h301", U"12'h302", U"12'h303", U"12'h304", U"12'h305", U"12'h340", U"12'h341", U"12'h342", U"12'h343", U"12'h344") { ok := True }
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

    val rawOpcode = trapInsn(6 downto 0)
    val rawFunct3 = trapInsn(14 downto 12)
    val rawFunct7 = trapInsn(31 downto 25)
    val rawSystemInsn = rawOpcode === B"7'b1110011"
    val rawCsrOp = trapInsnArrived && rawSystemInsn && (rawFunct3 =/= B"000")
    val csrAddr = trapInsn(31 downto 20).asUInt
    val csrOld = csrRead(csrAddr)
    val csrRs1 = up(borb.dispatch.SrcPlugin.RS1)
    val csrZimm = B(59 bits, default -> False) ## trapInsn(19 downto 15)
    val csrWriteData = Bits(64 bits)
    csrWriteData := csrOld
    val csrWriteEn = Bool()
    csrWriteEn := False

    val isCsrOp = rawCsrOp
    val csrPrivReq = csrAddr(9 downto 8)
    val csrReadOnly = csrAddr(11 downto 10) === U"2'b11"

    switch(rawFunct3) {
      is(B"001") { csrWriteData := csrRs1; csrWriteEn := True }
      is(B"010") { csrWriteData := csrOld | csrRs1; csrWriteEn := csrRs1 =/= 0 }
      is(B"011") { csrWriteData := csrOld & ~csrRs1; csrWriteEn := csrRs1 =/= 0 }
      is(B"101") { csrWriteData := csrZimm; csrWriteEn := True }
      is(B"110") { csrWriteData := csrOld | csrZimm; csrWriteEn := csrZimm =/= 0 }
      is(B"111") { csrWriteData := csrOld & ~csrZimm; csrWriteEn := csrZimm =/= 0 }
    }

    val csrSatpAccessIllegal = isCsrOp && (csrAddr === U"12'h180") && (currentPriv === PRV_S) && csrMstatus(20)
    val csrAccessIllegal = isCsrOp && (!csrSupported(csrAddr) || (currentPriv < csrPrivReq) || (csrWriteEn && csrReadOnly) || csrSatpAccessIllegal)
    val csrIllegal = trapInsnArrived && csrAccessIllegal
    val csrFire = up.isFiring && epochMatches && up(Decoder.VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOALU) && isCsrOp

    when(csrFire && !csrIllegal && csrWriteEn) {
      switch(csrAddr) {
        is(U"12'h100") { csrMstatus := sanitizeMstatusWrite(applyMaskedWrite(csrMstatus, csrWriteData, sstatusMask)) }
        is(U"12'h104") { csrMie := applyMaskedWrite(csrMie, csrWriteData, supervisorInterruptMask) }
        is(U"12'h105") { csrStvec := csrWriteData }
        is(U"12'h106") { csrScounteren := csrWriteData }
        is(U"12'h140") { csrSscratch := csrWriteData }
        is(U"12'h141") { csrSepc := csrWriteData }
        is(U"12'h142") { csrScause := csrWriteData }
        is(U"12'h143") { csrStval := csrWriteData }
        is(U"12'h144") { csrMip := applyMaskedWrite(csrMip, csrWriteData, supervisorInterruptMask) }
        is(U"12'h300") { csrMstatus := sanitizeMstatusWrite(csrWriteData) }
        is(U"12'h301") { csrMisa := csrWriteData }
        is(U"12'h302") { csrMedeleg := csrWriteData }
        is(U"12'h303") { csrMideleg := csrWriteData }
        is(U"12'h304") { csrMie := csrWriteData }
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
        is(U"12'h180") { csrSatp := satpSanitize(csrSatp, csrWriteData) }
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
    val aguActive = up(Decoder.VALID) && up(LANE_SEL) && lsu.logic.isAguRoute

    when(up.isFiring && up(LANE_SEL) && (up(PC.INSN_PC) =/= U(0, 64 bits))) {
      sawNonZeroPc := True
    }

    val mstatusMprv = csrMstatus(17)
    val mstatusMpp = csrMstatus(12 downto 11).asUInt
    val dataPriv = Mux((currentPriv === PRV_M) && mstatusMprv, mstatusMpp, currentPriv)
    val instPriv = currentPriv
    val translatedInstMode = (instPriv =/= PRV_M) && Sv39.isSv39(csrSatp)
    val translatedDataMode = (dataPriv =/= PRV_M) && Sv39.isSv39(csrSatp)

    def pmpAllow(addrRaw: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, accessBytes: UInt): Bool =
      Pmp.allow(pmpCfgBytes, pmpAddrRegs, pmpImplementedEntries, addrRaw, priv, needX, needR, needW, accessBytes, PRV_M)

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

    val pmpExecAllowed = if (config.cExtensionEnabled) {
      val fetchPc = up(PC.INSN_PC)
      val fetchPhysPc = up(Fetch.FETCH_PHYS_PC)
      val fetchSecondPhysPc = up(Fetch.FETCH_SECOND_PHYS_PC)
      val isCompressed = rawIsCompressed && !trapInsnDecodeIllegal
      val loParcelExecAllowed = pmpAllow(fetchPc, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
      val hiParcelExecAllowed = pmpAllow(fetchPc + U(2, 64 bits), instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
      val loTranslatedExecAllowed = pmpAllow(fetchPhysPc, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
      val hiTranslatedExecAllowed = pmpAllow(fetchSecondPhysPc, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
      translatedInstMode ? (loTranslatedExecAllowed && (isCompressed || hiTranslatedExecAllowed)) | (loParcelExecAllowed && (isCompressed || hiParcelExecAllowed))
    } else {
      translatedInstMode ? pmpAllow(up(Fetch.FETCH_PHYS_PC), instPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits)) |
        pmpAllow(up(PC.INSN_PC), instPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
    }

    val isAmoOp = up(Decoder.MicroCode).mux(
      uopAMOSWAPW -> True, uopAMOADDW -> True, uopAMOXORW -> True, uopAMOANDW -> True, uopAMOORW -> True,
      uopAMOMINW -> True, uopAMOMAXW -> True, uopAMOMINUW -> True, uopAMOMAXUW -> True,
      uopAMOSWAPD -> True, uopAMOADDD -> True, uopAMOXORD -> True, uopAMOANDD -> True, uopAMOORD -> True,
      uopAMOMIND -> True, uopAMOMAXD -> True, uopAMOMINUD -> True, uopAMOMAXUD -> True, default -> False
    )
    val pmpLoadAllowed = translatedDataMode || pmpAllow(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = True, needW = False, accessBytes = loadBytes)
    val pmpStoreAllowed = translatedDataMode || pmpAllow(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = False, needW = True, accessBytes = storeBytes)

    // Packetized fetch can legitimately have non-zero younger PCs in flight
    // before the oldest architectural PC=0 instruction retires, so treating a
    // later PC=0 observe as a stale-fetch fault is no longer sound here.
    val latePcZeroFetch = False
    val fetchPageFault = up(Fetch.FETCH_PAGE_FAULT) || up(Fetch.FETCH_SECOND_PAGE_FAULT)
    val fetchAccessFault = up(Fetch.FETCH_ACCESS_FAULT) || up(Fetch.FETCH_SECOND_ACCESS_FAULT)
    val pmpExecFault = up.isFiring && epochMatches && trapInsnArrived && !fetchPageFault && !fetchAccessFault && !pmpExecAllowed
    val pmpLoadFault = aguActive && lsu.logic.isLoad && !isAmoOp && !pmpLoadAllowed
    val pmpStoreFault = aguActive && (
      (lsu.logic.isStore && !isAmoOp && !pmpStoreAllowed) ||
      (isAmoOp && (!pmpLoadAllowed || !pmpStoreAllowed))
    )
    val pmpDataFault = pmpLoadFault || pmpStoreFault
    lsu.io.pmpFault := pmpDataFault

    val trapFromLoadMisalign = lsu.logic.misaligned && lsu.logic.isLoad && !isAmoOp && aguActive
    val trapFromStoreMisalign = lsu.logic.misaligned && lsu.logic.isStore && aguActive
    val trapFromLoadPage = aguActive && lsu.logic.isLoad && lsu.logic.pageFaultActive
    val trapFromStorePage = aguActive && (lsu.logic.isStore || isAmoOp) && lsu.logic.pageFaultActive
    val trapFromLoadAccess = pmpLoadFault || (aguActive && lsu.logic.isLoad && lsu.logic.accessFaultActive)
    val trapFromStoreAccess = pmpStoreFault || (aguActive && (lsu.logic.isStore || isAmoOp) && lsu.logic.accessFaultActive)
    val trapFromFetchPage = up.isFiring && epochMatches && trapInsnArrived && fetchPageFault
    val trapFromFetchAccess = up.isFiring && epochMatches && trapInsnArrived && (fetchAccessFault || pmpExecFault)
    val mretInsn = insn === B"32'h30200073"
    val sretInsn = insn === B"32'h10200073"
    val sfenceVmaInsn = rawSystemInsn && (rawFunct3 === B"000") && (rawFunct7 === B"0001001")
    val mretIllegal = mretInsn && (currentPriv =/= PRV_M)
    val sretIllegal = sretInsn && ((currentPriv =/= PRV_S) || csrMstatus(22))
    val sfenceVmaIllegal = sfenceVmaInsn && ((currentPriv === PRV_U) || ((currentPriv === PRV_S) && csrMstatus(20)))
    val trapFromEcall = up.isFiring && insn === B"32'h00000073"
    val trapFromEbreak = up.isFiring && insn === B"32'h00100073"
    val isHandledSystem = mretInsn || sretInsn || sfenceVmaInsn || trapFromEcall || trapFromEbreak
    val atomicOpcode = insn(6 downto 0) === B"0101111"
    val trapFromAtomicDisabled = if (config.aExtensionEnabled) False else atomicOpcode
    val trapFromIllegal32 = (trapInsnArrived && !trapInsnValid && !isHandledSystem) || trapFromAtomicDisabled
    val trapFromIllegalInsn = up.isFiring && (trapFromIllegal32 || (trapInsnArrived && csrAccessIllegal) || mretIllegal || sretIllegal || sfenceVmaIllegal)
    val mretFire = up.isFiring && epochMatches && mretInsn && (currentPriv === PRV_M)
    val sretFire = up.isFiring && epochMatches && sretInsn && (currentPriv === PRV_S) && !csrMstatus(22)
    val mretTarget = mepcMasked(csrMepc).asUInt
    val sretTarget = mepcMasked(csrSepc).asUInt
    val memoryTrapPending =
      trapFromLoadMisalign || trapFromStoreMisalign ||
      trapFromLoadPage || trapFromStorePage ||
      trapFromLoadAccess || trapFromStoreAccess
    val trapFire = epochMatches && (
      (up.isFiring && (trapFromBranch || trapFromFetchPage || trapFromFetchAccess || trapFromIllegalInsn || trapFromEcall || trapFromEbreak)) ||
      memoryTrapPending
    )

    val trapCause = Bits(64 bits)
    trapCause := CAUSE_MISALIGNED_STORE.asBits
    when(trapFromBranch) {
      trapCause := CAUSE_MISALIGNED_FETCH.asBits
    } elsewhen(trapFromFetchPage) {
      trapCause := CAUSE_FETCH_PAGE.asBits
    } elsewhen(trapFromFetchAccess) {
      trapCause := CAUSE_FETCH_ACCESS.asBits
    } elsewhen(trapFromIllegalInsn) {
      trapCause := CAUSE_ILLEGAL_INSTRUCTION.asBits
    } elsewhen(trapFromEbreak) {
      trapCause := U(3, 64 bits).asBits
    } elsewhen(trapFromEcall) {
      when(currentPriv === PRV_M) {
        trapCause := CAUSE_MACHINE_ECALL.asBits
      } elsewhen(currentPriv === PRV_S) {
        trapCause := CAUSE_SUPERVISOR_ECALL.asBits
      } otherwise {
        trapCause := CAUSE_USER_ECALL.asBits
      }
    } elsewhen(trapFromLoadMisalign) {
      trapCause := CAUSE_MISALIGNED_LOAD.asBits
    } elsewhen(trapFromLoadPage) {
      trapCause := CAUSE_LOAD_PAGE.asBits
    } elsewhen(trapFromLoadAccess) {
      trapCause := CAUSE_LOAD_ACCESS.asBits
    } elsewhen(trapFromStorePage) {
      trapCause := CAUSE_STORE_PAGE.asBits
    } elsewhen(trapFromStoreAccess) {
      trapCause := CAUSE_STORE_ACCESS.asBits
    }

    val pcRaw = up(PC.INSN_PC)
    val branchTargetRaw = branch.logic.target
    val memAddrRaw = lsu.logic.effectiveAddr
    val pcArch = pcRaw
    val branchTargetArch = branchTargetRaw
    val memAddrArch = memAddrRaw
    val secondParcelFetchFault = !rawIsCompressed &&
      (up(Fetch.FETCH_SECOND_PAGE_FAULT) || up(Fetch.FETCH_SECOND_ACCESS_FAULT) ||
        (trapFromFetchAccess &&
          (if (config.cExtensionEnabled) True else False) &&
          pmpAllow(pcRaw, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits)) &&
          !pmpAllow(pcRaw + U(2, 64 bits), instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))))

    val trapTval = Bits(64 bits)
    trapTval := memAddrArch.asBits
    when((trapFromLoadPage || trapFromStorePage || trapFromLoadAccess || trapFromStoreAccess) && (memAddrRaw === U(0, 64 bits))) {
      trapTval := memAddrRaw.asBits
    }
    when(trapFromBranch) {
      trapTval := branchTargetArch.asBits
    } elsewhen(trapFromFetchPage || trapFromFetchAccess) {
      trapTval := Mux(secondParcelFetchFault, (pcArch + U(2, 64 bits)).asBits, (latePcZeroFetch ? pcRaw.asBits | pcArch.asBits))
    } elsewhen(trapFromIllegalInsn) {
      val illegalInsnBits = trapInsn
      trapTval := Mux(illegalInsnBits(1 downto 0) =/= B"11", illegalInsnBits(15 downto 0).asBits.resize(64), illegalInsnBits.resized)
    } elsewhen(trapFromEbreak) {
      trapTval := pcArch.asBits
    } elsewhen(trapFromEcall) {
      trapTval := B(0, 64 bits)
    }

    val trapCauseIdx = trapCause(5 downto 0).asUInt
    val delegateTrap = trapFire && (currentPriv =/= PRV_M) && csrMedeleg(trapCauseIdx)

    when(trapFire) {
      val trapPc = (latePcZeroFetch ? pcRaw.asBits | pcArch.asBits)
      val nextStatus = Bits(64 bits)
      nextStatus := csrMstatus
      when(delegateTrap) {
        nextStatus(5) := csrMstatus(1)
        nextStatus(1) := False
        nextStatus(8) := currentPriv(0)
        csrSepc := trapPc
        csrScause := trapCause
        csrStval := trapTval
        currentPriv := PRV_S
      } otherwise {
        nextStatus(7) := csrMstatus(3)
        nextStatus(3) := False
        nextStatus(12 downto 11) := currentPriv.asBits
        csrMepc := trapPc
        csrMcause := trapCause
        csrMtval := trapTval
        currentPriv := PRV_M
      }
      csrMstatus := nextStatus
    }

    when(mretFire) {
      val mretPriv = csrMstatus(12 downto 11).asUInt
      currentPriv := mretPriv
      val nextMstatus = Bits(64 bits)
      nextMstatus := csrMstatus
      nextMstatus(3) := csrMstatus(7)
      nextMstatus(7) := True
      when(mretPriv =/= PRV_M) {
        nextMstatus(17) := False
      }
      nextMstatus(12 downto 11) := B"00"
      csrMstatus := nextMstatus
    }

    when(sretFire) {
      currentPriv := csrMstatus(8) ? PRV_S | PRV_U
      val nextMstatus = Bits(64 bits)
      nextMstatus := csrMstatus
      nextMstatus(1) := csrMstatus(5)
      nextMstatus(5) := True
      nextMstatus(8) := False
      csrMstatus := nextMstatus
    }

    val mtvecBase = csrMtvec.asUInt & U(BigInt("FFFFFFFFFFFFFFFC", 16), 64 bits)
    val stvecBase = csrStvec.asUInt & U(BigInt("FFFFFFFFFFFFFFFC", 16), 64 bits)
    pc.exception.valid.allowOverride := trapFire
    pc.exception.payload.vector.allowOverride := delegateTrap ? stvecBase | mtvecBase

  redirect.trapFire := trapFire
  redirect.trapTargetPriv := delegateTrap ? PRV_S | PRV_M
  redirect.mretFire := mretFire
  redirect.mretTarget := mretTarget
  redirect.mretTargetPriv := csrMstatus(12 downto 11).asUInt
  redirect.sretFire := sretFire
  redirect.sretTarget := sretTarget
  redirect.sretTargetPriv := csrMstatus(8) ? PRV_S | PRV_U
  redirect.trapCause := trapCause
  redirect.trapTval := trapTval

    vmContext.currentPriv := currentPriv
    vmContext.dataPriv := dataPriv
    vmContext.mprv := mstatusMprv
    vmContext.mxr := csrMstatus(19)
    vmContext.sum := csrMstatus(18)
    vmContext.tvm := csrMstatus(20)
    vmContext.sbe := csrMstatus(36)
    vmContext.satp := csrSatp
    vmContext.satpMode := Sv39.modeOf(csrSatp)
    vmContext.satpAsid := Sv39.asidOf(csrSatp)
    vmContext.satpPpn := Sv39.ppnOf(csrSatp)

    down(TRAP) := trapFromBranch || trapFromFetchPage || trapFromFetchAccess ||
      trapFromLoadMisalign || trapFromStoreMisalign ||
      trapFromLoadPage || trapFromStorePage ||
      trapFromLoadAccess || trapFromStoreAccess ||
      trapFromIllegalInsn || trapFromEcall || trapFromEbreak
  }
}
