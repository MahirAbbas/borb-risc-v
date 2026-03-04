#include <stdint.h>

volatile uint64_t tohost __attribute__((section(".tohost"))) = 0;
volatile uint64_t fromhost __attribute__((section(".tohost"))) = 0;

void _exit(int code) {
  // Match RISCOF-like convention: 1 == pass, other non-zero values encode failure.
  tohost = (code == 0) ? 1ULL : ((((uint64_t)(uint32_t)code) << 1) | 1ULL);
  while (1) {
    __asm__ volatile("wfi");
  }
}
