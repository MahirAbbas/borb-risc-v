package borb.core

import spinal.core._
import borb.fetch.FrontendConfig
import borb.vector.VectorConfig

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

  // Frontend configuration
  frontendConfig: FrontendConfig = FrontendConfig(
    addressWidth = 64,
    dataWidth = 64,
    lineBytes = 64,
    icacheBanks = 2,
    icacheSets = 64,
    icacheWays = 4,
    ftqDepth = 32,
    requestQueueDepth = 16,
    rasDepth = 48,
    gshareEntries = 512,
    globalHistoryWidth = 48,
    loopPredictorEnable = true,
    loopPredictorEntries = 32,
    nanoBtbEntries = 16,
    ftbEntries = 128,
    ftbWays = 4,
    indirectEntries = 64,
    indirectWays = 4,
    indirectHistoryWidth = 24,
    indirectTagWidth = 12,
    maxOutstandingMisses = 2,
    bundleQueueDepth = 32,
    withCompressed = true,
    enablePredictorTraining = true,
    enablePredictedRedirect = false
  ),

  // Shared vector-engine boundary. The engine is architected for four hart
  // contexts, with only hart 0 wired by the current single-core frontend.
  vectorConfig: VectorConfig = VectorConfig(),
  
  // Features
  perfCountersEnabled: Boolean = true,
  debugEnabled: Boolean = true,
  aExtensionEnabled: Boolean = true,
  mExtensionEnabled: Boolean = true,
  fExtensionEnabled: Boolean = true,
  dExtensionEnabled: Boolean = true,
  cExtensionEnabled: Boolean = true,

  // Trap/VM structure sizing
  pmpImplementedEntries: Int = 16,
  vmShadowEntries: Int = 512,
  vmTablePageEntries: Int = 64,
  itlbEntries: Int = 32,
  dtlbEntries: Int = 32,
  sharedTlbEntries: Int = 128
) {
  require(
    !dExtensionEnabled || fExtensionEnabled,
    "RV64D requires RV64F (D implies F in misa)"
  )
  require(pmpImplementedEntries > 0, "Need at least one PMP slot")
  require(vmShadowEntries > 0, "Need at least one VM shadow entry")
  require(vmTablePageEntries > 0, "Need at least one VM table page entry")
  require(itlbEntries > 0, "Need at least one ITLB entry")
  require(dtlbEntries > 0, "Need at least one DTLB entry")
  require(sharedTlbEntries > 0, "Need at least one shared TLB entry")
}

object CpuConfig {
  /** Default RV64I configuration */
  def default: CpuConfig = CpuConfig()
}
