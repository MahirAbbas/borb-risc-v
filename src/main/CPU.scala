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
import borb.execute.{DataBus, DataBusCmd}
import borb.execute.fpu.{FpuAddSub, FpuDivSqrt, FpuFma, FpuMul, FpuRegisterFile, FpuScalarMisc, FpuSoftFloatUtils}
import borb.execute.fpu.FpuSoftFloatUtils._
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
  private val cpuAxiConfig = Axi4Config(
    addressWidth = config.xlen,
    dataWidth = config.xlen,
    idWidth = 16,
    useId = true,
    useRegion = false,
    useLock = false,
    useQos = false,
    useProt = false,
    useCache = false
  )

  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
    val iAxi = master(Axi4Shared(cpuAxiConfig))
    val dAxi = master(Axi4Shared(cpuAxiConfig)).simPublic()
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
    pipeline.ctrls.filter(_._1 >= 7).foreach {
      case (_, ctrl) => ctrl.up(SELF_REDIRECT).setAsReg().init(False)
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

    // Global speculation epoch. Keep this wide enough to avoid wraparound
    // aliasing under branch-heavy tests.
    val currentEpoch = Reg(UInt(16 bits)) init 0

    val pc = new PC(pipeline.ctrl(0), addressWidth = 64, withCompressed = config.cExtensionEnabled)
    //pc.jump.setIdle()
    pc.exception.setIdle()
    pc.flush.setIdle()
    val fetch = Fetch(
      pipeline.ctrl(1),
      pipeline.ctrl(2),
      addressWidth = 64,
      dataWidth = 64,
      withCompressed = config.cExtensionEnabled
    )
    pc.sequentialValid := fetch.io.pcAdvance
    pc.sequentialStep := fetch.io.pcStep
    // RAM is external (via io.iAxi/io.dAxi)

    val decode = new Decoder(pipeline.ctrl(3), withCompressed = config.cExtensionEnabled, xlen = config.xlen)

    val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(pipeline.ctrl(4), hazardRange, pipeline)
    val srcPlugin = new SrcPlugin(pipeline.ctrl(5))
    val intalu = new IntAlu(pipeline.ctrl(6))
    val branch = new borb.execute.Branch(pipeline.ctrl(6), pc, withCompressed = config.cExtensionEnabled)
    val lsu = new borb.execute.Lsu(pipeline.ctrl(6), currentEpoch)

    val lsuBus = DataBus(addressWidth = 64, dataWidth = 64, idWidth = 16)
    lsuBus.cmd << lsu.io.dBus.cmd
    lsu.io.dBus.rsp << lsuBus.rsp

    // Always-on hardware counters; software/profile tooling can sample them
    // selectively (benchmark flows only).
    val perfCounters = new borb.core.PerfCountersPlugin(pipeline.ctrl(7))
    io.perf := perfCounters.counters

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
      // Baseline misa for RV64I (extension bits are conditionally OR'ed below).
      val misaBase = BigInt("8000000000000100", 16)
      val misaA = if (config.aExtensionEnabled) BigInt("0000000000000001", 16) else BigInt(0)
      val misaC = if (config.cExtensionEnabled) BigInt("0000000000000004", 16) else BigInt(0)
      val misaF = if (config.fExtensionEnabled) BigInt("0000000000000020", 16) else BigInt(0)
      val misaM = if (config.mExtensionEnabled) BigInt("0000000000001000", 16) else BigInt(0)
      val misaD = if (config.dExtensionEnabled) BigInt("0000000000000008", 16) else BigInt(0)
      val csrMisa = Reg(Bits(64 bits)) init(B(misaBase | misaA | misaC | misaF | misaM | misaD, 64 bits))
      val csrMedeleg = Reg(Bits(64 bits)) init(0)
      val csrMtvec = Reg(Bits(64 bits)) init(0)
      val csrMscratch = Reg(Bits(64 bits)) init(0)
      val csrMepc = Reg(Bits(64 bits)) init(0)
      val csrMcause = Reg(Bits(64 bits)) init(0)
      val csrMtval = Reg(Bits(64 bits)) init(0)
      val csrMip = Reg(Bits(64 bits)) init(0)
      // Floating-point CSRs (fflags/frm/fcsr)
      val csrFflags = Reg(Bits(5 bits)) init(0)
      val csrFrm = Reg(Bits(3 bits)) init(0)
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
      val fpRegFile = FpuRegisterFile(flen = 64, readPorts = 3, writePorts = 1)
      val fpWriteValid = Bool()
      val fpWriteAddr = UInt(5 bits)
      val fpWriteData = Bits(64 bits)
      fpWriteValid := False
      fpWriteAddr := 0
      fpWriteData := 0
      fpRegFile.io.writes(0).valid := fpWriteValid
      fpRegFile.io.writes(0).address := fpWriteAddr
      fpRegFile.io.writes(0).data := fpWriteData

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
          // Counter/timer CSR views used by benchmarking/profiling software.
          is(U"12'hB00") { out := perfCounters.counters.cycles.asBits }         // mcycle
          is(U"12'hB02") { out := perfCounters.counters.instret.asBits }        // minstret
          is(U"12'hB03") { out := perfCounters.counters.stallsHazard.asBits }   // mhpmcounter3
          is(U"12'hB04") { out := perfCounters.counters.stallsFetch.asBits }    // mhpmcounter4
          is(U"12'hB05") { out := perfCounters.counters.stallsMem.asBits }      // mhpmcounter5
          is(U"12'hB06") { out := perfCounters.counters.branches.asBits }       // mhpmcounter6
          is(U"12'hB07") { out := perfCounters.counters.branchesTaken.asBits }  // mhpmcounter7
          is(U"12'hB08") { out := perfCounters.counters.flushes.asBits }        // mhpmcounter8
          is(U"12'hB09") { out := perfCounters.counters.loads.asBits }          // mhpmcounter9
          is(U"12'hB0A") { out := perfCounters.counters.stores.asBits }         // mhpmcounter10
          is(U"12'hB0B") { out := perfCounters.counters.jumps.asBits }          // mhpmcounter11
          is(U"12'hB0C") { out := perfCounters.counters.csrOps.asBits }         // mhpmcounter12
          is(U"12'hB0D") { out := perfCounters.counters.mulDivOps.asBits }      // mhpmcounter13
          is(U"12'hB0E") { out := perfCounters.counters.trapCommits.asBits }    // mhpmcounter14
          is(U"12'hB80") { out := perfCounters.counters.cycles(63 downto 32).asBits.resized }        // mcycleh
          is(U"12'hB82") { out := perfCounters.counters.instret(63 downto 32).asBits.resized }       // minstreth
          is(U"12'hB83") { out := perfCounters.counters.stallsHazard(63 downto 32).asBits.resized }  // mhpmcounter3h
          is(U"12'hB84") { out := perfCounters.counters.stallsFetch(63 downto 32).asBits.resized }   // mhpmcounter4h
          is(U"12'hB85") { out := perfCounters.counters.stallsMem(63 downto 32).asBits.resized }     // mhpmcounter5h
          is(U"12'hB86") { out := perfCounters.counters.branches(63 downto 32).asBits.resized }      // mhpmcounter6h
          is(U"12'hB87") { out := perfCounters.counters.branchesTaken(63 downto 32).asBits.resized } // mhpmcounter7h
          is(U"12'hB88") { out := perfCounters.counters.flushes(63 downto 32).asBits.resized }       // mhpmcounter8h
          is(U"12'hB89") { out := perfCounters.counters.loads(63 downto 32).asBits.resized }         // mhpmcounter9h
          is(U"12'hB8A") { out := perfCounters.counters.stores(63 downto 32).asBits.resized }        // mhpmcounter10h
          is(U"12'hB8B") { out := perfCounters.counters.jumps(63 downto 32).asBits.resized }         // mhpmcounter11h
          is(U"12'hB8C") { out := perfCounters.counters.csrOps(63 downto 32).asBits.resized }        // mhpmcounter12h
          is(U"12'hB8D") { out := perfCounters.counters.mulDivOps(63 downto 32).asBits.resized }     // mhpmcounter13h
          is(U"12'hB8E") { out := perfCounters.counters.trapCommits(63 downto 32).asBits.resized }   // mhpmcounter14h
          is(U"12'hC00") { out := perfCounters.counters.cycles.asBits }         // cycle
          is(U"12'hC02") { out := perfCounters.counters.instret.asBits }        // instret
          is(U"12'hC80") { out := perfCounters.counters.cycles(63 downto 32).asBits.resized }        // cycleh
          is(U"12'hC82") { out := perfCounters.counters.instret(63 downto 32).asBits.resized }       // instreth
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
          is(U"12'h001") { ok := True }
          is(U"12'h002") { ok := True }
          is(U"12'h003") { ok := True }
          is(U"12'h180") { ok := True }
          is(U"12'h3A0") { ok := True }
          is(U"12'h3A2") { ok := True }
          is(U"12'h3A4") { ok := True }
          is(U"12'h3A6") { ok := True }
          is(U"12'h3A8") { ok := True }
          is(U"12'h3AA") { ok := True }
          is(U"12'h3AC") { ok := True }
          is(U"12'h3AE") { ok := True }
          is(U"12'hB00") { ok := True }
          is(U"12'hB02") { ok := True }
          is(U"12'hB03") { ok := True }
          is(U"12'hB04") { ok := True }
          is(U"12'hB05") { ok := True }
          is(U"12'hB06") { ok := True }
          is(U"12'hB07") { ok := True }
          is(U"12'hB08") { ok := True }
          is(U"12'hB09") { ok := True }
          is(U"12'hB0A") { ok := True }
          is(U"12'hB0B") { ok := True }
          is(U"12'hB0C") { ok := True }
          is(U"12'hB0D") { ok := True }
          is(U"12'hB0E") { ok := True }
          is(U"12'hB80") { ok := True }
          is(U"12'hB82") { ok := True }
          is(U"12'hB83") { ok := True }
          is(U"12'hB84") { ok := True }
          is(U"12'hB85") { ok := True }
          is(U"12'hB86") { ok := True }
          is(U"12'hB87") { ok := True }
          is(U"12'hB88") { ok := True }
          is(U"12'hB89") { ok := True }
          is(U"12'hB8A") { ok := True }
          is(U"12'hB8B") { ok := True }
          is(U"12'hB8C") { ok := True }
          is(U"12'hB8D") { ok := True }
          is(U"12'hB8E") { ok := True }
          is(U"12'hC00") { ok := True }
          is(U"12'hC02") { ok := True }
          is(U"12'hC80") { ok := True }
          is(U"12'hC82") { ok := True }
          for(i <- 0 until 64) {
            is(U(0x3B0 + i, 12 bits)) { ok := True }
          }
        }
        ok
      }

      val csrAddr = up(borb.frontend.Decoder.DECODED_INSTRUCTION)(31 downto 20).asUInt
      val csrOld = csrRead(csrAddr)
      val csrRs1 = up(borb.dispatch.SrcPlugin.RS1)
      val csrZimm = B(59 bits, default -> False) ## up(borb.frontend.Decoder.DECODED_INSTRUCTION)(19 downto 15)
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
      val insn = up(borb.frontend.Decoder.DECODED_INSTRUCTION)
      val aguFire = up(VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOAGU)
      val isFlwInsn = (insn(6 downto 0) === B"0000111") && (insn(14 downto 12) === B"010")
      val isFswInsn = (insn(6 downto 0) === B"0100111") && (insn(14 downto 12) === B"010")
      val isFcvtWsInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1100000") && (insn(24 downto 20) === B"00000")
      val isFcvtWuSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1100000") && (insn(24 downto 20) === B"00001")
      val isFcvtLsInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1100000") && (insn(24 downto 20) === B"00010")
      val isFcvtLuSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1100000") && (insn(24 downto 20) === B"00011")
      val isFcvtSwInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1101000") && (insn(24 downto 20) === B"00000")
      val isFcvtSwuInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1101000") && (insn(24 downto 20) === B"00001")
      val isFcvtSlInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1101000") && (insn(24 downto 20) === B"00010")
      val isFcvtSluInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1101000") && (insn(24 downto 20) === B"00011")
      val isFaddSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0000000")
      val isFsubSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0000100")
      val isFmulSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0001000")
      val isFdivSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0001100")
      val isFsqrtSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0101100") && (insn(24 downto 20) === B"00000")
      val isFmaddSInsn = (insn(6 downto 0) === B"1000011") && (insn(26 downto 25) === B"00")
      val isFmsubSInsn = (insn(6 downto 0) === B"1000111") && (insn(26 downto 25) === B"00")
      val isFnmsubSInsn = (insn(6 downto 0) === B"1001011") && (insn(26 downto 25) === B"00")
      val isFnmaddSInsn = (insn(6 downto 0) === B"1001111") && (insn(26 downto 25) === B"00")
      val isFmvXWInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1110000") && (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"000")
      val isFmvWXInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1111000") && (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"000")
      val isFclassSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1110000") && (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"001")
      val isFsgnjSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010000") && (insn(14 downto 12) === B"000")
      val isFsgnjnSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010000") && (insn(14 downto 12) === B"001")
      val isFsgnjxSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010000") && (insn(14 downto 12) === B"010")
      val isFminSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010100") && (insn(14 downto 12) === B"000")
      val isFmaxSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010100") && (insn(14 downto 12) === B"001")
      val isFleSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1010000") && (insn(14 downto 12) === B"000")
      val isFltSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1010000") && (insn(14 downto 12) === B"001")
      val isFeqSInsn = (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1010000") && (insn(14 downto 12) === B"010")
      val flwRd = insn(11 downto 7).asUInt
      val fswRs2 = insn(24 downto 20).asUInt
      val fcvtSRd = insn(11 downto 7).asUInt
      val fpRs1Addr = insn(19 downto 15).asUInt
      val fpRs2Addr = insn(24 downto 20).asUInt
      val fpRs3Addr = insn(31 downto 27).asUInt
      fpRegFile.io.reads(0).valid := True
      fpRegFile.io.reads(0).address := fpRs1Addr
      fpRegFile.io.reads(1).valid := True
      fpRegFile.io.reads(1).address := fpRs2Addr
      fpRegFile.io.reads(2).valid := True
      fpRegFile.io.reads(2).address := fpRs3Addr
      val fpRs1Data = fpRegFile.io.reads(0).data
      val fpRs2Data = fpRegFile.io.reads(1).data
      val fpRs3Data = fpRegFile.io.reads(2).data

      // Feed FSW store data from the internal FP register bank.
      when(aguFire && isFswInsn) {
        lsu.logic.rawStoreData.allowOverride := fpRs2Data(31 downto 0).resize(64)
      }

      // Capture FLW load payload into the internal FP register bank and suppress
      // integer register writeback for FLW.
      val flwWritebackFire = aguFire && isFlwInsn && lsu.logic.responseArriving && !lsu.logic.suppress
      when(flwWritebackFire) {
        fpWriteValid := True
        fpWriteAddr := flwRd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## lsu.logic.shiftedLoadData(31 downto 0)
        down(borb.execute.WriteBack.RESULT).data.allowOverride := 0
        down(borb.execute.WriteBack.RESULT).address.allowOverride := 0
        down(borb.execute.WriteBack.RESULT).valid.allowOverride := False
      }

      // FCVT conversions used by current RV64F bring-up.
      val aluFire = up(VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOALU)
      val isFcvtWsOp = (up(MicroCode) === uopFCVTWS) || isFcvtWsInsn
      val isFcvtWuSOp = (up(MicroCode) === uopFCVTWUS) || isFcvtWuSInsn
      val isFcvtLsOp = (up(MicroCode) === uopFCVTLS) || isFcvtLsInsn
      val isFcvtLuSOp = (up(MicroCode) === uopFCVTLUS) || isFcvtLuSInsn
      val isFmvXWOp = (up(MicroCode) === uopFMVXW) || isFmvXWInsn
      val isFmvWXOp = (up(MicroCode) === uopFMVWX) || isFmvWXInsn
      val fcvtFToIntFire = aluFire && (isFcvtWsOp || isFcvtWuSOp || isFcvtLsOp || isFcvtLuSOp)
      val fcvtSToFpFire = aluFire && (isFcvtSwInsn || isFcvtSwuInsn || isFcvtSlInsn || isFcvtSluInsn)
      val fmvXWFire = aluFire && isFmvXWOp
      val fmvWXFire = aluFire && isFmvWXOp
      val faddsubSFire = aluFire && (isFaddSInsn || isFsubSInsn)
      val fmulSFire = aluFire && isFmulSInsn
      val fdivSFire = aluFire && isFdivSInsn
      val fsqrtSFire = aluFire && isFsqrtSInsn
      val fmaSFire = aluFire && (isFmaddSInsn || isFmsubSInsn || isFnmsubSInsn || isFnmaddSInsn)
      val fclassSFire = aluFire && isFclassSInsn
      val fsgnjSFire = aluFire && (isFsgnjSInsn || isFsgnjnSInsn || isFsgnjxSInsn)
      val fminmaxSFire = aluFire && (isFminSInsn || isFmaxSInsn)
      val fcmpSFire = aluFire && (isFleSInsn || isFltSInsn || isFeqSInsn)
      val fcvtRmRaw = Mux(insn(14 downto 12) === B"111", csrFrm, insn(14 downto 12))
      val fcvtRm = Bits(3 bits)
      fcvtRm := fcvtRmRaw
      // Treat reserved rm encodings as RTZ for now.
      when(fcvtRmRaw === B"101" || fcvtRmRaw === B"110" || fcvtRmRaw === B"111") {
        fcvtRm := B"001"
      }

      val fcvtLSrc = fpRs1Data(31 downto 0)
      val fcvtLSign = fcvtLSrc(31)
      val fcvtLExp = fcvtLSrc(30 downto 23).asUInt
      val fcvtLFrac = fcvtLSrc(22 downto 0).asUInt
      val fcvtLIsZero = (fcvtLExp === U(0, 8 bits)) && (fcvtLFrac === U(0, 23 bits))
      val fcvtLIsInf = (fcvtLExp === U(255, 8 bits)) && (fcvtLFrac === U(0, 23 bits))
      val fcvtLIsNaN = (fcvtLExp === U(255, 8 bits)) && (fcvtLFrac =/= U(0, 23 bits))
      val fcvtLNormMant = (U(1, 1 bits) ## fcvtLFrac).asUInt
      val fcvtLMant = UInt(24 bits)
      fcvtLMant := fcvtLNormMant
      when(fcvtLExp === U(0, 8 bits)) {
        fcvtLMant := (U(0, 1 bits) ## fcvtLFrac).asUInt
      }
      val fcvtLTruncMag = UInt(65 bits)
      fcvtLTruncMag := 0
      val fcvtLRemNZ = Bool()
      fcvtLRemNZ := False
      val fcvtLGtHalf = Bool()
      fcvtLGtHalf := False
      val fcvtLEqHalf = Bool()
      fcvtLEqHalf := False
      when((fcvtLExp >= U(127, 8 bits)) && (fcvtLExp <= U(191, 8 bits))) {
        val e = (fcvtLExp - U(127, 8 bits)).resized
        when(e >= U(23, 8 bits)) {
          fcvtLTruncMag := fcvtLMant.resize(65) |<< (e - U(23, 8 bits)).resized
        } otherwise {
          val rshift = (U(23, 8 bits) - e).resized
          val mask = (U(1, 65 bits) |<< rshift) - U(1, 65 bits)
          val rem = fcvtLMant.resize(65) & mask
          val half = U(1, 65 bits) |<< (rshift - U(1, 8 bits)).resized
          fcvtLTruncMag := fcvtLMant.resize(65) |>> rshift
          fcvtLRemNZ := rem =/= 0
          fcvtLGtHalf := rem > half
          fcvtLEqHalf := rem === half
        }
      } elsewhen(fcvtLExp === U(126, 8 bits)) {
        // |x| is in [0.5, 1). Truncation is 0, but RNE/RMM may round to 1.
        val rem = fcvtLMant.resize(65)
        val half = U(1, 65 bits) |<< 23
        fcvtLRemNZ := rem =/= 0
        fcvtLGtHalf := rem > half
        fcvtLEqHalf := rem === half
      } elsewhen(!fcvtLIsZero && !fcvtLIsInf && !fcvtLIsNaN) {
        // Finite magnitude below 1.0 or above directly-computable range.
        fcvtLRemNZ := True
      }

      val fcvtLInc = FpuSoftFloatUtils.roundInc(fcvtRm, fcvtLSign, fcvtLRemNZ, fcvtLGtHalf, fcvtLEqHalf, fcvtLTruncMag(0))
      val fcvtLRoundedMag = fcvtLTruncMag + (fcvtLInc.asUInt.resize(65))
      val fcvtLInexact = fcvtLRemNZ

      val fcvtLResult = Bits(64 bits)
      fcvtLResult := 0
      val fcvtLFlags = Bits(5 bits) // NX,UF,OF,DZ,NV
      fcvtLFlags := 0

      val sMax = U(BigInt("7FFFFFFFFFFFFFFF", 16), 65 bits)
      val sMinMag = U(BigInt("8000000000000000", 16), 65 bits)
      val wSMax = U(BigInt("7FFFFFFF", 16), 65 bits)
      val wSMinMag = U(BigInt("80000000", 16), 65 bits)
      val fcvtTarget32 = isFcvtWsOp || isFcvtWuSOp
      val fcvtSigned = isFcvtWsOp || isFcvtLsOp
      val fcvtLTooLarge32 = (!fcvtLIsZero) && (!fcvtLIsInf) && (!fcvtLIsNaN) && (fcvtLExp > U(158, 8 bits))
      val fcvtLTooLarge64 = (!fcvtLIsZero) && (!fcvtLIsInf) && (!fcvtLIsNaN) && (fcvtLExp > U(191, 8 bits))
      val fcvtLTooLarge = fcvtTarget32 ? fcvtLTooLarge32 | fcvtLTooLarge64

      when(fcvtFToIntFire) {
        when(fcvtSigned) {
          val signedOverflow32 = fcvtLTooLarge32 || ((!fcvtLSign && (fcvtLRoundedMag > wSMax)) || (fcvtLSign && (fcvtLRoundedMag > wSMinMag)))
          val signedOverflow64 = fcvtLTooLarge64 || ((!fcvtLSign && (fcvtLRoundedMag > sMax)) || (fcvtLSign && (fcvtLRoundedMag > sMinMag)))
          val signedOverflow = fcvtTarget32 ? signedOverflow32 | signedOverflow64
          when(fcvtLIsNaN || fcvtLIsInf || signedOverflow) {
            fcvtLFlags(4) := True
            when(fcvtLIsNaN) {
              fcvtLResult := fcvtTarget32 ? B(BigInt("000000007FFFFFFF", 16), 64 bits) | B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits)
            } otherwise {
              when(fcvtTarget32) {
                fcvtLResult := Mux(fcvtLSign, B(BigInt("FFFFFFFF80000000", 16), 64 bits), B(BigInt("000000007FFFFFFF", 16), 64 bits))
              } otherwise {
                fcvtLResult := Mux(fcvtLSign, B(BigInt("8000000000000000", 16), 64 bits), B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits))
              }
            }
          } otherwise {
            val mag64 = fcvtLRoundedMag(63 downto 0).asBits
            val sVal = fcvtLSign ? (((~mag64).asUInt + U(1, 64 bits)).asBits) | mag64
            when(fcvtTarget32) {
              val mag32 = fcvtLRoundedMag(31 downto 0).asBits
              val sVal32 = fcvtLSign ? (((~mag32).asUInt + U(1, 32 bits)).asBits) | mag32
              fcvtLResult := sVal32.asSInt.resize(64).asBits
            } otherwise {
              fcvtLResult := sVal
            }
            when(fcvtLInexact) {
              fcvtLFlags(0) := True
            }
          }
        } otherwise {
          // FCVT.WU.S / FCVT.LU.S
          val unsignedInvalidNeg = fcvtLSign && (fcvtLRoundedMag =/= U(0, 65 bits))
          val unsignedOverflow32 = fcvtLTooLarge32 || fcvtLRoundedMag(64 downto 32).orR
          val unsignedOverflow64 = fcvtLTooLarge64 || fcvtLRoundedMag.msb
          val unsignedOverflow = fcvtTarget32 ? unsignedOverflow32 | unsignedOverflow64
          when(fcvtLIsNaN || fcvtLIsInf || unsignedInvalidNeg || unsignedOverflow) {
            fcvtLFlags(4) := True
            when(fcvtLIsNaN) {
              fcvtLResult := fcvtTarget32 ? B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits) | B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits)
            } otherwise {
              when(fcvtTarget32) {
                fcvtLResult := Mux(fcvtLSign, B(0, 64 bits), B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
              } otherwise {
                fcvtLResult := Mux(fcvtLSign, B(0, 64 bits), B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
              }
            }
          } otherwise {
            val wuVal32 = fcvtLRoundedMag(31 downto 0).asBits
            fcvtLResult := fcvtTarget32 ? wuVal32.asSInt.resize(64).asBits | fcvtLRoundedMag(63 downto 0).asBits
            when(fcvtLInexact) {
              fcvtLFlags(0) := True
            }
          }
        }

        down(borb.execute.WriteBack.RESULT).address.allowOverride := up(borb.frontend.Decoder.RD_ADDR).asUInt
        down(borb.execute.WriteBack.RESULT).data.allowOverride := (up(borb.frontend.Decoder.RD_ADDR).asUInt === 0) ? B(0, 64 bits) | fcvtLResult
        down(borb.execute.WriteBack.RESULT).valid.allowOverride := True
        csrFflags := csrFflags | fcvtLFlags
      }

      when(fcvtSToFpFire) {
        val srcInt64 = up(borb.dispatch.SrcPlugin.RS1).asUInt
        val srcInt32 = up(borb.dispatch.SrcPlugin.RS1)(31 downto 0).asUInt
        val srcFrom32 = isFcvtSwInsn || isFcvtSwuInsn
        val srcSigned = isFcvtSwInsn || isFcvtSlInsn
        val srcSign = srcSigned && (srcFrom32 ? srcInt32.msb | srcInt64.msb)
        val srcMag = UInt(64 bits)
        srcMag := srcFrom32 ? srcInt32.resize(64) | srcInt64
        when(srcSign) {
          when(srcFrom32) {
            srcMag := ((~srcInt32) + U(1, 32 bits)).resize(64)
          } otherwise {
            srcMag := ((~srcInt64) + U(1, 64 bits)).resized
          }
        }

        val msbIdx = UInt(6 bits)
        msbIdx := 0
        for(i <- 0 until 64) {
          when(srcMag(i)) {
            msbIdx := i
          }
        }

        val truncSig = UInt(24 bits)
        truncSig := 0
        val remNZ = Bool()
        remNZ := False
        val gtHalf = Bool()
        gtHalf := False
        val eqHalf = Bool()
        eqHalf := False

        when(srcMag =/= 0) {
          when(msbIdx > U(23, 6 bits)) {
            val rshift = (msbIdx - U(23, 6 bits)).resized
            val mask = (U(1, 64 bits) |<< rshift) - U(1, 64 bits)
            val rem = srcMag & mask
            val half = U(1, 64 bits) |<< (rshift - U(1, 6 bits)).resized
            truncSig := (srcMag |>> rshift).resize(24)
            remNZ := rem =/= 0
            gtHalf := rem > half
            eqHalf := rem === half
          } otherwise {
            truncSig := (srcMag |<< (U(23, 6 bits) - msbIdx).resized).resize(24)
          }
        }

        val inc = FpuSoftFloatUtils.roundInc(fcvtRm, srcSign, remNZ, gtHalf, eqHalf, truncSig(0))
        val rounded = truncSig.resize(25) + inc.asUInt.resize(25)
        val carry = rounded(24)
        val normSig = UInt(24 bits)
        normSig := rounded(23 downto 0)
        when(carry) {
          normSig := (rounded |>> 1).resize(24)
        }

        val outExp = UInt(8 bits)
        outExp := 0
        when(srcMag =/= 0) {
          outExp := (msbIdx.resize(8) + U(127, 8 bits) + carry.asUInt.resize(8)).resized
        }

        val outFrac = Bits(23 bits)
        outFrac := normSig(22 downto 0).asBits
        val outSign = Bool()
        outSign := srcSign && (srcMag =/= 0)
        val outFp32 = outSign.asBits ## outExp.asBits ## outFrac
        fpWriteValid := True
        fpWriteAddr := fcvtSRd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## outFp32
        when(remNZ) {
          csrFflags := csrFflags | B"00001"
        }
      }

      when(fmvXWFire) {
        val moved = fpRs1Data(31 downto 0).asSInt.resize(64).asBits
        down(borb.execute.WriteBack.RESULT).address.allowOverride := up(borb.frontend.Decoder.RD_ADDR).asUInt
        down(borb.execute.WriteBack.RESULT).data.allowOverride := (up(borb.frontend.Decoder.RD_ADDR).asUInt === 0) ? B(0, 64 bits) | moved
        down(borb.execute.WriteBack.RESULT).valid.allowOverride := True
      }

      when(faddsubSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rs2 = insn(24 downto 20).asUInt
        val rd = insn(11 downto 7).asUInt
        val addSub = FpuAddSub.addSubS(
          fpRs1Data(31 downto 0),
          fpRs2Data(31 downto 0),
          fcvtRm,
          isFsubSInsn
        )
        fpWriteValid := True
        fpWriteAddr := rd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## addSub.data
        csrFflags := csrFflags | addSub.flags
      }

      when(fmulSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rs2 = insn(24 downto 20).asUInt
        val rd = insn(11 downto 7).asUInt
        val mul = FpuMul.mulS(
          fpRs1Data(31 downto 0),
          fpRs2Data(31 downto 0),
          fcvtRm
        )
        fpWriteValid := True
        fpWriteAddr := rd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## mul.data
        csrFflags := csrFflags | mul.flags
      }

      when(fdivSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rs2 = insn(24 downto 20).asUInt
        val rd = insn(11 downto 7).asUInt
        val div = FpuDivSqrt.divS(
          fpRs1Data(31 downto 0),
          fpRs2Data(31 downto 0),
          fcvtRm
        )
        fpWriteValid := True
        fpWriteAddr := rd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## div.data
        csrFflags := csrFflags | div.flags
      }

      when(fsqrtSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rd = insn(11 downto 7).asUInt
        val sqrt = FpuDivSqrt.sqrtS(
          fpRs1Data(31 downto 0),
          fcvtRm
        )
        fpWriteValid := True
        fpWriteAddr := rd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## sqrt.data
        csrFflags := csrFflags | sqrt.flags
      }

      when(fmaSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rs2 = insn(24 downto 20).asUInt
        val rs3 = insn(31 downto 27).asUInt
        val rd = insn(11 downto 7).asUInt
        val fma = FpuFma.fmaS(
          fpRs1Data(31 downto 0),
          fpRs2Data(31 downto 0),
          fpRs3Data(31 downto 0),
          fcvtRm,
          isFmsubSInsn,
          isFnmsubSInsn,
          isFnmaddSInsn
        )
        fpWriteValid := True
        fpWriteAddr := rd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## fma.data
        csrFflags := csrFflags | fma.flags
      }

      when(fmvWXFire) {
        fpWriteValid := True
        fpWriteAddr := fcvtSRd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## up(borb.dispatch.SrcPlugin.RS1)(31 downto 0)
      }

      when(fclassSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val cls = FpuScalarMisc.classifyS(fpRs1Data(31 downto 0))

        down(borb.execute.WriteBack.RESULT).address.allowOverride := up(borb.frontend.Decoder.RD_ADDR).asUInt
        down(borb.execute.WriteBack.RESULT).data.allowOverride := (up(borb.frontend.Decoder.RD_ADDR).asUInt === 0) ? B(0, 64 bits) | (B(0, 54 bits) ## cls)
        down(borb.execute.WriteBack.RESULT).valid.allowOverride := True
      }

      when(fsgnjSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rs2 = insn(24 downto 20).asUInt
        val rd = insn(11 downto 7).asUInt
        val out = FpuScalarMisc.signInjectS(
          fpRs1Data(31 downto 0),
          fpRs2Data(31 downto 0),
          isFsgnjnSInsn,
          isFsgnjxSInsn
        )
        fpWriteValid := True
        fpWriteAddr := rd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## out
      }

      when(fcmpSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rs2 = insn(24 downto 20).asUInt
        val cmp = FpuScalarMisc.compareS(
          fpRs1Data(31 downto 0),
          fpRs2Data(31 downto 0),
          isFeqSInsn,
          isFltSInsn,
          isFleSInsn
        )
        csrFflags := csrFflags | cmp.flags

        down(borb.execute.WriteBack.RESULT).address.allowOverride := up(borb.frontend.Decoder.RD_ADDR).asUInt
        down(borb.execute.WriteBack.RESULT).data.allowOverride := (up(borb.frontend.Decoder.RD_ADDR).asUInt === 0) ? B(0, 64 bits) | cmp.result
        down(borb.execute.WriteBack.RESULT).valid.allowOverride := True
      }

      when(fminmaxSFire) {
        val rs1 = insn(19 downto 15).asUInt
        val rs2 = insn(24 downto 20).asUInt
        val rd = insn(11 downto 7).asUInt
        val minMax = FpuScalarMisc.minMaxS(
          fpRs1Data(31 downto 0),
          fpRs2Data(31 downto 0),
          isFminSInsn
        )
        csrFflags := csrFflags | minMax.flags
        fpWriteValid := True
        fpWriteAddr := rd
        fpWriteData := B(BigInt("FFFFFFFF", 16), 32 bits) ## minMax.data
      }
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
      val atomicWordAccess = up(MicroCode).mux(
        uopAMOSWAPW -> True,
        uopAMOADDW -> True,
        uopAMOXORW -> True,
        uopAMOANDW -> True,
        uopAMOORW -> True,
        uopAMOMINW -> True,
        uopAMOMAXW -> True,
        uopAMOMINUW -> True,
        uopAMOMAXUW -> True,
        default -> False
      )
      val atomicDoubleAccess = up(MicroCode).mux(
        uopAMOSWAPD -> True,
        uopAMOADDD -> True,
        uopAMOXORD -> True,
        uopAMOANDD -> True,
        uopAMOORD -> True,
        uopAMOMIND -> True,
        uopAMOMAXD -> True,
        uopAMOMINUD -> True,
        uopAMOMAXUD -> True,
        default -> False
      )
      when(atomicWordAccess) {
        loadBytes := U(4, 64 bits)
        storeBytes := U(4, 64 bits)
      }
      when(atomicDoubleAccess) {
        loadBytes := U(8, 64 bits)
        storeBytes := U(8, 64 bits)
      }

      val pmpExecAllowed = if(config.cExtensionEnabled) {
        // With C enabled, instruction fetch permission is checked per 16-bit
        // parcel. This allows a 32-bit instruction to legally straddle two PMP
        // regions when each halfword parcel is executable.
        val fetchPc = up(borb.fetch.PC.PC)
        // Use raw instruction low bits (parcel header) instead of the decoded
        // compressed flag in this fault path.
        val isCompressed = up(borb.frontend.Decoder.DECODED_INSTRUCTION)(1 downto 0) =/= B"11"
        val loParcelExecAllowed = pmpAllow(fetchPc, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
        val hiParcelExecAllowed = pmpAllow(fetchPc + U(2, 64 bits), instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
        loParcelExecAllowed && (isCompressed || hiParcelExecAllowed)
      } else {
        pmpAllow(up(borb.fetch.PC.PC), instPriv, needX = True, needR = False, needW = False, accessBytes = U(4, 64 bits))
      }
      val isAmoOp = up(MicroCode).mux(
        uopAMOSWAPW -> True,
        uopAMOADDW -> True,
        uopAMOXORW -> True,
        uopAMOANDW -> True,
        uopAMOORW -> True,
        uopAMOMINW -> True,
        uopAMOMAXW -> True,
        uopAMOMINUW -> True,
        uopAMOMAXUW -> True,
        uopAMOSWAPD -> True,
        uopAMOADDD -> True,
        uopAMOXORD -> True,
        uopAMOANDD -> True,
        uopAMOORD -> True,
        uopAMOMIND -> True,
        uopAMOMAXD -> True,
        uopAMOMINUD -> True,
        uopAMOMAXUD -> True,
        default -> False
      )
      val pmpLoadAllowed = pmpAllow(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = True, needW = False, accessBytes = loadBytes)
      val pmpStoreAllowed = pmpAllow(lsu.logic.effectiveAddr, dataPriv, needX = False, needR = False, needW = True, accessBytes = storeBytes)

      // Instruction-access faults are checked at fetch/execute boundary and
      // must not depend on decode validity of fetched bits.
      val latePcZeroFetch = up.isFiring && up(LANE_SEL) && sawNonZeroPc && (up(borb.fetch.PC.PC) === U(0, 64 bits))
      val pmpExecFault = up.isFiring && up(LANE_SEL) && (!pmpExecAllowed || latePcZeroFetch)
      val pmpLoadFault = aguFire && lsu.logic.isLoad && !isAmoOp && !pmpLoadAllowed
      // AMOs must report store/AMO access-fault class, even when the failed
      // permission check is on the read side.
      val pmpStoreFault = aguFire && (
        (lsu.logic.isStore && !isAmoOp && !pmpStoreAllowed) ||
        (isAmoOp && (!pmpLoadAllowed || !pmpStoreAllowed))
      )
      val pmpDataFault = pmpLoadFault || pmpStoreFault
      lsu.io.pmpFault := pmpDataFault

      val trapFromLoadMisalign = lsu.logic.misaligned && lsu.logic.isLoad && !isAmoOp && aguFire
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
      val atomicOpcode = insn(6 downto 0) === B"0101111"
      val trapFromAtomicDisabled = if(config.aExtensionEnabled) False else atomicOpcode
      val trapFromIllegal32 = (!up(VALID) && !isHandledSystem) || trapFromAtomicDisabled
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
      val secondParcelFetchFault = trapFromFetchAccess &&
        (if(config.cExtensionEnabled) True else False) &&
        (up(borb.frontend.Decoder.DECODED_INSTRUCTION)(1 downto 0) === B"11") &&
        pmpAllow(pcRaw, instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits)) &&
        !pmpAllow(pcRaw + U(2, 64 bits), instPriv, needX = True, needR = False, needW = False, accessBytes = U(2, 64 bits))
      val trapTval = Bits(64 bits)
      trapTval := memAddrArch.asBits
      when((trapFromLoadAccess || trapFromStoreAccess) && (memAddrRaw === U(0, 64 bits))) {
        trapTval := memAddrRaw.asBits
      }
      when(trapFromBranch) {
        trapTval := branchTargetArch.asBits
      } elsewhen(trapFromFetchAccess) {
        trapTval := Mux(
          secondParcelFetchFault,
          (pcArch + U(2, 64 bits)).asBits,
          (latePcZeroFetch ? pcRaw.asBits | pcArch.asBits)
        )
      } elsewhen(trapFromIllegalInsn) {
        // Match Spike/arch-test behavior: for illegal 16-bit encodings capture
        // the low halfword; otherwise capture the full 32-bit instruction.
        val illegalInsnBits = up(borb.frontend.Decoder.DECODED_INSTRUCTION)
        trapTval := Mux(
          illegalInsnBits(1 downto 0) =/= B"11",
          illegalInsnBits(15 downto 0).asBits.resize(64),
          illegalInsnBits.resized
        )
      } elsewhen(trapFromEbreak) {
        // Arch-test environment expects breakpoint mtval to carry the
        // faulting instruction address.
        trapTval := pcArch.asBits
      } elsewhen(trapFromEcall) {
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

    // Keep younger instructions from overtaking unresolved control-flow ops.
    // This is conservative, but it closes the wrong-path window while the
    // front-end/control-flow cleanup is still in progress.
    val srcCtrl = pipeline.ctrl(5)
    val exeCtrl = pipeline.ctrl(6)
    val srcHasControlFlow = srcCtrl.up.isValid &&
      srcCtrl(Decoder.VALID) &&
      srcCtrl(borb.common.Common.LANE_SEL) &&
      (srcCtrl(Decoder.EXECUTION_UNIT) === borb.frontend.ExecutionUnitEnum.BR)
    val exeHasControlFlow = exeCtrl.up.isValid &&
      exeCtrl(Decoder.VALID) &&
      exeCtrl(borb.common.Common.LANE_SEL) &&
      (exeCtrl(Decoder.EXECUTION_UNIT) === borb.frontend.ExecutionUnitEnum.BR)
    val controlHazardBusy = srcHasControlFlow || exeHasControlFlow
    Array(2, 3, 4).map(pipeline.ctrl(_)).foreach { ctrl =>
      ctrl.haltWhen(controlHazardBusy)
    }

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
    pipeline.ctrl(6).down(SELF_REDIRECT) := redirectPipeline
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
    
    // Flush fetched/decode-side younger instructions on redirect.
    // Stage 2 can still hold a wrong-path fetch packet when stage 6 resolves.
    // Stages 3..5 are decode/dispatch/src and are always younger at that point.
    // Stage 6 executes the redirecting instruction and stage 7 is older state.
    val executionStages = Array(2, 3, 4, 5).map(pipeline.ctrl(_))
    executionStages.foreach { ctrl =>
      ctrl.throwWhen(redirectPipeline)
    }

    val rvfiPlugin = new RvfiPlugin(pipeline.ctrl(7))
    io.rvfi := rvfiPlugin.io.rvfi

    val debugPlugin = new DebugPlugin(pipeline)
    io.dbg := debugPlugin.io.dbg

    // Wire event signals to performance counters
    perfCounters.hazardStall    := dispatcher.hcs.writes.hazard  // Hazard stall from HazardChecker
    perfCounters.fetchStall     := !fetch.sourceAvailable         // Fetch stalled waiting for instruction
    perfCounters.memStall       := lsu.logic.waitingResponse     // Waiting for load response
    perfCounters.branchExecuted := branch.logic.isBranch && branch.logic.up(LANE_SEL)
    perfCounters.branchTaken    := branch.logic.doJump
    perfCounters.pipelineFlush  := flushPipeline

    val write = pipeline.ctrl(7)
    //val dispCtrl = pipeline.ctrl(4)

    import borb.execute.WriteBack
    val writeback = new WriteBack(pipeline.ctrl(7), srcPlugin.regfileread.regfile.io.writes(0), currentEpoch)
    val wbArea = new write.Area {
      // Expose signals for simulation
      srcPlugin.regfileread.regfile.io.simPublic()
      fetch.io.source.simPublic()
      pc.PC_cur.simPublic()

      val simDebug = new borb.formal.SimDebugPlugin(
        write,
        pipeline,
        dispatcher,
        branch,
        fetch
      )
    }

    val fetchBridge = FetchBusBridge(
      FrontendConfig(
        addressWidth = config.xlen,
        dataWidth = config.xlen,
        withCompressed = config.cExtensionEnabled
      ),
      io.iAxi,
      fetch.io.source
    )

    io.iAxi.w.valid := False
    io.iAxi.w.data := 0
    io.iAxi.w.strb := 0
    io.iAxi.w.last := False
    io.iAxi.b.ready := True

    // LSU DataBus -> AXI4Shared bridge
    val dCmd = lsuBus.cmd
    val dRsp = lsuBus.rsp

    object DMemAxiState extends SpinalEnum {
      val idle, sendWrite, waitWriteResp, sendRead, waitReadResp = newElement()
    }
    import DMemAxiState._
    val dAxiState = RegInit(idle)
    val dActiveCmd = Reg(DataBusCmd(64, 64, 16))
    val dArwFired = RegInit(False)
    val dWFired = RegInit(False)

    dCmd.ready := False

    io.dAxi.arw.valid := False
    io.dAxi.arw.id := dActiveCmd.id.resized
    io.dAxi.arw.addr := dActiveCmd.address
    io.dAxi.arw.len := 0
    io.dAxi.arw.size := log2Up(config.xlen / 8)
    io.dAxi.arw.burst := Axi4.burst.INCR
    io.dAxi.arw.write := dActiveCmd.write

    io.dAxi.w.valid := False
    io.dAxi.w.data := dActiveCmd.data
    io.dAxi.w.strb := dActiveCmd.mask
    io.dAxi.w.last := True

    io.dAxi.b.ready := False
    io.dAxi.r.ready := False

    dRsp.valid := False
    dRsp.data := io.dAxi.r.data
    dRsp.id := io.dAxi.r.id.resized

    switch(dAxiState) {
      is(idle) {
        dCmd.ready := True
        dArwFired := False
        dWFired := False
        when(dCmd.valid) {
          dActiveCmd := dCmd.payload
          when(dCmd.write) {
            dAxiState := sendWrite
          } otherwise {
            dAxiState := sendRead
          }
        }
      }

      is(sendWrite) {
        io.dAxi.arw.valid := !dArwFired
        io.dAxi.w.valid := !dWFired
        when(io.dAxi.arw.fire) { dArwFired := True }
        when(io.dAxi.w.fire) { dWFired := True }
        when((dArwFired || io.dAxi.arw.fire) && (dWFired || io.dAxi.w.fire)) {
          dAxiState := waitWriteResp
        }
      }

      is(waitWriteResp) {
        io.dAxi.b.ready := True
        when(io.dAxi.b.valid) {
          dAxiState := idle
        }
      }

      is(sendRead) {
        io.dAxi.arw.valid := True
        when(io.dAxi.arw.ready) {
          dAxiState := waitReadResp
        }
      }

      is(waitReadResp) {
        io.dAxi.r.ready := True
        when(io.dAxi.r.valid) {
          dRsp.valid := True
          dAxiState := idle
        }
      }
    }

    pipeline.ctrls.drop(1).foreach(e => e._2.throwWhen(clockDomain.reset))
    // Build the pipeline
    pipeline.build()
  }
}
