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

  la t2, trap_handler
  csrw mtvec, t2

  # These adjacent ALU ops are pairable and should retire together in order.
  addi s1, x0, 0x11
  addi s2, x0, 0x22
  sd s1, 0(t0)
  sd s2, 8(t0)

  ecall

  # A precise trap must not fall through here.
  li t1, 0xdeadbeef
  sd t1, 16(t0)
  li a0, 3
  la t5, tohost
  sd a0, 0(t5)
1:
  j 1b

trap_handler:
  csrr t3, mcause
  li t4, 11
  bne t3, t4, fail
  ld t3, 0(t0)
  li t4, 0x11
  bne t3, t4, fail
  ld t3, 8(t0)
  li t4, 0x22
  bne t3, t4, fail
  ld t3, 16(t0)
  bne t3, x0, fail
  li t1, 0x3333
  sd t1, 24(t0)
  li a0, 1
  la t5, tohost
  sd a0, 0(t5)
2:
  j 2b

fail:
  li t1, 0xbad
  sd t1, 16(t0)
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
.globl end_signature
end_signature:
