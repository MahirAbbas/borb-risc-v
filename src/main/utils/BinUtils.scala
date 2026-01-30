package borb.utils

import spinal.core._
import spinal.lib._
import java.nio.file.{Files, Paths}

object BinUtils {
  def loadBin(filename: String): Array[Byte] = {
    Files.readAllBytes(Paths.get(filename))
  }

  def loadToMemory(filename: String, memory: Mem[Bits], offset: BigInt): Unit = {
    val binData = loadBin(filename)
    val widthBytes = memory.wordType.getBitsWidth / 8
    
    // Safety check
    require(memory.wordType.getBitsWidth % 8 == 0, "Memory word width must be byte aligned")
    
    for (i <- binData.indices by widthBytes) {
      var wordValue = BigInt(0)
      for (j <- 0 until widthBytes) {
        if (i + j < binData.length) {
          val byteVal = BigInt(binData(i + j) & 0xFF)
          wordValue = wordValue | (byteVal << (j * 8))
        }
      }
      
      val addr = (offset + i) / widthBytes
      if (addr < memory.wordCount) {
        memory.initBigInt(addr, wordValue)
      } else {
        // Warning or Error? For now silent or print
        // println(f"Warning: Address $addr%x out of bounds for memory loading")
      }
    }
  }
}
