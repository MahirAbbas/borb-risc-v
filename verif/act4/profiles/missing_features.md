# RVA23S64 Missing Feature Report

- Ledger: `rva23s64-profile-ledger`
- Source basis: Repo-local M40 seed derived from agent/plan4.md mandatory feature list and current borb documentation. M44 must attach upstream profile-suite provenance before final signoff.
- Total rows: 71
- Requirement counts: `localized`=1, `mandatory`=67, `optional`=1, `out-of-scope`=2
- Mandatory missing or blocked: 44

| Extension | Owner | Declaration | ACT4 | Directed | Reference | Blocker |
| --- | --- | --- | --- | --- | --- | --- |
| A | atomic-lsu | declared-current | covered | partial | supported | M53 still needs reservation/eventuality stress evidence |
| S | privilege | declared-current | covered | partial | supported | M62 privileged-version lift pending |
| U | privilege | declared-current | covered | partial | supported | M62 privileged-version lift pending |
| Zfhmin | fp | tooling-workaround | partial | covered | supported | riscv-config requires Zfh spelling for current Zfa flow; M57 must resolve true declaration |
| Zic64b | memory-pma-cache | validator-unsupported | missing | partial | blocked | M54/M58 need PMA metadata and profile checker support |
| Ziccif | memory-pma-cache | validator-unsupported | missing | partial | blocked | M54/M58 need cacheability contract tests |
| Ziccrse | memory-pma-cache | validator-unsupported | missing | partial | blocked | M54/M61 need ordering evidence |
| Ziccamoa | memory-pma-cache | validator-unsupported | missing | partial | blocked | M53/M54 need AMO cacheability coverage |
| Zicclsm | memory-pma-cache | validator-unsupported | missing | partial | blocked | M60 needs misaligned-access profile contract |
| Za64rs | atomic-lsu | validator-unsupported | missing | partial | blocked | M53 needs reservation-set proof/tests |
| Zihintntl | decode-hints | missing | missing | missing | unknown | M55 implementation and tests pending |
| Zawrs | decode-hints | validator-unsupported | missing | covered | blocked | M55 needs local profile validation/reference path |
| Zkt | timing-contract | missing | missing | missing | blocked | M55 needs data-independent timing evidence |
| PMA | platform-memory | missing | missing | partial | local-backfill | M58 PMA map not yet formalized |
| PMP | pmp | declared-current | covered | covered | supported | M59 full closure audit pending |
| Sv39 | vm | declared-current | covered | covered | supported | M63 real PTW contract pending |
| Svnapot | vm | declared-current | partial | covered | supported | M64 needs full NAPOT/superpage coverage |
| Svpbmt | vm-pma | validator-unsupported | negative-only | missing | blocked | M65 implementation pending; PBMT bits currently reserved |
| Svinval | vm-tlb | validator-unsupported | missing | covered | blocked | M66 needs stale-TLB/profile coverage |
| Svade | vm | missing | covered | partial | supported | M70 metadata declaration/profile validation pending |
| Sstc | csr-timer | validator-unsupported | missing | covered | blocked | M67 profile declaration/reference path pending |
| Sscofpmf | csr-counters | validator-unsupported | missing | covered | blocked | M68 profile declaration/reference path pending |
| Supm | address-generation | missing | missing | missing | unknown | M69 implementation pending |
| Ssnpm | address-generation | missing | missing | missing | unknown | M69 implementation pending |
| Ssu64xl | csr-privilege | missing | missing | covered | local-backfill | M70 metadata declaration/profile validation pending |
| Ss1p13 | privilege | missing | missing | partial | unknown | M62/M70 full privileged 1.13 metadata and coverage closure pending |
| Svbare | vm | missing | covered | partial | supported | M70 metadata declaration/profile validation pending |
| Ssccptr | vm-pmp | missing | covered | partial | supported | M70 metadata declaration/profile validation pending |
| Sstvecd | csr-privilege | missing | missing | covered | local-backfill | M70 metadata declaration/profile validation pending |
| Sstvala | csr-trap | missing | missing | covered | local-backfill | M70 metadata declaration/profile validation pending |
| Sscounterenw | csr-counters | missing | missing | covered | local-backfill | M70 metadata declaration/profile validation pending |
| V | vector | missing | missing | partial | supported | M71-M80 full base vector implementation/coverage pending |
| Zvfhmin | vector-fp | missing | missing | partial | supported | M81 full legal SEW/LMUL coverage pending |
| Zvbb | vector-alu | missing | missing | partial | supported | M82 full Zvbb coverage pending |
| Zvkt | vector-timing | missing | missing | missing | blocked | M83 constrained-latency timing contract pending |
| H | hypervisor | missing | missing | missing | supported | M84-M88 hypervisor implementation pending |
| Sha | hypervisor | missing | missing | missing | unknown | M89 Sha bundle closure pending |
| Ssstateen | hypervisor-csr | missing | missing | missing | unknown | M89 implementation pending |
| Shcounterenw | hypervisor-csr | missing | missing | missing | unknown | M89 implementation pending |
| Shvstvala | hypervisor-csr | missing | missing | missing | unknown | M89 implementation pending |
| Shtvala | hypervisor-csr | missing | missing | missing | unknown | M89 implementation pending |
| Shvstvecd | hypervisor-csr | missing | missing | missing | unknown | M89 implementation pending |
| Shvsatpa | hypervisor-vm | missing | missing | missing | unknown | M89 implementation pending |
| Shgatpa | hypervisor-vm | missing | missing | missing | unknown | M89 implementation pending |
