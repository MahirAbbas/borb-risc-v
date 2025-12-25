package test.frontend

import spinal.core._
import spinal.core.sim._
import borb.CPU
import borb.memory.UnifiedRam
import spinal.lib.misc.pipeline._
import spinal.lib._
import spinal.lib.sim._
import borb.fetch.PC
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.dispatch.Dispatch.SENDTOALU
import borb.execute.IntAlu._
import borb.dispatch.SrcPlugin._

case class TestCpuIntegration() extends Component {
  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
  }

  val cpu = CPU()
  cpu.io.clk := io.clk
  cpu.io.clkEnable := io.clkEnable
  cpu.io.reset := io.reset
  
  // Use the SAME clock domain for RAM? 
  // UnifiedRam typically expects to be in a clock domain or uses the current one.
  // In test_hazards, ram was inside coreArea (custom ClockDomain).
  // Here, let's put RAM in the same custom clock domain as CPU internals? 
  // OR just put it in the default domain if we drive io.clk with the default domain?
  
  // CPU internals use `coreClockDomain` derived from `io`.
  // If we want RAM to be synchronous with CPU, we should probably clock it with the same clock.
  // But UnifiedRam doesn't take CD in constructor, it uses the current CD.
  // So we should wrap RAM in a ClockingArea associated with cpu.coreClockDomain? 
  // access to cpu.coreClockDomain might be hard if it's val inside.
  // Simpler: The test bench drives `io.clk`. We can create a ClockDomain in TestTop that drives `io.clk` and wraps RAM.
  
  val testClockDomain = ClockDomain(
    io.clk,
    reset = io.reset,
    clockEnable = io.clkEnable,
    config = ClockDomainConfig(
      clockEdge = RISING,
      resetKind = SYNC,
      resetActiveLevel = HIGH
    )
  )
  
  val ramArea = new ClockingArea(testClockDomain) {
    val ram = new UnifiedRam(addressWidth = 32, dataWidth = 32, idWidth = 16)
    //ram.io.reads.cmd << cpu.io.iBus.cmd
    //ram.io.reads.rsp >> cpu.io.iBus.rsp
    ram.io.reads <> cpu.io.iBus
  }
}

