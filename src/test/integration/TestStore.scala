package test.frontend

import spinal.core._
import spinal.core.sim._
import borb.CPU
import borb.memory.UnifiedRam
import spinal.lib.misc.pipeline._
import spinal.lib._
import spinal.lib.sim._

// Define a top level that allows accessing dBus for verification
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

  // Clock Domain definition (similar to TestCpuIntegration)
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
  }

  // Drive dBus response to zero (unconnected in this test)
  cpu.io.dBus.rsp.valid := False
  cpu.io.dBus.rsp.payload.data := 0
}

object TestStoreApp extends App {
  SimConfig.withFstWave.compile(new TestStoreTop()).doSim { dut =>
    // Setup Clock
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

    // Reset
    dut.io.reset #= true
    sleep(period*2)
    dut.io.reset #= false

    // Clear RAM
    for(i <- 0 until 100) {
      dut.ramArea.ram.memory.setBigInt(i, BigInt(0))
    }

    // Instructions - Placeholders as requested
    // Format: (ByteAddress, MachineCode)
    // ??? placeholders used as BigInt(0) for now. User to fill.
    val instructions: List[(Long, BigInt)] = List(
      // # ----------------------------
      // # Setup: Base Address and Data
      // # ----------------------------
      (0,  BigInt("10000093", 16)), // addi x1, x0, 0x100   # Base Address = 0x100
      (4,  BigInt("0aa00113", 16)), // addi x2, x0, 0xAA    # Data Pattern 1 (0xAA)
      (8,  BigInt("00811193", 16)), // slli x3, x2, 8       # x3 = 0xAA00
      (12, BigInt("00316233", 16)), // or   x4, x2, x3      # x4 = 0xAAAA (0xAA | 0xAA00)
      (16, BigInt("fff00293", 16)), // addi x5, x0, -1      # x5 = 0xFF...FF (All ones)

      // # ----------------------------
      // # Store Byte (SB)
      // # ----------------------------
      (20, BigInt("00208023", 16)), // sb   x2, 0(x1)       # Mem[0x100] = 0xAA

      // # ----------------------------
      // # Store Half (SH)
      // # ----------------------------
      (24, BigInt("00409123", 16)), // sh   x4, 2(x1)       # Mem[0x102] = 0xAAAA

      // # ----------------------------
      // # Store Word (SW)
      // # ----------------------------
      (28, BigInt("0050a223", 16)), // sw   x5, 4(x1)       # Mem[0x104] = 0xFFFFFFFF

      // # ----------------------------
      // # Store Double (SD)
      // # ----------------------------
      (32, BigInt("0050b823", 16)), // sd   x5, 16(x1)      # Mem[0x110] = 0xFF...FF

      // # ----------------------------
      // # Park/Done
      // # ----------------------------
      (36, BigInt("0000006f", 16))  // j . (self loop)
    )

    for((addr, data) <- instructions) {
       if (data != 0) dut.ramArea.ram.memory.setBigInt(addr, data)
    }

    // Simulation Loop with dBus monitoring
    println("Starting Simulation...")
    for(i <- 0 to 50) {
       sleep(period)
       
       val dbus = dut.cpu.io.dBus.cmd
       if(dbus.valid.toBoolean) {
          println(f"Time $i: dBus Valid! Addr:${dbus.payload.address.toBigInt.toLong}%x Data:${dbus.payload.data.toBigInt.toLong}%x Mask:${dbus.payload.mask.toBigInt.toLong}%x Write:${dbus.payload.write.toBoolean}")
       }
    }
    println("Simulation Finished.")
  }
}
