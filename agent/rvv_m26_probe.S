.option arch, +v, +zvfhmin
.text
.globl rvv_m26_probe
rvv_m26_probe:
  vfadd.vv v3, v1, v2
  vfsub.vv v4, v3, v1
  vfwcvt.f.f.v v5, v6
  vfncvt.f.f.w v7, v8
