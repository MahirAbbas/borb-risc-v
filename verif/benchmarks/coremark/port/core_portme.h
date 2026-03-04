#ifndef CORE_PORTME_H
#define CORE_PORTME_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef int8_t ee_s8;
typedef uint8_t ee_u8;
typedef int16_t ee_s16;
typedef uint16_t ee_u16;
typedef int32_t ee_s32;
typedef uint32_t ee_u32;
typedef uint64_t ee_u64;
typedef int64_t ee_s64;
typedef uintptr_t ee_ptr_int;
typedef ee_u8 ee_u8_f;
typedef ee_u32 ee_u32_f;
typedef float ee_f32;
typedef double ee_f64;
typedef ee_u64 CORE_TICKS;
typedef ee_f64 secs_ret;

typedef struct {
  ee_u8 portable_id;
} core_portable;

#ifndef COMPILER_VERSION
#define COMPILER_VERSION "riscv-unknown-elf-gcc"
#endif

#ifndef COMPILER_FLAGS
#define COMPILER_FLAGS "-O3"
#endif

#ifndef MEM_LOCATION
#define MEM_LOCATION "STATIC"
#endif

#define HAS_FLOAT 0
#define HAS_TIME_H 0
#define USE_CLOCK 0
#define HAS_STDIO 0
#define HAS_PRINTF 0
#define HAS_ERRNO 0
#define HAS_STDLIB 0
#define HAS_STRING 1

#ifndef COREMARK_CPU_HZ
#define COREMARK_CPU_HZ 100000000ULL
#endif

#define EE_TICKS_PER_SEC COREMARK_CPU_HZ
#define NSECS_PER_SEC 1000000000ULL

#define MAIN_HAS_NOARGC 1
#define MAIN_HAS_NORETURN 0

#define SEED_METHOD SEED_VOLATILE

#define MULTITHREAD 1
#define USE_PTHREAD 0
#define USE_FORK 0
#define USE_SOCKET 0

void start_time(void);
void stop_time(void);
CORE_TICKS get_time(void);
secs_ret time_in_secs(CORE_TICKS ticks);
void portable_init(core_portable *p, int *argc, char *argv[]);
void portable_fini(core_portable *p);

#ifdef __cplusplus
}
#endif

#endif
