package borb.playground

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.misc.pipeline._

import scala.collection.mutable.ArrayBuffer

object PipelineInvestigationPayload {
  val DATA = Payload(UInt(8 bits))
}

case class LegalStageCtrlPlayground() extends Component {
  import PipelineInvestigationPayload._

  val io = new Bundle {
    val input = slave(Stream(UInt(8 bits)))
    val output = master(Stream(UInt(8 bits)))
    val haltStage1 = in Bool()
    val throwStage1 = in Bool()
    val freezeTail = in Bool()
    val valid = out(Vec(Bool(), 3))
    val ready = out(Vec(Bool(), 3))
    val firing = out(Vec(Bool(), 3))
    val payload = out(Vec(UInt(8 bits), 3))
  }

  val pipeline = new StageCtrlPipeline()
  val s0 = pipeline.ctrl(0)
  val s1 = pipeline.ctrl(1)
  val s2 = pipeline.ctrl(2)

  s0.up.valid := io.input.valid
  io.input.ready := s0.up.isReady || s0.up.isCancel
  s0.up(DATA) := io.input.payload

  s1.haltWhen(io.haltStage1)
  s1.throwWhen(io.throwStage1)

  io.output.valid := s2.down.isValid
  io.output.payload := s2.down(DATA)
  s2.down.ready := io.output.ready && !io.freezeTail

  for ((ctrl, idx) <- Seq(s0, s1, s2).zipWithIndex) {
    io.valid(idx) := ctrl.up.isValid
    io.ready(idx) := ctrl.up.isReady
    io.firing(idx) := ctrl.up.isFiring
    io.payload(idx) := ctrl.up(DATA)
  }

  pipeline.build()
}

case class VexiiStyleIndependentLinks() extends Component {
  import PipelineInvestigationPayload._

  val io = new Bundle {
    val input = slave(Stream(UInt(8 bits)))
    val output = master(Stream(UInt(8 bits)))
    val freezePipe = in Bool()
    val cancelMiddle = in Bool()
  }

  val ctrls = Seq.tabulate(3) { id =>
    CtrlLink().setCompositeName(this, s"ctrl_$id")
  }
  ctrls.head.up.valid := io.input.valid
  io.input.ready := ctrls.head.up.isReady || ctrls.head.up.isCancel
  ctrls.head.up(DATA) := io.input.payload

  ctrls(1).throwWhen(io.cancelMiddle, usingReady = false)

  io.output.valid := ctrls.last.down.isValid
  io.output.payload := ctrls.last.down(DATA)
  ctrls.last.down.ready := io.output.ready && !io.freezePipe

  val stages = (for ((from, to) <- (ctrls, ctrls.tail).zipped) yield {
    new StageLink(from.down, to.up).withoutCollapse()
  }).toSeq
  Builder(stages ++ ctrls)
}

case class LegalReadStatusOnly() extends Component {
  val io = new Bundle {
    val middleValid = out Bool()
    val middleReady = out Bool()
  }
  val pipeline = new StageCtrlPipeline()
  val s0 = pipeline.ctrl(0)
  val s1 = pipeline.ctrl(1)
  val s2 = pipeline.ctrl(2)
  s0.up.valid := True
  s2.down.ready := True
  io.middleValid := s1.up.isValid
  io.middleReady := s1.down.isReady
  pipeline.build()
}

case class IllegalMiddleValidDriver() extends Component {
  val pipeline = new StageCtrlPipeline()
  pipeline.ctrl(0)
  val s1 = pipeline.ctrl(1)
  pipeline.ctrl(2).down.ready := True
  s1.up.valid := False
  pipeline.build()
}

case class IllegalMiddleReadyDriver() extends Component {
  val pipeline = new StageCtrlPipeline()
  pipeline.ctrl(0).up.valid := True
  val s1 = pipeline.ctrl(1)
  pipeline.ctrl(2).down.ready := True
  s1.down.ready := True
  pipeline.build()
}

case class ManualMiddleValidAfterBuild() extends Component {
  val pipeline = new StageCtrlPipeline()
  pipeline.ctrl(0).up.valid := True
  val s1 = pipeline.ctrl(1)
  pipeline.ctrl(2).down.ready := True
  pipeline.build()
  s1.up.valid := False
}

case class IllegalStatusDriver() extends Component {
  val pipeline = new StageCtrlPipeline()
  val s0 = pipeline.ctrl(0)
  pipeline.ctrl(1)
  pipeline.ctrl(2).down.ready := True
  s0.up.valid := True
  s0.up.isValid := False
  pipeline.build()
}

object PipelineInvestigation extends App {
  def generate(name: String)(gen: => Component): Unit = {
    try {
      SpinalVerilog(gen)
      println(s"$name: generated")
    } catch {
      case e: Throwable =>
        println(s"$name: failed: ${e.getClass.getSimpleName}: ${e.getMessage.linesIterator.take(8).mkString(" | ")}")
    }
  }

  if (args.contains("--simulate")) {
    SimConfig.compile(LegalStageCtrlPlayground()).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.input.valid #= false
      dut.io.input.payload #= 0
      dut.io.output.ready #= true
      dut.io.haltStage1 #= false
      dut.io.throwStage1 #= false
      dut.io.freezeTail #= false
      dut.clockDomain.waitSampling(2)

      val rows = ArrayBuffer[String]()
      def sample(tag: String): Unit = {
        rows += f"$tag%-12s v=${dut.io.valid.map(_.toBoolean).mkString} r=${dut.io.ready.map(_.toBoolean).mkString} f=${dut.io.firing.map(_.toBoolean).mkString} p=${dut.io.payload.map(_.toInt).mkString(",")} out=${dut.io.output.valid.toBoolean}/${dut.io.output.ready.toBoolean}/${dut.io.output.payload.toInt}"
      }

      for (cycle <- 0 until 12) {
        dut.io.input.valid #= cycle < 6
        dut.io.input.payload #= (cycle + 10)
        dut.io.haltStage1 #= (cycle == 3 || cycle == 4)
        dut.io.freezeTail #= (cycle == 7 || cycle == 8)
        dut.io.throwStage1 #= (cycle == 10)
        dut.clockDomain.waitSampling()
        sample(s"cycle_$cycle")
      }
      rows.foreach(println)
    }
  } else {
    generate("legal StageCtrlPipeline + request APIs")(LegalStageCtrlPlayground())
    generate("legal Vexii-style independent CtrlLinks")(VexiiStyleIndependentLinks())
    generate("legal read-only status observation")(LegalReadStatusOnly())
    generate("manual middle valid before build")(IllegalMiddleValidDriver())
    generate("manual middle valid after build")(ManualMiddleValidAfterBuild())
    generate("illegal manual middle ready driver")(IllegalMiddleReadyDriver())
    generate("illegal status driver")(IllegalStatusDriver())
  }
}
