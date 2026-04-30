# Performance History

Tracking borb performance over time.

| Version | Date (UTC) | Benchmark | Frontend | Cycles | Instret | CPI | IPC | CoreMark/MHz | Delta vs off |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| v0.01 | 2026-04-08 14:05:09 | CoreMark | off | 2480840 | 711030 | 3.489079 | 0.286609 | 4.030893 | 0.00% |
| v0.01 | 2026-04-08 14:05:09 | CoreMark | on | 2286413 | 713155 | 3.206053 | 0.311910 | 4.373663 | 7.84% |
| v0.02 | 2026-04-08 17:34:34 | CoreMark | off | 2480840 | 711030 | 3.489079 | 0.286609 | 4.030893 | 0.00% |
| v0.02 | 2026-04-08 17:34:34 | CoreMark | on | 2229837 | 713155 | 3.126721 | 0.319824 | 4.484633 | 10.12% |
| v0.03 | 2026-04-18 20:27:19 | CoreMark | off | 2700753 | 713156 | 3.787044 | 0.264058 | 3.702671 | 0.00% |
| v0.03 | 2026-04-18 20:27:19 | CoreMark | on | 2708360 | 713156 | 3.797710 | 0.263317 | 3.692271 | -0.28% |
| v0.04 | 2026-04-27 12:55:57 | CoreMark | off | 1780676 | 713174 | 2.496832 | 0.400507 | 5.615845 | 0.00% |
| v0.04 | 2026-04-27 12:55:57 | CoreMark | on | 1783174 | 713174 | 2.500335 | 0.399946 | 5.607978 | -0.14% |
| v0.04 | 2026-04-27 13:09:34 | CoreMark | off | 1780676 | 713174 | 2.496832 | 0.400507 | 5.615845 | 0.00% |
| v0.04 | 2026-04-27 13:09:34 | CoreMark | on | 1780676 | 713174 | 2.496832 | 0.400507 | 5.615845 | 0.00% |
| v0.05 | 2026-04-27 14:20:02 | CoreMark | off | 2096792 | 713163 | 2.940130 | 0.340121 | 4.769190 | 0.00% |
| v0.05 | 2026-04-27 14:28:00 | CoreMark | on | 1780676 | 713174 | 2.496832 | 0.400507 | 5.615845 | 15.08% |

Current supported frontend-on baseline:

- `2026-04-27 22:00:00 UTC`: `1,780,676` cycles, `713,174` instret, `2.496832` CPI, `0.400507` IPC, `5.615845` CoreMark/MHz
- source: `verif/benchmarks/coremark/out/coremark_m8_now_20260427_220000`
- note: borb no longer maintains a supported frontend-disabled mode, so `v0.05` is the last checked-in true on/off pair and remains the historical A/B reference point.
