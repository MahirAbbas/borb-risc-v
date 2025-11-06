package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.LsuL1.PC
import borb.LsuL1.JumpCmd
// import borb.dispatch.SrcPlugin.IMMED
import borb.frontend.ExecutionUnitEnum
import borb.LsuL1.Jumper

// case class CtrlHazardThrowPipeline(decodeNode : CtrlLink, hzRange : Seq[CtrlLink], pc : PC) extends Area {
//   val logic = new decodeNode.Area {
//     when(up(borb.decode.Decoder.ALUOP) === AluOp.jal) {
//       decodeNode.haltWhen(pc.jumpCmd.valid && hzRange.map(e => e.up(borb.decode.Decoder.ALUOP) === AluOp.jal).reduce(_ || _))
//       decodeNode.throwWhen(pc.jumpCmd.valid)
//       // when JAL, stall pipeline, update PC, carryone
//     }

//     when(up(borb.decode.Decoder.ALUOP) === AluOp.jalr) {
//       decodeNode.haltWhen(pc.jumpCmd.valid && hzRange.map(e => e.up(borb.decode.Decoder.ALUOP) === AluOp.jalr).reduce(_ || _))
//       decodeNode.throwWhen(pc.jumpCmd.valid)
//       // when JAL, stall pipeline, update PC, carryone
//     }
//   }
// }

// case class Branch(node : CtrlLink, pc : PC) extends Area {
//   import borb.LsuL1.PC._
//
//   val jumpCmd = Flow(borb.LsuL1.JumpCmd())
//
//   pc.jumpCmd << jumpCmd
//
//   // override val FUType = ExecutionUnitEnum.BR
//
//   val SRC1 = borb.dispatch.SrcPlugin.RS1
//   val SRC2 = borb.dispatch.SrcPlugin.RS2
//
//   val doBranch = Bool()
//
//   val logic = new node.Area {
//   }
//
//   // val ctrlHz = CtrlHazardThrowPipeline(node, branchCtrlHzRange, pc)
//
//   val branchlogic = new node.Area {
//     doBranch := False
//     when(up(borb.dispatch.Dispatch.SENDTOBRANCH) === True && up.isFiring) {
//       doBranch := (ALUOP).muxDc(
//         AluOp.beq -> (SRC1.asSInt === SRC2.asSInt),
//         AluOp.bne -> (SRC1.asSInt =/= SRC2.asSInt),
//         AluOp.bge -> (SRC1.asSInt >= SRC2.asSInt),
//         AluOp.bgeu -> (SRC1.asUInt >= SRC2.asUInt),
//         AluOp.blt -> (SRC1.asSInt <= SRC2.asSInt),
//         AluOp.bltu -> (SRC1.asUInt <= SRC2.asUInt),
//       )
//     }
//     when(up(borb.frontend.Decoder.ALUOP) === AluOp.jal || up(borb.frontend.Decoder.ALUOP) === AluOp.jalr) {
//       doBranch := True
//       RESULT := PCPLUS4.asBits
//     }
//     jumpCmd.setIdle()
//     when(doBranch) {
//      jumpCmd.valid := True
//      jumpCmd.payload.address := IMMED.asUInt
//     }
//
//   }
//
//
//
//
// }

