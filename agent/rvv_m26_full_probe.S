.option arch, +v, +zvfhmin
.text
.globl rvv_m26_full_probe
rvv_m26_full_probe:
  vle32.v v1, (x6)
  vle32.v v2, (x7)
  vfadd.vv v3, v1, v2
  vfsub.vv v4, v3, v1
  vse32.v v3, (x28)
  vse32.v v4, (x29)
  vle32.v v6, (x30)
  vfwcvt.f.f.v v5, v6
  vse32.v v5, (x31)
  vfncvt.f.f.w v7, v5
  vse32.v v7, (x5)
