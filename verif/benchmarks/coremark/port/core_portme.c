#include "coremark.h"

#if defined(COREMARK_SEED1)
volatile ee_s32 seed1_volatile = COREMARK_SEED1;
#else
#if defined(PROFILE_RUN) && PROFILE_RUN
volatile ee_s32 seed1_volatile = 0x8;
#elif defined(VALIDATION_RUN) && VALIDATION_RUN
volatile ee_s32 seed1_volatile = 0x3415;
#else
volatile ee_s32 seed1_volatile = 0x0;
#endif
#endif

#if defined(COREMARK_SEED2)
volatile ee_s32 seed2_volatile = COREMARK_SEED2;
#else
#if defined(PROFILE_RUN) && PROFILE_RUN
volatile ee_s32 seed2_volatile = 0x8;
#elif defined(VALIDATION_RUN) && VALIDATION_RUN
volatile ee_s32 seed2_volatile = 0x3415;
#else
volatile ee_s32 seed2_volatile = 0x0;
#endif
#endif

#if defined(COREMARK_SEED3)
volatile ee_s32 seed3_volatile = COREMARK_SEED3;
#else
#if defined(PROFILE_RUN) && PROFILE_RUN
volatile ee_s32 seed3_volatile = 0x8;
#elif defined(VALIDATION_RUN) && VALIDATION_RUN
volatile ee_s32 seed3_volatile = 0x66;
#else
volatile ee_s32 seed3_volatile = 0x66;
#endif
#endif

#if defined(COREMARK_SEED4)
volatile ee_s32 seed4_volatile = COREMARK_SEED4;
#else
volatile ee_s32 seed4_volatile = ITERATIONS;
#endif

#if defined(COREMARK_SEED5)
volatile ee_s32 seed5_volatile = COREMARK_SEED5;
#else
volatile ee_s32 seed5_volatile = 0;
#endif
ee_u32 default_num_contexts = 1;

static ee_u64 start_cycles = 0;
static ee_u64 stop_cycles = 0;

static ee_u64 read_cycle(void) {
#if __riscv_xlen == 64
  ee_u64 c;
  __asm__ volatile("rdcycle %0" : "=r"(c));
  return c;
#else
  ee_u32 hi0, lo, hi1;
  do {
    __asm__ volatile("rdcycleh %0" : "=r"(hi0));
    __asm__ volatile("rdcycle %0" : "=r"(lo));
    __asm__ volatile("rdcycleh %0" : "=r"(hi1));
  } while (hi0 != hi1);
  return (((ee_u64)hi1) << 32) | lo;
#endif
}

void start_time(void) {
  start_cycles = read_cycle();
}

void stop_time(void) {
  stop_cycles = read_cycle();
}

CORE_TICKS get_time(void) {
  return stop_cycles - start_cycles;
}

ee_u32 time_in_secs(CORE_TICKS ticks) {
  return (ee_u32)(ticks / EE_TICKS_PER_SEC);
}

void portable_init(core_portable *p, int *argc, char *argv[]) {
  (void)argc;
  (void)argv;
  p->portable_id = 1;
}

void portable_fini(core_portable *p) {
  p->portable_id = 0;
}

void *portable_malloc(ee_size_t size) {
  (void)size;
  return NULL;
}

void portable_free(void *p) {
  (void)p;
}
