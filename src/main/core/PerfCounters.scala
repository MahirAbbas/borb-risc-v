package borb.core

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._

/**
  * Performance counters bundle - read-only output for observation.
  */
case class PerfCountersBundle() extends Bundle {
  val cycles        = UInt(64 bits)
  val instret       = UInt(64 bits)
  val stallsHazard  = UInt(64 bits)
  val stallsFetch   = UInt(64 bits)
  val stallsMem     = UInt(64 bits)
  val stallsBackend = UInt(64 bits)
  val stallsWriteback = UInt(64 bits)
  val stallsCommit = UInt(64 bits)
  val stallsMulDivBusy = UInt(64 bits)
  val stallsLsuReplayOrWait = UInt(64 bits)
  val stallsDispatchToSrc = UInt(64 bits)
  val stallsSrcToExec = UInt(64 bits)
  val stallsExecToWrite = UInt(64 bits)
  val cyclesDispatchValid = UInt(64 bits)
  val cyclesSrcValid = UInt(64 bits)
  val cyclesExecValid = UInt(64 bits)
  val cyclesWriteValid = UInt(64 bits)
  val cyclesDispatchFire = UInt(64 bits)
  val cyclesSrcFire = UInt(64 bits)
  val cyclesExecFire = UInt(64 bits)
  val cyclesWriteFire = UInt(64 bits)
  val frontendPendingReqCycles = UInt(64 bits)
  val frontendBeat0ValidCycles = UInt(64 bits)
  val frontendBeat1ValidCycles = UInt(64 bits)
  val frontendReqIssued = UInt(64 bits)
  val frontendRspAccepted = UInt(64 bits)
  val frontendNeedCurrentReq = UInt(64 bits)
  val frontendNeedNextReq = UInt(64 bits)
  val frontendPrefetchReq = UInt(64 bits)
  val frontendWaitCurBeat = UInt(64 bits)
  val frontendWaitNextBeat = UInt(64 bits)
  val frontendTakeInsn = UInt(64 bits)
  val frontendCurBeatHit = UInt(64 bits)
  val frontendNextBeatHit = UInt(64 bits)
  val frontendCmdValidCycles = UInt(64 bits)
  val frontendPrefetchWindow = UInt(64 bits)
  val frontendPrefetchBlockedNoCmd = UInt(64 bits)
  val frontendPrefetchBlockedPending = UInt(64 bits)
  val frontendPrefetchBlockedNextHit = UInt(64 bits)
  val backendOccupancy0 = UInt(64 bits)
  val backendOccupancy1 = UInt(64 bits)
  val backendOccupancy2 = UInt(64 bits)
  val backendOccupancy3 = UInt(64 bits)
  val backendOccupancy4 = UInt(64 bits)
  val backendOverlapDispatchSrc = UInt(64 bits)
  val backendOverlapSrcExec = UInt(64 bits)
  val backendOverlapExecWrite = UInt(64 bits)
  val branches      = UInt(64 bits)
  val branchesTaken = UInt(64 bits)
  val flushes       = UInt(64 bits)
  val loads         = UInt(64 bits)
  val stores        = UInt(64 bits)
  val jumps         = UInt(64 bits)
  val csrOps        = UInt(64 bits)
  val mulDivOps     = UInt(64 bits)
  val trapCommits   = UInt(64 bits)
}

/**
  * Performance monitoring plugin.
  * 
  * Contains internal 64-bit registers for each counter.
  * Exposes signals for event input and counter output.
  */
