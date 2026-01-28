package borb.core

import spinal.core._

/**
  * Centralized CPU configuration.
  * 
  * This case class captures all configurable parameters of the CPU,
  * replacing hardcoded values scattered throughout the codebase.
  */
case class CpuConfig(
  // ISA configuration
  xlen: Int = 64,
  
  // Address configuration  
  physicalAddrWidth: Int = 64,
  
  // Bus ID widths
  fetchIdWidth: Int = 16,
  dataIdWidth: Int = 16,
  
  // Features
  perfCountersEnabled: Boolean = true
)

object CpuConfig {
  /** Default RV64I configuration */
  def default: CpuConfig = CpuConfig()
}
