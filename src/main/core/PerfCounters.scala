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
  val branches      = UInt(64 bits)
  val branchesTaken = UInt(64 bits)
  val flushes       = UInt(64 bits)
}

/**
  * Performance monitoring plugin.
  * 
  * Contains internal 64-bit registers for each counter.
  * Exposes signals for event input and counter output.
  */
case class PerfCountersPlugin(wbStage: CtrlLink) extends Area {
  import borb.common.Common._
  
  // Internal counter registers
  val cycles        = Reg(UInt(64 bits)) init 0
  val instret       = Reg(UInt(64 bits)) init 0
  val stallsHazard  = Reg(UInt(64 bits)) init 0
  val stallsFetch   = Reg(UInt(64 bits)) init 0
  val stallsMem     = Reg(UInt(64 bits)) init 0
  val branches      = Reg(UInt(64 bits)) init 0
  val branchesTaken = Reg(UInt(64 bits)) init 0
  val flushes       = Reg(UInt(64 bits)) init 0
  
  // cycles: always increments
  cycles := cycles + 1
  
  // instret: increments on commit
  val wbArea = new wbStage.Area {
    when(up(COMMIT)) {
      instret := instret + 1
    }
  }
  
  // Event input signals (to be assigned from CPU)
  val hazardStall    = Bool()
  val fetchStall     = Bool()
  val memStall       = Bool()
  val branchExecuted = Bool()
  val branchTaken    = Bool()
  val pipelineFlush  = Bool()
  
  // Increment on events
  when(hazardStall)    { stallsHazard  := stallsHazard + 1 }
  when(fetchStall)     { stallsFetch   := stallsFetch + 1 }
  when(memStall)       { stallsMem     := stallsMem + 1 }
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
  counters.branches      := branches
  counters.branchesTaken := branchesTaken
  counters.flushes       := flushes
}

