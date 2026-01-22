package test.frontend

import spinal.core._
import spinal.core.sim._
import borb.CPU
import borb.memory.UnifiedRam
import spinal.lib.misc.pipeline._
import spinal.lib._
import spinal.lib.sim._
import scala.collection.mutable.Queue

case class TestStoreTop() extends Component {
  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
  }

  val cpu = CPU()
  cpu.io.clk := io.clk
  cpu.io.clkEnable := io.clkEnable
  cpu.io.reset := io.reset

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
    val ram = new UnifiedRam(addressWidth = 64, dataWidth = 64, idWidth = 16)
    ram.io.reads <> cpu.io.iBus
    ram.io.writes <> cpu.io.dBus
  }
}

object TestStoreApp extends App {
  SimConfig.withFstWave.compile(new TestStoreTop()).doSim { dut =>
    dut.io.clkEnable #= true
    val period = 50
    dut.io.clk #= false
    sleep(0)
    fork {
      while(true) {
        dut.io.clk #= false
        sleep(period/2)
        dut.io.clk #= true
        sleep(period/2)
      }
    }

    dut.io.reset #= true
    sleep(period*2)
    dut.io.reset #= false

    for(i <- 0 until 512) {
      dut.ramArea.ram.memory.setBigInt(i, BigInt(0))
    }
    
    // x1 = 0x100
    dut.cpu.coreArea.srcPlugin.regfileread.regfile.mem.setBigInt(1, 0x100L)
    // x4 = 0x4444 (SH)
    val dataVal = BigInt("4444444444444444", 16)
    dut.cpu.coreArea.srcPlugin.regfileread.regfile.mem.setBigInt(4, dataVal)
 
    // =========================================================================
    // Phase 3: SH (Store Halfword) Tests
    // Instructions:
    // sh x4, 0(x1)  -> Addr 0x100. Offset 0.
    // sh x4, 2(x1)  -> Addr 0x102. Offset 2.
    // sh x4, 4(x1)  -> Addr 0x104. Offset 4.
    // sh x4, 6(x1)  -> Addr 0x106. Offset 6.
    // =========================================================================
    
    val instructions: List[(Long, BigInt)] = List(
      // sh x4, 0(x1) -> 00409023
      (0, BigInt("00409023", 16)), 
      // sh x4, 2(x1) -> 00409123
      (4, BigInt("00409123", 16)),
      // sh x4, 4(x1) -> 00409223
      (8, BigInt("00409223", 16)),
      // sh x4, 6(x1) -> 00409323
      (12, BigInt("00409323", 16)),
      // Park
      (16, BigInt("0000006f", 16))
    )

    for((addr, data) <- instructions) {
       if (data != 0) dut.ramArea.ram.memory.setBigInt(addr,data)
    }
    
    val halfVal = BigInt("4444", 16)
    
    val expected = Queue(
        (BigInt(0x100), halfVal << 0, 0x03 << 0),       // 0x03
        (BigInt(0x102), halfVal << 16, 0x03 << 2),      // 0x0C
        (BigInt(0x104), halfVal << 32, 0x03 << 4),      // 0x30
        (BigInt(0x106), halfVal << 48, 0x03 << 6)       // 0xC0
    )

    println("Starting Phase 3: SH Test...")
    var error = false
    
    for(i <- 0 to 80) {
       sleep(period)
       
       val pc = dut.cpu.coreArea.pc.PC_cur.toBigInt
       // println(f"Cycle $i%d PC=0x$pc%x")

       // Monitor RVFI
       val rvfi = dut.cpu.io.rvfi
       if (rvfi.valid.toBoolean) {
           val r_addr = rvfi.mem_addr.toBigInt
           val r_mask = rvfi.mem_wmask.toBigInt
           val r_wdata = rvfi.mem_wdata.toBigInt
           // Only interested in stores (mask != 0)
           if (r_mask != 0) {
               println(f"RVFI Store @ Cycle $i%d: Addr=0x$r_addr%x Data=0x$r_wdata%x Mask=0x$r_mask%x")
           }
       }
       
       val dbus = dut.cpu.io.dBus.cmd
       if(dbus.valid.toBoolean && dbus.payload.write.toBoolean) {
          val addr = dbus.payload.address.toBigInt
          val data = dbus.payload.data.toBigInt
          val mask = dbus.payload.mask.toBigInt.toInt
          
          println(f"Store Issued @ Cycle $i%d: Addr=0x$addr%x Data=0x$data%x Mask=0x$mask%x PC=0x$pc%x")
          
          if (expected.isEmpty) {
              println("ERROR: Unexpected extra store transaction!")
              error = true
          } else {
              val (expAddr, expData, expMask) = expected.dequeue()
              if (addr != expAddr) {
                  println(f"ERROR: Address Mismatch! Expected 0x$expAddr%x, Got 0x$addr%x")
                  error = true
              }
              if (mask != expMask) {
                  println(f"ERROR: Mask Mismatch! Expected 0x$expMask%x, Got 0x$mask%x")
                  error = true
              }
              if (data != expData) {
                   println(f"ERROR: Data Mismatch! Expected 0x$expData%x, Got 0x$data%x")
                   error = true
              }
          }
       }
    }
    
    println("\n" + "=" * 50)
    if (!error && expected.isEmpty) {
      println("PHASE 3 PASSED: All SH stores verified.")
    } else {
      if (!expected.isEmpty) println(f"FAILED: Missing ${expected.size} expected transactions.")
      println("PHASE 3 FAILED!")
      throw new AssertionError("Phase 3 Failed")
    }
    println("=" * 50)
  }
}
