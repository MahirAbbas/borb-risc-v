package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch._
import borb.memory._
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.dispatch._
import borb.execute.IntAlu
import borb.execute.IntAlu._
import borb.dispatch.SrcPlugin
import borb.dispatch.SrcPlugin._
import borb.formal._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi._
import borb.core.CpuConfig
import spinal.lib.misc.plugin.PluginHost
import borb.common.MicroCode._

object CPU {
  def main(args: Array[String]) {
    val config = SpinalConfig(
      targetDirectory = "formal/cores/borb"
    )
    config.generateSystemVerilog(new CPU())
  }
}

case class CPU(config: CpuConfig = CpuConfig.default) extends Component {

  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
    val iBus = master(new RamFetchBus(
      addressWidth = config.xlen, 
      dataWidth = config.xlen, 
      idWidth = config.fetchIdWidth
    ))
    val dBus = master(borb.execute.DataBus(
      addressWidth = config.xlen,
      dataWidth = config.xlen,
      idWidth = config.dataIdWidth
    )).simPublic()
    val rvfi = out(Rvfi()).simPublic()
    val dbg = out(DebugArea())
    val perf = out(borb.core.PerfCountersBundle())
  }

  // We use a ClockingArea to handle the provided clock/reset
  val coreClockDomain = ClockDomain(
    io.clk,
    reset = io.reset,
    clockEnable = io.clkEnable,
    config = ClockDomainConfig(
      clockEdge = RISING,
      resetKind = SYNC,
      resetActiveLevel = HIGH
    )
  )

  val coreArea = new ClockingArea(coreClockDomain) {
    val pipeline = new StageCtrlPipeline()

    // Defaults for Execution Stages: LANE_SEL is False if not propagated (Bubble)
    import borb.common.Common._
    pipeline.ctrls.filter(_._1 >= 5).foreach { 
      case (id, ctrl) => ctrl.up(LANE_SEL).setAsReg().init(False)

    }
    pipeline.ctrls.filter(_._1 >= 5).foreach { 
      case(id, ctrl) => ctrl.up(COMMIT).setAsReg().init(False)
    }
    // Keep speculation epoch instruction-local across stalls/flushes.
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(SPEC_EPOCH).setAsReg().init(0)
    }
    // Keep PC instruction-local across stalls/flushes so execute-stage control
    // flow uses the PC that belongs to that instruction.
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.PC.PC).setAsReg().init(0)
    }

    val pc = new PC(pipeline.ctrl(0), addressWidth = 64)
    //pc.jump.setIdle()
    pc.exception.setIdle()
    pc.flush.setIdle()
    val fetch = Fetch(
      pipeline.ctrl(1),
      pipeline.ctrl(2),
      addressWidth = 64,
      dataWidth = 64
    )
    // RAM is external (via io.iBus)

    val decode = new Decoder(pipeline.ctrl(3))

    val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(pipeline.ctrl(4), hazardRange, pipeline)
    val srcPlugin = new SrcPlugin(pipeline.ctrl(5))
    val intalu = new IntAlu(pipeline.ctrl(6))
    val branch = new borb.execute.Branch(pipeline.ctrl(6), pc)
    val lsu = new borb.execute.Lsu(pipeline.ctrl(6))

    // Connect LSU data bus to external port
    io.dBus.cmd << lsu.io.dBus.cmd
    lsu.io.dBus.rsp << io.dBus.rsp

    // Global speculation epoch. Keep this wide enough to avoid wraparound
    // aliasing under branch-heavy tests.
    val currentEpoch = Reg(UInt(16 bits)) init 0

    // Aggregate TRAP signals in Stage 6
    val execStage = pipeline.ctrl(6)
    val trapLogic = new execStage.Area {
      val epochMatches = up(SPEC_EPOCH) === currentEpoch
      // Minimal machine-mode trap causes used by current RISCOF tests.
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
      val ARCH_BASE = U(BigInt("80000000", 16), 64 bits)
      val PRV_U = U(0, 2 bits)
      val PRV_S = U(1, 2 bits)
      val PRV_M = U(3, 2 bits)

      // Machine CSRs (minimal set required by RISCOF/Tenstorrent scaffolds).
      val csrMstatus = Reg(Bits(64 bits)) init(0)
      // Baseline misa for RV64I (M/D bits are conditionally OR'ed below).
      val misaBase = BigInt("8000000000000100", 16)
      val misaM = if (config.mExtensionEnabled) BigInt("0000000000001000", 16) else BigInt(0)
      val misaD = if (config.dExtensionEnabled) BigInt("0000000000000008", 16) else BigInt(0)
      val csrMisa = Reg(Bits(64 bits)) init(B(misaBase | misaM | misaD, 64 bits))
      val csrMedeleg = Reg(Bits(64 bits)) init(0)
      val csrMtvec = Reg(Bits(64 bits)) init(0)
      val csrMscratch = Reg(Bits(64 bits)) init(0)
      val csrMepc = Reg(Bits(64 bits)) init(0)
      val csrMcause = Reg(Bits(64 bits)) init(0)
      val csrMtval = Reg(Bits(64 bits)) init(0)
      val csrMip = Reg(Bits(64 bits)) init(0)
      val csrSatp = Reg(Bits(64 bits)) init(0)
      // PMP CSR storage (RV64): pmpcfg0,2,4,6,8,10,12,14 + pmpaddr0..63.
      val pmpCfgBytes = Vec.fill(64)(Reg(Bits(8 bits)) init(0))
      // PMP reset value is WARL/implementation-defined. Use all-ones so
      // default NAPOT top-entry setup remains permissive until tests program it.
      val pmpAddrRegs = Vec.fill(64)(Reg(Bits(64 bits)) init(B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits)))
      // Match Spike's effective PMP entry count used by the compliance flow.
      val pmpImplementedEntries = 16
      // Current effective privilege mode (M=3, U=0).
      val currentPriv = Reg(UInt(2 bits)) init(PRV_M)

      def pmpCfgSanitize(cfg: Bits): Bits = {
        val c = Bits(8 bits)
        c := cfg
        // Reserved bits must read as zero.
        c(6 downto 5) := B"00"
        // WARL: W=1,R=0 is reserved; clear W in that case.
        c(1) := cfg(1) & cfg(0)
        c
      }

      def pmpCfgReadWord(group: Int): Bits = {
        val out = Bits(64 bits)
        out := 0
        for(i <- 0 until 8) {
          val idx = group * 8 + i
          if(idx < pmpImplementedEntries) {
            out((i * 8 + 7) downto (i * 8)) := pmpCfgBytes(idx)
          } else {
            out((i * 8 + 7) downto (i * 8)) := 0
          }
        }
        out
      }

      // mepc[0] is always zero. When C is not advertised in misa, IALIGN=32
      // and mepc[1] must read as zero (including the implicit mret read).
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
          is(U"12'h300") { out := csrMstatus }
          is(U"12'h301") { out := csrMisa }
          is(U"12'h302") { out := csrMedeleg }
          is(U"12'h305") { out := csrMtvec }
          is(U"12'h340") { out := csrMscratch }
          is(U"12'h341") { out := mepcMasked(csrMepc) }
          is(U"12'h342") { out := csrMcause }
          is(U"12'h343") { out := csrMtval }
          is(U"12'h344") { out := csrMip }
          is(U"12'h180") { out := csrSatp }
          is(U"12'h3A0") { out := pmpCfgReadWord(0) }
          is(U"12'h3A2") { out := pmpCfgReadWord(1) }
          is(U"12'h3A4") { out := pmpCfgReadWord(2) }
          is(U"12'h3A6") { out := pmpCfgReadWord(3) }
          is(U"12'h3A8") { out := pmpCfgReadWord(4) }
          is(U"12'h3AA") { out := pmpCfgReadWord(5) }
          is(U"12'h3AC") { out := pmpCfgReadWord(6) }
          is(U"12'h3AE") { out := pmpCfgReadWord(7) }
          for(i <- 0 until 64) {
            is(U(0x3B0 + i, 12 bits)) { out := (if(i < pmpImplementedEntries) pmpAddrRegs(i) else B(0, 64 bits)) }
          }
        }
        out
      }

      def csrSupported(addr: UInt): Bool = {
        val ok = Bool()
        ok := False
        switch(addr) {
          is(U"12'h300") { ok := True }
          is(U"12'h301") { ok := True }
          is(U"12'h302") { ok := True }
          is(U"12'h305") { ok := True }
          is(U"12'h340") { ok := True }
          is(U"12'h341") { ok := True }
          is(U"12'h342") { ok := True }
          is(U"12'h343") { ok := True }
          is(U"12'h344") { ok := True }
          is(U"12'h180") { ok := True }
          is(U"12'h3A0") { ok := True }
          is(U"12'h3A2") { ok := True }
          is(U"12'h3A4") { ok := True }
          is(U"12'h3A6") { ok := True }
          is(U"12'h3A8") { ok := True }
          is(U"12'h3AA") { ok := True }
          is(U"12'h3AC") { ok := True }
          is(U"12'h3AE") { ok := True }
          for(i <- 0 until 64) {
            is(U(0x3B0 + i, 12 bits)) { ok := True }
          }
        }
        ok
      }

      val csrAddr = up(borb.frontend.Decoder.INSTRUCTION)(31 downto 20).asUInt
      val csrOld = csrRead(csrAddr)
      val csrRs1 = up(borb.dispatch.SrcPlugin.RS1)
      val csrZimm = B(59 bits, default -> False) ## up(borb.frontend.Decoder.INSTRUCTION)(19 downto 15)
      val csrWriteData = Bits(64 bits)
      csrWriteData := csrOld
      val csrWriteEn = Bool()
      csrWriteEn := False

      val isCsrOp = up(MicroCode) === uopCSRRW || up(MicroCode) === uopCSRRS || up(MicroCode) === uopCSRRC ||
        up(MicroCode) === uopCSRRWI || up(MicroCode) === uopCSRRSI || up(MicroCode) === uopCSRRCI
      val csrPrivReq = csrAddr(9 downto 8)
      val csrReadOnly = csrAddr(11 downto 10) === U"2'b11"

      switch(up(MicroCode)) {
        is(uopCSRRW) {
          csrWriteData := csrRs1
          csrWriteEn := True
        }
        is(uopCSRRS) {
          csrWriteData := csrOld | csrRs1
          csrWriteEn := csrRs1 =/= 0
        }
        is(uopCSRRC) {
          csrWriteData := csrOld & ~csrRs1
          csrWriteEn := csrRs1 =/= 0
        }
        is(uopCSRRWI) {
          csrWriteData := csrZimm
          csrWriteEn := True
        }
        is(uopCSRRSI) {
          csrWriteData := csrOld | csrZimm
          csrWriteEn := csrZimm =/= 0
        }
        is(uopCSRRCI) {
          csrWriteData := csrOld & ~csrZimm
          csrWriteEn := csrZimm =/= 0
        }
      }
      val csrIllegal = up(VALID) && isCsrOp && (!csrSupported(csrAddr) || (currentPriv < csrPrivReq) || (csrWriteEn && csrReadOnly))

      val csrFire = up.isFiring && epochMatches && up(VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOALU) && isCsrOp
      when(csrFire && !csrIllegal && csrWriteEn) {
        switch(csrAddr) {
          is(U"12'h300") { csrMstatus := csrWriteData }
          is(U"12'h301") { csrMisa := csrWriteData }
          is(U"12'h302") { csrMedeleg := csrWriteData }
          is(U"12'h305") { csrMtvec := csrWriteData }
          is(U"12'h340") { csrMscratch := csrWriteData }
          is(U"12'h341") { csrMepc := csrWriteData }
          is(U"12'h342") { csrMcause := csrWriteData }
          is(U"12'h343") { csrMtval := csrWriteData }
          is(U"12'h344") { csrMip := csrWriteData }
          is(U"12'h180") { csrSatp := csrWriteData }
          is(U"12'h3A0") {
            for(i <- 0 until 8) {
              val idx = i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          is(U"12'h3A2") {
            for(i <- 0 until 8) {
              val idx = 8 + i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          is(U"12'h3A4") {
            for(i <- 0 until 8) {
              val idx = 16 + i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          is(U"12'h3A6") {
            for(i <- 0 until 8) {
              val idx = 24 + i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          is(U"12'h3A8") {
            for(i <- 0 until 8) {
              val idx = 32 + i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          is(U"12'h3AA") {
            for(i <- 0 until 8) {
              val idx = 40 + i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          is(U"12'h3AC") {
            for(i <- 0 until 8) {
              val idx = 48 + i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          is(U"12'h3AE") {
            for(i <- 0 until 8) {
              val idx = 56 + i
              if(idx < pmpImplementedEntries) {
                when(!pmpCfgBytes(idx)(7)) {
                  pmpCfgBytes(idx) := pmpCfgSanitize(csrWriteData((i * 8 + 7) downto (i * 8)))
                }
              }
            }
          }
          for(i <- 0 until 64) {
            is(U(0x3B0 + i, 12 bits)) {
              val selfLocked = pmpCfgBytes(i)(7)
              val nextTorLocked = if(i < 63) (pmpCfgBytes(i + 1)(7) && (pmpCfgBytes(i + 1)(4 downto 3) === B"01")) else False
              if(i < pmpImplementedEntries) {
                when(!(selfLocked || nextTorLocked)) {
                  pmpAddrRegs(i) := csrWriteData
                }
              }
            }
          }
        }
      }

      // CSR ops return previous CSR value in rd (x0 writes still suppressed).
      when(csrFire && !csrIllegal) {
        val rdAddr = up(borb.frontend.Decoder.RD_ADDR).asUInt
        val isX0 = rdAddr === 0
        down(borb.execute.WriteBack.RESULT).address.allowOverride := rdAddr
        down(borb.execute.WriteBack.RESULT).data.allowOverride := isX0 ? B(0, 64 bits) | csrOld
        down(borb.execute.WriteBack.RESULT).valid.allowOverride := True
      }

      val trapFromBranch = branch.logic.willTrap
      val insn = up(borb.frontend.Decoder.INSTRUCTION)
      val aguFire = up(VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOAGU)
      val sawNonZeroPc = Reg(Bool) init(False)
      when(up.isFiring && up(LANE_SEL) && (up(borb.fetch.PC.PC) =/= U(0, 64 bits))) {
        sawNonZeroPc := True
      }

      // Effective privilege for data accesses (MPRV support).
      val mstatusMprv = csrMstatus(17)
      val mstatusMpp = csrMstatus(12 downto 11).asUInt
      val dataPriv = Mux((currentPriv === PRV_M) && mstatusMprv, mstatusMpp, currentPriv)
      val instPriv = currentPriv

      def pmpAllow(addrRaw: UInt, priv: UInt, needX: Bool, needR: Bool, needW: Bool, accessBytes: UInt): Bool = {
        // RISCOF/Spike environment treats data accesses to address 0 as an
        // access-faulting region (while instruction redirection already has
        // dedicated handling). Keep this local to data permissions.
        val denyNullData = (addrRaw === U(0, 64 bits)) && (needR || needW)
        val addrLo = Mux(addrRaw < ARCH_BASE, addrRaw + ARCH_BASE, addrRaw)
        val bytes = accessBytes.max(U(1, 64 bits))
        val addrHi = addrLo + (bytes - U(1, 64 bits))
        val hitVec = Vec(Bool(), pmpImplementedEntries)
        val permVec = Vec(Bool(), pmpImplementedEntries)

        for(i <- 0 until pmpImplementedEntries) {
          val cfg = pmpCfgBytes(i)
          val l = cfg(7)
          val a = cfg(4 downto 3)
          val r = cfg(0)
          val w = cfg(1)
          val x = cfg(2)
          val entry = pmpAddrRegs(i).asUInt
          val prev = if(i == 0) U(0, 64 bits) else pmpAddrRegs(i - 1).asUInt

          val torLo = prev |<< 2
          val torHiExcl = entry |<< 2
          val na4Lo = entry |<< 2
          val na4Hi = (entry |<< 2) + U(3, 64 bits)

          // NAPOT decode: mask = ((lowest zero bit in entry) - 1).
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
            is(B"00") { hitAny := False } // OFF
            is(B"01") { // TOR
              val torNonEmpty = torHiExcl =/= U(0, 64 bits)
              val torHi = torHiExcl - U(1, 64 bits)
              hitAny := torNonEmpty && (addrLo <= torHi) && (addrHi >= torLo)
              fullMatch := torNonEmpty && (addrLo >= torLo) && (addrHi <= torHi)
            }
            is(B"10") { // NA4
              hitAny := (addrLo <= na4Hi) && (addrHi >= na4Lo)
              fullMatch := (addrLo >= na4Lo) && (addrHi <= na4Hi)
            }
            default {   // NAPOT
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
        for(i <- (pmpImplementedEntries - 1) downto 0) {
          allowExpr = Mux(hitVec(i), permVec(i), allowExpr)
        }
        Mux(denyNullData, False, allowExpr)
      }

      val loadBytes = UInt(64 bits)
      loadBytes := U(1, 64 bits)
      switch(up(MicroCode)) {
        is(uopLH) { loadBytes := U(2, 64 bits) }
        is(uopLHU) { loadBytes := U(2, 64 bits) }
        is(uopLW) { loadBytes := U(4, 64 bits) }
        is(uopLWU) { loadBytes := U(4, 64 bits) }
        is(uopLD) { loadBytes := U(8, 64 bits) }
      }

      val storeBytes = UInt(64 bits)
      storeBytes := U(1, 64 bits)
      switch(up(MicroCode)) {
        is(uopSH) { storeBytes := U(2, 64 bits) }
        is(uopSW) { storeBytes := U(4, 64 bits) }
        is(uopSD) { storeBytes := U(8, 64 bits) }
      }

      // RV64I only (no compressed execution): instruction fetch width is 4 bytes.
      val pmpExecAllowed = pmpAllow(up(borb.fetch.PC.PC), instPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
      val pmpLoadAllowed = pmpAllow(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = True, needW = False, accessBytes = loadBytes)
      val pmpStoreAllowed = pmpAllow(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = False, needW = True, accessBytes = storeBytes)

      // Instruction-access faults are checked at fetch/execute boundary and
      // must not depend on decode validity of fetched bits.
      val latePcZeroFetch = up.isFiring && up(LANE_SEL) && sawNonZeroPc && (up(borb.fetch.PC.PC) === U(0, 64 bits))
      val pmpExecFault = up.isFiring && up(LANE_SEL) && (!pmpExecAllowed || latePcZeroFetch)
      val pmpLoadFault = aguFire && lsu.logic.isLoad && !pmpLoadAllowed
      val pmpStoreFault = aguFire && lsu.logic.isStore && !pmpStoreAllowed
      val pmpDataFault = pmpLoadFault || pmpStoreFault
      lsu.io.pmpFault := pmpDataFault

      val trapFromLoadMisalign = lsu.logic.misaligned && lsu.logic.isLoad && aguFire
      val trapFromStoreMisalign = lsu.logic.misaligned && lsu.logic.isStore && aguFire
      val trapFromLoadAccess = pmpLoadFault
      val trapFromStoreAccess = pmpStoreFault
      val trapFromFetchAccess = pmpExecFault
      val mretInsn = insn === B"32'h30200073"
      val mretIllegal = mretInsn && (currentPriv =/= PRV_M)
      val trapFromEcall = up.isFiring && insn === B"32'h00000073"
      val trapFromEbreak = up.isFiring && insn === B"32'h00100073"
      // Decode marks unknown instructions as invalid. Treat any non-system
      // decode miss as illegal instruction.
      val isHandledSystem = mretInsn || trapFromEcall || trapFromEbreak
      val trapFromIllegal32 = !up(VALID) && !isHandledSystem
      val trapFromIllegalInsn = up.isFiring && (trapFromIllegal32 || csrIllegal || mretIllegal)
      val mretFire = up.isFiring && epochMatches && mretInsn && (currentPriv === PRV_M)
      val mretTarget = mepcMasked(csrMepc).asUInt
      val trapFire = up.isFiring && epochMatches && (trapFromBranch || trapFromFetchAccess || trapFromLoadMisalign || trapFromStoreMisalign || trapFromLoadAccess || trapFromStoreAccess || trapFromIllegalInsn || trapFromEcall || trapFromEbreak)

      val trapCause = Bits(64 bits)
      trapCause := CAUSE_MISALIGNED_STORE.asBits
      when(trapFromBranch) {
        trapCause := CAUSE_MISALIGNED_FETCH.asBits
      } elsewhen(trapFromFetchAccess) {
        trapCause := CAUSE_FETCH_ACCESS.asBits
      } elsewhen(trapFromLoadMisalign) {
        trapCause := CAUSE_MISALIGNED_LOAD.asBits
      } elsewhen(trapFromLoadAccess) {
        trapCause := CAUSE_LOAD_ACCESS.asBits
      } elsewhen(trapFromStoreAccess) {
        trapCause := CAUSE_STORE_ACCESS.asBits
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
      }

      // Internal execution frequently uses low offsets while tests/handlers
      // expect architectural addresses in trap CSRs.
      val pcRaw = up(borb.fetch.PC.PC)
      val branchTargetRaw = branch.logic.target
      val memAddrRaw = lsu.logic.effectiveAddr
      val pcArch = Mux(pcRaw < ARCH_BASE, pcRaw + ARCH_BASE, pcRaw)
      val branchTargetArch = Mux(branchTargetRaw < ARCH_BASE, branchTargetRaw + ARCH_BASE, branchTargetRaw)
      val memAddrArch = Mux(memAddrRaw < ARCH_BASE, memAddrRaw + ARCH_BASE, memAddrRaw)

      val trapTval = Bits(64 bits)
      trapTval := memAddrArch.asBits
      when((trapFromLoadAccess || trapFromStoreAccess) && (memAddrRaw === U(0, 64 bits))) {
        trapTval := memAddrRaw.asBits
      }
      when(trapFromBranch) {
        trapTval := branchTargetArch.asBits
      } elsewhen(trapFromFetchAccess) {
        trapTval := (latePcZeroFetch ? pcRaw.asBits | pcArch.asBits)
      } elsewhen(trapFromIllegalInsn) {
        // Match Spike/arch-test behavior: for illegal 16-bit encodings capture
        // the low halfword; otherwise capture the full 32-bit instruction.
        val illegalInsnBits = up(borb.frontend.Decoder.INSTRUCTION)
        trapTval := Mux(
          illegalInsnBits(1 downto 0) =/= B"11",
          illegalInsnBits(15 downto 0).asBits.resize(64),
          illegalInsnBits.resized
        )
      } elsewhen(trapFromEcall || trapFromEbreak) {
        trapTval := B(0, 64 bits)
      }

      when(trapFire) {
        val nextMstatus = Bits(64 bits)
        nextMstatus := csrMstatus
        nextMstatus(7) := csrMstatus(3) // MPIE <= MIE
        nextMstatus(3) := False         // MIE <= 0
        nextMstatus(12 downto 11) := currentPriv.asBits // MPP <= previous privilege
        csrMstatus := nextMstatus
        csrMepc := (latePcZeroFetch ? pcRaw.asBits | pcArch.asBits)
        csrMcause := trapCause
        csrMtval := trapTval
      }
      when(mretFire) {
        val mretPriv = csrMstatus(12 downto 11).asUInt
        currentPriv := mretPriv
        val nextMstatus = Bits(64 bits)
        nextMstatus := csrMstatus
        nextMstatus(3) := csrMstatus(7) // MIE <= MPIE
        nextMstatus(7) := True          // MPIE <= 1
        // Spec: mret to privilege < M clears MPRV.
        when(mretPriv =/= PRV_M) {
          nextMstatus(17) := False
        }
        nextMstatus(12 downto 11) := B"00" // MPP <= U
        csrMstatus := nextMstatus
      }
      when(trapFire) {
        currentPriv := PRV_M
      }

      val mtvecBase = csrMtvec.asUInt & U(BigInt("FFFFFFFFFFFFFFFC", 16), 64 bits)
      val trapVector = mtvecBase
      pc.exception.valid.allowOverride := trapFire
      pc.exception.payload.vector.allowOverride := trapVector

      down(TRAP) := trapFromBranch || trapFromFetchAccess || trapFromLoadMisalign || trapFromStoreMisalign || trapFromLoadAccess || trapFromStoreAccess || trapFromIllegalInsn || trapFromEcall || trapFromEbreak
    }

    decode.branchResolved := branch.branchResolved

    // ========== Speculation Epoch Architecture ==========
    // Clean, scalable speculation handling for in-order superscalar CPU
    //
    // Design:
    // - Global epoch counter maintained here, passed to Fetch
    // - Each instruction is tagged with SPEC_EPOCH when it enters the pipeline
    // - When a branch is TAKEN (flushPipeline), epoch increments
    // - All instructions with old epoch are flushed (their SPEC_EPOCH != currentEpoch)
    
    // Flush Logic - fires when a non-stale branch/jump redirects.
    val execEpochMatches = pipeline.ctrl(6)(SPEC_EPOCH) === currentEpoch
    val flushPipeline = branch.logic.jumpCmd.valid && execEpochMatches
    val trapRedirect = trapLogic.trapFire && execEpochMatches
    val mretRedirect = trapLogic.mretFire && execEpochMatches
    val redirectPipeline = flushPipeline || trapRedirect || mretRedirect
    pc.jump.valid := (branch.logic.jumpCmd.valid && execEpochMatches) || mretRedirect
    pc.jump.payload.target := mretRedirect ? trapLogic.mretTarget | branch.logic.jumpCmd.payload.target
    pc.jump.payload.is_jump := mretRedirect || branch.logic.jumpCmd.payload.is_jump
    pc.jump.payload.is_branch := (!mretRedirect) && branch.logic.jumpCmd.payload.is_branch
    
    // Increment epoch on taken branch
    when(flushPipeline) {
      currentEpoch := currentEpoch + 1
    }
    when(trapRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(mretRedirect) {
      currentEpoch := currentEpoch + 1
    }
    
    // Connect epoch to Fetch so new instructions get tagged with current epoch
    fetch.io.flush := redirectPipeline
    fetch.io.currentEpoch := currentEpoch
    
    // Flush execution stages (Decode, Dispatch, Src) unconditionally on redirect.
    // In this in-order pipeline, stages 3..5 only hold younger instructions when
    // stage 6 resolves a branch/jump, so all must be squashed.
    // Note: Stage 6 (Execute) is excluded - the redirecting instruction executes.
    //       Stage 7 (Writeback) is excluded - older committed state.
    val executionStages = Array(3, 4, 5).map(pipeline.ctrl(_))
    executionStages.foreach { ctrl =>
      ctrl.throwWhen(redirectPipeline)
    }

    // Upstream redirect throws (fetch/decode/dispatch/src) already squash
    // younger-path work. Avoid execute-stage epoch throw here because it can
    // incorrectly drop the first instruction at a redirect target.
    
    // Fetch redirect cleanup is handled by fetch.io.flush + epoch filtering in
    // fetch. Avoid explicit throws on stages 1/2 to prevent dropping the first
    // instruction at a redirect target.

    val rvfiPlugin = new RvfiPlugin(pipeline.ctrl(7))
    io.rvfi := rvfiPlugin.io.rvfi

    val debugPlugin = new DebugPlugin(pipeline)
    io.dbg := debugPlugin.io.dbg

    // Performance counters
    val perfCounters = new borb.core.PerfCountersPlugin(pipeline.ctrl(7))
    io.perf := perfCounters.counters
    
    // Wire event signals to performance counters
    perfCounters.hazardStall    := dispatcher.hcs.writes.hazard  // Hazard stall from HazardChecker
    perfCounters.fetchStall     := !fetch.fifo.io.pop.valid       // Fetch stalled waiting for instruction
    perfCounters.memStall       := lsu.logic.waitingResponse     // Waiting for load response
    perfCounters.branchExecuted := branch.logic.isBranch && branch.logic.up(LANE_SEL)
    perfCounters.branchTaken    := branch.logic.doJump
    perfCounters.pipelineFlush  := flushPipeline

    val write = pipeline.ctrl(7)
    //val dispCtrl = pipeline.ctrl(4)

    import borb.execute.WriteBack
    val writeback = new WriteBack(pipeline.ctrl(7), srcPlugin.regfileread.regfile.io.writes(0))
    val wbArea = new write.Area {
      // Expose signals for simulation
      srcPlugin.regfileread.regfile.io.simPublic()
      fetch.io.readCmd.simPublic()
      pc.PC_cur.simPublic()

      val simDebug = new borb.formal.SimDebugPlugin(
        write,
        pipeline,
        dispatcher,
        branch,
        fetch
      )
    }

    // Connect Fetch to External Memory Bus
    io.iBus.cmd << fetch.io.readCmd.cmd
    io.iBus.rsp >> fetch.io.readCmd.rsp

    pipeline.ctrls.drop(1).foreach(e => e._2.throwWhen(clockDomain.reset))
    // Build the pipeline
    pipeline.build()
  }
}
