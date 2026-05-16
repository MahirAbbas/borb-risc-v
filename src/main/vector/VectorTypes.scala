package borb.vector

import borb.execute.fpu.FpuAddSub
import spinal.core._
import spinal.lib._

case class VectorConfig(
    elen: Int = 64,
    vlen: Int = 128,
    hartContexts: Int = 4,
    activeHarts: Int = 1,
    xlen: Int = 64,
    addressWidth: Int = 64
) {
  require(elen == 64, "Milestone 21 freezes RVV ELEN at 64")
  require(vlen == 128, "Milestone 21 freezes RVV VLEN at 128")
  require(hartContexts == 4, "Shared vector context file is sized for four harts")
  require(activeHarts == 1, "Only hart 0 is wired in the current single-core design")
  require(xlen == 64, "The current shared-vector boundary is RV64-only")
  require(addressWidth == 64, "The current shared-vector memory boundary is 64-bit addressed")

  def hartIdWidth: Int = log2Up(hartContexts)
  def vectorBytes: Int = vlen / 8
  def vectorByteMaskWidth: Int = vectorBytes
}

object VectorOpClass extends SpinalEnum {
  val Config, Load, Store, Integer, Mask, Permute, Reduction, Float, System, Unknown = newElement()
}

object VectorMemOp extends SpinalEnum {
  val Load, Store = newElement()
}

object VectorExceptionCause extends SpinalEnum {
  val None, IllegalInstruction, VectorDisabled, MemoryMisaligned, MemoryAccessFault, MemoryPageFault = newElement()
}

case class VectorException(cfg: VectorConfig) extends Bundle {
  val valid = Bool()
  val cause = VectorExceptionCause()
  val tval = Bits(cfg.xlen bits)
}

case class VectorHartContext(cfg: VectorConfig) extends Bundle {
  val valid = Bool()
  val hartId = UInt(cfg.hartIdWidth bits)
  val vl = UInt(cfg.xlen bits)
  val vtype = Bits(cfg.xlen bits)
  val vstart = UInt(cfg.xlen bits)
  val vxrm = Bits(2 bits)
  val vxsat = Bool()
  val busy = Bool()
  val dirty = Bool()
}

case class VectorCommand(cfg: VectorConfig) extends Bundle {
  val hartId = UInt(cfg.hartIdWidth bits)
  val pc = UInt(cfg.xlen bits)
  val instruction = Bits(32 bits)
  val opClass = VectorOpClass()
  val rd = UInt(5 bits)
  val rs1 = UInt(5 bits)
  val rs2 = UInt(5 bits)
  val rs3 = UInt(5 bits)
  val intRs1 = Bits(cfg.xlen bits)
  val intRs2 = Bits(cfg.xlen bits)
  val funct3 = Bits(3 bits)
  val funct6 = Bits(6 bits)
  val vm = Bool()
  val context = VectorHartContext(cfg)
}

case class VectorResponse(cfg: VectorConfig) extends Bundle {
  val hartId = UInt(cfg.hartIdWidth bits)
  val instruction = Bits(32 bits)
  val complete = Bool()
  val writesInt = Bool()
  val intRd = UInt(5 bits)
  val intData = Bits(cfg.xlen bits)
  val exception = VectorException(cfg)
}

case class VectorMemReq(cfg: VectorConfig) extends Bundle {
  val hartId = UInt(cfg.hartIdWidth bits)
  val op = VectorMemOp()
  val address = UInt(cfg.addressWidth bits)
  val data = Bits(cfg.vlen bits)
  val mask = Bits(cfg.vectorByteMaskWidth bits)
  val elementBytes = UInt(4 bits)
  val ordered = Bool()
}

case class VectorMemResp(cfg: VectorConfig) extends Bundle {
  val hartId = UInt(cfg.hartIdWidth bits)
  val data = Bits(cfg.vlen bits)
  val exception = VectorException(cfg)
}

object VectorDecode {
  def isVectorInstruction(instruction: Bits): Bool = instruction(6 downto 0) === B"7'b1010111"

