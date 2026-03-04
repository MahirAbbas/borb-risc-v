.section .text.init
.globl _start
_start:
  la sp, _stack_top
  call main
  mv a0, a0
  call _exit
1:
  j 1b

.section .signature, "aw", @progbits
.align 4
.global begin_signature
begin_signature:
  .word 0
.global end_signature
end_signature:
