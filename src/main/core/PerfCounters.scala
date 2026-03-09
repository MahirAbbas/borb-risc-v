package borb.core

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._

/**
  * Performance counters bundle - read-only output for observation.
  */
case class PerfCountersBundle() extends Bundle {
  val cycles        = UInt(64 bits)
  val instret       = UInt(64 bits)
  val stallsHazard  = UInt(64 bits)
  val stallsFetch   = UInt(64 bits)
  val stallsMem     = UInt(64 bits)
  val stallsBackend = UInt(64 bits)
  val stallsWriteback = UInt(64 bits)
  val stallsCommit = UInt(64 bits)
  val stallsMulDivBusy = UInt(64 bits)
  val stallsLsuReplayOrWait = UInt(64 bits)
  val branches      = UInt(64 bits)
  val branchesTaken = UInt(64 bits)
  val flushes       = UInt(64 bits)
  val loads         = UInt(64 bits)
  val stores        = UInt(64 bits)
  val jumps         = UInt(64 bits)
  val csrOps        = UInt(64 bits)
  val mulDivOps     = UInt(64 bits)
  val trapCommits   = UInt(64 bits)
}

/**
  * Performance monitoring plugin.
  * 
  * Contains internal 64-bit registers for each counter.
  * Exposes signals for event input and counter output.
  */
case class PerfCountersPlugin(wbStage: CtrlLink) extends Area {
  import borb.common.Common._
  import borb.common.MicroCode._
  
  // Internal counter registers
  val cycles        = Reg(UInt(64 bits)) init 0
  val instret       = Reg(UInt(64 bits)) init 0
  val stallsHazard  = Reg(UInt(64 bits)) init 0
  val stallsFetch   = Reg(UInt(64 bits)) init 0
  val stallsMem     = Reg(UInt(64 bits)) init 0
  val stallsBackend = Reg(UInt(64 bits)) init 0
  val stallsWriteback = Reg(UInt(64 bits)) init 0
  val stallsCommit = Reg(UInt(64 bits)) init 0
  val stallsMulDivBusy = Reg(UInt(64 bits)) init 0
  val stallsLsuReplayOrWait = Reg(UInt(64 bits)) init 0
  val branches      = Reg(UInt(64 bits)) init 0
  val branchesTaken = Reg(UInt(64 bits)) init 0
  val flushes       = Reg(UInt(64 bits)) init 0
  val loads         = Reg(UInt(64 bits)) init 0
  val stores        = Reg(UInt(64 bits)) init 0
  val jumps         = Reg(UInt(64 bits)) init 0
  val csrOps        = Reg(UInt(64 bits)) init 0
  val mulDivOps     = Reg(UInt(64 bits)) init 0
  val trapCommits   = Reg(UInt(64 bits)) init 0
  
  // cycles: always increments
  cycles := cycles + 1
  
  // instret: increments on commit
  val wbArea = new wbStage.Area {
    when(up(COMMIT)) {
      instret := instret + 1

      switch(up(borb.frontend.Decoder.MicroCode)) {
        is(uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD) {
          loads := loads + 1
        }
        is(uopSB, uopSH, uopSW, uopSD) {
          stores := stores + 1
        }
        is(uopJAL, uopJALR) {
          jumps := jumps + 1
        }
        is(uopCSRRW, uopCSRRS, uopCSRRC, uopCSRRWI, uopCSRRSI, uopCSRRCI) {
          csrOps := csrOps + 1
        }
        is(
          uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
          uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW
        ) {
          mulDivOps := mulDivOps + 1
        }
      }
      when(up(TRAP)) {
        trapCommits := trapCommits + 1
      }
    }
  }
  
  // Event input signals (to be assigned from CPU)
  val hazardStall    = Bool()
  val fetchStall     = Bool()
  val memStall       = Bool()
  val backendStall   = Bool()
  val writebackStall = Bool()
  val commitStall = Bool()
  val mulDivBusyStall = Bool()
  val lsuReplayOrWaitStall = Bool()
  val branchExecuted = Bool()
  val branchTaken    = Bool()
  val pipelineFlush  = Bool()
  
  // Increment on events
  when(hazardStall)    { stallsHazard  := stallsHazard + 1 }
  when(fetchStall)     { stallsFetch   := stallsFetch + 1 }
  when(memStall)       { stallsMem     := stallsMem + 1 }
  when(backendStall)   { stallsBackend := stallsBackend + 1 }
  when(writebackStall) { stallsWriteback := stallsWriteback + 1 }
  when(commitStall) { stallsCommit := stallsCommit + 1 }
  when(mulDivBusyStall) { stallsMulDivBusy := stallsMulDivBusy + 1 }
  when(lsuReplayOrWaitStall) { stallsLsuReplayOrWait := stallsLsuReplayOrWait + 1 }
  when(branchExecuted) { branches      := branches + 1 }
  when(branchTaken)    { branchesTaken := branchesTaken + 1 }
  when(pipelineFlush)  { flushes       := flushes + 1 }
  
  // Output bundle
  val counters = PerfCountersBundle()
  counters.cycles        := cycles
  counters.instret       := instret
  counters.stallsHazard  := stallsHazard
  counters.stallsFetch   := stallsFetch
  counters.stallsMem     := stallsMem
  counters.stallsBackend := stallsBackend
  counters.stallsWriteback := stallsWriteback
  counters.stallsCommit := stallsCommit
  counters.stallsMulDivBusy := stallsMulDivBusy
  counters.stallsLsuReplayOrWait := stallsLsuReplayOrWait
  counters.branches      := branches
  counters.branchesTaken := branchesTaken
  counters.flushes       := flushes
  counters.loads         := loads
  counters.stores        := stores
  counters.jumps         := jumps
  counters.csrOps        := csrOps
  counters.mulDivOps     := mulDivOps
  counters.trapCommits   := trapCommits
}
