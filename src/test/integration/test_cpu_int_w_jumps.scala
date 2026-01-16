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



object test_cpu_int_w_jumps_app extends App {
  SimConfig.withWave.withFstWave.compile(new TestCpuIntegration()).doSim { dut =>
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
      (48,  BigInt("00200693",16 )),  // addi x13, x0, 2         # independent # x13 = 2
      (52,  BigInt("00c68733",16 )),  // add  x14, x13, x12      # depends only on x12 # x14 = 41
      (56,  BigInt("401707b3",16 )),  // sub  x15, x14, x1       # depends on x14 # x15 = 36

      // # ----------------------------
      // # Forwarding test (cross reuse)
      // # ----------------------------
      (60,  BigInt("00208833",16 )),  // add  x16, x1, x2        # produces x16 # x16 = 8
      (64,  BigInt("003808b3",16 )),  // add  x17, x16, x3       # uses x16 immediately # x17 = 16
      (68,  BigInt("00488933",16 )),  // add  x18, x17, x4       # uses x17 immediately # x18 = 29
      (72,  BigInt("005909b3",16 )),  // add  x19, x18, x5       # uses x18 immediately # x19 = 39

      // # ----------------------------
      // # Delayed dependency (stall test)
      // # ----------------------------
      (76,  BigInt("00208a33",16 )),  // add  x20, x1, x2        # x20 = 8
      (80,  BigInt("00418ab3",16 )),  // add  x21, x3, x4        # unrelated, should not stall # x21 = 21
      (84,  BigInt("015a0b33",16 )),  // add  x22, x20, x21      # depends on x20, tests correct stall or bypass timing # x22 = 29

      // =====================================================================
      // ==============  JUMP + BRANCH EXTENSION (PLACEHOLDER HEX) ============
      // =====================================================================

      // # --------------------------------
      // # JAL: link register + squash test
      // # --------------------------------
      (88,  BigInt("00900b93",16 )),   // addi x23, x0, 9          # x23 = 9 (fresh)
      (92,  BigInt("00800fef",16 )),   // jal  x31, L_jal_target   # x31 = pc+4, jump # x31 = 96
      (96,  BigInt("06f00c13",16 )),   // addi x24, x0, 111        # MUST BE SQUASHED (jal taken)

      //// L_jal_target:
      (100, BigInt("00300c13",16 )),   // addi x24, x0, 3          # x24 = 3 (proves squash)

      //// # --------------------------------
      //// # JALR: computed target + squash
      //// # --------------------------------
      (104, BigInt("00000c97",16 )),   // auipc x25, 0             # x25 = PC (104)
      (108, BigInt("010c8c93",16 )),   // addi  x25, x25, <off>    # x25 = 120
      (112, BigInt("000c8f67",16 )),   // jalr  x30, 0(x25)        # x30 = pc+4, jump
      (116, BigInt("0de00d13",16 )),   // addi  x26, x0, 222       # MUST BE SQUASHED (jalr taken)

      //// L_jalr_target:
      (120, BigInt("00700d13",16 )),   // addi x26, x0, 7          # x26 = 7

      //// # --------------------------------
      //// # Basic branches: taken/not-taken
      //// # --------------------------------
      //(124, BigInt("00a00d93",16 )),   // addi x27, x0, 10         # x27 = 10
      //(128, BigInt("00a00d93",16 )),   // addi x28, x0, 10         # x28 = 10
      //(132, BigInt("01cd9a63",16 )),   // bne  x27, x28, L_bad_bne # NOT taken
      //(136, BigInt("00100e93",16 )),   // addi x29, x0, 1          # x29 = 1

      //(140, BigInt("????????",16 )),   // beq  x27, x28, L_beq_t   # taken
      //(144, BigInt("????????",16 )),   // addi x29, x29, 100       # MUST BE SQUASHED if beq taken

      //// L_beq_t:
      //(148, BigInt("????????",16 )),   // addi x29, x29, 2         # x29 = 3

      //// L_bad_bne:
      //(152, BigInt("????????",16 )),   // addi x29, x0, 999        # should never happen (optional trap reg)

      //// # --------------------------------
      //// # Signed vs unsigned branch checks
      //// # --------------------------------
      //(156, BigInt("????????",16 )),   // addi x6,  x0, -1         # x6 = -1 (0xFFFF...)
      //(160, BigInt("????????",16 )),   // addi x7,  x0, 1          # x7 = 1
      //(164, BigInt("????????",16 )),   // blt  x6, x7, L_blt_t     # taken (signed)
      //(168, BigInt("????????",16 )),   // addi x8, x0, 55          # MUST BE SQUASHED
      //// L_blt_t:
      //(172, BigInt("????????",16 )),   // addi x8, x0, 11          # x8 = 11

      //(176, BigInt("????????",16 )),   // bltu x6, x7, L_bltu_t    # NOT taken (unsigned)
      //(180, BigInt("????????",16 )),   // addi x9, x0, 22          # x9 = 22
      //(184, BigInt("????????",16 )),   // j    L_after_bltu        # unconditional jump (or jal x0, ...)
      //// L_bltu_t:
      //(188, BigInt("????????",16 )),   // addi x9, x0, 99          # should never happen
      //// L_after_bltu:
      //(192, BigInt("????????",16 )),   // addi x10, x0, 0          # (optional marker / nop-ish)

      //// # --------------------------------
      //// # Forwarding into branch compare (critical)
      //// # --------------------------------
      //(196, BigInt("????????",16 )),   // addi x11, x0, 5
      //(200, BigInt("????????",16 )),   // addi x12, x0, 6
      //(204, BigInt("????????",16 )),   // add  x13, x11, x12       # x13 = 11
      //(208, BigInt("????????",16 )),   // beq  x13, x0, L_bad_fwd  # NOT taken (uses x13 immediately)
      //(212, BigInt("????????",16 )),   // addi x14, x0, 33         # x14 = 33
      //(216, BigInt("????????",16 )),   // j    L_fwd_ok
      //// L_bad_fwd:
      //(220, BigInt("????????",16 )),   // addi x14, x0, 999        # should never happen
      //// L_fwd_ok:
      //(224, BigInt("????????",16 )),   // addi x15, x0, 0          # (optional marker)

      //// # --------------------------------
      //// # Backward branch (loop) + exit
      //// # --------------------------------
      //(228, BigInt("????????",16 )),   // addi x16, x0, 4          # count = 4
      //(232, BigInt("????????",16 )),   // addi x17, x0, 0          # sum = 0
      //// L_loop:
      //(236, BigInt("????????",16 )),   // addi x17, x17, 3         # sum += 3
      //(240, BigInt("????????",16 )),   // addi x16, x16, -1        # count--
      //(244, BigInt("????????",16 )),   // bne  x16, x0, L_loop     # taken 3 times, not taken once
      //// After loop: x17 = 12

      //// # --------------------------------
      //// # Done / park
      //// # --------------------------------
      //(248, BigInt("????????",16 ))    // j done (self-loop) or jal x0, 0
    )

  
    for ((address, data) <- instructions) {
      dut.ramArea.ram.memory.setBigInt(address.toLong, data)
    }

