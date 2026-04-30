.option arch, +v, +zvbb
.text
.globl rvv_m27_full_probe
rvv_m27_full_probe:
  vle32.v v1, (x6)
  vle32.v v2, (x7)
  vandn.vv v3, v1, v2
  vbrev8.v v4, v3
  vrev8.v v5, v4
  vclz.v v6, v5
  vcpop.v v7, v5
  vror.vi v8, v5, 4
  vse32.v v3, (x28)
  vse32.v v4, (x28)
  vse32.v v5, (x28)
  vse32.v v6, (x28)
  vse32.v v7, (x28)
  vse32.v v8, (x28)
