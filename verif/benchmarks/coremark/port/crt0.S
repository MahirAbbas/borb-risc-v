.section .text.init
.globl _start
_start:
  la sp, _stack_top
  la t0, __bss_start
  la t1, __bss_end
  beq t0, t1, 2f
1:
  sd zero, 0(t0)
  addi t0, t0, 8
  bltu t0, t1, 1b
2:
  call main
  mv a0, a0
  call _exit
3:
  j 3b

.section .signature, "aw", @progbits
.align 4
.global begin_signature
begin_signature:
  .word 0
.global end_signature
end_signature:
