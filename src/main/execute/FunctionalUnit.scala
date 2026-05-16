package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._

import borb.common.MicroCode
import borb.frontend.ExecutionUnitEnum
import scala.collection.mutable.ArrayBuffer

class FunctionalUnit(val executionUnit: ExecutionUnitEnum.E) extends Area {
  val SEL = Payload(Bool())
  val supportedUops: ArrayBuffer[MicroCode.E] = ArrayBuffer[MicroCode.E]()

  def add(uop: MicroCode.E): this.type = {
    supportedUops += uop
    this
  }

  def supports(uop: MicroCode.E): Boolean = supportedUops.contains(uop)

  def supportsHw(microCode: MicroCode.C): Bool = {
    supportedUops.map(microCode === _).reduceOption(_ || _).getOrElse(False)
  }
}
