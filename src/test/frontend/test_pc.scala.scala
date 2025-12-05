package test.frontend

import spinal.core._
import spinal.core.sim._
import borb.frontend._
import spinal.lib.misc.pipeline._
import spinal.lib._

object PC_formal_increment extends App {
  import spinal.core.formal._
  import borb.fetch.PC.PC
  FormalConfig
    .withBMC(100)
    .doVerify(new Component {

      val dut = FormalDut {
        new Component {
          val pipeline = new StageCtrlPipeline
          val stage = pipeline.ctrl(0)
          val read_stage = pipeline.ctrl(1)
          val pc = new borb.fetch.PC(stage, addressWidth = 64)

          // Expose PC output
          pc.exception.setIdle()
          pc.jump.setIdle()
          pc.flush.setIdle()
          val pc_out = out port UInt(64 bits)
          val read = new read_stage.Area {
            pc_out := PC
          }
          pipeline.build()
        }
      }
      assumeInitial(ClockDomain.current.isResetActive)

      when(pastValid() && !initstate()) {
        assert(dut.pc_out === past(dut.pc_out) + 4, "PC must increment by 4")
      }
    })
}
object PCTest extends App {
  import borb.fetch.PC.PC
  SimConfig.withWave
    .compile(new Component {
      import borb.fetch._

      val io = new Bundle {
        val in_jump = in(Flow(JumpCmd(addressWidth = 64)))
        val in_flush = in(Flow(FlushCmd(addressWidth = 64)))
        val in_exception = in(Flow(ExceptionCmd(addressWidth = 64)))
      }

      val pipeline = new StageCtrlPipeline
      val stage = pipeline.ctrl(0)
      // val read_stage = pipeline.ctrl(1)
      val pc = new PC(stage, addressWidth = 64)

      // Expose PC output
      val pc_out = out(UInt(64 bits))
      val read = new stage.Area {
        pc_out := borb.fetch.PC.PC
      }

      // Connect the top-level IO to the PC module's inputs
      pc.jump := io.in_jump
      pc.flush := io.in_flush
      pc.exception := io.in_exception

      pipeline.build()
    })
    .doSim { dut =>
      dut.clockDomain.forkStimulus(10)

      dut.io.in_jump.valid #= false
      dut.io.in_jump.payload.is_branch #= false
      dut.io.in_jump.payload.is_jump #= false
      dut.io.in_jump.payload.target #= 0
      dut.io.in_flush.valid #= false
      dut.io.in_flush.payload.address #= 0
      dut.io.in_exception.valid #= false
      dut.io.in_exception.payload.vector #= 0

      // 1. Sequential increment
      dut.clockDomain.waitSampling(2)
      var lastPC = dut.pc_out.toBigInt
      for (i <- 1 to 1000) {
        dut.clockDomain.waitSampling(1)
        val currentPC = dut.pc_out.toBigInt
        assert(
          currentPC == lastPC + 4,
          s"PC did not increment by 4 at cycle $i: got $currentPC, expected ${lastPC + 4}"
        )
        println(s"Cycle $i: PC = $currentPC (OK)")
        lastPC = currentPC
      }

      // // 2. Jump
      val jumpTarget = BigInt(0x100)
      dut.io.in_exception.valid #= false
      dut.io.in_flush.valid #= false
      dut.io.in_jump.valid #= true
      dut.io.in_jump.payload.is_branch #= false
      dut.io.in_jump.payload.is_jump #= true
      dut.io.in_jump.payload.target #= jumpTarget

      dut.clockDomain.waitSampling(1)
      println(s"got ${dut.pc_out.toBigInt}, expected $jumpTarget")
      dut.io.in_jump.valid #= false

      dut.clockDomain.waitSampling(1)
      assert(
        dut.pc_out.toBigInt == jumpTarget,
        s"Jump test failed: got ${dut.pc_out.toBigInt}, expected $jumpTarget"
      )
      println(s"got ${dut.pc_out.toBigInt}, expected $jumpTarget")

      dut.clockDomain.waitSampling(1)
      assert(
        dut.pc_out.toBigInt == jumpTarget + 4,
        s"Jump test failed: got ${dut.pc_out.toBigInt}, expected $jumpTarget"
      )
      println(s"got ${dut.pc_out.toBigInt}, expected $jumpTarget")

      dut.clockDomain.waitSampling(1)
      assert(
        dut.pc_out.toBigInt == jumpTarget + 8,
        s"Jump test failed: got ${dut.pc_out.toBigInt}, expected $jumpTarget"
      )
      println(s"got ${dut.pc_out.toBigInt}, expected $jumpTarget")

      dut.clockDomain.waitSampling(1)
      assert(
        dut.pc_out.toBigInt == jumpTarget + 12,
        s"Jump test failed: got ${dut.pc_out.toBigInt}, expected $jumpTarget"
      )
      println(s"got ${dut.pc_out.toBigInt}, expected $jumpTarget")

      // 3. Flush
      println("--- Testing Flush ---")
      val flushAddr = BigInt(0x200)
      dut.io.in_jump.valid #= false
      dut.io.in_flush.valid #= true
      dut.io.in_flush.payload.address #= flushAddr
      dut.io.in_exception.valid #= false

      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == flushAddr,
        s"Flush test failed: got ${dut.pc_out.toBigInt}, expected $flushAddr"
      )
      println("Flush test passed")

