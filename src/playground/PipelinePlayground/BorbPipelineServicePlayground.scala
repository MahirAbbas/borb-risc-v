package borb.playground

import spinal.core._
import spinal.idslplugin.Location
import spinal.lib._
import spinal.lib.misc.pipeline._

import scala.collection.mutable.ArrayBuffer
import _root_.borb.fetch.FlushCmd
import _root_.borb.fetch.Fetch.{addressWidth => addressWidth}

case class BorbBroadcastSpec(
  lane: Int,
  ctrlId: Int,
  rd: Payload[UInt],
  enable: Payload[Bool],
  data: Payload[UInt]
)


case class RescheduleConfig(
  addressWidth: Int = 64,
  laneCount: Int = 1
) {
  require(addressWidth > 0)
  require(laneCount > 0)

  val laneWidth: Int = log2Up(laneCount max 2)
}

object RescheduleReason extends SpinalEnum {
  val branch, trap, mret, fencei, replay, external = newElement()
}

object Ages {
  val STAGE = 10
  val NOT_PREDICTION = 1
  val FETCH = 0
  val DECODE = 1000
  val EXECUTE = 2000
  val TRAP = 3000
}

case class RescheduleCmd(
  laneAgeWidth: Int,
  config: RescheduleConfig = RescheduleConfig()
) extends Bundle {
  val pc = UInt(config.addressWidth bits)
  val laneAge = UInt(laneAgeWidth bits)
  val self = Bool()
  val flushFrontend = Bool()
  val reason = RescheduleReason()
}

class ReschedulePlugin(val config: RescheduleConfig = RescheduleConfig()) extends Area {
  case class ReschedulePort(age: Int, flow: Flow[RescheduleCmd])

  val requests = ArrayBuffer[ReschedulePort]()
  val reschedule = Flow(RescheduleCmd(config.laneWidth, config))

  reschedule.valid := False
  reschedule.payload.assignDontCare()
  reschedule.payload.pc := 0
  reschedule.payload.laneAge := 0
  reschedule.payload.self := False
  reschedule.payload.flushFrontend := False
  reschedule.payload.reason := RescheduleReason.branch

  def newRequest(age: Int, laneAgeWidth: Int = config.laneWidth): Flow[RescheduleCmd] = {
    val r = Flow(RescheduleCmd(laneAgeWidth, config))
    r.valid := False
    r.payload.assignDontCare()
    r.payload.pc := 0
    r.payload.laneAge := 0
    r.payload.self := False
    r.payload.flushFrontend := False
    r.payload.reason := RescheduleReason.branch
    requests += ReschedulePort(age, r)
    r
  }

  def buildArbitration(): Unit = {
    // Fixed priority: earlier allocated request ports win.
    for (request <- requests.reverse) {
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

  def isFlushedAt(age: Int, laneAge: UInt): Option[Bool] = {
    val filtered = requests.filter(_.age >= age)
    if (filtered.isEmpty) return None
    val hits = filtered.map { request =>
      val flow = request.flow
      request.age match {
        case `age` => flow.valid && ((flow.payload.laneAge < laneAge) || (flow.payload.laneAge === laneAge && flow.payload.self))
        case _ => flow.valid
      }
    }
    Some(hits.orR)
  }
}

class BorbPipelineService(lanes: Int) extends Area {

  val rescheduleConfig = RescheduleConfig(laneCount = lanes)
  val LANE_SEL = Payload(Bool())
  val pipeline = new StageCtrlPipeline()
  val flushes = ArrayBuffer[(Int, Bool)]()

  //val reschedulePlugon = new Area {
    //val flushRequests = ArrayBuffer(FlushCmd(addressWidth = 64))
  //}
  val reschedulePlugin = new ReschedulePlugin(rescheduleConfig)

  def apply(id: Int): CtrlLink = pipeline.ctrl(id)
  def down: NodeApi = pipeline.ctrls.last._2.down
  def getAge(id: Int, prediction: Boolean = false): Int = {
    Ages.FETCH + id * Ages.STAGE + (!prediction).toInt * Ages.NOT_PREDICTION
  }
  def flushWhen(id: Int, cond: Bool): Unit = flushes += ((id, cond))
  //TODO add limit of ctrls.size? ctrls.head or ctrls.drop?? or ctrls.take
  // Fetch Flush
  val flushRange = 1 until pipeline.ctrls.take(2).size
  val flushyFetch = for (id <- flushRange) yield new Area {
    val c = apply(id)
    val doIt = reschedulePlugin.reschedule.valid && reschedulePlugin.reschedule.payload.flushFrontend
    c.throwWhen(doIt, usingReady = false)
  }
  // decode Flush?? maybe execute flush as well
  for (ctrlId <- pipeline.ctrls) {
    val c = ctrlId._2
    if (ctrlId._1 != 0) c.up(LANE_SEL).setAsReg().init(False)
    when(!c.up.isReady && c.up.isCancel) {
      c.up(LANE_SEL) := False
    }
  }
  val clearRange = for (id <- pipeline.ctrls) yield new Area {
    val link = id._2

    val onLanes = for (laneId <- 0 until lanes) yield new Area {
      if (id._1 != 0) {
        link.up(LANE_SEL, laneId).setAsReg().init(False)
      }
      val age = getAge(id._1)
      val laneAge = U(laneId, rescheduleConfig.laneWidth bits)
      reschedulePlugin.isFlushedAt(age, laneAge) match {
        case Some(doIt) =>
          when(link.up.isValid && doIt) {
            link.bypass(LANE_SEL, laneId) := False
          }
        case None =>
      }
    }
  }
  // execute pipeline

  val freeze = new Area {
    val requests = ArrayBuffer[Bool]()
    val valid = Bool()
  }

  //val broadcasts = ArrayBuffer[BorbBroadcastSpec]()

  def freezeWhen(cond: Bool)(implicit loc: Location): Unit = freeze.requests += cond
  def freezeIt()(implicit loc: Location): Unit = freezeWhen(ConditionalContext.isTrue())
  def isFreezed: Bool = freeze.valid

  freeze.valid := freeze.requests.orR
  pipeline.ctrls.last._2.down.ready := !freeze.valid
  //def publishBroadcast(spec: BorbBroadcastSpec): Unit = broadcasts += spec

  //// Keep the lane-shaped API for now, but route broadcasts through scalar payloads.
  //def bypassRs1FromBroadcasts( target: CtrlLink, rs: Payload[UInt], rd: Payload[UInt], value: Payload[UInt]): Unit = {
    //val _ = rs
    //val hits = broadcasts.map { b =>
      //val source = pipeline.ctrl(b.ctrlId)
      //source.isValid && source(b.enable) && source(b.rd) === target(rd)
    //}
    //if (hits.nonEmpty) {
      //val sel = OHMasking.firstV2(Cat(hits))
      //val datas = broadcasts.map(b => pipeline.ctrl(b.ctrlId)(b.data))
      //target.bypass(value) := OHMux.or(sel, datas, true)
    //}
  //}

  //for (id <- pipeline.ctrls.keys) pipeline.ctrl(id)
  //val ctrls = pipeline.ctrls.toList.sortBy(_._1).map(_._2)

  //for ((id, cond) <- flushes if id > 0 && id < ctrls.size) {
    //if (id == ctrls.size - 1) {
      //ctrls(id).terminateWhen(cond)
    //} else {
      //ctrls(id).throwWhen(cond, usingReady = false)
    //}
  //}

  reschedulePlugin.buildArbitration()
  pipeline.build()
  //(pipeline.links ++ ctrls).toSeq
}
