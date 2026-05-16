package borb.playground

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.misc.pipeline._

import scala.collection.mutable.ArrayBuffer

object IndependentPipelinePlaygroundPayload {
  val PC = Payload(UInt(8 bits))
  val INSN = Payload(UInt(8 bits))
  val UOP = Payload(UInt(8 bits))
  val RESULT = Payload(UInt(8 bits))
}

class ManualStageLink(val up: Node, val down: Node, collapseBubble: Boolean = false) extends Link {
  down.up = this
  up.down = this

  override def ups: Seq[Node] = Seq(up)
  override def downs: Seq[Node] = Seq(down)

  override def propagateDown(): Unit = {
    propagateDownAll()
    if (up.ctrl.valid.nonEmpty) down.valid
    down.ctrl.forgetOneSupported = true
  }

  override def propagateUp(): Unit = {
    propagateUpAll()
    if (down.ctrl.ready.nonEmpty) up.ready
  }

  override def build(): Unit = {
    val matches = down.fromUp.payload.intersect(up.fromDown.payload)

    if (down.ctrl.valid.nonEmpty) down.valid.setAsReg().init(False)
    matches.foreach(p => down(p).setAsReg())

    down.ctrl.forgetOne.foreach(cond => down.valid.clearWhen(cond))

    if (down.ctrl.valid.nonEmpty) {
      when(up.isReady) {
        down.valid := up.isValid
      }
    }

    when(up.isReady) {
      matches.foreach(p => down(p) := up(p))
    }

    if (up.ctrl.ready.nonEmpty) {
      up.ready := down.ready
      if (collapseBubble) up.ready.setWhen(!down.isValid)
    }
  }
}

case class IndependentPipelinePlayground() extends Component {
  import IndependentPipelinePlaygroundPayload._

  val io = new Bundle {
    val start = in Bool()
    val fetchFlush = in Bool()
    val decodeStall = in Bool()
    val executeStall = in Bool()
    val retire = master(Stream(UInt(8 bits)))
    val dbgFetchValid = out(Vec(Bool(), 2))
    val dbgDecodeValid = out(Vec(Bool(), 2))
    val dbgExecuteValid = out(Vec(Bool(), 2))
    val dbgFetchReady = out(Vec(Bool(), 2))
    val dbgDecodeReady = out(Vec(Bool(), 2))
    val dbgExecuteReady = out(Vec(Bool(), 2))
  }

  val fetchToDecode = StreamFifo(UInt(8 bits), depth = 4)
  val decodeToExecute = StreamFifo(UInt(8 bits), depth = 4)

  val fetch0, fetch1 = CtrlLink()
  val decode0, decode1 = CtrlLink()
  val execute0, execute1 = CtrlLink()

  val pc = Reg(UInt(8 bits)) init(0)

  fetch0.up.valid := io.start
  fetch0.up(PC) := pc
  when(fetch0.up.isFiring) {
    pc := pc + 4
  }
  fetch0.throwWhen(io.fetchFlush, usingReady = true)
  fetch1.down(INSN) := fetch1.down(PC) + 1
  fetchToDecode.io.push.valid := fetch1.down.isValid
  fetchToDecode.io.push.payload := fetch1.down(INSN)
  fetch1.down.ready := fetchToDecode.io.push.ready

  decode0.up.valid := fetchToDecode.io.pop.valid
  fetchToDecode.io.pop.ready := decode0.up.isReady || decode0.up.isCancel
  decode0.up(INSN) := fetchToDecode.io.pop.payload
  decode0.haltWhen(io.decodeStall)
  decode1.down(UOP) := decode1.down(INSN) + 2
  decodeToExecute.io.push.valid := decode1.down.isValid
  decodeToExecute.io.push.payload := decode1.down(UOP)
  decode1.down.ready := decodeToExecute.io.push.ready

  execute0.up.valid := decodeToExecute.io.pop.valid
  decodeToExecute.io.pop.ready := execute0.up.isReady || execute0.up.isCancel
  execute0.up(UOP) := decodeToExecute.io.pop.payload
  execute0.haltWhen(io.executeStall)
  execute1.down(RESULT) := execute1.down(UOP) + 3
  io.retire.valid := execute1.down.isValid
  io.retire.payload := execute1.down(RESULT)
  execute1.down.ready := io.retire.ready

  def tap(ctrls: Seq[CtrlLink], valid: Vec[Bool], ready: Vec[Bool]): Unit = {
    for ((ctrl, id) <- ctrls.zipWithIndex) {
      valid(id) := ctrl.up.isValid
      ready(id) := ctrl.up.isReady
    }
  }

  tap(Seq(fetch0, fetch1), io.dbgFetchValid, io.dbgFetchReady)
  tap(Seq(decode0, decode1), io.dbgDecodeValid, io.dbgDecodeReady)
  tap(Seq(execute0, execute1), io.dbgExecuteValid, io.dbgExecuteReady)

  Builder(Seq(
    new ManualStageLink(fetch0.down, fetch1.up).setCompositeName(this, "fetch_stage"),
    fetch0,
    fetch1
  ))
  Builder(Seq(
    new ManualStageLink(decode0.down, decode1.up).setCompositeName(this, "decode_stage"),
    decode0,
    decode1
  ))
  Builder(Seq(
    new ManualStageLink(execute0.down, execute1.up).setCompositeName(this, "execute_stage"),
    execute0,
    execute1
  ))
}

