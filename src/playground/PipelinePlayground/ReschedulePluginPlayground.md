# Reschedule Plugin Playground

This playground is intentionally isolated from the production CPU.  It tries three shapes:

- `OldestAgeReschedulePlugin`: request ports arbitrate by oldest pipeline age first.  This is the direction closest to a CPU recovery fabric, because trap/writeback style redirects naturally outrank younger branch/replay redirects.
- `AllocationPriorityReschedulePlugin`: request ports arbitrate in plugin allocation order.  This is useful when the CPU wants explicit top-level policy, but it is easier to make a bad priority decision by accident.
- `AreaRescheduleMockCpu`: the same basic fabric without `PluginHost`, to keep a small non-plugin reference around.

The plugin-host mock CPU uses `PluginHost` and `FiberPlugin` as a service locator:

- `StageCtrlPipelineServicePlugin` owns the `StageCtrlPipeline`, stage ages, lane-live payload, lane flushing, and freeze requests.
- `BasePlaygroundReschedulePlugin` owns request allocation, shared redirect output, and age/lane flush matching.
- `FetchToyPlugin`, `DecodeToyPlugin`, `ExecuteToyPlugin`, and `RetireToyPlugin` show normal stage wiring.
- `BranchRedirectPlugin`, `RelaxedBranchRedirectPlugin`, `ReplayRedirectPlugin`, and `TrapRedirectPlugin` show request producer plugins.

The relaxed branch plugin adds one cycle between detecting a redirect and publishing the request.  That mirrors the VexiiRiscv-style performance knob where branch side effects can be delayed to cut a tight redirect path.

Commands:

```sh
sbt "runMain borb.playground.ReschedulePluginPlayground"
sbt "runMain borb.playground.ReschedulePluginPlayground --simulate"
```

