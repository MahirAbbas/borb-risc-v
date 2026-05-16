package borb.playground

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.misc.pipeline._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object ComposableCpuPayload {
  val PC = Payload(UInt(8 bits))
  val INSN = Payload(UInt(8 bits))
  val UOP = Payload(UInt(8 bits))
  val RESULT = Payload(UInt(8 bits))
}

class CtrlIsland(prefix: String) extends Area {
  val idToCtrl = mutable.LinkedHashMap[Int, CtrlLink]()
  def ctrl(id: Int): CtrlLink = idToCtrl.getOrElseUpdate(id, CtrlLink().setCompositeName(this, s"${prefix}_$id"))
  def apply(id: Int): CtrlLink = ctrl(id)
  def up: NodeApi = ctrl(0).up

  def ordered: Seq[CtrlLink] = {
    val idMax = (0 +: idToCtrl.keys.toSeq).max
    for (id <- 0 to idMax) ctrl(id)
    idToCtrl.toSeq.sortBy(_._1).map(_._2)
  }

  def last: CtrlLink = ordered.last
  def down: NodeApi = last.down
  def validVec: Vec[Bool] = Vec(ordered.map(_.up.isValid))
  def readyVec: Vec[Bool] = Vec(ordered.map(_.up.isReady))

  def build(makeStage: (Int, CtrlLink, CtrlLink) => StageLink = (_, from, to) => new StageLink(from.down, to.up)): Seq[Link] = {
    val ctrls = ordered
    val stages = for (stageId <- ctrls.indices.dropRight(1)) yield {
      makeStage(stageId, ctrls(stageId), ctrls(stageId + 1)).withoutCollapse().setCompositeName(this, s"${prefix}_stage_$stageId")
    }
    val links = (stages ++ ctrls).toSeq
    Builder(links)
    links
  }
}

class LaneCtrlIsland(prefix: String, laneCount: Int) extends Area {
  case class Lane(ctrl: LaneCtrl, laneId: Int) extends Area {
    val cancel = Bool()
    def up: NodeApi = ctrl.link.up
    def down: NodeApi = ctrl.link.down
  }

  class LaneCtrl(id: Int) extends Area {
    val idToLane = mutable.LinkedHashMap[Int, Lane]()
    val link = CtrlLink().setCompositeName(this, s"${prefix}_$id")
    def lane(laneId: Int): Lane = idToLane.getOrElseUpdate(laneId, Lane(this, laneId).setCompositeName(this, s"lane_$laneId"))
  }

  val idToCtrl = mutable.LinkedHashMap[Int, LaneCtrl]()
  def ctrl(id: Int): LaneCtrl = idToCtrl.getOrElseUpdate(id, new LaneCtrl(id).setCompositeName(this, s"${prefix}_$id"))
  def lane(ctrlId: Int, laneId: Int): Lane = ctrl(ctrlId).lane(laneId)
  def up: NodeApi = ctrl(0).link.up

  def ordered: Seq[LaneCtrl] = {
    val idMax = (0 +: idToCtrl.keys.toSeq).max
    for (id <- 0 to idMax) ctrl(id)
    idToCtrl.toSeq.sortBy(_._1).map(_._2)
  }

  def last: LaneCtrl = ordered.last
  def down: NodeApi = last.link.down
  def validVec: Vec[Bool] = Vec(ordered.map(_.link.up.isValid))
  def readyVec: Vec[Bool] = Vec(ordered.map(_.link.up.isReady))

  def build(): Seq[Link] = {
    val ctrls = ordered
    val stages = for (stageId <- ctrls.indices.dropRight(1)) yield {
      val from = ctrls(stageId)
      val to = ctrls(stageId + 1)
      new StageLink(from.link.down, to.link.up) {
        override def build(): Unit = {
          super.build()
          for (laneId <- 0 until laneCount) {
            val l = to.lane(laneId)
            l.cancel := False
          }
        }
      }.withoutCollapse().setCompositeName(this, s"${prefix}_stage_$stageId")
    }
    val links = (stages ++ ctrls.map(_.link)).toSeq
    Builder(links)
    links
  }
}

case class ComposableDummyCpu() extends Component {
  import ComposableCpuPayload._

  val io = new Bundle {
    val start = in Bool()
    val fetchFlush = in Bool()
    val scheduleHold = in Bool()
    val executeFreeze = in Bool()
    val retire = master(Stream(UInt(8 bits)))
    val fetchValid = out(Vec(Bool(), 2))
    val scheduleValid = out(Vec(Bool(), 2))
    val executeValid = out(Vec(Bool(), 3))
    val fetchReady = out(Vec(Bool(), 2))
    val scheduleReady = out(Vec(Bool(), 2))
    val executeReady = out(Vec(Bool(), 3))
  }

  val fetch = new CtrlIsland("fetch")
  val schedule = new LaneCtrlIsland("schedule", laneCount = 2)
  val execute = new CtrlIsland("execute")
  val fetchToSchedule = StreamFifo(UInt(8 bits), depth = 4)
  val scheduleToExecute = StreamFifo(UInt(8 bits), depth = 4)

  val pc = Reg(UInt(8 bits)) init(0)
  fetch.up.valid := io.start
  fetch.up(PC) := pc
  when(fetch.up.isFiring) {
    pc := pc + 4
  }
  fetch(0).throwWhen(io.fetchFlush, usingReady = true)
  fetch(1)
  fetch.down(INSN) := fetch.down(PC) + 1
  fetchToSchedule.io.push.valid := fetch.down.isValid
  fetchToSchedule.io.push.payload := fetch.down(INSN)
  fetch.down.ready := fetchToSchedule.io.push.ready

  schedule.up.valid := fetchToSchedule.io.pop.valid
  schedule.up(INSN) := fetchToSchedule.io.pop.payload
  fetchToSchedule.io.pop.ready := schedule.up.isReady || schedule.up.isCancel
  schedule.ctrl(0).link.haltWhen(io.scheduleHold)
  schedule.ctrl(1)
  schedule.down(UOP) := schedule.down(INSN) ^ U(0x55, 8 bits)
  scheduleToExecute.io.push.valid := schedule.down.isValid
  scheduleToExecute.io.push.payload := schedule.down(UOP)
  schedule.down.ready := scheduleToExecute.io.push.ready

  val executeFreeze = Bool()
  executeFreeze := io.executeFreeze
  execute(1)
  execute(2)
  execute.up.valid := scheduleToExecute.io.pop.valid
  execute.up(UOP) := scheduleToExecute.io.pop.payload
  scheduleToExecute.io.pop.ready := execute.up.isReady || execute.up.isCancel
  execute.down(RESULT) := execute.down(UOP) + 3
  io.retire.valid := execute.down.isValid
  io.retire.payload := execute.down(RESULT)
  execute.down.ready := io.retire.ready && !executeFreeze

  io.fetchValid := fetch.validVec
  io.scheduleValid := schedule.validVec
  io.executeValid := execute.validVec
  io.fetchReady := fetch.readyVec
  io.scheduleReady := schedule.readyVec
  io.executeReady := execute.readyVec

  fetch.build()
  schedule.build()
  execute.build()
}

object ComposableCpuPipelinePlayground extends App {
  if (args.contains("--simulate")) {
    SimConfig.compile(ComposableDummyCpu()).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.start #= false
      dut.io.fetchFlush #= false
      dut.io.scheduleHold #= false
      dut.io.executeFreeze #= false
      dut.io.retire.ready #= true
      dut.clockDomain.waitSampling(2)

      val rows = ArrayBuffer[String]()
      def bits(v: Vec[Bool]) = v.map(_.toBoolean).mkString
      def sample(tag: String): Unit = {
        rows += f"$tag%-8s fv=${bits(dut.io.fetchValid)} fr=${bits(dut.io.fetchReady)} sv=${bits(dut.io.scheduleValid)} sr=${bits(dut.io.scheduleReady)} xv=${bits(dut.io.executeValid)} xr=${bits(dut.io.executeReady)} retire=${dut.io.retire.valid.toBoolean}/${dut.io.retire.ready.toBoolean}/${dut.io.retire.payload.toInt}"
      }

      for (cycle <- 0 until 18) {
        dut.io.start #= cycle < 10
        dut.io.scheduleHold #= (cycle == 5 || cycle == 6)
        dut.io.executeFreeze #= (cycle == 11 || cycle == 12)
        dut.io.fetchFlush #= (cycle == 14)
        dut.io.retire.ready #= cycle != 15
        dut.clockDomain.waitSampling()
        sample(s"c$cycle")
      }
      rows.foreach(println)
    }
  } else {
    SpinalVerilog(ComposableDummyCpu())
  }
}
