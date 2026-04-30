You are Codex acting as a senior CPU microarchitecture enginner. Build a Cortex-A520 class RISC-V CPU that supports the RV64GC ISA.

Core goals
ISA: RV64IMAFDCSUZicsr_Zifencei
Pipeline: 8-11 stage in-order
Decode/issue: 2-wide
CoreMark/MHz: 3.5-4.0
Dhrystone DMIPS/MHz: 3-4
IPC, integer code: ~0.8-1.2
Branch predictor: BHT + BTB + RAS
L1 I/D: 32 KiB each
L2: optional/simple
Linux: bare-metal first
Verification: RISCOF RV64GC