case class PerfCountersPlugin(wbStage: CtrlLink) extends Area {
  import borb.common.Common._
  import borb.common.MicroCode._
  
  // Internal counter registers
  val cycles        = Reg(UInt(64 bits)) init 0
  val instret       = Reg(UInt(64 bits)) init 0
  val stallsHazard  = Reg(UInt(64 bits)) init 0
  val stallsFetch   = Reg(UInt(64 bits)) init 0
  val stallsMem     = Reg(UInt(64 bits)) init 0
  val stallsBackend = Reg(UInt(64 bits)) init 0
  val stallsWriteback = Reg(UInt(64 bits)) init 0
  val stallsCommit = Reg(UInt(64 bits)) init 0
  val stallsMulDivBusy = Reg(UInt(64 bits)) init 0
  val stallsLsuReplayOrWait = Reg(UInt(64 bits)) init 0
  val stallsDispatchToSrc = Reg(UInt(64 bits)) init 0
  val stallsSrcToExec = Reg(UInt(64 bits)) init 0
  val stallsExecToWrite = Reg(UInt(64 bits)) init 0
  val cyclesDispatchValid = Reg(UInt(64 bits)) init 0
  val cyclesSrcValid = Reg(UInt(64 bits)) init 0
  val cyclesExecValid = Reg(UInt(64 bits)) init 0
  val cyclesWriteValid = Reg(UInt(64 bits)) init 0
  val cyclesDispatchFire = Reg(UInt(64 bits)) init 0
  val cyclesSrcFire = Reg(UInt(64 bits)) init 0
  val cyclesExecFire = Reg(UInt(64 bits)) init 0
  val cyclesWriteFire = Reg(UInt(64 bits)) init 0
  val frontendPendingReqCycles = Reg(UInt(64 bits)) init 0
  val frontendBeat0ValidCycles = Reg(UInt(64 bits)) init 0
  val frontendBeat1ValidCycles = Reg(UInt(64 bits)) init 0
  val frontendReqIssued = Reg(UInt(64 bits)) init 0
  val frontendRspAccepted = Reg(UInt(64 bits)) init 0
  val frontendNeedCurrentReq = Reg(UInt(64 bits)) init 0
  val frontendNeedNextReq = Reg(UInt(64 bits)) init 0
  val frontendPrefetchReq = Reg(UInt(64 bits)) init 0
  val frontendWaitCurBeat = Reg(UInt(64 bits)) init 0
  val frontendWaitNextBeat = Reg(UInt(64 bits)) init 0
  val frontendTakeInsn = Reg(UInt(64 bits)) init 0
  val frontendCurBeatHit = Reg(UInt(64 bits)) init 0
  val frontendNextBeatHit = Reg(UInt(64 bits)) init 0
  val frontendCmdValidCycles = Reg(UInt(64 bits)) init 0
  val frontendPrefetchWindow = Reg(UInt(64 bits)) init 0
  val frontendPrefetchBlockedNoCmd = Reg(UInt(64 bits)) init 0
  val frontendPrefetchBlockedPending = Reg(UInt(64 bits)) init 0
  val frontendPrefetchBlockedNextHit = Reg(UInt(64 bits)) init 0
  val backendOccupancy0 = Reg(UInt(64 bits)) init 0
  val backendOccupancy1 = Reg(UInt(64 bits)) init 0
  val backendOccupancy2 = Reg(UInt(64 bits)) init 0
  val backendOccupancy3 = Reg(UInt(64 bits)) init 0
  val backendOccupancy4 = Reg(UInt(64 bits)) init 0
  val backendOverlapDispatchSrc = Reg(UInt(64 bits)) init 0
  val backendOverlapSrcExec = Reg(UInt(64 bits)) init 0
  val backendOverlapExecWrite = Reg(UInt(64 bits)) init 0
  val branches      = Reg(UInt(64 bits)) init 0
  val branchesTaken = Reg(UInt(64 bits)) init 0
  val flushes       = Reg(UInt(64 bits)) init 0
  val loads         = Reg(UInt(64 bits)) init 0
  val stores        = Reg(UInt(64 bits)) init 0
  val jumps         = Reg(UInt(64 bits)) init 0
  val csrOps        = Reg(UInt(64 bits)) init 0
  val mulDivOps     = Reg(UInt(64 bits)) init 0
  val trapCommits   = Reg(UInt(64 bits)) init 0
  
  // cycles: always increments
  cycles := cycles + 1
  
  // instret: increments on commit
  val wbArea = new wbStage.Area {
    when(up(COMMIT)) {
      instret := instret + 1

      switch(up(borb.frontend.Decoder.MicroCode)) {
        is(uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD) {
          loads := loads + 1
        }
        is(uopSB, uopSH, uopSW, uopSD) {
          stores := stores + 1
        }
        is(uopJAL, uopJALR) {
          jumps := jumps + 1
        }
        is(uopCSRRW, uopCSRRS, uopCSRRC, uopCSRRWI, uopCSRRSI, uopCSRRCI) {
          csrOps := csrOps + 1
        }
        is(
          uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
          uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW
        ) {
          mulDivOps := mulDivOps + 1
        }
      }
      when(up(TRAP)) {
        trapCommits := trapCommits + 1
      }
    }
  }
  
  // Event input signals (to be assigned from CPU)
  val hazardStall    = Bool()
  val fetchStall     = Bool()
  val memStall       = Bool()
  val backendStall   = Bool()
  val writebackStall = Bool()
  val commitStall = Bool()
  val mulDivBusyStall = Bool()
  val lsuReplayOrWaitStall = Bool()
  val dispatchToSrcStall = Bool()
  val srcToExecStall = Bool()
  val execToWriteStall = Bool()
  val dispatchValid = Bool()
  val srcValid = Bool()
  val execValid = Bool()
  val writeValid = Bool()
  val dispatchFire = Bool()
  val srcFire = Bool()
  val execFire = Bool()
  val writeFire = Bool()
  val frontendPendingReq = Bool()
  val frontendBeat0Valid = Bool()
  val frontendBeat1Valid = Bool()
  val frontendReqIssuedEvent = Bool()
  val frontendRspAcceptedEvent = Bool()
  val frontendNeedCurrentReqEvent = Bool()
  val frontendNeedNextReqEvent = Bool()
  val frontendPrefetchReqEvent = Bool()
  val frontendWaitCurBeatEvent = Bool()
  val frontendWaitNextBeatEvent = Bool()
  val frontendTakeInsnEvent = Bool()
  val frontendCurBeatHitEvent = Bool()
  val frontendNextBeatHitEvent = Bool()
  val frontendCmdValidCycleEvent = Bool()
  val frontendPrefetchWindowEvent = Bool()
  val frontendPrefetchBlockedNoCmdEvent = Bool()
  val frontendPrefetchBlockedPendingEvent = Bool()
  val frontendPrefetchBlockedNextHitEvent = Bool()
  val backendOcc0 = Bool()
  val backendOcc1 = Bool()
  val backendOcc2 = Bool()
  val backendOcc3 = Bool()
  val backendOcc4 = Bool()
  val backendOverlapDispatchSrcEvent = Bool()
  val backendOverlapSrcExecEvent = Bool()
  val backendOverlapExecWriteEvent = Bool()
  val branchExecuted = Bool()
  val branchTaken    = Bool()
  val pipelineFlush  = Bool()
  
  // Increment on events
  when(hazardStall)    { stallsHazard  := stallsHazard + 1 }
  when(fetchStall)     { stallsFetch   := stallsFetch + 1 }
  when(memStall)       { stallsMem     := stallsMem + 1 }
  when(backendStall)   { stallsBackend := stallsBackend + 1 }
  when(writebackStall) { stallsWriteback := stallsWriteback + 1 }
  when(commitStall) { stallsCommit := stallsCommit + 1 }
  when(mulDivBusyStall) { stallsMulDivBusy := stallsMulDivBusy + 1 }
  when(lsuReplayOrWaitStall) { stallsLsuReplayOrWait := stallsLsuReplayOrWait + 1 }
  when(dispatchToSrcStall) { stallsDispatchToSrc := stallsDispatchToSrc + 1 }
  when(srcToExecStall) { stallsSrcToExec := stallsSrcToExec + 1 }
  when(execToWriteStall) { stallsExecToWrite := stallsExecToWrite + 1 }
  when(dispatchValid) { cyclesDispatchValid := cyclesDispatchValid + 1 }
  when(srcValid) { cyclesSrcValid := cyclesSrcValid + 1 }
  when(execValid) { cyclesExecValid := cyclesExecValid + 1 }
  when(writeValid) { cyclesWriteValid := cyclesWriteValid + 1 }
  when(dispatchFire) { cyclesDispatchFire := cyclesDispatchFire + 1 }
  when(srcFire) { cyclesSrcFire := cyclesSrcFire + 1 }
  when(execFire) { cyclesExecFire := cyclesExecFire + 1 }
  when(writeFire) { cyclesWriteFire := cyclesWriteFire + 1 }
  when(frontendPendingReq) { frontendPendingReqCycles := frontendPendingReqCycles + 1 }
  when(frontendBeat0Valid) { frontendBeat0ValidCycles := frontendBeat0ValidCycles + 1 }
  when(frontendBeat1Valid) { frontendBeat1ValidCycles := frontendBeat1ValidCycles + 1 }
  when(frontendReqIssuedEvent) { frontendReqIssued := frontendReqIssued + 1 }
  when(frontendRspAcceptedEvent) { frontendRspAccepted := frontendRspAccepted + 1 }
  when(frontendNeedCurrentReqEvent) { frontendNeedCurrentReq := frontendNeedCurrentReq + 1 }
  when(frontendNeedNextReqEvent) { frontendNeedNextReq := frontendNeedNextReq + 1 }
  when(frontendPrefetchReqEvent) { frontendPrefetchReq := frontendPrefetchReq + 1 }
  when(frontendWaitCurBeatEvent) { frontendWaitCurBeat := frontendWaitCurBeat + 1 }
  when(frontendWaitNextBeatEvent) { frontendWaitNextBeat := frontendWaitNextBeat + 1 }
  when(frontendTakeInsnEvent) { frontendTakeInsn := frontendTakeInsn + 1 }
  when(frontendCurBeatHitEvent) { frontendCurBeatHit := frontendCurBeatHit + 1 }
  when(frontendNextBeatHitEvent) { frontendNextBeatHit := frontendNextBeatHit + 1 }
  when(frontendCmdValidCycleEvent) { frontendCmdValidCycles := frontendCmdValidCycles + 1 }
  when(frontendPrefetchWindowEvent) { frontendPrefetchWindow := frontendPrefetchWindow + 1 }
  when(frontendPrefetchBlockedNoCmdEvent) { frontendPrefetchBlockedNoCmd := frontendPrefetchBlockedNoCmd + 1 }
  when(frontendPrefetchBlockedPendingEvent) { frontendPrefetchBlockedPending := frontendPrefetchBlockedPending + 1 }
  when(frontendPrefetchBlockedNextHitEvent) { frontendPrefetchBlockedNextHit := frontendPrefetchBlockedNextHit + 1 }
  when(backendOcc0) { backendOccupancy0 := backendOccupancy0 + 1 }
  when(backendOcc1) { backendOccupancy1 := backendOccupancy1 + 1 }
  when(backendOcc2) { backendOccupancy2 := backendOccupancy2 + 1 }
  when(backendOcc3) { backendOccupancy3 := backendOccupancy3 + 1 }
  when(backendOcc4) { backendOccupancy4 := backendOccupancy4 + 1 }
  when(backendOverlapDispatchSrcEvent) { backendOverlapDispatchSrc := backendOverlapDispatchSrc + 1 }
  when(backendOverlapSrcExecEvent) { backendOverlapSrcExec := backendOverlapSrcExec + 1 }
  when(backendOverlapExecWriteEvent) { backendOverlapExecWrite := backendOverlapExecWrite + 1 }
  when(branchExecuted) { branches      := branches + 1 }
  when(branchTaken)    { branchesTaken := branchesTaken + 1 }
  when(pipelineFlush)  { flushes       := flushes + 1 }
  
  // Output bundle
  val counters = PerfCountersBundle()
  counters.cycles        := cycles
  counters.instret       := instret
  counters.stallsHazard  := stallsHazard
  counters.stallsFetch   := stallsFetch
  counters.stallsMem     := stallsMem
  counters.stallsBackend := stallsBackend
  counters.stallsWriteback := stallsWriteback
  counters.stallsCommit := stallsCommit
  counters.stallsMulDivBusy := stallsMulDivBusy
  counters.stallsLsuReplayOrWait := stallsLsuReplayOrWait
  counters.stallsDispatchToSrc := stallsDispatchToSrc
  counters.stallsSrcToExec := stallsSrcToExec
  counters.stallsExecToWrite := stallsExecToWrite
  counters.cyclesDispatchValid := cyclesDispatchValid
  counters.cyclesSrcValid := cyclesSrcValid
  counters.cyclesExecValid := cyclesExecValid
  counters.cyclesWriteValid := cyclesWriteValid
  counters.cyclesDispatchFire := cyclesDispatchFire
  counters.cyclesSrcFire := cyclesSrcFire
  counters.cyclesExecFire := cyclesExecFire
  counters.cyclesWriteFire := cyclesWriteFire
  counters.frontendPendingReqCycles := frontendPendingReqCycles
  counters.frontendBeat0ValidCycles := frontendBeat0ValidCycles
  counters.frontendBeat1ValidCycles := frontendBeat1ValidCycles
  counters.frontendReqIssued := frontendReqIssued
  counters.frontendRspAccepted := frontendRspAccepted
  counters.frontendNeedCurrentReq := frontendNeedCurrentReq
  counters.frontendNeedNextReq := frontendNeedNextReq
  counters.frontendPrefetchReq := frontendPrefetchReq
  counters.frontendWaitCurBeat := frontendWaitCurBeat
  counters.frontendWaitNextBeat := frontendWaitNextBeat
  counters.frontendTakeInsn := frontendTakeInsn
  counters.frontendCurBeatHit := frontendCurBeatHit
  counters.frontendNextBeatHit := frontendNextBeatHit
  counters.frontendCmdValidCycles := frontendCmdValidCycles
  counters.frontendPrefetchWindow := frontendPrefetchWindow
  counters.frontendPrefetchBlockedNoCmd := frontendPrefetchBlockedNoCmd
  counters.frontendPrefetchBlockedPending := frontendPrefetchBlockedPending
  counters.frontendPrefetchBlockedNextHit := frontendPrefetchBlockedNextHit
  counters.backendOccupancy0 := backendOccupancy0
  counters.backendOccupancy1 := backendOccupancy1
  counters.backendOccupancy2 := backendOccupancy2
  counters.backendOccupancy3 := backendOccupancy3
  counters.backendOccupancy4 := backendOccupancy4
  counters.backendOverlapDispatchSrc := backendOverlapDispatchSrc
  counters.backendOverlapSrcExec := backendOverlapSrcExec
  counters.backendOverlapExecWrite := backendOverlapExecWrite
  counters.branches      := branches
  counters.branchesTaken := branchesTaken
  counters.flushes       := flushes
  counters.loads         := loads
  counters.stores        := stores
  counters.jumps         := jumps
  counters.csrOps        := csrOps
  counters.mulDivOps     := mulDivOps
  counters.trapCommits   := trapCommits
}