      // After flush, should increment from flush address
      dut.io.in_flush.valid #= false
      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == flushAddr + 4,
        s"Post-flush increment failed: got ${dut.pc_out.toBigInt}, expected ${flushAddr + 4}"
      )
      println("Post-flush increment test passed")

      // 4. Exception
      println("--- Testing Exception ---")
      val excVec = BigInt(0xdead)
      dut.io.in_jump.valid #= false
      dut.io.in_flush.valid #= false
      dut.io.in_exception.valid #= true
      dut.io.in_exception.payload.vector #= excVec

      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == excVec,
        s"Exception test failed: got ${dut.pc_out.toBigInt}, expected $excVec"
      )
      println("Exception test passed")

      // After exception, should increment from exception vector
      dut.io.in_exception.valid #= false
      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == excVec + 4,
        s"Post-exception increment failed: got ${dut.pc_out.toBigInt}, expected ${excVec + 4}"
      )
      println("Post-exception increment test passed")

      // 5. Priority: exception > flush > jump > sequential
      println("--- Testing Priority ---")
      val jumpTargetPrio = BigInt(0x111)
      val flushAddrPrio = BigInt(0x222)
      val excVecPrio = BigInt(0x333)
      var lastPCOut = dut.pc_out.toBigInt

      // All three valid: should take exception
      println("Priority test: E > F > J")
      dut.io.in_jump.valid #= true
      dut.io.in_jump.payload.target #= jumpTargetPrio
      dut.io.in_flush.valid #= true
      dut.io.in_flush.payload.address #= flushAddrPrio
      dut.io.in_exception.valid #= true
      dut.io.in_exception.payload.vector #= excVecPrio
      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == excVecPrio,
        s"Priority test (exception) failed: got ${dut.pc_out.toBigInt}, expected $excVecPrio"
      )
      println("Priority exception test passed")

      // Only flush and jump valid: should take flush
      println("Priority test: F > J")
      dut.io.in_exception.valid #= false
      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == flushAddrPrio,
        s"Priority test (flush) failed: got ${dut.pc_out.toBigInt}, expected $flushAddrPrio"
      )
      println("Priority flush test passed")

      // Only jump valid: should take jump
      println("Priority test: J > Sequential")
      dut.io.in_flush.valid #= false
      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == jumpTargetPrio,
        s"Priority test (jump) failed: got ${dut.pc_out.toBigInt}, expected $jumpTargetPrio"
      )
      println("Priority jump test passed")

      // None valid: sequential
      println("Priority test: Sequential")
      lastPCOut = dut.pc_out.toBigInt
      dut.io.in_jump.valid #= false
      dut.clockDomain.waitSampling(2)
      assert(
        dut.pc_out.toBigInt == lastPCOut + 4,
        s"Priority test (sequential) failed: got ${dut.pc_out.toBigInt}, expected ${lastPCOut + 4}"
      )
      println("Priority sequential test passed")
    }
}
