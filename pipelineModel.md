# VexiiRiscv execution model

This document details how VexiiRiscv ensures execution correctness, handles pipeline bubbles, enforces safe writebacks, and manages instruction invalidation (flushing).

## 1. Ensuring Correct Execution (Validity & Traps)

VexiiRiscv uses a "validity mask" approach combined with trap handling to ensure only correct instructions execute and have side effects.

### The `LANE_SEL` Signal - The Pulse of Instruction Validity

In the `ExecuteLanePlugin`, everyday instruction validity is tracked via a payload often referred to as `LANE_SEL` (Lane Select). This signal acts as the primary "valid" bit for an instruction in a specific lane. It is not just a passive flag; it is the active driver that determines if a pipeline slot contains a meaningless bubble or a real architectural instruction.

**Initialization and Arbitration:**
Instructions enter the execution lanes via the `DispatchPlugin`. The dispatcher performs complex arbitration to map candidates (decoded instructions) to available functional units (execution lanes). When an arbitration win occurs for a specific lane, the `DispatchPlugin` drives the `LANE_SEL` signal high for the first stage (stage 0) of that lane.

```scala
// vexiiriscv/schedule/DispatchPlugin.scala

// "insertNode" represents the interface to the first stage of the Execution Lane
val insertNode = eu.ctrl(0).up

// LANE_SEL is driven True only if:
// 1. The scheduler selected this lane (oh.orR)
// 2. The instruction wasn't cancelled (e.g. by a flush)
// 3. Dispatch isn't halted globally
insertNode(CtrlLaneApi.LANE_SEL) := oh.orR && !mux(_.cancel) && !api.haltDispatch
```

**Propagation and Bubble Default:**
For all subsequent stages in the execution lane (stages 1 to N), `LANE_SEL` behaves like a synchronous register that defaults to `False`. This is a critical safety feature: if a stage does not explicitly capture a valid signal from the previous stage, it automatically becomes a bubble.

```scala
// vexiiriscv/execute/ExecuteLanePlugin.scala

// Iterate over all control stages in the lane
for(ctrlId <- 0 to idToCtrl.keys.max){
  val c = idToCtrl(ctrlId)
  
  // If not the first stage, the valid bit (LANE_SEL) defaults to False (Bubble)
  // This means any gap in the pipeline automatically becomes an invalid instruction
  if(ctrlId != 0) c.up(c.LANE_SEL).setAsReg().init(False)
}
```

**Functional Unit Usage:**
Functional units (like ALUs, Multipliers) typically don't read `LANE_SEL` directly. Instead, they operate based on the `isValid` signal provided by the pipeline control logic. The `isValid` signal is derived from `LANE_SEL` being active *and* the upstream stage firing valid data.

```scala
// Conceptually, for any plugin:
val logic = during setup new Area {
  val process = new eu.Execute(id) {
    // 'isValid' here implicitly checks LANE_SEL
    // If LANE_SEL is False (Bubble), these signals will not trigger side effects
    when(isValid) {
       // Perform ALU operation, etc.
    }
  }
}
```

### Trap Handling

If an instruction is valid but malformed (e.g., Illegal Instruction) or problematic (e.g., Load Access Fault), it triggers a **Trap**. The `ExecuteLanePlugin` aggregates trap signals from various plugins (decoder, LSU, etc.). The `Global.TRAP` payload propagates down the pipeline. If any stage asserts `TRAP`, it effectively poisons the instruction, preventing it from committing, even if `LANE_SEL` remains high.

```scala
// vexiiriscv/execute/ExecuteLanePlugin.scala
// Aggregate trap signals from all stages
trapPending(hartId) := (for (ctrlId <- 1 to ...; c = ctrl(ctrlId)) yield 
  c.isValid && c(Global.HART_ID) === hartId && c(TRAP)
).orR
```

---

## 2. Handling Bubbles

VexiiRiscv employs a **"No Collapse"** pipeline philosophy in the Execution stage.

### Interlocked Pipeline

Unlike some pipelines that squeeze out bubbles to maximize density, VexiiRiscv maintains the relative timing between instructions in the execute lanes. This simplifies the complex forwarding (bypassing) networks compared to out-of-order or collapsing pipelines.

*   **Representation**: A bubble is simply a pipeline slot where `down.isFiring` is false (because `LANE_SEL` is false).
*   **Behavior**: The bubble slot moves forward every cycle unless the *entire* pipeline freezes. It occupies physical resources but performs no work.

**Mechanism**:
The `ExecutePipelinePlugin` constructs the pipeline stages using `StageLink` with the `.withoutCollapse()` option. This strictly enforces that Stage `N` cannot proceed if Stage `N+1` is halted, enforcing rigid lockstep movement.

```scala
// vexiiriscv/execute/ExecutePipelinePlugin.scala
val sc = for ((from, to) <- (ctrls, ctrls.tail).zipped) yield 
  new StageLink(from.down, to.up).withoutCollapse()
```