object test_cpu_integration_app extends App {
  SimConfig.withWave.compile(new TestCpuIntegration()).doSim { dut =>
    dut.io.clkEnable #= true
    
    // We need to drive dut.io.clk toggling.
    // Fork a process to toggle clk
    val period = 50
    dut.io.clk #= false
    sleep(0) // Align
    
    // Fork clock generation on the input pin
    fork {
      while(true) {
        dut.io.clk #= false
        sleep(period/2)
        dut.io.clk #= true
        sleep(period/2)
      }
    }
    
    // Initial reset
    dut.io.reset #= true
    sleep(period*2)
    dut.io.reset #= false
    
    // Initialize RAM (accessed via dut.ramArea.ram.memory)
    for(i <- 0 until 31) {
       dut.ramArea.ram.memory.setBigInt(i, BigInt("0", 10))
    }
    
    val instructions: List[(Long, BigInt)] = List(
                                      // # ----------------------------
                                      // # RAW Hazards (Back-to-Back)
                                      // # ----------------------------
      (0,   BigInt("00500093",16 )),  // addi x1, x0, 5          # x1 = 5
      (4,   BigInt("00300113",16 )),  // addi x2, x0, 3          # x2 = 3
      (8,   BigInt("002081b3",16 )),  // add  x3, x1, x2         # x3 = 8
      (12,  BigInt("00118233",16 )),  // add  x4, x3, x1         # DEPENDS on x3 immediately # x4 = 13
      (16,  BigInt("402202b3",16 )),  // sub  x5, x4, x2         # DEPENDS on x4 immediately # x5 = 10
      (20,  BigInt("0032f333",16 )),  // and  x6, x5, x3         # DEPENDS on x5 # x6 = 8
      (24,  BigInt("004363b3",16 )),  // or   x7, x6, x4         # DEPENDS on x6 # x7 = 13
      (28,  BigInt("0053c433",16 )),  // xor  x8, x7, x5         # DEPENDS on x7 # x8 = 7
      
                                    // # ----------------------------
                                    // # Mixed dependency patterns
                                    // # ----------------------------
      (32,  BigInt("002084b3",16 )),  // add  x9, x1, x2         # x9 = 8 (fresh) 
      (36,  BigInt("00348533",16 )),  // add  x10, x9, x3        # depends on x9 # x10 = 16
      (40,  BigInt("004505b3",16 )),  // add  x11, x10, x4       # depends on x10 # x11 = 29
      (44,  BigInt("00558633",16 )),  // add  x12, x11, x5       # chain dependency # x12 = 39
     
                                   // # ----------------------------
                                   // # One-source dependencies
                                   // # ----------------------------
      (48,  BigInt("00200693",16 )), // addi x13, x0, 2         # independent # x13 = 2
      (52,  BigInt("00c68733",16 )), // add  x14, x13, x12      # depends only on x12 # x14 = 41
      (56,  BigInt("401707b3",16 )), // sub  x15, x14, x1       # depends on x14 # x15 = 36
    
                                   // # ----------------------------
                                   // # Forwarding test (cross reuse)
                                   // # ----------------------------
      (60,  BigInt("00208833",16 )), // add  x16, x1, x2        # produces x16 # x16 = 8
      (64,  BigInt("003808b3",16 )), // add  x17, x16, x3       # uses x16 immediately # x17 = 16
      (68,  BigInt("00488933",16 )), // add  x18, x17, x4       # uses x17 immediately # x18 = 29
      (72,  BigInt("005909b3",16 )), // add  x19, x18, x5       # uses x18 immediately # x19 = 39
   
                                    // # ----------------------------
                                    // # Delayed dependency (stall test)
                                    // # ----------------------------
      (76,  BigInt("00208a33",16 )),  // add  x20, x1, x2        # x20 = 8
      (80,  BigInt("00418ab3",16 )),  // add  x21, x3, x4        # unrelated, should not stall # x21 = 21
      (84,  BigInt("015a0b33",16 )),  // add  x22, x20, x21      # depends on x20, tests correct stall or bypass timing # x22 = 29

  )
    for ((address, data) <- instructions) {
      dut.ramArea.ram.memory.setBigInt(address.toLong, data)
    }

    // Access internal signals via dut.cpu.coreArea
    // We need to wait for clock edges. Since we generate clock manually, we can just sleep period.
    // Or we can use `dut.testClockDomain.waitSampling` if we exposed the clock domain appropriately, but easier to just loop and sleep.
    
    for(i <- 0 to 75) {
       // Wait one clock cycle
       sleep(period)
       
       // Access signals. Note: in sim, we can access public fields.
       // scala code inside cpu module: `val readHere = new readStage.Area ...`
       // But accessing deep nested invisible areas might be tricky if they are not exposed.
       // The original test_hazards had `readHere` inside `coreArea`.
       // We can try to access `dut.cpu.coreArea.writeback...` if available.
       // In CPU class, `writeback` is defined. `write` is defined.
       // But `readHere` was specifically defined in `test_hazards` for debug.
       // `CPU.scala` does NOT have `readHere`. 
       
       // We should rely on `writeback` logic in CPU or add `readHere` to CPU (which makes it ugly),
       // or just look at `dut.cpu.coreArea.srcPlugin.regfileread.regfile.io.writer`
       
       val valid_result = dut.cpu.coreArea.srcPlugin.regfileread.regfile.io.writer.valid.toBoolean
       val rdaddr = dut.cpu.coreArea.srcPlugin.regfileread.regfile.io.writer.address.toLong
       val result = dut.cpu.coreArea.srcPlugin.regfileread.regfile.io.writer.data.toLong
       
       val pc = dut.cpu.coreArea.pc.PC_cur.toLong
       val cmd_valid = dut.cpu.coreArea.fetch.io.readCmd.cmd.valid.toBoolean
       val cmd_ready = dut.cpu.coreArea.fetch.io.readCmd.cmd.ready.toBoolean
       val rsp_valid = dut.cpu.coreArea.fetch.io.readCmd.rsp.valid.toBoolean
       
       val rh = dut.cpu.coreArea.writeback.readHere
       //println(s"PC: $pc CMD: v=$cmd_valid r=$cmd_ready RSP: v=$rsp_valid | RESULT.valid ${rh.valid_result.toBoolean}, RESULT.address ${rh.rdaddr.toLong}, RESULT.data ${rh.result.toLong}, IMMED: ${rh.immed.toLong}, SENDTOALU: ${rh.sendtoalu.toBoolean}, VALID: ${rh.valid.toBoolean}")
       println(s"RESULT.valid ${rh.valid_result.toBoolean}, RESULT.address ${rh.rdaddr.toLong}, RESULT.data ${rh.result.toLong}, IMMED: ${rh.immed.toLong}, SENDTOALU: ${rh.sendtoalu.toBoolean}, VALID: ${rh.valid.toBoolean}")
    }
  }
}