    // Access internal signals via dut.cpu.coreArea
    // We need to wait for clock edges. Since we generate clock manually, we can just sleep period.
    // Or we can use `dut.testClockDomain.waitSampling` if we exposed the clock domain appropriately, but easier to just loop and sleep.
    
    for(i <- 0 to 145) {
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
       
       val pc = dut.cpu.coreArea.pc.PC_cur.toBigInt.toLong
       val cmd_valid = dut.cpu.coreArea.fetch.io.readCmd.cmd.valid.toBoolean
       val cmd_ready = dut.cpu.coreArea.fetch.io.readCmd.cmd.ready.toBoolean
       val rsp_valid = dut.cpu.coreArea.fetch.io.readCmd.rsp.valid.toBoolean
       
       val rh = dut.cpu.coreArea.wbArea.readHere
       //println(s"PC: $pc CMD: v=$cmd_valid r=$cmd_ready RSP: v=$rsp_valid | RESULT.valid ${rh.valid_result.toBoolean}, RESULT.address ${rh.rdaddr.toLong}, RESULT.data ${rh.result.toLong}, IMMED: ${rh.immed.toLong}, SENDTOALU: ${rh.sendtoalu.toBoolean}, VALID: ${rh.valid.toBoolean}")
        val pc_dec = dut.cpu.coreArea.pipeline.ctrls(3).up(borb.fetch.PC.PC).toBigInt.toLong
        val pc_dsp = dut.cpu.coreArea.pipeline.ctrls(4).up(borb.fetch.PC.PC).toBigInt.toLong
        val pc_ex  = dut.cpu.coreArea.pipeline.ctrls(6).up(borb.fetch.PC.PC).toBigInt.toLong
        val v_dsp = dut.cpu.coreArea.pipeline.ctrls(4).isValid.toBoolean
        val f_dsp = dut.cpu.coreArea.pipeline.ctrls(4).down.isFiring.toBoolean
        val r_busy = dut.cpu.coreArea.dispatcher.hcs.regBusy.toBigInt
        
        val may_flush_ex = dut.cpu.coreArea.pipeline.ctrls(6).up(borb.common.Common.MAY_FLUSH).toBoolean
        val ucode_ex     = dut.cpu.coreArea.pipeline.ctrls(6).up(borb.frontend.Decoder.MicroCode).toEnum
        val flush_cmd    = dut.cpu.coreArea.branch.logic.jumpCmd.valid.toBoolean
        
        val inflight = dut.cpu.coreArea.fetch.inflight.toBigInt
        val epoch    = dut.cpu.coreArea.fetch.epoch.toBigInt
        val avail    = dut.cpu.coreArea.fetch.fifo.io.availability.toBigInt
        val cmd_v    = dut.cpu.coreArea.fetch.io.readCmd.cmd.valid.toBoolean
        val rsp_v    = dut.cpu.coreArea.fetch.io.readCmd.rsp.valid.toBoolean
        
       // if(i > 90 && i < 155) {
       //      println(f"Cycle $i%3d | DEC:$pc_dec%3d | DSP:$pc_dsp%3d | EX:$pc_ex%3d (MF=$may_flush_ex, FL=$flush_cmd, UC=$ucode_ex) | WB: ${rh.pc.toBigInt.toLong}%3d (Commit=${rh.commit.toBoolean}) | Inflight:$inflight Epoch:$epoch Avail:$avail CmdV:$cmd_v RspV:$rsp_v")
       // }
        

       if(rh.commit.toBoolean == true){
        println(s"RESULT.valid ${rh.valid_result.toBoolean}, RESULT.address ${rh.rdaddr.toLong}, RESULT.data ${rh.result.toLong}, IMMED: ${rh.immed.toLong}, LANE_SEL: ${rh.lane_sel.toBoolean}, COMMIT: ${rh.commit.toBoolean}, PC: ${rh.pc.toBigInt.toLong}")

       }
       
       
    }
  }
}
