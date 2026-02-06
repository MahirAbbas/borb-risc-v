Below is a complete, structured write‑up of what was implemented and how the RISCOF integration works end‑to‑end. It includes the “why” behind the decisions, how the DUT and reference models are wired, and how data flows through the system.

**Overview**

RISCOF is a framework that:
1. Selects tests based on your ISA/platform YAMLs.
2. Compiles each test.
3. Runs the test on a DUT model and on a reference model.
4. Compares the signatures in memory between `begin_signature` and `end_signature`.

RISCOF **does not** provide a memory model or execution environment for your DUT. Your DUT runner must:
- Load the compiled test into memory.
- Run it on the model.
- Extract the signature range from memory.
- Write a signature file matching RISCOF’s expected format.

---

**Key Files and Changes**

**1. ISA YAMLs (RISCOF inputs)**  
We aligned the ISA to RV64I + Zicsr + Zifencei, and fixed RV64‑specific constraints.

- `/Users/mahir/fun/borb/verif/riscof/borb/borb_isa.yaml`
- `/Users/mahir/fun/borb/verif/riscof/spike/spike_isa.yaml`

Key fields:
- `ISA: RV64IZicsr_Zifencei`
- `physical_addr_sz: 56` (per RV64 max)
- `misa.reset-val: 0x8000000000000100` (RV64I: MXL=2, I bit set)

**Why this matters:**  
RISCOF uses `riscv_config` to validate the ISA YAML. Incorrect `misa` or invalid `physical_addr_sz` causes validation failure. The Z* extensions do **not** appear in `misa`, so the reset value only needs I + RV64 MXL.

---

**2. SoC RAM Size Increase**  
RISCOF tests can require more than 16 KiB. We increased to 256 KiB.

- `/Users/mahir/fun/borb/src/main/SoC.scala`  
  - `byteCount = 256 KiB`

We regenerated the Verilog to match the new RAM size:

- `/Users/mahir/fun/borb/SoC.v` (generated)

**Why this matters:**  
The Verilator runner builds against `SoC.v`. If you change the Scala but don’t regenerate, the runner won’t match actual RAM sizing.

---

**3. DUT Runner: Verilator Binary (`borb-sim`)**

**Files:**
- `/Users/mahir/fun/borb/verif/riscof/borb/sim/borb_sim.cpp`
- `/Users/mahir/fun/borb/verif/riscof/borb/sim/Makefile`

**Purpose:**  
This is the actual executable the RISCOF Python plugin runs. It loads a binary into the SoC RAM, runs until `tohost != 0` or timeout, then dumps a signature.

**CLI:**
- `--bin <path>`
- `--sig-begin <hex>`
- `--sig-end <hex>`
- `--tohost <hex>`
- `--signature <path>`
- `--max-cycles <int>` (default 200000)
- `--vcd <path>` (optional)

**Important detail: word‑addressed RAM**  
Your SoC RAM is word‑addressed (8 bytes). The runner maps bytes into the eight byte-lane arrays (`ram_symbol0..7`) like this:

- `word_index = (addr >> 3) & (word_count - 1)`
- `lane = addr & 0x7`
- `ram_symbol<lane>[word_index] = byte`

This matches the way the SoC truncates addresses (64‑bit to 14‑bit in the AXI shim).

**Signature extraction format**  
RISCOF expects one **32‑bit hex word per line**, most‑significant byte on the left. We reconstruct each 32‑bit value from bytes in memory and output like `XXXXXXXX`.

---

**4. DUT Plugin (`riscof_borb.py`)**

File:
- `/Users/mahir/fun/borb/verif/riscof/borb/riscof_borb.py`

**Build step**
- Runs `make -C /Users/mahir/fun/borb/verif/riscof/borb/sim`
- This builds `borb-sim` using Verilator.

**Per‑test run**
1. Compile test to ELF via `riscv64-unknown-elf-gcc`.
2. Convert ELF → BIN via `riscv64-unknown-elf-objcopy -O binary`.
3. Extract symbol addresses from ELF:
   - `begin_signature`
   - `end_signature`
   - `tohost`
4. Invoke `borb-sim` with the addresses and signature output path:
   - Signature file goes to `DUT-borb.signature` in each test’s `dut` directory.