case class CtrlLinkIslandPlayground() extends Component {
  import IndependentPipelinePlaygroundPayload._

  val io = new Bundle {
    val start = in Bool()
    val fetchFlush = in Bool()
    val decodeStall = in Bool()
    val executeStall = in Bool()
    val retire = master(Stream(UInt(8 bits)))
    val dbgFetchValid = out(Vec(Bool(), 3))
    val dbgDecodeValid = out(Vec(Bool(), 3))
    val dbgExecuteValid = out(Vec(Bool(), 3))
    val dbgFetchReady = out(Vec(Bool(), 3))
    val dbgDecodeReady = out(Vec(Bool(), 3))
    val dbgExecuteReady = out(Vec(Bool(), 3))
  }

  val fetchToDecode = StreamFifo(UInt(8 bits), depth = 4)
  val decodeToExecute = StreamFifo(UInt(8 bits), depth = 4)

  def ctrl(name: String, id: Int): CtrlLink = CtrlLink().setCompositeName(this, s"${name}_$id")
  val fetch = Seq.tabulate(3)(ctrl("fetch", _))
  val decode = Seq.tabulate(3)(ctrl("decode", _))
  val execute = Seq.tabulate(3)(ctrl("execute", _))

  val pc = Reg(UInt(8 bits)) init(0)

  fetch(0).up.valid := io.start
  fetch(0).up(PC) := pc
  when(fetch(0).up.isFiring) {
    pc := pc + 4
  }
  fetch(0).throwWhen(io.fetchFlush, usingReady = true)
  fetch(2).down(INSN) := fetch(2).down(PC) + 1
  fetchToDecode.io.push.valid := fetch(2).down.isValid
  fetchToDecode.io.push.payload := fetch(2).down(INSN)
  fetch(2).down.ready := fetchToDecode.io.push.ready

  decode(0).up.valid := fetchToDecode.io.pop.valid
  fetchToDecode.io.pop.ready := decode(0).up.isReady || decode(0).up.isCancel
  decode(0).up(INSN) := fetchToDecode.io.pop.payload
  decode(1).haltWhen(io.decodeStall)
  decode(2).down(UOP) := decode(2).down(INSN) + 2
  decodeToExecute.io.push.valid := decode(2).down.isValid
  decodeToExecute.io.push.payload := decode(2).down(UOP)
  decode(2).down.ready := decodeToExecute.io.push.ready

  execute(0).up.valid := decodeToExecute.io.pop.valid
  decodeToExecute.io.pop.ready := execute(0).up.isReady || execute(0).up.isCancel
  execute(0).up(UOP) := decodeToExecute.io.pop.payload
  execute(1).haltWhen(io.executeStall)
  execute(2).down(RESULT) := execute(2).down(UOP) + 3
  io.retire.valid := execute(2).down.isValid
  io.retire.payload := execute(2).down(RESULT)
  execute(2).down.ready := io.retire.ready

  def tap(ctrls: Seq[CtrlLink], valid: Vec[Bool], ready: Vec[Bool]): Unit = {
    for ((ctrl, id) <- ctrls.zipWithIndex) {
      valid(id) := ctrl.up.isValid
      ready(id) := ctrl.up.isReady
    }
  }

  tap(fetch, io.dbgFetchValid, io.dbgFetchReady)
  tap(decode, io.dbgDecodeValid, io.dbgDecodeReady)
  tap(execute, io.dbgExecuteValid, io.dbgExecuteReady)

  def buildIsland(name: String, ctrls: Seq[CtrlLink]): Unit = {
    val stages = (for ((from, to) <- (ctrls, ctrls.tail).zipped) yield {
      new StageLink(from.down, to.up).withoutCollapse().setCompositeName(this, s"${name}_stage")
    }).toSeq
    Builder(stages ++ ctrls)
  }

  buildIsland("fetch", fetch)
  buildIsland("decode", decode)
  buildIsland("execute", execute)
}

