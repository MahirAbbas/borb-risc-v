
.section .text
.globl _start
_start:
    addi x1, x0, 256
    addi x2, x0, 170
    slli x3, x2, 8
    or   x4, x2, x3
    addi x5, x0, -1
    sb   x2, 0(x1)
    sh   x4, 2(x1)
    sw   x5, 4(x1)
    sd   x5, 16(x1)
    jal  x0, .

