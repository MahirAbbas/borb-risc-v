.option push
.option norvc

.section .text.init
.globl rvtest_entry_point
rvtest_entry_point:
  la s0, begin_signature

  # ALU + ALU
  addi t0, zero, 5
  addi t1, zero, 7
  addi a0, zero, 5
  bne t0, a0, fail
  addi a0, zero, 7
  bne t1, a0, fail

  # Same-cycle RAW must reject pairing; t3 must see the new t2.
  addi t2, zero, 10
  addi t3, t2, 1
  addi a0, zero, 11
  bne t3, a0, fail

  # Same-cycle WAW must preserve older-then-younger program order.
  addi t4, zero, 1
  addi t4, zero, 2
  addi a0, zero, 2
  bne t4, a0, fail

  # ALU + lane-1 branch redirect.
  addi s1, zero, 0
  beq s1, zero, lane1_taken
  jal zero, fail
lane1_taken:
  addi s2, zero, 3
  addi a0, zero, 3
  bne s2, a0, fail

  # Lane-0 branch not taken + lane-1 ALU.
  addi s3, zero, 0
  bne s3, zero, fail
  addi s4, zero, 44
  addi a0, zero, 44
  bne s4, a0, fail

  # Branch + branch: older branch falls through, younger branch redirects.
  addi s5, zero, 0
  bne s5, zero, fail
  beq s5, zero, branch_branch_taken
  jal zero, fail
branch_branch_taken:
  addi s6, zero, 6
  addi a0, zero, 6
  bne s6, a0, fail

  # Lane-0 taken redirect must squash younger lane-1 integer side effects.
  addi s7, zero, 0
  beq zero, zero, lane0_redirect_taken
  addi s7, zero, 99
  jal zero, fail
lane0_redirect_taken:
  bne s7, zero, fail

  sw t0, 0(s0)
  sw t1, 4(s0)
  sw t3, 8(s0)
  sw t4, 12(s0)
  sw s2, 16(s0)
  sw s4, 20(s0)
  sw s6, 24(s0)
  sw s7, 28(s0)
  addi a0, zero, 1
  la t5, tohost
  sd a0, 0(t5)
1:
  jal zero, 1b

fail:
  sw t0, 0(s0)
  sw t1, 4(s0)
  sw t3, 8(s0)
  sw t4, 12(s0)
  sw s2, 16(s0)
  sw s4, 20(s0)
  sw s6, 24(s0)
  sw s7, 28(s0)
  addi a0, zero, 2
  la t5, tohost
  sd a0, 0(t5)
2:
  jal zero, 2b

.section .tohost, "aw", @progbits
.align 3
.globl tohost
tohost:
  .dword 0

.section .data, "aw", @progbits
.align 4
.globl begin_signature
begin_signature:
  .word 0
  .word 0
  .word 0
  .word 0
  .word 0
  .word 0
  .word 0
  .word 0
.globl end_signature
end_signature:

.option pop
