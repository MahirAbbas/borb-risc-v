package borb.core

import spinal.core._
import borb.fetch.FrontendConfig

object BORBEnvConfig {
  private def parseBool(name: String, default: Boolean): Boolean = {
    sys.env.get(name).map(_.trim.toLowerCase) match {
      case Some("1" | "true" | "yes" | "on" | "enable" | "enabled") => true
      case Some("0" | "false" | "no" | "off" | "disable" | "disabled") => false
      case _ => default
    }
  }

  val frontendEnabled: Boolean = parseBool("BORB_FRONTEND_ENABLE", default = true)
}

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
    withCompressed = true,
    experimentalFrontendEnable = BORBEnvConfig.frontendEnabled,
    enablePredictorTraining = BORBEnvConfig.frontendEnabled,
    enablePredictedRedirect = BORBEnvConfig.frontendEnabled
  ),
  
  // Features
  perfCountersEnabled: Boolean = true,
  aExtensionEnabled: Boolean = true,
  mExtensionEnabled: Boolean = true,
  fExtensionEnabled: Boolean = true,
  dExtensionEnabled: Boolean = false,
  cExtensionEnabled: Boolean = true
) {
  require(
    !dExtensionEnabled || fExtensionEnabled,
    "RV64D requires RV64F (D implies F in misa)"
  )
}

object CpuConfig {
  /** Default RV64I configuration */
  def default: CpuConfig = CpuConfig()
}
