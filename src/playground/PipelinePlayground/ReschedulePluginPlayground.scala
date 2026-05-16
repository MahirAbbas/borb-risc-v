package borb.playground

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.misc.pipeline._
import spinal.lib.misc.plugin._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object ReschedulePlaygroundPayload {
  val PC = Payload(UInt(32 bits))
  val INSN = Payload(UInt(8 bits))
  val UOP = Payload(UInt(8 bits))
  val RESULT = Payload(UInt(8 bits))
}

case class PlaygroundRescheduleConfig(
    addressWidth: Int = 32,
    laneCount: Int = 2
) {
  require(addressWidth > 0)
  require(laneCount > 0)

  val laneAgeWidth: Int = log2Up(laneCount max 2)
}

object PlaygroundRescheduleReason extends SpinalEnum {
  val branch, trap, replay, fencei, external = newElement()
}

case class PlaygroundRescheduleCmd(
    laneAgeWidth: Int,
    config: PlaygroundRescheduleConfig = PlaygroundRescheduleConfig()
) extends Bundle {
  val pc = UInt(config.addressWidth bits)
  val laneAge = UInt(laneAgeWidth bits)
  val self = Bool()
  val flushFrontend = Bool()
  val reason = PlaygroundRescheduleReason()
}

object PlaygroundRescheduleCmd {
  def idle(flow: Flow[PlaygroundRescheduleCmd]): Unit = {
    flow.valid := False
    flow.payload.assignDontCare()
    flow.payload.pc := 0
    flow.payload.laneAge := 0
    flow.payload.self := False
    flow.payload.flushFrontend := False
    flow.payload.reason := PlaygroundRescheduleReason.branch
  }
}

trait PlaygroundRescheduleService {
  def config: PlaygroundRescheduleConfig
  def reschedule: Flow[PlaygroundRescheduleCmd]
  def newRequest(
      pluginName: String,
      age: Int,
      laneAgeWidth: Int = config.laneAgeWidth
  ): Flow[PlaygroundRescheduleCmd]
  def isFlushedAt(age: Int, laneAge: UInt): Bool
  def buildArbitration(): Unit
}

abstract class BasePlaygroundReschedulePlugin(
    val config: PlaygroundRescheduleConfig
) extends FiberPlugin
    with PlaygroundRescheduleService {
  case class RequestPort(
      pluginName: String,
      age: Int,
      ordinal: Int,
      flow: Flow[PlaygroundRescheduleCmd]
  )

  val requests = ArrayBuffer[RequestPort]()
  val reschedule = Flow(PlaygroundRescheduleCmd(config.laneAgeWidth, config))

  def newRequest(
      pluginName: String,
      age: Int,
      laneAgeWidth: Int = config.laneAgeWidth
  ): Flow[PlaygroundRescheduleCmd] = {
    val flow = Flow(PlaygroundRescheduleCmd(laneAgeWidth, config))
    flow.setCompositeName(this, s"${pluginName}_request")
    requests += RequestPort(pluginName, age, requests.length, flow)
    flow
  }

  protected def highToLowPriority: Seq[RequestPort]

  def buildArbitration(): Unit = {
    PlaygroundRescheduleCmd.idle(reschedule)
    for (request <- highToLowPriority.reverse) {
      when(request.flow.valid) {
        reschedule.valid := True
        reschedule.payload.pc := request.flow.payload.pc
        reschedule.payload.laneAge := request.flow.payload.laneAge.resized
        reschedule.payload.self := request.flow.payload.self
        reschedule.payload.flushFrontend := request.flow.payload.flushFrontend
        reschedule.payload.reason := request.flow.payload.reason
      }
    }
  }

  def isFlushedAt(age: Int, laneAge: UInt): Bool = {
    val hits = requests.filter(_.age >= age).map { request =>
      val flow = request.flow
      if (request.age == age) {
        flow.valid && (
          flow.payload.laneAge > laneAge ||
            (flow.payload.laneAge === laneAge && flow.payload.self)
        )
      } else {
        flow.valid
      }
    }
    if (hits.isEmpty) False else hits.orR
  }
}

