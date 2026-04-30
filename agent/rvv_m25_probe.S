.option norvc
.text
  vslideup.vi v2, v1, 1
  vslidedown.vi v3, v2, 1
  vrgather.vi v4, v3, 2
  vmv.v.i v5, 0
  vredsum.vs v5, v4, v5
  vmv.x.s x5, v5
  vse32.v v4, (x7)
