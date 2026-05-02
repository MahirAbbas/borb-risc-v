#ifndef _RVMODEL_MACROS_H
#define _RVMODEL_MACROS_H

#define CLINT_BASE_ADDRESS 0x02000000

#define RVMODEL_DATA_SECTION                                      \
        .pushsection .tohost,"aw",@progbits;                    \
        .align 8; .global tohost; tohost: .dword 0;              \
        .align 8; .global fromhost; fromhost: .dword 0;          \
        .popsection

#define RVMODEL_BOOT

#define RVMODEL_HALT_PASS      \
  li x1, 1                    ;\
  la t0, tohost               ;\
1:                            ;\
  sw x1, 0(t0)                ;\
  sw x0, 4(t0)                ;\
  j 1b

#define RVMODEL_HALT_FAIL      \
  li x1, 3                    ;\
  la t0, tohost               ;\
1:                            ;\
  sw x1, 0(t0)                ;\
  sw x0, 4(t0)                ;\
  j 1b

#define RVMODEL_IO_INIT(_R1, _R2, _R3)

/*
 * Borb ACT4 runs synthesize the RVCP summary in the host-side ELF runner
 * from the self-checking tohost value. Leaving IO empty avoids HTIF console
 * writes being mistaken for test termination by the minimal simulator.
 */
#define RVMODEL_IO_WRITE_STR(_R1, _R2, _R3, _STR_PTR)

#define RVMODEL_ACCESS_FAULT_ADDRESS 0x00000000

#define RVMODEL_INTERRUPT_LATENCY 10
#define RVMODEL_TIMER_INT_SOON_DELAY 100
#define RVMODEL_MTIME_ADDRESS 0x0200BFF8
#define RVMODEL_MTIMECMP_ADDRESS 0x02004000

#define MSIP_ADDRESS (CLINT_BASE_ADDRESS + 0x0)
#define RVMODEL_SET_MEXT_INT(_R1, _R2)
#define RVMODEL_CLR_MEXT_INT(_R1, _R2)
#define RVMODEL_SET_MSW_INT(_R1, _R2) \
  li _R1, 1;                         \
  li _R2, MSIP_ADDRESS;              \
  sw _R1, 0(_R2);
#define RVMODEL_CLR_MSW_INT(_R1, _R2) \
  li _R2, MSIP_ADDRESS;              \
  sw zero, 0(_R2);

#define BORb_SSIP_ADDRESS (CLINT_BASE_ADDRESS + 0xC000)
#define RVMODEL_SET_SEXT_INT(_R1, _R2)
#define RVMODEL_CLR_SEXT_INT(_R1, _R2)
#define RVMODEL_SET_SSW_INT(_R1, _R2) \
  li _R1, 1;                         \
  li _R2, BORb_SSIP_ADDRESS;         \
  sw _R1, 0(_R2);
#define RVMODEL_CLR_SSW_INT(_R1, _R2) \
  li _R2, BORb_SSIP_ADDRESS;         \
  sw zero, 0(_R2);

#endif

