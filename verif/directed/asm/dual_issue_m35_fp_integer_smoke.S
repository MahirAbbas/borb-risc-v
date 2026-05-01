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

  li t1, 0x6000
  csrs mstatus, t1

  li t1, 0x3ff0000000000000
  fmv.d.x f0, t1
  li t1, 0x4000000000000000
  fmv.d.x f1, t1

  # FALU add/sub pipe next to younger integer ALU.
  fadd.d f2, f0, f1
  addi s1, x0, 1
  fmv.x.d t2, f2
  li t3, 0x4008000000000000
  bne t2, t3, fail
  sd s1, 0(t0)

  # FMAC pipe owns multiply as well as fused multiply-add.
  fmul.d f3, f1, f1
  addi s2, x0, 2
  fmv.x.d t2, f3
  li t3, 0x4010000000000000
  bne t2, t3, fail
  sd s2, 8(t0)

  fmadd.d f4, f1, f1, f0
  addi s3, x0, 3
  fmv.x.d t2, f4
  li t3, 0x4014000000000000
  bne t2, t3, fail
  sd s3, 16(t0)

  li t4, 0x35
  sd t4, 24(t0)
  li a0, 1
  la t5, tohost
  sd a0, 0(t5)
1:
  j 1b

fail:
  li t4, 0xbad
  sd t4, 24(t0)
  li a0, 2
  la t5, tohost
  sd a0, 0(t5)
2:
  j 2b
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
