.section .text.init
.globl rvtest_entry_point
rvtest_entry_point:
  .option push
  .option norvc
  la t0, begin_signature
  sd x0, 0(t0)
  sd x0, 8(t0)
  sd x0, 16(t0)
  sd x0, 24(t0)
  sd x0, 32(t0)
  sd x0, 40(t0)

  la t1, data_block
  li t2, 0x111
  sd t2, 0(t1)
  li t2, 0x222
  sd t2, 8(t1)

  la t3, trap_handler
  csrw mtvec, t3

  # Older load plus younger independent ALU may issue together; the younger
  # side waits for ordered memory retirement before committing.
  ld s1, 0(t1)
  addi s2, x0, 0x33
  sd s1, 0(t0)
  sd s2, 8(t0)

  # Older store plus younger ALU uses the same ordered handoff.
  li s3, 0x444
  sd s3, 8(t1)
  addi s4, x0, 0x55
  ld s5, 8(t1)
  sd s4, 16(t0)
  sd s5, 24(t0)

  # Adjacent memory operations must remain scalarized onto the single LSU.
  ld s6, 0(t1)
  ld s7, 8(t1)
  add s8, s6, s7
  sd s8, 32(t0)

  # AMO stays on the memory pipe while independent scalar work remains ordered.
  li t4, 1
  amoadd.d s9, t4, (t1)
  addi s10, x0, 0x66
  ld t5, 0(t1)
  add s10, s10, t5

  # The older memory trap must kill the paired younger ALU side effect.
  addi s11, x0, 0
  ld t6, 1(t1)
  addi s11, x0, 0x77

  # Must never reach here.
  li a0, 3
  la t5, tohost
  sd a0, 0(t5)
1:
  j 1b

trap_handler:
  csrr t3, mcause
  li t4, 4
  bne t3, t4, fail
  bne s11, x0, fail

  ld t3, 0(t0)
  li t4, 0x111
  bne t3, t4, fail
  ld t3, 8(t0)
  li t4, 0x33
  bne t3, t4, fail
  ld t3, 16(t0)
  li t4, 0x55
  bne t3, t4, fail
  ld t3, 24(t0)
  li t4, 0x444
  bne t3, t4, fail
  ld t3, 32(t0)
  li t4, 0x555
  bne t3, t4, fail

  li a0, 1
  la t5, tohost
  sd a0, 0(t5)
2:
  j 2b

fail:
  li t3, 0xbad
  sd t3, 40(t0)
  li a0, 2
  la t5, tohost
  sd a0, 0(t5)
3:
  j 3b
  .option pop

.section .tohost, "aw", @progbits
.align 3
.globl tohost
tohost:
  .dword 0

.section .data, "aw", @progbits
.align 4
.globl begin_signature
begin_signature:
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
.globl end_signature
end_signature:

.align 3
data_block:
  .dword 0
  .dword 0
