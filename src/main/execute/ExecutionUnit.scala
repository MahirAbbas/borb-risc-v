package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.dispatch.RegFileWrite
import scala.collection.mutable.ArrayBuffer
import spinal.lib.misc.plugin._

trait FunctionalUnit {
  // Marker trait for Functional Units
  def getWriteback(): Option[(Bool, RegFileWrite)]
}

class ExecutionUnit(executeStage: CtrlLink, writebackStage: CtrlLink, writePort: RegFileWrite, fus: Seq[FunctionalUnit]) extends FiberPlugin {

  // Instantiate WriteBack service
  // This reads WriteBack.RESULT from the writebackStage and drives the register file write port
  val writeback = new WriteBack(writebackStage, writePort)
}