class AllocationPriorityReschedulePlugin(
    config: PlaygroundRescheduleConfig
) extends BasePlaygroundReschedulePlugin(config) {
  protected def highToLowPriority: Seq[RequestPort] = requests.toSeq
}

class OldestAgeReschedulePlugin(
    config: PlaygroundRescheduleConfig
) extends BasePlaygroundReschedulePlugin(config) {
  protected def highToLowPriority: Seq[RequestPort] =
    requests.sortBy(request => (-request.age, request.ordinal)).toSeq
}

trait PlaygroundPipelineService {
  def config: PlaygroundRescheduleConfig
  def laneLive: Payload[Bool]
  def ctrl(id: Int): CtrlLink
  def apply(id: Int): CtrlLink = ctrl(id)
  def stageAge(id: Int, prediction: Boolean = false): Int
  def stageIds: Seq[Int]
  def connectFrontendFlushes(reschedule: PlaygroundRescheduleService, lastFrontendStage: Int): Unit
  def connectLaneFlushes(reschedule: PlaygroundRescheduleService): Unit
  def freezeWhen(cond: Bool): Unit
  def freeze: Bool
  def buildPipeline(): Unit
}

class StageCtrlPipelineServicePlugin(
    val config: PlaygroundRescheduleConfig,
    stageStride: Int = 10
) extends FiberPlugin
    with PlaygroundPipelineService {
  val laneLive = Payload(Bool())
  val pipeline = new StageCtrlPipeline()
  val freezeRequests = ArrayBuffer[Bool]()
  val freeze = Bool()

  def ctrl(id: Int): CtrlLink = pipeline.ctrl(id)

  def stageAge(id: Int, prediction: Boolean = false): Int =
    id * stageStride + (if (prediction) 0 else 1)

  def stageIds: Seq[Int] = pipeline.ctrls.keys.toSeq.sorted

  def connectFrontendFlushes(reschedule: PlaygroundRescheduleService, lastFrontendStage: Int): Unit = {
    val flushFrontend = reschedule.reschedule.valid && reschedule.reschedule.payload.flushFrontend
    for (id <- stageIds if id > 0 && id <= lastFrontendStage) {
      ctrl(id).throwWhen(flushFrontend, usingReady = false)
    }
  }

  def connectLaneFlushes(reschedule: PlaygroundRescheduleService): Unit = {
    for (id <- stageIds; laneId <- 0 until config.laneCount) {
      val link = ctrl(id)
      if (id != 0) {
        link.up(laneLive, laneId).setAsReg().init(False)
      }
      val doIt = reschedule.isFlushedAt(
        stageAge(id),
        U(laneId, config.laneAgeWidth bits)
      )
      when(link.up.isValid && doIt) {
        link.bypass(laneLive, laneId) := False
      }
    }
  }

  def freezeWhen(cond: Bool): Unit = freezeRequests += cond

  def buildPipeline(): Unit = {
    freeze := (if (freezeRequests.isEmpty) False else freezeRequests.orR)
    pipeline.build()
  }
}

class FetchToyPlugin(stageId: Int = 0) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(start: Bool): Unit = {
    val pipe = host[PlaygroundPipelineService]
    val fetch = pipe.ctrl(stageId)
    val pc = Reg(UInt(pipe.config.addressWidth bits)) init(0)

    fetch.up.valid := start
    fetch.up(PC) := pc.resized
    for (laneId <- 0 until pipe.config.laneCount) {
      val acceptLane = if (laneId == 0) True else pc(2)
      fetch.up(pipe.laneLive, laneId) := start && acceptLane
    }

    when(fetch.up.isFiring) {
      pc := pc + (pipe.config.laneCount * 4)
    }
  }
}

class DecodeToyPlugin(stageId: Int = 1) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(): Unit = {
    val decode = host[PlaygroundPipelineService].ctrl(stageId)
    decode.down(INSN) := decode.down(PC)(7 downto 0) ^ U(0x13, 8 bits)
  }
}