This ensures that if stage N stalls, stage N-1 also stalls, preserving the "bubble" gap between instructions. This predictability is essential for the `ExecuteLanePlugin`'s bypass logic, which calculates operand forwarding based on fixed stage delays (`readLatency`, etc.).

---

## 3. Ensuring Correct Writeback

Writeback safety is arguably the most critical part of the commits. It ensures that speculative, flushed, or trapped instructions **never** corrupt the register file.

### The `Global.COMMIT` Signal

The `Global.COMMIT` signal is the master gatekeeper for architectural state updates. It is not enough for an instruction to reach the end of the pipeline (`isValid`); it must also be free of exceptions.

**Derivation:**
The signal is initially derived in the `ExecuteLanePlugin` by inverting the aggregate trap signal.

```scala
// vexiiriscv/execute/ExecuteLanePlugin.scala
// An instruction commits ONLY if it hasn't trapped
execute(0).up(COMMIT) := !execute(0).up(TRAP)
```
*Note: While defined at `execute(0)`, due to SpinalHDL's signal elaboration, this logic logically applies to the instruction's validity context.*

**Veto by LSU:**
Crucially, plugins like the **LSU (Load Store Unit)** can veto a commit at the last moment. If a memory access faults (e.g., page fault) or requires a retry (e.g., cache miss in a non-blocking setup, or "redo"), the `LsuPlugin` acts to kill the commit to prevent the register file from being written with invalid loaded data.

```scala
// vexiiriscv/execute/lsu/LsuPlugin.scala
when(lsuTrap) {
  // Force a trap downstream
  bypass(Global.TRAP) := True
  
  // EXPLICITLY KILL THE COMMIT
  // This overrides the default COMMIT := !TRAP logic for this stage
  bypass(Global.COMMIT) := False
}
```

### The `WriteBackPlugin` Check

In the `WriteBackPlugin`, the actual write to the register file is gated by multiple checks. It serves as the final barrier.

*   `down.isFiring`: Is the pipeline stage active? (Not stalled, not a bubble)
*   `hits.orR`: Is there data to write?
*   `up(rfa.ENABLE)`: Did the instruction actually request a register write?
*   `Global.COMMIT`: Is the instruction verified as safe?

```scala
// vexiiriscv/execute/WriteBackPlugin.scala
val write = Flow(RegFileWriter(rf))

// The critical safety AND gate:
write.valid := down.isFiring && hits.orR && up(rfa.ENABLE) && Global.COMMIT

write.hartId := HART_ID
write.data := muxed
```

If `Global.COMMIT` is false (due to a trap) or `down.isFiring` is false (due to a bubble/flush), `write.valid` becomes false, and the Register File remains untouched.

**Other Consumers of `Global.COMMIT`:**
*   **FpuCsrPlugin**: Updates FPU exception flags (NX, OF, etc.) only when `COMMIT` is true.
*   **CfuPlugin**: Custom Functional Units often produce side effects that must only occur on commit.
*   **PerformanceCounterService**: Events are often counted only if `COMMIT` is true to measure "retired" instructions vs "executed" instructions.

---

## 4. Invalidating Bubbles (Flushing)

Flushing is the process of actively turning a "valid" instruction into a "bubble" (invalidating it), usually due to a branch misprediction or exception.

### The `ReschedulePlugin`

This plugin acts as the central arbiter for flush requests. It tracks "Flush Commands" which specify an `age`. The VexiiRiscv pipeline assigns an age to every instruction (based on its position in the pipeline and issue order). Any instruction *younger* than the flush age must be killed.

### Execution Lane Invalidation

The `ExecuteLanePlugin` listens to the `ReschedulePlugin` at every single pipeline stage. If a flush is active for a given instruction's age, the plugin overrides the pipeline control signals.

```scala
// vexiiriscv/execute/ExecuteLanePlugin.scala

val age = getCtrlAge(ctrlId)
// Check if this specific stage needs to be flushed
// 'rp' is the ReschedulePlugin
val doIt = rp.isFlushedAt(age, c(Global.HART_ID), c(Execute.LANE_AGE))

doIt match {
  case Some(cond) =>
    when(cond) {
      // KILL the instruction:
      // 1. Turn it into a bubble for the next stage
      c.bypass(c.LANE_SEL) := False      
      
      // 2. Prevent any register writes immediately (redundant safety)
      c.bypass(rdRfa.ENABLE) := False    
    }
  case None => // No flush logic needed for this stage (e.g. older than commit point)
}
```

By forcing `LANE_SEL` to `False`, the instruction effectively ceases to exist for downstream plugins. It allows the pipeline to "drain" these now-invalid instructions as harmless bubbles that drift to the end of the pipe execution without triggering writeback or exceptions.