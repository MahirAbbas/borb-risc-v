package borb.memory

import spinal.core._
import spinal.lib._

object Pmp {
  def allow(
      cfgBytes: Vec[Bits],
      addrRegs: Vec[Bits],
      implementedEntries: Int,
      addrRaw: UInt,
      priv: UInt,
      needX: Bool,
      needR: Bool,
      needW: Bool,
      accessBytes: UInt,
      prvM: UInt
  ): Bool = {
    val addrLo = addrRaw
    val bytes = accessBytes.max(U(1, accessBytes.getWidth bits)).resized
    val addrHi = addrLo + (bytes - U(1, accessBytes.getWidth bits)).resized
    val hitVec = Vec(Bool(), implementedEntries)
    val permVec = Vec(Bool(), implementedEntries)

    for (i <- 0 until implementedEntries) {
      val cfg = cfgBytes(i)
      val l = cfg(7)
      val a = cfg(4 downto 3)
      val r = cfg(0)
      val w = cfg(1)
      val x = cfg(2)
      val entry = addrRegs(i).asUInt
      val prev = if (i == 0) U(0, entry.getWidth bits) else addrRegs(i - 1).asUInt

      val torLo = prev |<< 2
      val torHiExcl = entry |<< 2
      val na4Lo = entry |<< 2
      val na4Hi = (entry |<< 2) + U(3, entry.getWidth bits)
      val lowestZero = ((~entry) & (entry + U(1, entry.getWidth bits)))
      val napotMask = lowestZero - U(1, entry.getWidth bits)
      val napotBase = (entry & ~napotMask) |<< 2
      val napotSpan = (napotMask |<< 3) | U(7, entry.getWidth bits)
      val napotTop = napotBase + napotSpan

      val hitAny = Bool()
      val fullMatch = Bool()
      hitAny := False
      fullMatch := False
      switch(a) {
        is(B"00") {
          hitAny := False
        }
        is(B"01") {
          val torNonEmpty = torHiExcl =/= U(0, entry.getWidth bits)
          val torHi = torHiExcl - U(1, entry.getWidth bits)
          hitAny := torNonEmpty && (addrLo <= torHi) && (addrHi >= torLo)
          fullMatch := torNonEmpty && (addrLo >= torLo) && (addrHi <= torHi)
        }
        is(B"10") {
          hitAny := (addrLo <= na4Hi) && (addrHi >= na4Lo)
          fullMatch := (addrLo >= na4Lo) && (addrHi <= na4Hi)
        }
        default {
          hitAny := (addrLo <= napotTop) && (addrHi >= napotBase)
          fullMatch := (addrLo >= napotBase) && (addrHi <= napotTop)
        }
      }

      val reqPerm = (!needX || x) && (!needR || r) && (!needW || w)
      val accessOk = fullMatch && reqPerm
      val mPerm = l ? accessOk | True
      val suPerm = accessOk
      val perm = (priv === prvM) ? mPerm | suPerm

      hitVec(i) := hitAny
      permVec(i) := perm
    }

    // PMP priority is lowest-numbered matching entry first. Fold from the
    // highest entry down so entry 0 has the final override.
    var allowExpr: Bool = (priv === prvM)
    for (i <- (implementedEntries - 1) to 0 by -1) {
      allowExpr = Mux(hitVec(i), permVec(i), allowExpr)
    }
    allowExpr
  }
}