class ExecuteToyPlugin(stageId: Int = 2) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(stall: Bool): Unit = {
    val execute = host[PlaygroundPipelineService].ctrl(stageId)
    execute.haltWhen(stall)
    execute.down(UOP) := execute.down(INSN) + 2
  }
}

class RetireToyPlugin(stageId: Int = 3) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(retire: Stream[UInt]): Unit = {
    val wb = host[PlaygroundPipelineService].ctrl(stageId)
    wb.down(RESULT) := wb.down(UOP) + 3
    retire.valid := wb.down.isValid && !host[PlaygroundPipelineService].freeze
    retire.payload := wb.down(RESULT)
    wb.down.ready := retire.ready && !host[PlaygroundPipelineService].freeze
  }
}

class BranchRedirectPlugin(stageId: Int = 2, laneId: Int = 0) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(trigger: Bool): Unit = {
    val pipe = host[PlaygroundPipelineService]
    val reschedule = host[PlaygroundRescheduleService]
    val stage = pipe.ctrl(stageId)
    val req = reschedule.newRequest(getName("branch"), pipe.stageAge(stageId))

    req.valid := trigger && stage.up.isValid && stage.up(pipe.laneLive, laneId)
    req.payload.pc := (stage.up(PC) + 16).resized
    req.payload.laneAge := laneId
    req.payload.self := True
    req.payload.flushFrontend := True
    req.payload.reason := PlaygroundRescheduleReason.branch
  }
}

class RelaxedBranchRedirectPlugin(stageId: Int = 2, laneId: Int = 1) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(trigger: Bool): Unit = {
    val pipe = host[PlaygroundPipelineService]
    val reschedule = host[PlaygroundRescheduleService]
    val stage = pipe.ctrl(stageId)
    val fire = trigger && stage.up.isValid && stage.up(pipe.laneLive, laneId)
    val capturedPc = RegNextWhen(stage.up(PC), fire) init(0)
    val req = reschedule.newRequest(getName("relaxedBranch"), pipe.stageAge(stageId) + 1)

    req.valid := RegNext(fire) init(False)
    req.payload.pc := (capturedPc + 32).resized
    req.payload.laneAge := laneId
    req.payload.self := True
    req.payload.flushFrontend := True
    req.payload.reason := PlaygroundRescheduleReason.branch
  }
}

class TrapRedirectPlugin(stageId: Int = 3) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(trigger: Bool): Unit = {
    val pipe = host[PlaygroundPipelineService]
    val reschedule = host[PlaygroundRescheduleService]
    val stage = pipe.ctrl(stageId)
    val req = reschedule.newRequest(getName("trap"), pipe.stageAge(stageId))

    req.valid := trigger && stage.up.isValid
    req.payload.pc := U(0x80, pipe.config.addressWidth bits)
    req.payload.laneAge := 0
    req.payload.self := False
    req.payload.flushFrontend := True
    req.payload.reason := PlaygroundRescheduleReason.trap
  }
}

class ReplayRedirectPlugin(stageId: Int = 2, laneId: Int = 0) extends FiberPlugin {
  import ReschedulePlaygroundPayload._

  def build(trigger: Bool): Unit = {
    val pipe = host[PlaygroundPipelineService]
    val reschedule = host[PlaygroundRescheduleService]
    val stage = pipe.ctrl(stageId)
    val req = reschedule.newRequest(getName("replay"), pipe.stageAge(stageId))

    req.valid := trigger && stage.up.isValid && stage.up(pipe.laneLive, laneId)
    req.payload.pc := stage.up(PC)
    req.payload.laneAge := laneId
    req.payload.self := True
    req.payload.flushFrontend := False
    req.payload.reason := PlaygroundRescheduleReason.replay
  }
}

case class PluginRescheduleMockCpu(useOldestAgePriority: Boolean = true) extends Component {
  import ReschedulePlaygroundPayload._

  val io = new Bundle {
    val start = in Bool()
    val branch = in Bool()
    val relaxedBranch = in Bool()
    val trap = in Bool()
    val replay = in Bool()
    val executeStall = in Bool()
    val tailFreeze = in Bool()
    val retire = master(Stream(UInt(8 bits)))
    val stageValid = out(Vec(Bool(), 4))
    val stageReady = out(Vec(Bool(), 4))
    val laneLiveByStage = out(Vec(Bits(2 bits), 4))
    val rescheduleValid = out Bool()
    val reschedulePc = out UInt(32 bits)
    val rescheduleReason = out(PlaygroundRescheduleReason())
  }

  val config = PlaygroundRescheduleConfig(addressWidth = 32, laneCount = 2)
  val host = new PluginHost()

  val pipeline = new StageCtrlPipelineServicePlugin(config)
  val reschedule =
    if (useOldestAgePriority) new OldestAgeReschedulePlugin(config)
    else new AllocationPriorityReschedulePlugin(config)
  val fetch = new FetchToyPlugin()
  val decode = new DecodeToyPlugin()
  val execute = new ExecuteToyPlugin()
  val retire = new RetireToyPlugin()
  val branch = new BranchRedirectPlugin()
  val relaxedBranch = new RelaxedBranchRedirectPlugin()
  val trap = new TrapRedirectPlugin()
  val replay = new ReplayRedirectPlugin()

  pipeline.setName("pipelineService")
  reschedule.setName(if (useOldestAgePriority) "oldestAgeReschedule" else "allocationReschedule")
  fetch.setName("fetchToy")
  decode.setName("decodeToy")
  execute.setName("executeToy")
  retire.setName("retireToy")
  branch.setName("branchRedirect")
  relaxedBranch.setName("relaxedBranchRedirect")
  trap.setName("trapRedirect")
  replay.setName("replayRedirect")

  host.asHostOf(Seq(
    pipeline,
    reschedule,
    fetch,
    decode,
    execute,
    retire,
    branch,
    relaxedBranch,
    trap,
    replay
  ))

  fetch.build(io.start)
  decode.build()
  execute.build(io.executeStall)
  retire.build(io.retire)
  branch.build(io.branch)
  relaxedBranch.build(io.relaxedBranch)
  trap.build(io.trap)
  replay.build(io.replay)

  pipeline.freezeWhen(io.tailFreeze)
  pipeline.connectFrontendFlushes(reschedule, lastFrontendStage = 1)
  pipeline.connectLaneFlushes(reschedule)
  reschedule.buildArbitration()

  for (stageId <- 0 until 4) {
    val ctrl = pipeline.ctrl(stageId)
    io.stageValid(stageId) := ctrl.up.isValid
    io.stageReady(stageId) := ctrl.up.isReady
    io.laneLiveByStage(stageId) := Cat(
      (0 until config.laneCount).reverse.map(laneId => ctrl.up(pipeline.laneLive, laneId))
    )
  }

  io.rescheduleValid := reschedule.reschedule.valid
  io.reschedulePc := reschedule.reschedule.payload.pc
  io.rescheduleReason := reschedule.reschedule.payload.reason

  pipeline.buildPipeline()
}

