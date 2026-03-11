package borb.memory

import spinal.core._

object Sv39 {
  val ModeBareValue = 0
  val ModeSv39Value = 8

  def modeBare: UInt = U(ModeBareValue, 4 bits)
  def modeSv39: UInt = U(ModeSv39Value, 4 bits)

  def modeOf(satp: Bits): UInt = satp(63 downto 60).asUInt
  def asidOf(satp: Bits): UInt = satp(59 downto 44).asUInt
  def ppnOf(satp: Bits): UInt = satp(43 downto 0).asUInt
  def isBare(satp: Bits): Bool = modeOf(satp) === modeBare
  def isSv39(satp: Bits): Bool = modeOf(satp) === modeSv39
}

case class VmContext() extends Bundle {
  val currentPriv = UInt(2 bits)
  val dataPriv = UInt(2 bits)
  val mprv = Bool()
  val mxr = Bool()
  val sum = Bool()
  val tvm = Bool()
  val sbe = Bool()
  val satp = Bits(64 bits)
  val satpMode = UInt(4 bits)
  val satpAsid = UInt(16 bits)
  val satpPpn = UInt(44 bits)
}

case class TlbReq(vpnWidth: Int, tagWidth: Int) extends Bundle {
  val valid = Bool()
  val vpn = UInt(vpnWidth bits)
  val asid = UInt(tagWidth bits)
  val isFetch = Bool()
  val isStore = Bool()
}

case class TlbRsp(ppnWidth: Int) extends Bundle {
  val hit = Bool()
  val ppn = UInt(ppnWidth bits)
  val allowRead = Bool()
  val allowWrite = Bool()
  val allowExecute = Bool()
  val user = Bool()
  val global = Bool()
  val accessed = Bool()
  val dirty = Bool()
  val pageFault = Bool()
}

case class PageWalkReq() extends Bundle {
  val valid = Bool()
  val va = UInt(64 bits)
  val accessIsFetch = Bool()
  val accessIsStore = Bool()
  val priv = UInt(2 bits)
  val satp = Bits(64 bits)
}

case class PageWalkRsp() extends Bundle {
  val valid = Bool()
  val pa = UInt(64 bits)
  val pageFault = Bool()
  val accessFault = Bool()
  val leafLevel = UInt(2 bits)
  val pte = Bits(64 bits)
}