case class IllegalUserDrivesBuiltManualStage() extends Component {
  val a, b = CtrlLink()
  a.up.valid := True
  b.down.ready := True
  Builder(Seq(new ManualStageLink(a.down, b.up), a, b))
  b.up.ready := True
}

object IndependentPipelinePlayground extends App {
  def generate(name: String)(gen: => Component): Unit = {
    try {
      SpinalVerilog(gen)
      println(s"$name: generated")
    } catch {
      case e: Throwable =>
        println(s"$name: failed: ${e.getClass.getSimpleName}: ${e.getMessage.linesIterator.take(8).mkString(" | ")}")
    }
  }

  if (args.contains("--simulate-ctrl")) {
    SimConfig.compile(CtrlLinkIslandPlayground()).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.start #= false
      dut.io.fetchFlush #= false
      dut.io.decodeStall #= false
      dut.io.executeStall #= false
      dut.io.retire.ready #= true
      dut.clockDomain.waitSampling(2)

      val rows = ArrayBuffer[String]()
      def bits(v: Vec[Bool]) = v.map(_.toBoolean).mkString
      def sample(tag: String): Unit = {
        rows += f"$tag%-8s fv=${bits(dut.io.dbgFetchValid)} fr=${bits(dut.io.dbgFetchReady)} dv=${bits(dut.io.dbgDecodeValid)} dr=${bits(dut.io.dbgDecodeReady)} ev=${bits(dut.io.dbgExecuteValid)} er=${bits(dut.io.dbgExecuteReady)} retire=${dut.io.retire.valid.toBoolean}/${dut.io.retire.ready.toBoolean}/${dut.io.retire.payload.toInt}"
      }

      for (cycle <- 0 until 20) {
        dut.io.start #= cycle < 11
        dut.io.decodeStall #= (cycle == 6 || cycle == 7)
        dut.io.executeStall #= (cycle == 12 || cycle == 13)
        dut.io.fetchFlush #= (cycle == 15)
        dut.io.retire.ready #= (cycle != 17)
        dut.clockDomain.waitSampling()
        sample(s"c$cycle")
      }
      rows.foreach(println)
    }
  } else if (args.contains("--simulate")) {
    SimConfig.compile(IndependentPipelinePlayground()).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.start #= false
      dut.io.fetchFlush #= false
      dut.io.decodeStall #= false
      dut.io.executeStall #= false
      dut.io.retire.ready #= true
      dut.clockDomain.waitSampling(2)

      val rows = ArrayBuffer[String]()
      def bits(v: Vec[Bool]) = v.map(_.toBoolean).mkString
      def sample(tag: String): Unit = {
        rows += f"$tag%-8s fv=${bits(dut.io.dbgFetchValid)} fr=${bits(dut.io.dbgFetchReady)} dv=${bits(dut.io.dbgDecodeValid)} dr=${bits(dut.io.dbgDecodeReady)} ev=${bits(dut.io.dbgExecuteValid)} er=${bits(dut.io.dbgExecuteReady)} retire=${dut.io.retire.valid.toBoolean}/${dut.io.retire.ready.toBoolean}/${dut.io.retire.payload.toInt}"
      }

      for (cycle <- 0 until 18) {
        dut.io.start #= cycle < 10
        dut.io.decodeStall #= (cycle == 5 || cycle == 6)
        dut.io.executeStall #= (cycle == 10 || cycle == 11)
        dut.io.fetchFlush #= (cycle == 13)
        dut.io.retire.ready #= (cycle != 15)
        dut.clockDomain.waitSampling()
        sample(s"c$cycle")
      }
      rows.foreach(println)
    }
  } else {
    generate("CtrlLink/StageLink independent islands")(CtrlLinkIslandPlayground())
    generate("independent fetch/decode/execute pipelines")(IndependentPipelinePlayground())
    generate("illegal external ready drive after manual link build")(IllegalUserDrivesBuiltManualStage())
  }
}
