# test_add.S
addi x1, x0, 5
addi x2, x0, 10
add  x3, x1, x2     # expect 15
sub  x4, x3, x2     # expect 5
sll  x5, x1, x0     # expect 5 << 0 = 5
xor  x6, x1, x2     # expect 15