case class AreaRescheduleMockCpu() extends Component {
  import ReschedulePlaygroundPayload._

  val io = new Bundle {
    val start = in Bool()
    val branch = in Bool()
    val trap = in Bool()
    val retire = master(Stream(UInt(8 bits)))
    val rescheduleValid = out Bool()
    val reschedulePc = out UInt(32 bits)
  }

  val config = PlaygroundRescheduleConfig(addressWidth = 32, laneCount = 2)
  val pipeline = new StageCtrlPipeline()
  val reschedule = new OldestAgeReschedulePlugin(config)
  reschedule.setName("areaReschedule")

  val fetch = pipeline.ctrl(0)
  val decode = pipeline.ctrl(1)
  val execute = pipeline.ctrl(2)
  val wb = pipeline.ctrl(3)

  val pc = Reg(UInt(32 bits)) init(0)
  fetch.up.valid := io.start
  fetch.up(PC) := pc
  when(fetch.up.isFiring) {
    pc := pc + 4
  }

  decode.down(INSN) := decode.down(PC)(7 downto 0) ^ U(0x33, 8 bits)
  execute.down(UOP) := execute.down(INSN) + 7
  wb.down(RESULT) := wb.down(UOP) + 1

  val branchReq = reschedule.newRequest("areaBranch", age = 21)
  branchReq.valid := io.branch && execute.up.isValid
  branchReq.payload.pc := execute.up(PC) + 8
  branchReq.payload.laneAge := 0
  branchReq.payload.self := True
  branchReq.payload.flushFrontend := True
  branchReq.payload.reason := PlaygroundRescheduleReason.branch

  val trapReq = reschedule.newRequest("areaTrap", age = 31)
  trapReq.valid := io.trap && wb.up.isValid
  trapReq.payload.pc := U(0x80, 32 bits)
  trapReq.payload.laneAge := 0
  trapReq.payload.self := False
  trapReq.payload.flushFrontend := True
  trapReq.payload.reason := PlaygroundRescheduleReason.trap

  fetch.throwWhen(reschedule.reschedule.valid && reschedule.reschedule.payload.flushFrontend, usingReady = true)
  decode.throwWhen(reschedule.isFlushedAt(11, U(0, 1 bits)), usingReady = false)
  execute.throwWhen(reschedule.isFlushedAt(21, U(0, 1 bits)), usingReady = false)

  io.retire.valid := wb.down.isValid
  io.retire.payload := wb.down(RESULT)
  wb.down.ready := io.retire.ready

  reschedule.buildArbitration()
  io.rescheduleValid := reschedule.reschedule.valid
  io.reschedulePc := reschedule.reschedule.payload.pc
  pipeline.build()
}

object ReschedulePluginPlayground extends App {
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
    SimConfig.compile(PluginRescheduleMockCpu(useOldestAgePriority = true)).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.start #= false
      dut.io.branch #= false
      dut.io.relaxedBranch #= false
      dut.io.trap #= false
      dut.io.replay #= false
      dut.io.executeStall #= false
      dut.io.tailFreeze #= false
      dut.io.retire.ready #= true
      dut.clockDomain.waitSampling(2)

      val rows = ArrayBuffer[String]()
      def bits(v: Vec[Bool]) = v.map(_.toBoolean).mkString
      def laneBits(v: Vec[Bits]) = v.map(_.toBigInt.toString(2)).mkString(",")
      def sample(tag: String): Unit = {
        rows += f"$tag%-8s valid=${bits(dut.io.stageValid)} ready=${bits(dut.io.stageReady)} lanes=${laneBits(dut.io.laneLiveByStage)} redirect=${dut.io.rescheduleValid.toBoolean}/${dut.io.reschedulePc.toBigInt} retire=${dut.io.retire.valid.toBoolean}/${dut.io.retire.payload.toInt}"
      }

      for (cycle <- 0 until 20) {
        dut.io.start #= cycle < 12
        dut.io.branch #= cycle == 7
        dut.io.relaxedBranch #= cycle == 9
        dut.io.replay #= cycle == 11
        dut.io.trap #= cycle == 14
        dut.io.executeStall #= (cycle == 5 || cycle == 6)
        dut.io.tailFreeze #= (cycle == 16)
        dut.io.retire.ready #= cycle != 17
        dut.clockDomain.waitSampling()
        sample(s"c$cycle")
      }
      rows.foreach(println)
    }
  } else {
    generate("plugin host with oldest-age reschedule priority") {
      PluginRescheduleMockCpu(useOldestAgePriority = true)
    }
    generate("plugin host with allocation-order reschedule priority") {
      PluginRescheduleMockCpu(useOldestAgePriority = false)
    }
    generate("area-only reschedule fabric") {
      AreaRescheduleMockCpu()
    }
  }
}
