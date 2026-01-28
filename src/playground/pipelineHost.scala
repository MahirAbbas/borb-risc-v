package playground

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import spinal.lib.misc.plugin._
import spinal.core.sim._

class pipelineHost() extends FiberPlugin {
    var pipeline = new StageCtrlPipeline()
    
    val build = during build {
      pipeline.build()
    }
}

class hoster() extends Component {
    val host = new PluginHost()
    host.addService(new pipelineHost())
    host[pipelineHost].pipeline.ctrl(2)
    host[pipelineHost].pipeline.build()

}

object buildhoster extends App {
    SpinalVerilog(new hoster())
}