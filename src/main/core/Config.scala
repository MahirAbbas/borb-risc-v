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
  perfCountersEnabled: Boolean = true,
  mExtensionEnabled: Boolean = true,
  dExtensionEnabled: Boolean = false,

  // Floating-point scaffold (not integrated into CPU pipeline yet)
  fpuEnabled: Boolean = false,
  fpuFlen: Int = 64,
  fpuIssueTagWidth: Int = 4
)

object CpuConfig {
  /** Default RV64I configuration */
  def default: CpuConfig = CpuConfig()
}
