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
  * Exposes read-only output port for external observation.
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
  
  // Increment logic - wired from pipeline signals
  
  // cycles: always increments
  cycles := cycles + 1
  
  // instret: increments on commit
  val wbArea = new wbStage.Area {
    when(up(COMMIT)) {
      instret := instret + 1
    }
  }
  
  // Inputs for other counters (to be wired from CPU)
  val io = new Bundle {
    // Event inputs
    val hazardStall   = in Bool()
    val fetchStall    = in Bool()
    val memStall      = in Bool()
    val branchExecuted = in Bool()
    val branchTaken   = in Bool()
    val pipelineFlush = in Bool()
    
    // Output bundle
    val counters = out(PerfCountersBundle())
  }
  
  // Increment on events
  when(io.hazardStall)    { stallsHazard  := stallsHazard + 1 }
  when(io.fetchStall)     { stallsFetch   := stallsFetch + 1 }
  when(io.memStall)       { stallsMem     := stallsMem + 1 }
  when(io.branchExecuted) { branches      := branches + 1 }
  when(io.branchTaken)    { branchesTaken := branchesTaken + 1 }
  when(io.pipelineFlush)  { flushes       := flushes + 1 }
  
  // Wire registers to output bundle
  io.counters.cycles        := cycles
  io.counters.instret       := instret
  io.counters.stallsHazard  := stallsHazard
  io.counters.stallsFetch   := stallsFetch
  io.counters.stallsMem     := stallsMem
  io.counters.branches      := branches
  io.counters.branchesTaken := branchesTaken
  io.counters.flushes       := flushes
}