  def classify(instruction: Bits): VectorOpClass.C = {
    val opClass = VectorOpClass()
    opClass := VectorOpClass.Unknown
    when(isVectorInstruction(instruction)) {
      switch(instruction(14 downto 12)) {
        is(B"111") { opClass := VectorOpClass.Config }
        is(B"000", B"010", B"011") { opClass := VectorOpClass.Integer }
        is(B"001") { opClass := VectorOpClass.Mask }
        is(B"100") { opClass := VectorOpClass.Float }
        is(B"101") { opClass := VectorOpClass.Permute }
        is(B"110") { opClass := VectorOpClass.Reduction }
      }
    }
    opClass
  }
}

case class DormantSharedVectorEngine(cfg: VectorConfig) extends Component {
  val io = new Bundle {
    val hartContext = out(Vec(VectorHartContext(cfg), cfg.hartContexts))
    val command = slave(Stream(VectorCommand(cfg)))
    val response = master(Stream(VectorResponse(cfg)))
    val memReq = master(Stream(VectorMemReq(cfg)))
    val memResp = slave(Stream(VectorMemResp(cfg)))
    val memoryComplete = out Bool()
    val memoryException = out(VectorException(cfg))
    val memoryFaultElement = out UInt(64 bits)
  }

  val vectorRegs = Vec.fill(cfg.hartContexts, 32)(Reg(Bits(cfg.vlen bits)) init 0)

  for (hart <- 0 until cfg.hartContexts) {
    io.hartContext(hart).assignDontCare()
    io.hartContext(hart).valid := Bool(hart == 0)
    io.hartContext(hart).hartId := U(hart, cfg.hartIdWidth bits)
    io.hartContext(hart).vl := 0
    io.hartContext(hart).vtype := 0
    io.hartContext(hart).vstart := 0
    io.hartContext(hart).vxrm := 0
    io.hartContext(hart).vxsat := False
    io.hartContext(hart).busy := False
    io.hartContext(hart).dirty := False
  }

  val commandInsn = io.command.payload.instruction
  val commandVd = commandInsn(11 downto 7).asUInt
  val commandVs1 = commandInsn(19 downto 15).asUInt
  val commandVs2 = commandInsn(24 downto 20).asUInt
  val commandFunct3 = commandInsn(14 downto 12)
  val commandFunct6 = commandInsn(31 downto 26)
  val commandOpcode = commandInsn(6 downto 0)
  val commandVm = commandInsn(25)
  val commandFire = io.command.fire && (io.command.payload.hartId === U(0, cfg.hartIdWidth bits))
  val hart0Regs = vectorRegs(0)
  val sew32Active = io.command.payload.context.vtype(5 downto 3) === B"010"
  val sew16Active = io.command.payload.context.vtype(5 downto 3) === B"001"

  val isVaddVv = commandFunct6 === B"000000" && commandFunct3 === B"000"
  val isVaddVi = commandFunct6 === B"000000" && commandFunct3 === B"011"
  val isVmvVi = commandFunct6 === B"010111" && commandVm && commandVs2 === 0 && commandFunct3 === B"011"
  val isVmvXs = commandFunct6 === B"010000" && commandVm && commandVs1 === 0 && commandFunct3 === B"010"
  val isVle32 = commandOpcode === B"7'b0000111" && commandFunct6 === B"000000" && commandFunct3 === B"110"
  val isVse32 = commandOpcode === B"7'b0100111" && commandFunct6 === B"000000" && commandFunct3 === B"110"
  val isVslideupVi = commandFunct6 === B"001110" && commandFunct3 === B"011"
  val isVslidedownVi = commandFunct6 === B"001111" && commandFunct3 === B"011"
  val isVrgatherVi = commandFunct6 === B"001100" && commandFunct3 === B"011"
  val isVredsumVs = commandFunct6 === B"000000" && commandFunct3 === B"010"
  val isVfaddVv = commandFunct6 === B"000000" && commandFunct3 === B"001"
  val isVfsubVv = commandFunct6 === B"000010" && commandFunct3 === B"001"
  val isVfwcvtFfv = commandFunct6 === B"010010" && commandVm && commandVs1 === U(12, 5 bits) && commandFunct3 === B"001"
  val isVfncvtFfw = commandFunct6 === B"010010" && commandVm && commandVs1 === U(20, 5 bits) && commandFunct3 === B"001"
  val isVandnVv = commandFunct6 === B"000001" && commandFunct3 === B"000"
  val isVbrev8V = commandFunct6 === B"010010" && commandVm && commandVs1 === U(8, 5 bits) && commandFunct3 === B"010"
  val isVrev8V = commandFunct6 === B"010010" && commandVm && commandVs1 === U(9, 5 bits) && commandFunct3 === B"010"
  val isVclzV = commandFunct6 === B"010010" && commandVm && commandVs1 === U(12, 5 bits) && commandFunct3 === B"010"
  val isVcpopV = commandFunct6 === B"010010" && commandVm && commandVs1 === U(14, 5 bits) && commandFunct3 === B"010"
  val isVrorVi = commandFunct6 === B"010100" && commandFunct3 === B"011"
  val isVectorMemory = isVle32 || isVse32

  val memIdle = U(0, 2 bits)
  val memIssue = U(1, 2 bits)
  val memWait = U(2, 2 bits)
  val memDone = U(3, 2 bits)
  val memState = Reg(UInt(2 bits)) init memIdle
  val memIsStore = Reg(Bool()) init False
  val memRegIndex = Reg(UInt(5 bits)) init 0
  val memBase = Reg(UInt(cfg.addressWidth bits)) init 0
  val memVl = Reg(UInt(3 bits)) init 0
  val memElem = Reg(UInt(2 bits)) init 0
  val memLoadData = Reg(Bits(cfg.vlen bits)) init 0
  val memException = Reg(VectorException(cfg)) init(VectorException(cfg).getZero)
  val memFaultElement = Reg(UInt(64 bits)) init 0

  val memActive = memState =/= memIdle
  io.command.ready := !memActive || (memState === memDone)
  io.memoryComplete := memState === memDone
  io.memoryException := memException
  io.memoryFaultElement := memFaultElement

  val viImm32 = commandVs1.asBits.asSInt.resize(32).asBits
  val viImmUInt = viImm32.asUInt
  val viIndex = commandVs1.resize(3)
  val vdOld = hart0Regs(commandVd)
  val vs1Data = hart0Regs(commandVs1)
  val vs2Data = hart0Regs(commandVs2)
  def vectorElem32(data: Bits, index: UInt): Bits = {
    val out = Bits(32 bits)
    out := 0
    switch(index(1 downto 0)) {
      is(U(0, 2 bits)) { out := data(31 downto 0) }
      is(U(1, 2 bits)) { out := data(63 downto 32) }
      is(U(2, 2 bits)) { out := data(95 downto 64) }
      is(U(3, 2 bits)) { out := data(127 downto 96) }
    }
    out
  }
  def vectorElem16(data: Bits, index: UInt): Bits = {
    val out = Bits(16 bits)
    out := 0
    switch(index(1 downto 0)) {
      is(U(0, 2 bits)) { out := data(15 downto 0) }
      is(U(1, 2 bits)) { out := data(31 downto 16) }
      is(U(2, 2 bits)) { out := data(47 downto 32) }
      is(U(3, 2 bits)) { out := data(63 downto 48) }
    }
    out
  }
  def halfToSingle(data: Bits): Bits = {
    val out = Bits(32 bits)
    val sign = data(15)
    val exp = data(14 downto 10).asUInt
    val frac = data(9 downto 0)
    out := sign.asBits ## B(0, 31 bits)
    when(exp === U(31, 5 bits)) {
      out := sign.asBits ## B(255, 8 bits) ## Mux(frac =/= B(0, 10 bits), B(BigInt("400000", 16), 23 bits), B(0, 23 bits))
    } elsewhen(exp =/= U(0, 5 bits)) {
      out := sign.asBits ## (exp.resize(8) + U(112, 8 bits)).asBits ## frac ## B(0, 13 bits)
    }
    out
  }
  def singleToHalf(data: Bits): Bits = {
    val out = Bits(16 bits)
    val sign = data(31)
    val exp = data(30 downto 23).asUInt
    val frac = data(22 downto 0)
    out := sign.asBits ## B(0, 15 bits)
    when(exp === U(255, 8 bits)) {
      out := sign.asBits ## B(31, 5 bits) ## Mux(frac =/= B(0, 23 bits), B(BigInt("200", 16), 10 bits), B(0, 10 bits))
    } elsewhen(exp >= U(143, 8 bits)) {
      out := sign.asBits ## B(31, 5 bits) ## B(0, 10 bits)
    } elsewhen(exp >= U(113, 8 bits)) {
      out := sign.asBits ## (exp - U(112, 8 bits)).resize(5).asBits ## frac(22 downto 13)
    }
    out
  }
  def reverseBits8(data: Bits): Bits = data(0) ## data(1) ## data(2) ## data(3) ## data(4) ## data(5) ## data(6) ## data(7)
  def brev8Word(data: Bits): Bits = {
    val out = Bits(32 bits)
    out := reverseBits8(data(31 downto 24)) ## reverseBits8(data(23 downto 16)) ## reverseBits8(data(15 downto 8)) ## reverseBits8(data(7 downto 0))
    out
  }
  def rev8Word(data: Bits): Bits = data(7 downto 0) ## data(15 downto 8) ## data(23 downto 16) ## data(31 downto 24)
  def leadingZeroCount32(data: Bits): Bits = {
    val count = UInt(6 bits)
    count := 32
    for(i <- 0 until 32) {
      when(data(i)) {
        count := 31 - i
      }
    }
    count.asBits.resize(32)
  }
  def rotateRight32(data: Bits, amount: UInt): Bits = {
    val amt = amount(4 downto 0)
    ((data.asUInt |>> amt) | (data.asUInt |<< (U(32, 6 bits) - amt.resize(6)).resize(5))).asBits
  }
  val reductionSum32 = (
    vs1Data(31 downto 0).asUInt +
    Mux(io.command.payload.context.vl > U(0, cfg.xlen bits), vs2Data(31 downto 0).asUInt, U(0, 32 bits)) +
    Mux(io.command.payload.context.vl > U(1, cfg.xlen bits), vs2Data(63 downto 32).asUInt, U(0, 32 bits)) +
    Mux(io.command.payload.context.vl > U(2, cfg.xlen bits), vs2Data(95 downto 64).asUInt, U(0, 32 bits)) +
    Mux(io.command.payload.context.vl > U(3, cfg.xlen bits), vs2Data(127 downto 96).asUInt, U(0, 32 bits))
  ).resize(32)
  val vectorResult = Bits(cfg.vlen bits)
  vectorResult := vdOld
  for (elem <- 0 until 4) {
    val lo = elem * 32
    val hi = lo + 31
    val elemActive = U(elem, 64 bits) < io.command.payload.context.vl
    val vs1Elem = vs1Data(hi downto lo).asUInt
    val vs2Elem = vs2Data(hi downto lo).asUInt
    val elemIndex = U(elem, 3 bits)
    val slideDownIndex = elemIndex + viIndex
    val slideUpIndex = elemIndex - viIndex
    val fpAdd = FpuAddSub.addSubS(vs2Data(hi downto lo), vs1Data(hi downto lo), B"000", False)
    val fpSub = FpuAddSub.addSubS(vs2Data(hi downto lo), vs1Data(hi downto lo), B"000", True)
    when(elemActive && sew32Active) {
      when(isVmvVi) {
        vectorResult(hi downto lo) := viImm32
      } elsewhen(isVaddVi) {
        vectorResult(hi downto lo) := (vs2Elem + viImmUInt).asBits
      } elsewhen(isVaddVv) {
        vectorResult(hi downto lo) := (vs2Elem + vs1Elem).asBits
      } elsewhen(isVslideupVi) {
        when(elemIndex >= viIndex) {
          vectorResult(hi downto lo) := vectorElem32(vs2Data, slideUpIndex)
        }
      } elsewhen(isVslidedownVi) {
        vectorResult(hi downto lo) := Mux(
          slideDownIndex.resize(cfg.xlen) < io.command.payload.context.vl,
          vectorElem32(vs2Data, slideDownIndex),
          B(0, 32 bits)
        )
      } elsewhen(isVrgatherVi) {
        vectorResult(hi downto lo) := Mux(
          viIndex.resize(cfg.xlen) < io.command.payload.context.vl,
          vectorElem32(vs2Data, viIndex),
          B(0, 32 bits)
        )
      } elsewhen(isVredsumVs) {
        if (elem == 0) {
          vectorResult(hi downto lo) := reductionSum32.asBits
        }
      } elsewhen(isVfaddVv) {
        vectorResult(hi downto lo) := fpAdd.data
      } elsewhen(isVfsubVv) {
        vectorResult(hi downto lo) := fpSub.data
      } elsewhen(isVandnVv) {
        vectorResult(hi downto lo) := vs2Data(hi downto lo) & ~vs1Data(hi downto lo)
      } elsewhen(isVbrev8V) {
        vectorResult(hi downto lo) := brev8Word(vs2Data(hi downto lo))
      } elsewhen(isVrev8V) {
        vectorResult(hi downto lo) := rev8Word(vs2Data(hi downto lo))
      } elsewhen(isVclzV) {
        vectorResult(hi downto lo) := leadingZeroCount32(vs2Data(hi downto lo))
      } elsewhen(isVcpopV) {
        vectorResult(hi downto lo) := CountOne(vs2Data(hi downto lo)).asBits.resize(32)
      } elsewhen(isVrorVi) {
        vectorResult(hi downto lo) := rotateRight32(vs2Data(hi downto lo), commandVs1)
      }
    }
    val halfLo = elem * 16
    val halfHi = halfLo + 15
    when(elemActive && sew16Active) {
      when(isVfwcvtFfv) {
        vectorResult(hi downto lo) := halfToSingle(vectorElem16(vs2Data, elemIndex))
      } elsewhen(isVfncvtFfw) {
        vectorResult(halfHi downto halfLo) := singleToHalf(vectorElem32(vs2Data, elemIndex))
      }
    }
  }

  val vectorRegisterWrite =
    (sew32Active && (isVmvVi || isVaddVi || isVaddVv || isVslideupVi || isVslidedownVi || isVrgatherVi || isVredsumVs || isVfaddVv || isVfsubVv || isVandnVv || isVbrev8V || isVrev8V || isVclzV || isVcpopV || isVrorVi)) ||
    (sew16Active && (isVfwcvtFfv || isVfncvtFfw))
  when(commandFire && vectorRegisterWrite) {
    hart0Regs(commandVd) := vectorResult
  }

  val memStartVl = UInt(3 bits)
  memStartVl := Mux(io.command.payload.context.vl > U(4, cfg.xlen bits), U(4, 3 bits), io.command.payload.context.vl(2 downto 0))
  val memStartElement = UInt(3 bits)
  memStartElement := Mux(io.command.payload.context.vstart > U(4, cfg.xlen bits), U(4, 3 bits), io.command.payload.context.vstart(2 downto 0))
  val startMemory = io.command.fire &&
    (io.command.payload.hartId === U(0, cfg.hartIdWidth bits)) &&
    (memState === memIdle) &&
    isVectorMemory
  when(startMemory) {
    memIsStore := isVse32
    memRegIndex := commandVd
    memBase := io.command.payload.intRs1.asUInt.resized
    memVl := memStartVl
    memElem := memStartElement(1 downto 0)
    memLoadData := Mux(isVse32, B(0, cfg.vlen bits), hart0Regs(commandVd))
    memException.valid := False
    memException.cause := VectorExceptionCause.None
    memException.tval := 0
    memFaultElement := memStartElement.resized
    memState := Mux((memStartVl === 0) || (memStartElement >= memStartVl), memDone, memIssue)
  }

  val memElemAddress = memBase + (memElem.resize(cfg.addressWidth) |<< 2)
  val memByteOffset = memElemAddress(2 downto 0)
  val memByteShift = memByteOffset.resize(6) |<< 3
  val memElementData = Bits(32 bits)
  memElementData := 0
  switch(memElem) {
    is(U(0, 2 bits)) { memElementData := hart0Regs(memRegIndex)(31 downto 0) }
    is(U(1, 2 bits)) { memElementData := hart0Regs(memRegIndex)(63 downto 32) }
    is(U(2, 2 bits)) { memElementData := hart0Regs(memRegIndex)(95 downto 64) }
    is(U(3, 2 bits)) { memElementData := hart0Regs(memRegIndex)(127 downto 96) }
  }

  val memStoreData64 = memElementData.resize(64) |<< memByteShift
  val memMask8 = B"00001111" |<< memByteOffset
  val memMisaligned = memElemAddress(1 downto 0) =/= 0
  val memElemNext = memElem.resize(3) + U(1, 3 bits)

  io.memReq.valid := memState === memIssue && !memMisaligned
  io.memReq.payload.hartId := 0
  io.memReq.payload.op := Mux(memIsStore, VectorMemOp.Store, VectorMemOp.Load)
  io.memReq.payload.address := memElemAddress
  io.memReq.payload.data := memStoreData64.resize(cfg.vlen)
  io.memReq.payload.mask := memMask8.resize(cfg.vectorByteMaskWidth)
  io.memReq.payload.elementBytes := 4
  io.memReq.payload.ordered := True
  io.memResp.ready := True

  when(memState === memIssue) {
    when(memMisaligned) {
      memException.valid := True
      memException.cause := VectorExceptionCause.MemoryMisaligned
      memException.tval := memElemAddress.asBits
      memFaultElement := memElem.resized
      memState := memDone
    } elsewhen(io.memReq.fire) {
      when(memIsStore) {
        when(memElemNext >= memVl) {
          memState := memDone
        } otherwise {
          memElem := memElem + 1
        }
      } otherwise {
        memState := memWait
      }
    }
  }

  when(memState === memWait && io.memResp.valid) {
    val loadWord = (io.memResp.payload.data(63 downto 0) >> memByteShift)(31 downto 0)
    when(io.memResp.payload.exception.valid) {
      memException := io.memResp.payload.exception
      memFaultElement := memElem.resized
      memState := memDone
    } otherwise {
      switch(memElem) {
        is(U(0, 2 bits)) { memLoadData(31 downto 0) := loadWord }
        is(U(1, 2 bits)) { memLoadData(63 downto 32) := loadWord }
        is(U(2, 2 bits)) { memLoadData(95 downto 64) := loadWord }
        is(U(3, 2 bits)) { memLoadData(127 downto 96) := loadWord }
      }
      when(memElemNext >= memVl) {
        memState := memDone
      } otherwise {
        memElem := memElem + 1
        memState := memIssue
      }
    }
  }

  when(memState === memDone && commandFire) {
    when(!memIsStore && !memException.valid) {
      hart0Regs(memRegIndex) := memLoadData
    }
    memState := memIdle
  }

  io.response.valid := io.command.valid && (io.command.payload.hartId === U(0, cfg.hartIdWidth bits)) && sew32Active && isVmvXs
  io.response.payload.hartId := 0
  io.response.payload.instruction := commandInsn
  io.response.payload.complete := io.response.valid
  io.response.payload.writesInt := io.response.valid
  io.response.payload.intRd := commandVd
  io.response.payload.intData := hart0Regs(commandVs2)(31 downto 0).asSInt.resize(cfg.xlen).asBits
  io.response.payload.exception.valid := False
  io.response.payload.exception.cause := VectorExceptionCause.None
  io.response.payload.exception.tval := 0

}
