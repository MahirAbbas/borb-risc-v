.option arch, +v, +zvbb
.text
.globl rvv_m27_probe
rvv_m27_probe:
  vandn.vv v3, v1, v2
  vbrev8.v v4, v3
  vrev8.v v5, v4
  vclz.v v6, v5
  vcpop.v v7, v5
  vror.vi v8, v5, 4
