.option arch, +v
.text
.globl rvv_m26_vset_probe
rvv_m26_vset_probe:
  vsetvli x0, x6, e16, m1, ta, ma
  vsetvli x0, x6, e32, m1, ta, ma
