package test.frontend

import spinal.core._
import spinal.core.sim._
import borb.CPU
import borb.memory.UnifiedRam
import spinal.lib.misc.pipeline._
import spinal.lib._
import spinal.lib.sim._
import scala.collection.mutable.Queue

case class TestLoadTop() extends Component {
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

object TestLoadApp extends App {
  SimConfig.withFstWave.compile(new TestLoadTop()).doSim { dut =>
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

    // Clear RAM
    for(i <- 0 until 512) {
      dut.ramArea.ram.memory.setBigInt(i, BigInt(0))
    }
    
    // Pattern: 0xF7E6D5C4B3A29180
    // LSB (Byte 0) = 0x80
    // Byte 1 = 0x91
    // Byte 2 = 0xA2
    // Byte 3 = 0xB3
    // Half 0 (0-1) = 0x9180
    // Word 0 (0-3) = 0xB3A29180
    val pattern = BigInt("F7E6D5C4B3A29180", 16)
    dut.ramArea.ram.memory.setBigInt(0x100 / 8, pattern) // Address 0x100 is index 32 if word aligned?
    // UnifiedRam uses word addressing internally (index = addr >> 3)
    val ramIndex = 0x100 / 8
    dut.ramArea.ram.memory.setBigInt(ramIndex, pattern)
    
    // x1 = 0x100
    dut.cpu.coreArea.srcPlugin.regfileread.regfile.mem.setBigInt(1, 0x100L)
    
    // Instructions
    // lb x2, 0(x1)   -> expect 0xFFFFFFFFFFFFFF80 (Sign Extended 0x80)
    // lbu x3, 0(x1)  -> expect 0x0000000000000080
    // lh x4, 0(x1)   -> expect 0xFFFFFFFFFFFF9180 (Sign Extended 0x9180)
    // lhu x5, 0(x1)  -> expect 0x0000000000009180
    // lw x6, 0(x1)   -> expect 0xFFFFFFFFB3A29180 (Sign Extended)
    // lwu x7, 0(x1)  -> expect 0x00000000B3A29180
    // ld x8, 0(x1)   -> expect 0xF7E6D5C4B3A29180
    
    // Encode instructions
    // lb x2, 0(x1)  -> 00008103
    // lbu x3, 0(x1) -> 0000c183
    // lh x4, 0(x1)  -> 00009203
    // lhu x5, 0(x1) -> 0000d283
    // lw x6, 0(x1)  -> 0000a303
    // lwu x7, 0(x1) -> 0000e383
    // ld x8, 0(x1)  -> 0000b403
    
    val instructions: List[(Long, BigInt)] = List(
      (0, BigInt("00008103", 16)), 
      (4, BigInt("0000c183", 16)),
      (8, BigInt("00009203", 16)),
      (12, BigInt("0000d283", 16)), // lhu
      (16, BigInt("0000a303", 16)), // lw 
      (20, BigInt("0000e383", 16)), // lwu (corrected offset/instr?) wait, lwu is OP_LOAD?
      (24, BigInt("0000b403", 16)), // ld
      (28, BigInt("0000006f", 16))  // Park
    )

    // Map of Index -> Value
    val memMap = scala.collection.mutable.Map[Int, BigInt]()
    
    for((addr, data) <- instructions) {
       val index = (addr / 8).toInt
       val isHigh = (addr % 8) == 4
       
       val current = memMap.getOrElse(index, BigInt(0))
       val newData = if (isHigh) {
          current | (data << 32)
       } else {
          current | (data & BigInt("FFFFFFFF", 16))
       }
       memMap(index) = newData
    }
    
    // Write map to RAM
    for((idx, data) <- memMap) {
       dut.ramArea.ram.memory.setBigInt(idx, data)
    }

    println("Starting TestLoad...")
    var error = false
    
    // Expected values map
    // x2 (lb)  -> 0xFFFFFFFFFFFFFF80
    // x3 (lbu) -> 0x0000000000000080
    // x4 (lh)  -> 0xFFFFFFFFFFFF9180
    // x5 (lhu) -> 0x0000000000009180
    // x6 (lw)  -> 0xFFFFFFFFB3A29180
    // x7 (lwu) -> 0x00000000B3A29180
    // x8 (ld)  -> 0xF7E6D5C4B3A29180
    
    val expectedC = Map(
       2 -> BigInt("FFFFFFFFFFFFFF80", 16),
       3 -> BigInt("0000000000000080", 16),
       4 -> BigInt("FFFFFFFFFFFF9180", 16),
       5 -> BigInt("0000000000009180", 16),
       6 -> BigInt("FFFFFFFFB3A29180", 16),
       7 -> BigInt("00000000B3A29180", 16),
       8 -> BigInt("F7E6D5C4B3A29180", 16)
    )
    
    val seen = scala.collection.mutable.Set[Int]()
    
    for(i <- 0 to 150) {
       sleep(period)
       
       val pc = dut.cpu.coreArea.pc.PC_cur.toBigInt
       println(f"Cycle $i%d PC=0x$pc%x")

       val dbus = dut.cpu.io.dBus.cmd
       if(dbus.valid.toBoolean) {
          println(f"DBus Cmd @ Cycle $i%d: Addr=0x${dbus.payload.address.toBigInt}%x Write=${dbus.payload.write.toBoolean} ID=${dbus.payload.id.toBigInt}%d")
       }
       val drsp = dut.cpu.io.dBus.rsp
       if(drsp.valid.toBoolean) {
          println(f"DBus Rsp @ Cycle $i%d: Data=0x${drsp.payload.data.toBigInt}%x ID=${drsp.payload.id.toBigInt}%d")
       }
       
       val ibus = dut.cpu.io.iBus.rsp
       if(ibus.valid.toBoolean) {
          println(f"IBus Rsp @ Cycle $i%d: Data=0x${ibus.payload.data.toBigInt}%x")
       }
       
       val rvfi = dut.cpu.io.rvfi
       if (rvfi.valid.toBoolean) {
           val rd = rvfi.rd_addr.toBigInt.toInt
           val wdata = rvfi.rd_wdata.toBigInt
           
           if (rd >= 2 && rd <= 8) {
               println(f"RVFI Commit: x$rd%d = 0x$wdata%x")
               if (expectedC.contains(rd)) {
                   seen.add(rd)
                   val exp = expectedC(rd)
                   // Handle signed comparison if needed, but BigInt hex matches
                   // 64-bit hex string comparison
                   // Mask to 64-bit to be safe
                   val mask64 = (BigInt(1) << 64) - 1
                   val wdata64 = wdata & mask64
                   val exp64 = exp & mask64
                   
                   if (wdata64 != exp64) {
                       println(f"ERROR: x$rd%d Mismatch! Expected 0x$exp64%x, Got 0x$wdata64%x")
                       error = true
                   }
               }
           }
       }
    }
    
    println("\n" + "=" * 50)
    if (!error && seen.size == 7) {
       println("TestLoad PASSED: All loads verified.")
    } else {
       println(f"TestLoad FAILED! Seen ${seen.size}/7 loads.")
       throw new AssertionError("TestLoad Failed")
    }
    println("=" * 50)
  }
}
