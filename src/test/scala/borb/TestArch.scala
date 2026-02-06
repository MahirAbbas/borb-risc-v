package borb


import spinal.core._
import spinal.core.sim._
import borb.utils.BinUtils

object TestArch {
  def main(args: Array[String]): Unit = {
    println("Starting Arch Test Execution...")
    
    val compiledTestPath = "verif/target/borb/test.elf"
    val compiledBinPath = "verif/target/borb/test.elf.bin"
    
    // Get signature range from ELF
    val beginSig = BinUtils.getSymbolAddress(compiledTestPath, "begin_signature")
    val endSig = BinUtils.getSymbolAddress(compiledTestPath, "end_signature")
    
    println(f"Signature Range: 0x$beginSig%x - 0x$endSig%x")

    SimConfig.withWave.compile(SoC()).doSim { dut =>
      dut.io.clk.simPublic()
      dut.io.reset.simPublic()
      
      dut.clockDomain.forkStimulus(10)
      
      // Load Binary into RAM
      val ramMem = dut.area.ram.ram
      BinUtils.loadToMemory(compiledBinPath, ramMem, 0)
      
      var cycle = 0
      var signatureComplete = false
      
      // Run for enough cycles
      while(cycle < 20000 && !signatureComplete) {
        dut.clockDomain.waitSampling()
        cycle += 1
      }
      
      println(s"Simulation finished after $cycle cycles.")
      
      // Dump Signature to file
      val sigSize = endSig - beginSig
      val sigBytes = new Array[Byte](sigSize.toInt)
      val pw = new java.io.PrintWriter(new java.io.File("verif/target/borb/test.signature"))
      
      try {
        // Read memory 128-bits at a time (2 words) to match Spike format
        // Spike format: [Offset+8][Offset+0]
        for(addr <- beginSig until endSig by 16) {
           val wordIndex0 = addr.toLong / 8
           val wordIndex1 = (addr.toLong + 8) / 8
           
           val word0 = ramMem.getBigInt(wordIndex0)
           // Check if word1 is within bounds (signature size might not be multiple of 16)
           val word1 = if (addr + 8 < endSig) ramMem.getBigInt(wordIndex1) else BigInt(0)
           
           val hexStr = f"$word1%016x$word0%016x"
           pw.println(hexStr)
        }
      } finally {
        pw.close()
      }
      println("Signature dumped to verif/target/borb/test.signature")
    }
  }
}