**Why this design:**  
RISCOF expects the Python plugin to be the integration layer. The actual DUT execution can be any binary, as long as it produces the signature file in the right location.

---

**5. Reference Plugin (`riscof_spike.py`)**

File:
- `/Users/mahir/fun/borb/verif/riscof/spike/riscof_spike.py`

**Changes**
- We actually invoke Spike for each test (previously it did nothing).
- `--isa=rv64i_Zicsr_Zifencei`
- `+signature=<path> +signature-granularity=4`
- Optional instruction cap:
  - `max_instructions=<n>` set in config
  - Added to spike command as `--instructions=<n>`

**Why this matters:**  
If Spike never reaches `tohost`, it can run forever. The cap forces it to terminate so it can write a signature.

---

**6. RISCOF Config**

File:
- `/Users/mahir/fun/borb/verif/riscof/config.ini`

Key additions:
- Under `[borb]`:
  - `dut_exe=/Users/mahir/fun/borb/verif/riscof/borb/build/borb-sim`
  - `jobs=1`
- Under `[spike]`:
  - `max_instructions=5000000`

**Why `jobs=1`:**  
Parallel DUT simulations are likely to fight for resources and complicate debugging. RISCOF can parallelize, but start with 1 to be safe.

---

**Data Flow End‑to‑End**

1. RISCOF reads `borb_isa.yaml` + `borb_platform.yaml`.
2. RISCOF selects tests from the suite.
3. For each test, DUT plugin:
   - Compiles test → ELF.
   - Extracts signature + tohost symbols.
   - Converts ELF → BIN.
   - Runs `borb-sim` with those symbols.
   - Writes `DUT-borb.signature`.
4. Reference plugin:
   - Compiles test → ELF.
   - Runs Spike with signature output.
   - Writes `Reference-spike.signature`.
5. RISCOF compares the two signatures line‑by‑line.

---

**Memory Model Clarification (RISCOF)**

RISCOF **does not** provide a memory model or loader for the DUT.  
Your DUT plugin and runner must:
- Load the program into DUT memory.
- Run it.
- Extract the signature.

This is consistent with RISCOF design: the framework only coordinates testing and signature comparison.

---

**Common Issues and Fixes**

**1. YAML validation failures**
- If you see `physical_addr_sz` errors, use `56` for RV64.
- If `misa.reset-val` mismatches, use `0x8000000000000100` for RV64I.

**2. Spike hangs**
- Use `max_instructions` in `config.ini`.
- Confirm Spike produces signature for a single test.

**3. Verilator memory access**
- Use `rootp->SoC__DOT__area_ram__DOT__ram_symbolX`.
- Verilator doesn’t always expose internal memory directly on `VSoC`.

**4. `$readmem` warnings**
- SpinalHDL generated `SoC.v` expects a regfile init bin:
  - `SoC.v_toplevel_area_cpu_coreArea_srcPlugin_regfileread_regfile_mem.bin`
- This is a **warning only**. If you want silence, we can auto-create an empty file per test.

---

**How to Build and Run**

**Build DUT runner**
```bash
make -C /Users/mahir/fun/borb/verif/riscof/borb/sim
```

**Validate YAML**
```bash
riscof validateyaml --config=/Users/mahir/fun/borb/verif/riscof/config.ini
```

**Run full suite**
```bash
riscof run --config=/Users/mahir/fun/borb/verif/riscof/config.ini \
  --suite=/Users/mahir/fun/borb/verif/riscof/riscv-arch-test/riscv-test-suite \
  --env=/Users/mahir/fun/borb/verif/riscof/riscv-arch-test/riscv-test-suite/env
```

**Run one Spike test manually (debug)**
```bash
cd /Users/mahir/fun/borb/verif/riscof/riscof_work/rv64i_m/I/src/add-01.S/ref
spike --isa=rv64i_Zicsr_Zifencei --instructions=1000000 \
  +signature=Reference-spike.signature +signature-granularity=4 ref.elf
```

---

**Mental Model Summary**

- RISCOF orchestrates, but **you** own the DUT runtime and memory model.
- The DUT runner must output a signature in the RISCOF format.
- Spike reference is the source of truth for comparison.
- ISA YAML correctness is critical to picking the right tests.

---

If you want, I can add a concise “quickstart” section or diagrams for the flow.