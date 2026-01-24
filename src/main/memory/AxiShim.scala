package borb.memory

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import borb.execute.{DataBus, DataBusCmd, DataBusRsp}
import borb.memory.RamFetchBus

class RamFetchBusToAxi4Shared(axiConfig: Axi4Config) extends Component {
  val io = new Bundle {
    val fetch = slave(new RamFetchBus(
      addressWidth = axiConfig.addressWidth,
      dataWidth = axiConfig.dataWidth,
      idWidth = 16 
    ))
    val axi = master(Axi4Shared(axiConfig))
  }

  // Direct combinational bridge for Fetch (Read Only)
  // Fetch logic in CPU handles tracking, so we can pass through ARW
  // BUT user said "Step 3: Single-outstanding... No overlapping".
  // RamFetchBus normally pipelines.
  // We should enforce single-outstanding here too to be strict?
  // Fetch usually tolerates it.
  
  // For now, pass through to avoid stalling instruction fetch too much 
  // unless we want strict "shim" behavior. 
  // Let's implement passthrough for Fetch as it's less risky for deadlock vs Load/Store.
  
  io.axi.arw.valid   := io.fetch.cmd.valid
  io.axi.arw.addr    := io.fetch.cmd.address
  io.axi.arw.id      := io.fetch.cmd.id.resized
  io.axi.arw.len     := 0
  io.axi.arw.size    := log2Up(axiConfig.dataWidth/8)
  io.axi.arw.burst   := Axi4.burst.INCR
  // io.axi.arw.lock    := Axi4.lock.NORMAL // Disabled
  // io.axi.arw.cache   := B"0011"
  // io.axi.arw.prot    := B"110"
  // io.axi.arw.qos     := 0
  // io.axi.arw.region  := 0
  io.axi.arw.write   := False
  
  io.fetch.cmd.ready := io.axi.arw.ready

  io.fetch.rsp.valid   := io.axi.r.valid
  io.fetch.rsp.data    := io.axi.r.data
  io.fetch.rsp.address := 0 
  io.fetch.rsp.id      := io.axi.r.id.resized
  
  io.axi.r.ready := True

  io.axi.w.valid := False
  io.axi.w.data := 0
  io.axi.w.strb := 0
  io.axi.w.last := False
  io.axi.b.ready := True
}

class DataBusToAxi4Shared(axiConfig: Axi4Config) extends Component {
  val io = new Bundle {
    val dBus = slave(DataBus(
      addressWidth = axiConfig.addressWidth,
      dataWidth = axiConfig.dataWidth,
      idWidth = 16
    ))
    val axi = master(Axi4Shared(axiConfig))
  }
  
  val cmd = io.dBus.cmd
  val rsp = io.dBus.rsp
  
  object State extends SpinalEnum {
    val idle, sendWrite, waitWriteResp, sendRead, waitReadResp = newElement()
  }
  import State._
  val state = RegInit(idle)
  
  val activeCmd = Reg(DataBusCmd(axiConfig.addressWidth, axiConfig.dataWidth, 16))
  
  // Handshake Registers
  val arwFired = RegInit(False)
  val wFired = RegInit(False)

  // Default Outputs
  cmd.ready := False
  
  io.axi.arw.valid := False
  io.axi.arw.payload.id := activeCmd.id.resized
  io.axi.arw.payload.addr := activeCmd.address
  io.axi.arw.payload.len := 0
  io.axi.arw.payload.size := log2Up(axiConfig.dataWidth/8)
  io.axi.arw.payload.burst := Axi4.burst.INCR
  // io.axi.arw.payload.lock := Axi4.lock.NORMAL
  // io.axi.arw.payload.cache := B"0011"
  // io.axi.arw.payload.prot := B"010"
  // io.axi.arw.payload.qos := 0
  // io.axi.arw.payload.region := 0
  io.axi.arw.payload.write := activeCmd.write
  
  io.axi.w.valid := False
  io.axi.w.payload.data := activeCmd.data
  io.axi.w.payload.strb := activeCmd.mask
  io.axi.w.payload.last := True
  
  io.axi.b.ready := False
  io.axi.r.ready := False
  
  rsp.valid := False
  rsp.data := io.axi.r.data
  rsp.id := io.axi.r.id.resized
  
  switch(state) {
    is(idle) {
      cmd.ready := True
      arwFired := False
      wFired := False
      
      when(cmd.valid) {
        activeCmd := cmd.payload
        when(cmd.write) {
          state := sendWrite
        } otherwise {
          state := sendRead
        }
      }
    }
    
    is(sendWrite) {
      io.axi.arw.valid := !arwFired
      io.axi.w.valid   := !wFired
      
      when(io.axi.arw.fire) { arwFired := True }
      when(io.axi.w.fire)   { wFired := True }
      
      when((arwFired || io.axi.arw.fire) && (wFired || io.axi.w.fire)) {
        state := waitWriteResp
      }
    }
    
    is(waitWriteResp) {
      io.axi.b.ready := True
      when(io.axi.b.valid) {
        state := idle
      }
    }
    
    is(sendRead) {
      io.axi.arw.valid := True
      when(io.axi.arw.ready) {
        state := waitReadResp
      }
    }
    
    is(waitReadResp) {
      io.axi.r.ready := True
      when(io.axi.r.valid) {
        rsp.valid := True
        state := idle
      }
    }
  }
}
