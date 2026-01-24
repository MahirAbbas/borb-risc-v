// Generator : SpinalHDL v1.12.3    git head : 591e64062329e5e2e2b81f4d52422948053edb97
// Component : CPU
// Git hash  : 7a15d122fe3bad2a67424c6d3a9b5785e058f0df

`timescale 1ns/1ps

module CPU (
  input  wire          io_clk,
  input  wire          io_clkEnable,
  input  wire          io_reset,
  output wire          io_iBus_cmd_valid,
  input  wire          io_iBus_cmd_ready,
  output wire [63:0]   io_iBus_cmd_payload_address,
  output wire [15:0]   io_iBus_cmd_payload_id,
  input  wire          io_iBus_rsp_valid,
  input  wire [63:0]   io_iBus_rsp_payload_data,
  input  wire [63:0]   io_iBus_rsp_payload_address,
  input  wire [15:0]   io_iBus_rsp_payload_id,
  output wire          io_dBus_cmd_valid,
  input  wire          io_dBus_cmd_ready,
  output wire [63:0]   io_dBus_cmd_payload_address,
  output wire [63:0]   io_dBus_cmd_payload_data,
  output wire [7:0]    io_dBus_cmd_payload_mask,
  output wire [15:0]   io_dBus_cmd_payload_id,
  output wire          io_dBus_cmd_payload_write,
  input  wire          io_dBus_rsp_valid,
  input  wire [63:0]   io_dBus_rsp_payload_data,
  input  wire [15:0]   io_dBus_rsp_payload_id,
  output wire          io_rvfi_valid,
  output wire [63:0]   io_rvfi_order,
  output wire [31:0]   io_rvfi_insn,
  output wire          io_rvfi_trap,
  output wire          io_rvfi_halt,
  output wire          io_rvfi_intr,
  output wire [1:0]    io_rvfi_mode,
  output wire [1:0]    io_rvfi_ixl,
  output wire [4:0]    io_rvfi_rs1_addr,
  output wire [4:0]    io_rvfi_rs2_addr,
  output wire [63:0]   io_rvfi_rs1_rdata,
  output wire [63:0]   io_rvfi_rs2_rdata,
  output wire [4:0]    io_rvfi_rd_addr,
  output wire [63:0]   io_rvfi_rd_wdata,
  output wire [63:0]   io_rvfi_pc_rdata,
  output wire [63:0]   io_rvfi_pc_wdata,
  output wire [63:0]   io_rvfi_mem_addr,
  output wire [7:0]    io_rvfi_mem_rmask,
  output wire [7:0]    io_rvfi_mem_wmask,
  output wire [63:0]   io_rvfi_mem_rdata,
  output wire [63:0]   io_rvfi_mem_wdata,
  output wire          io_dbg_commitValid,
  output wire [63:0]   io_dbg_commitPc,
  output wire [31:0]   io_dbg_commitInsn,
  output wire [4:0]    io_dbg_commitRd,
  output wire          io_dbg_commitWe,
  output wire [63:0]   io_dbg_commitWdata,
  output wire          io_dbg_squashed,
  output wire [63:0]   io_dbg_f_pc,
  output wire [63:0]   io_dbg_d_pc,
  output wire [63:0]   io_dbg_x_pc,
  output wire [63:0]   io_dbg_wb_pc,
  output wire [63:0]   io_perf_cycles,
  output wire [63:0]   io_perf_instret,
  output wire [63:0]   io_perf_stallsHazard,
  output wire [63:0]   io_perf_stallsFetch,
  output wire [63:0]   io_perf_stallsMem,
  output wire [63:0]   io_perf_branches,
  output wire [63:0]   io_perf_branchesTaken,
  output wire [63:0]   io_perf_flushes,
  (* keep , syn_keep *) output wire          coreArea_rvfiPlugin_io_rvfi_valid /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_order /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [31:0]   coreArea_rvfiPlugin_io_rvfi_insn /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire          coreArea_rvfiPlugin_io_rvfi_trap /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire          coreArea_rvfiPlugin_io_rvfi_halt /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire          coreArea_rvfiPlugin_io_rvfi_intr /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [1:0]    coreArea_rvfiPlugin_io_rvfi_mode /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [1:0]    coreArea_rvfiPlugin_io_rvfi_ixl /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [4:0]    coreArea_rvfiPlugin_io_rvfi_rs1_addr /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [4:0]    coreArea_rvfiPlugin_io_rvfi_rs2_addr /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_rs1_rdata /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_rs2_rdata /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [4:0]    coreArea_rvfiPlugin_io_rvfi_rd_addr /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_rd_wdata /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_pc_rdata /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_pc_wdata /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_mem_addr /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [7:0]    coreArea_rvfiPlugin_io_rvfi_mem_rmask /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [7:0]    coreArea_rvfiPlugin_io_rvfi_mem_wmask /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_mem_rdata /* synthesis syn_keep = 1 */ ,
  (* keep , syn_keep *) output wire [63:0]   coreArea_rvfiPlugin_io_rvfi_mem_wdata /* synthesis syn_keep = 1 */ ,
  output wire          coreArea_debugPlugin_io_dbg_commitValid,
  output wire [63:0]   coreArea_debugPlugin_io_dbg_commitPc,
  output wire [31:0]   coreArea_debugPlugin_io_dbg_commitInsn,
  output wire [4:0]    coreArea_debugPlugin_io_dbg_commitRd,
  output wire          coreArea_debugPlugin_io_dbg_commitWe,
  output wire [63:0]   coreArea_debugPlugin_io_dbg_commitWdata,
  output wire          coreArea_debugPlugin_io_dbg_squashed,
  output wire [63:0]   coreArea_debugPlugin_io_dbg_f_pc,
  output wire [63:0]   coreArea_debugPlugin_io_dbg_d_pc,
  output wire [63:0]   coreArea_debugPlugin_io_dbg_x_pc,
  output wire [63:0]   coreArea_debugPlugin_io_dbg_wb_pc
);
  localparam MicroCode_uopNOP = 6'd0;
  localparam MicroCode_uopLUI = 6'd1;
  localparam MicroCode_uopAUIPC = 6'd2;
  localparam MicroCode_uopJAL = 6'd3;
  localparam MicroCode_uopJALR = 6'd4;
  localparam MicroCode_uopBEQ = 6'd5;
  localparam MicroCode_uopBNE = 6'd6;
  localparam MicroCode_uopBLT = 6'd7;
  localparam MicroCode_uopBGE = 6'd8;
  localparam MicroCode_uopBLTU = 6'd9;
  localparam MicroCode_uopBGEU = 6'd10;
  localparam MicroCode_uopLB = 6'd11;
  localparam MicroCode_uopLH = 6'd12;
  localparam MicroCode_uopLW = 6'd13;
  localparam MicroCode_uopLBU = 6'd14;
  localparam MicroCode_uopLHU = 6'd15;
  localparam MicroCode_uopSB = 6'd16;
  localparam MicroCode_uopSH = 6'd17;
  localparam MicroCode_uopSW = 6'd18;
  localparam MicroCode_uopADDI = 6'd19;
  localparam MicroCode_uopSLTI = 6'd20;
  localparam MicroCode_uopSLTIU = 6'd21;
  localparam MicroCode_uopXORI = 6'd22;
  localparam MicroCode_uopORI = 6'd23;
  localparam MicroCode_uopANDI = 6'd24;
  localparam MicroCode_uopSLLI = 6'd25;
  localparam MicroCode_uopSRLI = 6'd26;
  localparam MicroCode_uopSRAI = 6'd27;
  localparam MicroCode_uopADD = 6'd28;
  localparam MicroCode_uopSUB = 6'd29;
  localparam MicroCode_uopSLL = 6'd30;
  localparam MicroCode_uopSLT = 6'd31;
  localparam MicroCode_uopSLTU = 6'd32;
  localparam MicroCode_uopXOR = 6'd33;
  localparam MicroCode_uopSRL = 6'd34;
  localparam MicroCode_uopSRA = 6'd35;
  localparam MicroCode_uopOR = 6'd36;
  localparam MicroCode_uopAND = 6'd37;
  localparam MicroCode_uopFENCE = 6'd38;
  localparam MicroCode_uopFENCE_I = 6'd39;
  localparam MicroCode_uopECALL = 6'd40;
  localparam MicroCode_uopEBREAK = 6'd41;
  localparam MicroCode_uopCSRRW = 6'd42;
  localparam MicroCode_uopCSRRS = 6'd43;
  localparam MicroCode_uopCSRRC = 6'd44;
  localparam MicroCode_uopCSRRWI = 6'd45;
  localparam MicroCode_uopCSRRSI = 6'd46;
  localparam MicroCode_uopCSRRCI = 6'd47;
  localparam MicroCode_uopLWU = 6'd48;
  localparam MicroCode_uopLD = 6'd49;
  localparam MicroCode_uopSD = 6'd50;
  localparam MicroCode_uopADDIW = 6'd51;
  localparam MicroCode_uopSLLIW = 6'd52;
  localparam MicroCode_uopSRLIW = 6'd53;
  localparam MicroCode_uopSRAIW = 6'd54;
  localparam MicroCode_uopADDW = 6'd55;
  localparam MicroCode_uopSUBW = 6'd56;
  localparam MicroCode_uopSLLW = 6'd57;
  localparam MicroCode_uopSRLW = 6'd58;
  localparam MicroCode_uopSRAW = 6'd59;
  localparam YESNO_Y = 1'd0;
  localparam YESNO_N = 1'd1;
  localparam Imm_Select_N_IMM = 3'd0;
  localparam Imm_Select_I_IMM = 3'd1;
  localparam Imm_Select_S_IMM = 3'd2;
  localparam Imm_Select_B_IMM = 3'd3;
  localparam Imm_Select_U_IMM = 3'd4;
  localparam Imm_Select_J_IMM = 3'd5;
  localparam RSTYPE_RS_INT = 3'd0;
  localparam RSTYPE_RS_FP = 3'd1;
  localparam RSTYPE_RS_VEC = 3'd2;
  localparam RSTYPE_IMMED = 3'd3;
  localparam RSTYPE_RS_NA = 3'd4;
  localparam ExecutionUnitEnum_ALU = 3'd0;
  localparam ExecutionUnitEnum_FPU = 3'd1;
  localparam ExecutionUnitEnum_AGU = 3'd2;
  localparam ExecutionUnitEnum_BR = 3'd3;
  localparam ExecutionUnitEnum_NA = 3'd4;
  localparam RDTYPE_RD_INT = 2'd0;
  localparam RDTYPE_RD_FP = 2'd1;
  localparam RDTYPE_RD_VEC = 2'd2;
  localparam RDTYPE_RD_NA = 2'd3;

  wire                coreArea_fetch_fifo_io_pop_ready;
  wire                coreArea_srcPlugin_regfileread_regfile_io_writes_0_valid;
  wire                coreArea_fetch_fifo_io_push_ready;
  wire                coreArea_fetch_fifo_io_pop_valid;
  wire       [63:0]   coreArea_fetch_fifo_io_pop_payload_data;
  wire       [15:0]   coreArea_fetch_fifo_io_pop_payload_epoch;
  wire       [1:0]    coreArea_fetch_fifo_io_occupancy;
  wire       [1:0]    coreArea_fetch_fifo_io_availability;
  wire       [63:0]   coreArea_srcPlugin_regfileread_regfile_io_reads_0_data;
  wire       [63:0]   coreArea_srcPlugin_regfileread_regfile_io_reads_1_data;
  wire       [3:0]    _zz_coreArea_fetch_inflight;
  wire       [3:0]    _zz_coreArea_fetch_inflight_1;
  wire       [0:0]    _zz_coreArea_fetch_inflight_2;
  wire       [3:0]    _zz_coreArea_fetch_inflight_3;
  wire       [0:0]    _zz_coreArea_fetch_inflight_4;
  wire       [3:0]    _zz_coreArea_fetch_io_readCmd_cmd_valid;
  wire       [31:0]   _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID;
  wire       [31:0]   _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_1;
  wire       [31:0]   _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_2;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_3;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_4;
  wire       [9:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_5;
  wire       [31:0]   _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_6;
  wire       [31:0]   _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_7;
  wire       [31:0]   _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_8;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_9;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_10;
  wire       [3:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_11;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_1;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_2;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_3;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_4;
  wire       [1:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_5;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_6;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_7;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_8;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_9;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_10;
  wire       [4:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_11;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_12;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_13;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_14;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_15;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_16;
  wire       [2:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_17;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_18;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_19;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_20;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_21;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_22;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_23;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_24;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_25;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_26;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_27;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_28;
  wire       [8:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_29;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_30;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_31;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_32;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_33;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_34;
  wire       [6:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_35;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_36;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_37;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_38;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_39;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_40;
  wire       [4:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_41;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_42;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_43;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_44;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_45;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_46;
  wire       [2:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_47;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_48;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_49;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_50;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_51;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_52;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_53;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_54;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_55;
  wire       [10:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_56;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_57;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_58;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_59;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_60;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_61;
  wire       [8:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_62;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_63;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_64;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_65;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_66;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_67;
  wire       [6:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_68;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_69;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_70;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_71;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_72;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_73;
  wire       [4:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_74;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_75;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_76;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_77;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_78;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_79;
  wire       [2:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_80;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_81;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_82;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_83;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_84;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_85;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_86;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_87;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_88;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_89;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_90;
  wire       [12:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_91;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_92;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_93;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_94;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_95;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_96;
  wire       [10:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_97;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_98;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_99;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_100;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_101;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_102;
  wire       [8:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_103;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_104;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_105;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_106;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_107;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_108;
  wire       [6:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_109;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_110;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_111;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_112;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_113;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_114;
  wire       [4:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_115;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_116;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_117;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_118;
  wire       [2:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_119;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_120;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_121;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_122;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_123;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_124;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_125;
  wire       [16:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_126;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_127;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_128;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_129;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_130;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_131;
  wire       [14:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_132;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_133;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_134;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_135;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_136;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_137;
  wire       [12:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_138;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_139;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_140;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_141;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_142;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_143;
  wire       [10:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_144;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_145;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_146;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_147;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_148;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_149;
  wire       [8:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_150;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_151;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_152;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_153;
  wire       [6:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_154;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_155;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_156;
  wire                _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_157;
  wire       [0:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_158;
  wire       [3:0]    _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_159;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_160;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_161;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_162;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_163;
  wire       [31:0]   _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_164;
  wire       [2:0]    _zz_coreArea_dispatcher_hcs_init_valueNext;
  wire       [0:0]    _zz_coreArea_dispatcher_hcs_init_valueNext_1;
  wire       [31:0]   _zz__zz_coreArea_srcPlugin_immsel_sext;
  wire       [11:0]   _zz__zz_coreArea_srcPlugin_immsel_sext_1;
  wire       [11:0]   _zz__zz_coreArea_srcPlugin_immsel_sext_1_1;
  wire       [12:0]   _zz__zz_coreArea_srcPlugin_immsel_sext_1_2;
  wire       [20:0]   _zz__zz_coreArea_srcPlugin_immsel_sext_1_3;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_1;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_2;
  wire       [0:0]    _zz__zz_IntAlu_aluNodeStage_result_3;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_4;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_5;
  wire       [0:0]    _zz__zz_IntAlu_aluNodeStage_result_6;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_7;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_8;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_9;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_10;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_11;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_12;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_13;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_14;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_15;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_16;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_17;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_18;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_19;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_20;
  wire       [0:0]    _zz__zz_IntAlu_aluNodeStage_result_21;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_22;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_23;
  wire       [0:0]    _zz__zz_IntAlu_aluNodeStage_result_24;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_25;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_26;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_27;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_28;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_29;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_30;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_31;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_32;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_33;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_34;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_35;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_36;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_37;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_38;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_39;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_40;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_41;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_42;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_43;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_44;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_45;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_46;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_47;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_48;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_49;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_50;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_51;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_52;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_53;
  wire       [31:0]   _zz__zz_IntAlu_aluNodeStage_result_54;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_55;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_56;
  wire       [63:0]   _zz__zz_IntAlu_aluNodeStage_result_57;
  wire       [63:0]   _zz_coreArea_branch_logic_target;
  wire       [63:0]   _zz_coreArea_branch_logic_target_1;
  wire       [63:0]   _zz_coreArea_branch_logic_target_2;
  wire       [63:0]   _zz_coreArea_branch_logic_target_3;
  wire       [63:0]   _zz_coreArea_branch_logic_target_4;
  wire       [63:0]   _zz_coreArea_branch_logic_target_5;
  wire       [63:0]   _zz_coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data;
  wire       [63:0]   _zz_coreArea_lsu_logic_effectiveAddr;
  wire       [63:0]   _zz_coreArea_lsu_logic_effectiveAddr_1;
  wire       [63:0]   _zz_coreArea_lsu_logic_effectiveAddr_2;
  wire       [7:0]    _zz__zz_coreArea_lsu_logic_rawStoreData;
  wire       [15:0]   _zz__zz_coreArea_lsu_logic_rawStoreData_1;
  wire       [31:0]   _zz__zz_coreArea_lsu_logic_rawStoreData_2;
  wire       [5:0]    _zz_coreArea_lsu_logic_storeData;
  wire       [5:0]    _zz_coreArea_lsu_logic_shiftedLoadData;
  wire       [63:0]   _zz__zz_coreArea_lsu_logic_loadResult;
  wire       [7:0]    _zz__zz_coreArea_lsu_logic_loadResult_1;
  wire       [7:0]    _zz__zz_coreArea_lsu_logic_loadResult_2;
  wire       [63:0]   _zz__zz_coreArea_lsu_logic_loadResult_3;
  wire       [15:0]   _zz__zz_coreArea_lsu_logic_loadResult_4;
  wire       [15:0]   _zz__zz_coreArea_lsu_logic_loadResult_5;
  wire       [63:0]   _zz__zz_coreArea_lsu_logic_loadResult_6;
  wire       [31:0]   _zz__zz_coreArea_lsu_logic_loadResult_7;
  wire       [31:0]   _zz__zz_coreArea_lsu_logic_loadResult_8;
  wire       [63:0]   _zz_coreArea_rvfiPlugin_io_rvfi_pc_wdata;
  wire                coreArea_pipeline_ctrl_7_down_isValid;
  wire                coreArea_pipeline_ctrl_4_up_isCancel;
  wire                coreArea_pipeline_ctrl_4_up_isReady;
  wire                coreArea_pipeline_ctrl_7_down_isReady;
  wire                coreArea_pipeline_ctrl_6_down_Dispatch_SENDTOALU;
  wire                coreArea_pipeline_ctrl_6_down_Common_LANE_SEL;
  wire       [4:0]    coreArea_pipeline_ctrl_6_down_Decoder_RS2_ADDR;
  wire       [4:0]    coreArea_pipeline_ctrl_6_down_Decoder_RS1_ADDR;
  wire                coreArea_pipeline_ctrl_6_down_Decoder_VALID;
  wire       [3:0]    coreArea_pipeline_ctrl_6_down_Common_SPEC_EPOCH;
  wire       [31:0]   coreArea_pipeline_ctrl_6_down_Decoder_INSTRUCTION;
  wire                coreArea_pipeline_ctrl_6_down_isValid;
  wire                coreArea_pipeline_ctrl_6_down_isReady;
  wire                coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOAGU;
  wire                coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOBRANCH;
  wire                coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOALU;
  wire                coreArea_pipeline_ctrl_5_down_Common_LANE_SEL;
  wire       [4:0]    coreArea_pipeline_ctrl_5_down_Decoder_RS2_ADDR;
  wire       [4:0]    coreArea_pipeline_ctrl_5_down_Decoder_RS1_ADDR;
  wire       [4:0]    coreArea_pipeline_ctrl_5_down_Decoder_RD_ADDR;
  wire       [5:0]    coreArea_pipeline_ctrl_5_down_Decoder_MicroCode;
  wire       [0:0]    coreArea_pipeline_ctrl_5_down_Decoder_LEGAL;
  wire                coreArea_pipeline_ctrl_5_down_Decoder_VALID;
  wire       [63:0]   coreArea_pipeline_ctrl_5_down_PC_PC;
  wire                coreArea_pipeline_ctrl_5_down_isValid;
  wire                coreArea_pipeline_ctrl_5_down_isReady;
  reg        [4:0]    coreArea_pipeline_ctrl_6_up_Decoder_RS2_ADDR;
  reg        [4:0]    coreArea_pipeline_ctrl_6_up_Decoder_RS1_ADDR;
  reg        [31:0]   coreArea_pipeline_ctrl_6_up_Decoder_INSTRUCTION;
  wire       [4:0]    coreArea_pipeline_ctrl_4_down_Decoder_RS2_ADDR;
  wire       [4:0]    coreArea_pipeline_ctrl_4_down_Decoder_RS1_ADDR;
  wire       [4:0]    coreArea_pipeline_ctrl_4_down_Decoder_RD_ADDR;
  wire       [5:0]    coreArea_pipeline_ctrl_4_down_Decoder_MicroCode;
  wire       [2:0]    coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL;
  wire       [2:0]    coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE;
  wire       [2:0]    coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE;
  wire       [0:0]    coreArea_pipeline_ctrl_4_down_Decoder_LEGAL;
  wire                coreArea_pipeline_ctrl_4_down_Decoder_VALID;
  wire       [31:0]   coreArea_pipeline_ctrl_4_down_Decoder_INSTRUCTION;
  wire       [63:0]   coreArea_pipeline_ctrl_4_down_PC_PC;
  wire                coreArea_pipeline_ctrl_4_down_isValid;
  wire                coreArea_pipeline_ctrl_4_down_isReady;
  reg                 coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOAGU;
  reg                 coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOBRANCH;
  reg                 coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOALU;
  reg                 coreArea_pipeline_ctrl_5_up_Common_LANE_SEL;
  reg        [4:0]    coreArea_pipeline_ctrl_5_up_Decoder_RD_ADDR;
  reg        [5:0]    coreArea_pipeline_ctrl_5_up_Decoder_MicroCode;
  reg        [0:0]    coreArea_pipeline_ctrl_5_up_Decoder_LEGAL;
  reg        [31:0]   coreArea_pipeline_ctrl_5_up_Decoder_INSTRUCTION;
  wire                coreArea_pipeline_ctrl_3_down_isValid;
  wire                coreArea_pipeline_ctrl_3_down_isReady;
  reg        [5:0]    coreArea_pipeline_ctrl_4_up_Decoder_MicroCode;
  reg        [2:0]    coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL;
  reg        [2:0]    coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE;
  reg        [2:0]    coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE;
  reg        [0:0]    coreArea_pipeline_ctrl_4_up_Decoder_LEGAL;
  reg        [3:0]    coreArea_pipeline_ctrl_4_up_Common_SPEC_EPOCH;
  reg        [31:0]   coreArea_pipeline_ctrl_4_up_Decoder_INSTRUCTION;
  wire                coreArea_pipeline_ctrl_2_down_isValid;
  wire                coreArea_pipeline_ctrl_2_down_isReady;
  reg        [3:0]    coreArea_pipeline_ctrl_3_up_Common_SPEC_EPOCH;
  wire                coreArea_pipeline_ctrl_1_down_isValid;
  wire                coreArea_pipeline_ctrl_1_down_isReady;
  reg        [63:0]   coreArea_pipeline_ctrl_2_up_PC_PC;
  wire                coreArea_pipeline_ctrl_0_down_isValid;
  reg        [63:0]   coreArea_pipeline_ctrl_1_up_PC_PC;
  wire                coreArea_pipeline_ctrl_7_down_valid;
  reg                 coreArea_pipeline_ctrl_7_up_valid;
  reg                 coreArea_pipeline_ctrl_6_down_valid;
  reg                 coreArea_pipeline_ctrl_6_up_valid;
  reg                 coreArea_pipeline_ctrl_5_down_valid;
  reg                 coreArea_pipeline_ctrl_5_up_valid;
  reg                 coreArea_pipeline_ctrl_4_down_valid;
  reg                 coreArea_pipeline_ctrl_4_up_valid;
  reg                 coreArea_pipeline_ctrl_3_down_valid;
  reg                 coreArea_pipeline_ctrl_3_up_valid;
  reg                 coreArea_pipeline_ctrl_2_down_valid;
  reg                 coreArea_pipeline_ctrl_2_up_valid;
  reg                 coreArea_pipeline_ctrl_1_down_valid;
  reg                 coreArea_pipeline_ctrl_1_up_valid;
  wire                coreArea_pipeline_ctrl_0_down_valid;
  wire                coreArea_pipeline_ctrl_0_up_ready;
  reg                 coreArea_pipeline_ctrl_0_down_ready;
  reg                 coreArea_pipeline_ctrl_1_up_ready;
  wire                coreArea_pipeline_ctrl_1_up_cancel;
  reg                 coreArea_pipeline_ctrl_1_down_ready;
  reg                 coreArea_pipeline_ctrl_2_up_ready;
  wire                coreArea_pipeline_ctrl_2_up_cancel;
  reg                 coreArea_pipeline_ctrl_2_down_ready;
  wire                coreArea_pipeline_ctrl_3_up_ready;
  wire                coreArea_pipeline_ctrl_3_up_cancel;
  reg                 coreArea_pipeline_ctrl_3_down_ready;
  reg                 coreArea_pipeline_ctrl_4_up_ready;
  wire                coreArea_pipeline_ctrl_4_up_cancel;
  reg                 coreArea_pipeline_ctrl_4_down_ready;
  wire                coreArea_pipeline_ctrl_5_up_ready;
  wire                coreArea_pipeline_ctrl_5_up_cancel;
  reg                 coreArea_pipeline_ctrl_5_down_ready;
  reg                 coreArea_pipeline_ctrl_6_up_ready;
  reg                 coreArea_pipeline_ctrl_6_down_ready;
  wire                coreArea_pipeline_ctrl_7_up_ready;
  reg        [3:0]    coreArea_pipeline_ctrl_5_up_Common_SPEC_EPOCH;
  reg        [3:0]    coreArea_pipeline_ctrl_6_up_Common_SPEC_EPOCH;
  reg        [63:0]   coreArea_pipeline_ctrl_5_up_PC_PC;
  reg        [63:0]   coreArea_pipeline_ctrl_4_up_PC_PC;
  reg        [63:0]   coreArea_pipeline_ctrl_3_up_PC_PC;
  wire                coreArea_pipeline_ctrl_7_up_isValid;
  wire                coreArea_pipeline_ctrl_6_up_isValid;
  wire                coreArea_pipeline_ctrl_5_down_isFiring;
  wire                coreArea_pipeline_ctrl_5_up_isValid;
  wire                coreArea_pipeline_ctrl_4_down_isFiring;
  wire                coreArea_pipeline_ctrl_4_up_isValid;
  wire                coreArea_pipeline_ctrl_3_down_isFiring;
  wire                coreArea_pipeline_ctrl_3_up_isValid;
  wire                coreArea_pipeline_ctrl_2_up_isValid;
  wire                coreArea_pipeline_ctrl_0_down_isFiring;
  wire                coreArea_pipeline_ctrl_0_up_isValid;
  reg        [3:0]    coreArea_pipeline_ctrl_7_up_Common_SPEC_EPOCH;
  reg                 coreArea_pipeline_ctrl_7_up_Dispatch_SENDTOALU;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_SrcPlugin_IMMED;
  reg                 coreArea_pipeline_ctrl_7_up_Decoder_VALID;
  wire                coreArea_pipeline_ctrl_7_down_ready;
  wire       [63:0]   coreArea_pipeline_ctrl_3_down_PC_PC;
  wire       [63:0]   coreArea_pipeline_ctrl_2_down_PC_PC;
  reg                 coreArea_pipeline_ctrl_7_up_Common_LANE_SEL;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_LSU_MEM_WDATA;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_LSU_MEM_RDATA;
  reg        [7:0]    coreArea_pipeline_ctrl_7_up_LSU_MEM_WMASK;
  reg        [7:0]    coreArea_pipeline_ctrl_7_up_LSU_MEM_RMASK;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_LSU_MEM_ADDR;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_Branch_BRANCH_TARGET;
  reg                 coreArea_pipeline_ctrl_7_up_Branch_BRANCH_TAKEN;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_PC_PC;
  reg                 coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_valid;
  reg        [4:0]    coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_address;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_data;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_SrcPlugin_RS2;
  reg        [63:0]   coreArea_pipeline_ctrl_7_up_SrcPlugin_RS1;
  reg        [4:0]    coreArea_pipeline_ctrl_7_up_Decoder_RS2_ADDR;
  reg        [4:0]    coreArea_pipeline_ctrl_7_up_Decoder_RS1_ADDR;
  reg                 coreArea_pipeline_ctrl_7_up_Common_TRAP;
  reg        [31:0]   coreArea_pipeline_ctrl_7_up_Decoder_INSTRUCTION;
  wire                coreArea_pipeline_ctrl_7_up_Common_COMMIT;
  wire       [3:0]    coreArea_pipeline_ctrl_5_down_Common_SPEC_EPOCH;
  wire       [3:0]    coreArea_pipeline_ctrl_4_down_Common_SPEC_EPOCH;
  wire       [3:0]    coreArea_pipeline_ctrl_3_down_Common_SPEC_EPOCH;
  wire                coreArea_pipeline_ctrl_6_down_Common_TRAP;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_LSU_MEM_RDATA;
  wire       [7:0]    coreArea_pipeline_ctrl_6_down_LSU_MEM_RMASK;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_LSU_MEM_WDATA;
  wire       [7:0]    coreArea_pipeline_ctrl_6_down_LSU_MEM_WMASK;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_LSU_MEM_ADDR;
  reg                 coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOAGU;
  reg                 _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l168;
  reg                 _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l159;
  reg                 _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l157;
  wire       [0:0]    coreArea_pipeline_ctrl_6_down_Decoder_LEGAL;
  wire                coreArea_pipeline_ctrl_6_down_isFiring;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_Branch_BRANCH_TARGET;
  wire                coreArea_pipeline_ctrl_6_down_Branch_BRANCH_TAKEN;
  reg                 coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOBRANCH;
  reg                 coreArea_pipeline_ctrl_6_up_Common_LANE_SEL;
  reg        [63:0]   coreArea_pipeline_ctrl_6_up_SrcPlugin_IMMED;
  reg        [63:0]   coreArea_pipeline_ctrl_6_up_PC_PC;
  reg        [63:0]   coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2;
  reg        [63:0]   coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1;
  reg        [0:0]    coreArea_pipeline_ctrl_6_up_Decoder_LEGAL;
  reg        [4:0]    coreArea_pipeline_ctrl_6_up_Decoder_RD_ADDR;
  reg                 coreArea_pipeline_ctrl_6_up_Decoder_VALID;
  reg                 coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_valid;
  reg        [4:0]    coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_address;
  reg        [63:0]   coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_PC_PC;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED;
  wire       [63:0]   coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  reg        [5:0]    coreArea_pipeline_ctrl_6_up_Decoder_MicroCode;
  reg                 coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOALU;
  reg        [2:0]    coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE;
  reg        [2:0]    coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE;
  reg        [63:0]   coreArea_pipeline_ctrl_5_down_SrcPlugin_IMMED;
  reg        [63:0]   coreArea_pipeline_ctrl_5_down_SrcPlugin_RS2;
  reg        [63:0]   coreArea_pipeline_ctrl_5_down_SrcPlugin_RS1;
  reg        [4:0]    coreArea_pipeline_ctrl_5_up_Decoder_RS2_ADDR;
  reg        [4:0]    coreArea_pipeline_ctrl_5_up_Decoder_RS1_ADDR;
  wire       [2:0]    coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE;
  reg                 coreArea_pipeline_ctrl_5_up_Decoder_VALID;
  wire       [2:0]    coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE;
  reg        [2:0]    coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL;
  wire       [31:0]   coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION;
  reg        [4:0]    coreArea_pipeline_ctrl_4_up_Decoder_RS2_ADDR;
  reg        [4:0]    coreArea_pipeline_ctrl_4_up_Decoder_RS1_ADDR;
  wire                coreArea_pipeline_ctrl_7_down_isFiring;
  wire                coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_valid;
  wire       [4:0]    coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_address;
  wire       [63:0]   coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_data;
  reg        [4:0]    coreArea_pipeline_ctrl_4_up_Decoder_RD_ADDR;
  reg                 coreArea_pipeline_ctrl_4_up_Decoder_VALID;
  reg        [2:0]    coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT;
  wire                coreArea_pipeline_ctrl_4_up_isFiring;
  reg                 coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOAGU;
  reg                 coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOBRANCH;
  reg                 coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOALU;
  reg                 coreArea_pipeline_ctrl_4_down_Common_LANE_SEL;
  wire       [4:0]    coreArea_pipeline_ctrl_3_down_Decoder_RS2_ADDR;
  wire       [4:0]    coreArea_pipeline_ctrl_3_down_Decoder_RS1_ADDR;
  wire       [4:0]    coreArea_pipeline_ctrl_3_down_Decoder_RD_ADDR;
  wire       [0:0]    coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ;
  wire       [0:0]    coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ;
  wire       [0:0]    coreArea_pipeline_ctrl_3_down_Decoder_IS_W;
  wire       [0:0]    coreArea_pipeline_ctrl_3_down_Decoder_IS_BR;
  wire       [5:0]    coreArea_pipeline_ctrl_3_down_Decoder_MicroCode;
  wire       [2:0]    coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL;
  wire       [0:0]    coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN;
  wire       [2:0]    coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE;
  wire       [2:0]    coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE;
  wire       [1:0]    coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE;
  wire       [2:0]    coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT;
  wire       [0:0]    coreArea_pipeline_ctrl_3_down_Decoder_IS_FP;
  reg        [31:0]   coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION;
  wire       [0:0]    coreArea_pipeline_ctrl_3_down_Decoder_LEGAL;
  wire       [31:0]   coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION;
  wire                coreArea_pipeline_ctrl_3_down_Decoder_VALID;
  wire                coreArea_pipeline_ctrl_2_down_isFiring;
  wire       [3:0]    coreArea_pipeline_ctrl_2_down_Common_SPEC_EPOCH;
  wire       [31:0]   coreArea_pipeline_ctrl_2_down_Decoder_INSTRUCTION;
  wire       [63:0]   coreArea_pipeline_ctrl_1_down_PC_PC;
  wire                coreArea_pipeline_ctrl_1_up_isValid;
  wire                coreArea_pipeline_ctrl_1_down_isFiring;
  wire       [63:0]   coreArea_pipeline_ctrl_0_down_PC_PC;
  wire                coreArea_pipeline_ctrl_0_down_isReady;
  wire                coreArea_pipeline_ctrl_0_up_valid;
  wire                coreArea_pc_jump_valid;
  wire       [63:0]   coreArea_pc_jump_payload_target;
  wire                coreArea_pc_jump_payload_is_jump;
  wire                coreArea_pc_jump_payload_is_branch;
  wire                coreArea_pc_flush_valid;
  wire       [63:0]   coreArea_pc_flush_payload_address;
  wire                coreArea_pc_exception_valid;
  wire       [63:0]   coreArea_pc_exception_payload_vector;
  reg        [63:0]   coreArea_pc_PC_cur;
  wire                coreArea_fetch_io_readCmd_cmd_valid;
  wire                coreArea_fetch_io_readCmd_cmd_ready;
  wire       [63:0]   coreArea_fetch_io_readCmd_cmd_payload_address;
  wire       [15:0]   coreArea_fetch_io_readCmd_cmd_payload_id;
  wire                coreArea_fetch_io_readCmd_rsp_valid;
  wire       [63:0]   coreArea_fetch_io_readCmd_rsp_payload_data;
  wire       [63:0]   coreArea_fetch_io_readCmd_rsp_payload_address;
  wire       [15:0]   coreArea_fetch_io_readCmd_rsp_payload_id;
  wire                coreArea_fetch_io_flush;
  wire       [3:0]    coreArea_fetch_io_currentEpoch;
  reg        [3:0]    coreArea_fetch_inflight;
  wire                coreArea_fetch_cmdFire;
  reg        [15:0]   coreArea_fetch_epoch;
  reg                 coreArea_fetch_flushPending;
  reg                 coreArea_fetch_cmdArea_reqSent;
  wire                coreArea_pipeline_ctrl_1_haltRequest_Fetch_l80;
  wire                coreArea_fetch_rspArea_epochMatch;
  wire                coreArea_fetch_rspArea_stalePacket;
  wire                coreArea_pipeline_ctrl_2_throwWhen_Fetch_l92;
  wire                coreArea_pipeline_ctrl_2_haltRequest_Fetch_l95;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL;
  wire       [1:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE;
  wire       [1:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1;
  wire       [1:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_1;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_1;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_1;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3;
  wire       [2:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4;
  wire                _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_2;
  wire       [5:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3;
  wire       [5:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4;
  wire       [5:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3;
  wire       [0:0]    _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4;
  wire                coreArea_decode_branchResolved;
  wire                when_scheduler_l144;
  wire                when_scheduler_l148;
  wire                when_scheduler_l152;
  reg        [31:0]   coreArea_dispatcher_hcs_regBusy;
  wire                when_scheduler_l187;
  wire                when_scheduler_l196;
  wire                when_scheduler_l200;
  wire                coreArea_dispatcher_hcs_writes_rs1Busy;
  wire                coreArea_dispatcher_hcs_writes_rs2Busy;
  wire                coreArea_dispatcher_hcs_writes_hazard;
  wire                coreArea_pipeline_ctrl_4_haltRequest_scheduler_l214;
  wire       [3:0]    _zz_coreArea_dispatcher_hcs_hazards;
  reg        [3:0]    coreArea_dispatcher_hcs_hazards;
  reg                 coreArea_dispatcher_hcs_init_willIncrement;
  wire                coreArea_dispatcher_hcs_init_willClear;
  reg        [2:0]    coreArea_dispatcher_hcs_init_valueNext;
  reg        [2:0]    coreArea_dispatcher_hcs_init_value;
  wire                coreArea_dispatcher_hcs_init_willOverflowIfInc;
  wire                coreArea_dispatcher_hcs_init_willOverflow;
  wire                when_scheduler_l250;
  wire                coreArea_srcPlugin_wasReset;
  reg        [63:0]   coreArea_srcPlugin_immsel_sext;
  wire       [63:0]   _zz_coreArea_srcPlugin_immsel_sext;
  reg        [63:0]   _zz_coreArea_srcPlugin_immsel_sext_1;
  wire                coreArea_srcPlugin_rs1Reader_valid;
  wire       [4:0]    coreArea_srcPlugin_rs1Reader_address;
  wire       [63:0]   coreArea_srcPlugin_rs1Reader_data;
  wire                coreArea_srcPlugin_rs2Reader_valid;
  wire       [4:0]    coreArea_srcPlugin_rs2Reader_address;
  wire       [63:0]   coreArea_srcPlugin_rs2Reader_data;
  wire       [63:0]   coreArea_srcPlugin_rs_rs1Data;
  wire       [63:0]   coreArea_srcPlugin_rs_rs2Data;
  reg        [63:0]   IntAlu_aluNodeStage_result;
  wire                when_IntAlu_l40;
  reg        [63:0]   _zz_IntAlu_aluNodeStage_result;
  wire                when_IntAlu_l83;
  wire                coreArea_branch_branchResolved;
  wire       [63:0]   coreArea_branch_logic_src1;
  wire       [63:0]   coreArea_branch_logic_src2;
  wire       [63:0]   coreArea_branch_logic_src1U;
  wire       [63:0]   coreArea_branch_logic_src2U;
  wire       [63:0]   coreArea_branch_logic_imm;
  reg                 coreArea_branch_logic_condition;
  reg        [63:0]   coreArea_branch_logic_target;
  reg                 coreArea_branch_logic_isJump;
  reg                 coreArea_branch_logic_isBranch;
  wire                coreArea_branch_logic_doJump;
  wire                coreArea_branch_logic_misaligned;
  wire                coreArea_branch_logic_willTrap;
  wire                coreArea_branch_logic_jumpCmd_valid;
  wire       [63:0]   coreArea_branch_logic_jumpCmd_payload_target;
  wire                coreArea_branch_logic_jumpCmd_payload_is_jump;
  wire                coreArea_branch_logic_jumpCmd_payload_is_branch;
  wire                when_branch_l95;
  wire                coreArea_lsu_io_dBus_cmd_valid;
  wire                coreArea_lsu_io_dBus_cmd_ready;
  wire       [63:0]   coreArea_lsu_io_dBus_cmd_payload_address;
  wire       [63:0]   coreArea_lsu_io_dBus_cmd_payload_data;
  wire       [7:0]    coreArea_lsu_io_dBus_cmd_payload_mask;
  wire       [15:0]   coreArea_lsu_io_dBus_cmd_payload_id;
  wire                coreArea_lsu_io_dBus_cmd_payload_write;
  wire                coreArea_lsu_io_dBus_rsp_valid;
  wire       [63:0]   coreArea_lsu_io_dBus_rsp_payload_data;
  wire       [15:0]   coreArea_lsu_io_dBus_rsp_payload_id;
  wire       [63:0]   coreArea_lsu_logic_effectiveAddr;
  reg                 LSU_isStore;
  wire                coreArea_lsu_logic_misaligned;
  reg                 _zz_coreArea_lsu_logic_misaligned;
  wire                coreArea_lsu_logic_localTrap;
  wire       [2:0]    coreArea_lsu_logic_byteOffset;
  wire       [7:0]    coreArea_lsu_logic_accessSizeMask;
  reg        [7:0]    _zz_coreArea_lsu_logic_accessSizeMask;
  wire       [7:0]    coreArea_lsu_logic_writeMask;
  wire       [63:0]   coreArea_lsu_logic_rawStoreData;
  reg        [63:0]   _zz_coreArea_lsu_logic_rawStoreData;
  wire       [63:0]   coreArea_lsu_logic_storeData;
  reg                 LSU_isLoad;
  reg                 coreArea_lsu_logic_waitingResponse;
  reg        [15:0]   coreArea_lsu_logic_nextId;
  reg        [15:0]   coreArea_lsu_logic_waitId;
  wire                coreArea_lsu_logic_fireLoad;
  reg        [63:0]   coreArea_lsu_logic_latchedRspData;
  wire                coreArea_lsu_logic_responseArriving;
  wire                when_Lsu_l151;
  wire                when_Lsu_l152;
  wire                when_Lsu_l153;
  wire                coreArea_pipeline_ctrl_6_haltRequest_Lsu_l157;
  wire                coreArea_pipeline_ctrl_6_haltRequest_Lsu_l159;
  wire                coreArea_pipeline_ctrl_6_haltRequest_Lsu_l168;
  wire       [63:0]   coreArea_lsu_logic_rspData;
  wire       [63:0]   coreArea_lsu_logic_shiftedLoadData;
  wire       [63:0]   coreArea_lsu_logic_loadResult;
  reg        [63:0]   _zz_coreArea_lsu_logic_loadResult;
  wire       [4:0]    coreArea_lsu_logic_rdAddr;
  wire                coreArea_lsu_logic_isX0;
  wire       [63:0]   coreArea_lsu_logic_maskedLoadResult;
  wire       [7:0]    coreArea_lsu_logic_readMaskShifted;
  reg        [3:0]    coreArea_currentEpoch;
  wire                coreArea_pipeline_ctrl_3_throwWhen_CPU_l152;
  wire                coreArea_pipeline_ctrl_4_throwWhen_CPU_l152;
  wire                coreArea_pipeline_ctrl_5_throwWhen_CPU_l152;
  wire                coreArea_pipeline_ctrl_1_throwWhen_CPU_l158;
  wire                coreArea_pipeline_ctrl_2_throwWhen_CPU_l158;
  reg        [63:0]   coreArea_rvfiPlugin_order;
  reg        [63:0]   coreArea_perfCounters_cycles;
  reg        [63:0]   coreArea_perfCounters_instret;
  reg        [63:0]   coreArea_perfCounters_stallsHazard;
  reg        [63:0]   coreArea_perfCounters_stallsFetch;
  reg        [63:0]   coreArea_perfCounters_stallsMem;
  reg        [63:0]   coreArea_perfCounters_branches;
  reg        [63:0]   coreArea_perfCounters_branchesTaken;
  reg        [63:0]   coreArea_perfCounters_flushes;
  wire                coreArea_perfCounters_hazardStall;
  wire                coreArea_perfCounters_fetchStall;
  wire                coreArea_perfCounters_memStall;
  wire                coreArea_perfCounters_branchExecuted;
  wire                coreArea_perfCounters_branchTaken;
  wire                coreArea_perfCounters_pipelineFlush;
  wire       [63:0]   coreArea_perfCounters_counters_cycles;
  wire       [63:0]   coreArea_perfCounters_counters_instret;
  wire       [63:0]   coreArea_perfCounters_counters_stallsHazard;
  wire       [63:0]   coreArea_perfCounters_counters_stallsFetch;
  wire       [63:0]   coreArea_perfCounters_counters_stallsMem;
  wire       [63:0]   coreArea_perfCounters_counters_branches;
  wire       [63:0]   coreArea_perfCounters_counters_branchesTaken;
  wire       [63:0]   coreArea_perfCounters_counters_flushes;
  wire                coreArea_pipeline_ctrl_5_up_forgetOne;
  wire                coreArea_pipeline_ctrl_4_up_forgetOne;
  wire                coreArea_pipeline_ctrl_3_up_forgetOne;
  wire                coreArea_pipeline_ctrl_2_up_forgetOne;
  wire                coreArea_pipeline_ctrl_1_up_forgetOne;
  wire                when_StageLink_l71;
  wire                when_StageLink_l71_1;
  wire                when_StageLink_l71_2;
  wire                when_StageLink_l71_3;
  wire                when_StageLink_l71_4;
  wire                when_StageLink_l71_5;
  wire                when_StageLink_l71_6;
  wire                when_CtrlLink_l191;
  wire                when_CtrlLink_l198;
  wire                when_CtrlLink_l191_1;
  wire                when_CtrlLink_l198_1;
  wire                when_CtrlLink_l198_2;
  wire                when_CtrlLink_l191_2;
  wire                when_CtrlLink_l198_3;
  wire                when_CtrlLink_l198_4;
  wire                when_CtrlLink_l191_3;
  `ifndef SYNTHESIS
  reg [79:0] coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string;
  reg [7:0] coreArea_pipeline_ctrl_5_down_Decoder_LEGAL_string;
  reg [79:0] coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string;
  reg [39:0] coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string;
  reg [47:0] coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE_string;
  reg [47:0] coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE_string;
  reg [7:0] coreArea_pipeline_ctrl_4_down_Decoder_LEGAL_string;
  reg [79:0] coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string;
  reg [7:0] coreArea_pipeline_ctrl_5_up_Decoder_LEGAL_string;
  reg [79:0] coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string;
  reg [39:0] coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string;
  reg [47:0] coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE_string;
  reg [47:0] coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE_string;
  reg [7:0] coreArea_pipeline_ctrl_4_up_Decoder_LEGAL_string;
  reg [7:0] coreArea_pipeline_ctrl_6_down_Decoder_LEGAL_string;
  reg [7:0] coreArea_pipeline_ctrl_6_up_Decoder_LEGAL_string;
  reg [79:0] coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string;
  reg [47:0] coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE_string;
  reg [47:0] coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE_string;
  reg [47:0] coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE_string;
  reg [47:0] coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE_string;
  reg [39:0] coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string;
  reg [23:0] coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT_string;
  reg [7:0] coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_string;
  reg [7:0] coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_string;
  reg [7:0] coreArea_pipeline_ctrl_3_down_Decoder_IS_W_string;
  reg [7:0] coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string;
  reg [79:0] coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string;
  reg [39:0] coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string;
  reg [7:0] coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string;
  reg [47:0] coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string;
  reg [47:0] coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string;
  reg [47:0] coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string;
  reg [23:0] coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string;
  reg [7:0] coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string;
  reg [7:0] coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2_string;
  reg [23:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string;
  reg [23:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1_string;
  reg [23:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1_string;
  reg [47:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2_string;
  reg [39:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string;
  reg [39:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string;
  reg [39:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string;
  reg [79:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string;
  reg [79:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string;
  reg [79:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3_string;
  reg [7:0] _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4_string;
  `endif


  assign _zz_coreArea_fetch_inflight = (coreArea_fetch_inflight + _zz_coreArea_fetch_inflight_1);
  assign _zz_coreArea_fetch_inflight_2 = coreArea_fetch_cmdFire;
  assign _zz_coreArea_fetch_inflight_1 = {3'd0, _zz_coreArea_fetch_inflight_2};
  assign _zz_coreArea_fetch_inflight_4 = coreArea_fetch_io_readCmd_rsp_valid;
  assign _zz_coreArea_fetch_inflight_3 = {3'd0, _zz_coreArea_fetch_inflight_4};
  assign _zz_coreArea_fetch_io_readCmd_cmd_valid = {2'd0, coreArea_fetch_fifo_io_availability};
  assign _zz_coreArea_dispatcher_hcs_init_valueNext_1 = coreArea_dispatcher_hcs_init_willIncrement;
  assign _zz_coreArea_dispatcher_hcs_init_valueNext = {2'd0, _zz_coreArea_dispatcher_hcs_init_valueNext_1};
  assign _zz__zz_coreArea_srcPlugin_immsel_sext = {coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[31 : 12],12'h0};
  assign _zz__zz_coreArea_srcPlugin_immsel_sext_1 = coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[31 : 20];
  assign _zz__zz_coreArea_srcPlugin_immsel_sext_1_1 = {coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[31 : 25],coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[11 : 7]};
  assign _zz__zz_coreArea_srcPlugin_immsel_sext_1_2 = {{{{coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[31],coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[7]},coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[30 : 25]},coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[11 : 8]},1'b0};
  assign _zz__zz_coreArea_srcPlugin_immsel_sext_1_3 = {{{{coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[31],coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[19 : 12]},coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[20]},coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION[30 : 21]},1'b0};
  assign _zz__zz_IntAlu_aluNodeStage_result = ($signed(_zz__zz_IntAlu_aluNodeStage_result_1) + $signed(_zz__zz_IntAlu_aluNodeStage_result_2));
  assign _zz__zz_IntAlu_aluNodeStage_result_1 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  assign _zz__zz_IntAlu_aluNodeStage_result_2 = coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED;
  assign _zz__zz_IntAlu_aluNodeStage_result_3 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_4) < $signed(_zz__zz_IntAlu_aluNodeStage_result_5));
  assign _zz__zz_IntAlu_aluNodeStage_result_4 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  assign _zz__zz_IntAlu_aluNodeStage_result_5 = coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED;
  assign _zz__zz_IntAlu_aluNodeStage_result_6 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 < coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED);
  assign _zz__zz_IntAlu_aluNodeStage_result_7 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 <<< coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED[5 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_8 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED[5 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_9 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_10) >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED[5 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_10 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  assign _zz__zz_IntAlu_aluNodeStage_result_11 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_12) + $signed(_zz__zz_IntAlu_aluNodeStage_result_13));
  assign _zz__zz_IntAlu_aluNodeStage_result_12 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  assign _zz__zz_IntAlu_aluNodeStage_result_13 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2;
  assign _zz__zz_IntAlu_aluNodeStage_result_14 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 <<< coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[5 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_15 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[5 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_16 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_17) >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[5 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_17 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  assign _zz__zz_IntAlu_aluNodeStage_result_18 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_19) - $signed(_zz__zz_IntAlu_aluNodeStage_result_20));
  assign _zz__zz_IntAlu_aluNodeStage_result_19 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  assign _zz__zz_IntAlu_aluNodeStage_result_20 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2;
  assign _zz__zz_IntAlu_aluNodeStage_result_21 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_22) < $signed(_zz__zz_IntAlu_aluNodeStage_result_23));
  assign _zz__zz_IntAlu_aluNodeStage_result_22 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
  assign _zz__zz_IntAlu_aluNodeStage_result_23 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2;
  assign _zz__zz_IntAlu_aluNodeStage_result_24 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 < coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2);
  assign _zz__zz_IntAlu_aluNodeStage_result_26 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_27) + $signed(_zz__zz_IntAlu_aluNodeStage_result_28));
  assign _zz__zz_IntAlu_aluNodeStage_result_25 = {{32{_zz__zz_IntAlu_aluNodeStage_result_26[31]}}, _zz__zz_IntAlu_aluNodeStage_result_26};
  assign _zz__zz_IntAlu_aluNodeStage_result_27 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_28 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_30 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_31) - $signed(_zz__zz_IntAlu_aluNodeStage_result_32));
  assign _zz__zz_IntAlu_aluNodeStage_result_29 = {{32{_zz__zz_IntAlu_aluNodeStage_result_30[31]}}, _zz__zz_IntAlu_aluNodeStage_result_30};
  assign _zz__zz_IntAlu_aluNodeStage_result_31 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_32 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_34 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_35) + $signed(_zz__zz_IntAlu_aluNodeStage_result_36));
  assign _zz__zz_IntAlu_aluNodeStage_result_33 = {{32{_zz__zz_IntAlu_aluNodeStage_result_34[31]}}, _zz__zz_IntAlu_aluNodeStage_result_34};
  assign _zz__zz_IntAlu_aluNodeStage_result_35 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_36 = coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_38 = _zz__zz_IntAlu_aluNodeStage_result_39[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_37 = {{32{_zz__zz_IntAlu_aluNodeStage_result_38[31]}}, _zz__zz_IntAlu_aluNodeStage_result_38};
  assign _zz__zz_IntAlu_aluNodeStage_result_39 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0] <<< coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[4 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_41 = _zz__zz_IntAlu_aluNodeStage_result_42;
  assign _zz__zz_IntAlu_aluNodeStage_result_40 = {{32{_zz__zz_IntAlu_aluNodeStage_result_41[31]}}, _zz__zz_IntAlu_aluNodeStage_result_41};
  assign _zz__zz_IntAlu_aluNodeStage_result_42 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0] >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[4 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_44 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_45) >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2[4 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_43 = {{32{_zz__zz_IntAlu_aluNodeStage_result_44[31]}}, _zz__zz_IntAlu_aluNodeStage_result_44};
  assign _zz__zz_IntAlu_aluNodeStage_result_45 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_47 = _zz__zz_IntAlu_aluNodeStage_result_48[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_46 = {{32{_zz__zz_IntAlu_aluNodeStage_result_47[31]}}, _zz__zz_IntAlu_aluNodeStage_result_47};
  assign _zz__zz_IntAlu_aluNodeStage_result_48 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0] <<< coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED[4 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_50 = _zz__zz_IntAlu_aluNodeStage_result_51;
  assign _zz__zz_IntAlu_aluNodeStage_result_49 = {{32{_zz__zz_IntAlu_aluNodeStage_result_50[31]}}, _zz__zz_IntAlu_aluNodeStage_result_50};
  assign _zz__zz_IntAlu_aluNodeStage_result_51 = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0] >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED[4 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_53 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_54) >>> coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED[4 : 0]);
  assign _zz__zz_IntAlu_aluNodeStage_result_52 = {{32{_zz__zz_IntAlu_aluNodeStage_result_53[31]}}, _zz__zz_IntAlu_aluNodeStage_result_53};
  assign _zz__zz_IntAlu_aluNodeStage_result_54 = coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1[31 : 0];
  assign _zz__zz_IntAlu_aluNodeStage_result_55 = ($signed(_zz__zz_IntAlu_aluNodeStage_result_56) + $signed(_zz__zz_IntAlu_aluNodeStage_result_57));
  assign _zz__zz_IntAlu_aluNodeStage_result_56 = coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED;
  assign _zz__zz_IntAlu_aluNodeStage_result_57 = coreArea_pipeline_ctrl_6_down_PC_PC;
  assign _zz_coreArea_branch_logic_target = ($signed(_zz_coreArea_branch_logic_target_1) + $signed(_zz_coreArea_branch_logic_target_2));
  assign _zz_coreArea_branch_logic_target_1 = coreArea_branch_logic_src1U;
  assign _zz_coreArea_branch_logic_target_2 = coreArea_branch_logic_imm;
  assign _zz_coreArea_branch_logic_target_3 = ($signed(_zz_coreArea_branch_logic_target_4) + $signed(_zz_coreArea_branch_logic_target_5));
  assign _zz_coreArea_branch_logic_target_4 = coreArea_pipeline_ctrl_6_up_PC_PC;
  assign _zz_coreArea_branch_logic_target_5 = coreArea_branch_logic_imm;
  assign _zz_coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data = (coreArea_pipeline_ctrl_6_up_PC_PC + 64'h0000000000000004);
  assign _zz_coreArea_lsu_logic_effectiveAddr = ($signed(_zz_coreArea_lsu_logic_effectiveAddr_1) + $signed(_zz_coreArea_lsu_logic_effectiveAddr_2));
  assign _zz_coreArea_lsu_logic_effectiveAddr_1 = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1;
  assign _zz_coreArea_lsu_logic_effectiveAddr_2 = coreArea_pipeline_ctrl_6_up_SrcPlugin_IMMED;
  assign _zz__zz_coreArea_lsu_logic_rawStoreData = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2[7 : 0];
  assign _zz__zz_coreArea_lsu_logic_rawStoreData_1 = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2[15 : 0];
  assign _zz__zz_coreArea_lsu_logic_rawStoreData_2 = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2[31 : 0];
  assign _zz_coreArea_lsu_logic_storeData = ({3'd0,coreArea_lsu_logic_byteOffset} <<< 2'd3);
  assign _zz_coreArea_lsu_logic_shiftedLoadData = ({3'd0,coreArea_lsu_logic_byteOffset} <<< 2'd3);
  assign _zz__zz_coreArea_lsu_logic_loadResult_1 = coreArea_lsu_logic_shiftedLoadData[7 : 0];
  assign _zz__zz_coreArea_lsu_logic_loadResult = {{56{_zz__zz_coreArea_lsu_logic_loadResult_1[7]}}, _zz__zz_coreArea_lsu_logic_loadResult_1};
  assign _zz__zz_coreArea_lsu_logic_loadResult_2 = coreArea_lsu_logic_shiftedLoadData[7 : 0];
  assign _zz__zz_coreArea_lsu_logic_loadResult_4 = coreArea_lsu_logic_shiftedLoadData[15 : 0];
  assign _zz__zz_coreArea_lsu_logic_loadResult_3 = {{48{_zz__zz_coreArea_lsu_logic_loadResult_4[15]}}, _zz__zz_coreArea_lsu_logic_loadResult_4};
  assign _zz__zz_coreArea_lsu_logic_loadResult_5 = coreArea_lsu_logic_shiftedLoadData[15 : 0];
  assign _zz__zz_coreArea_lsu_logic_loadResult_7 = coreArea_lsu_logic_shiftedLoadData[31 : 0];
  assign _zz__zz_coreArea_lsu_logic_loadResult_6 = {{32{_zz__zz_coreArea_lsu_logic_loadResult_7[31]}}, _zz__zz_coreArea_lsu_logic_loadResult_7};
  assign _zz__zz_coreArea_lsu_logic_loadResult_8 = coreArea_lsu_logic_shiftedLoadData[31 : 0];
  assign _zz_coreArea_rvfiPlugin_io_rvfi_pc_wdata = (coreArea_pipeline_ctrl_7_up_PC_PC + 64'h0000000000000004);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID = 32'h0000106f;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_1 = (coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h0000405f);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_2 = 32'h00000003;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_3 = ((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h0000407f) == 32'h00004063);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_4 = ((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h0000207f) == 32'h00002013);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_5 = {((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h0000207f) == 32'h00000063),{((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h0000207f) == 32'h00000003),{((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_6) == 32'h00000063),{(_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_7 == _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_8),{_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_9,{_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_10,_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_11}}}}}};
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_6 = 32'h0000707b;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_7 = (coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h00007077);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_8 = 32'h00000013;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_9 = ((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'hbc007077) == 32'h00005013);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_10 = ((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'hfc003077) == 32'h00001013);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_11 = {((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'hfe00007f) == 32'h00000033),{((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'hbe007077) == 32'h00005033),{((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'hfe003077) == 32'h00001033),((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'hbe007077) == 32'h00000033)}}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_1) == 32'h00004020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_2 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_3 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_4);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_5 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_6,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_8};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_10 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_11 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_12,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_14,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_17}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_26 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_27 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_28);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_29 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_30,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_32,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_35}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_56 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_57,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_59,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_62}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_89 = (|{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_90,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_91});
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_124 = (|{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_125,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_126});
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_1 = 32'h00004064;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_3 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00003050);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_4 = 32'h00003000;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_6 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_7) == 32'h00006000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_8 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_9) == 32'h00003020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_12 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_13) == 32'h00000010);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_14 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_15 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_16);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_17 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_18,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_20,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_23}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_27 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00002048);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_28 = 32'h00002040;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_30 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_31) == 32'h0);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_32 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_33 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_34);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_35 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_36,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_38,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_41}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_57 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_58) == 32'h00001008);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_59 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_60 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_61);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_62 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_63,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_65,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_68}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_90 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_1;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_91 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_92,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_94,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_97}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_125 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_1;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_126 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_127,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_129,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_132}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_7 = 32'h00006050;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_9 = 32'h00003064;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_13 = 32'h00000034;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_15 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00003024);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_16 = 32'h00003000;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_18 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_19) == 32'h00006000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_20 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_21 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_22);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_23 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_24 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_25);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_31 = 32'h00002030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_33 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00004068);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_34 = 32'h00004028;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_36 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_37) == 32'h00005000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_38 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_39 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_40);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_41 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_42,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_44,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_47}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_58 = 32'h00001028;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_60 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00004048);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_61 = 32'h00000040;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_63 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_64) == 32'h00001000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_65 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_66 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_67);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_68 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_69,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_71,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_74}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_92 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_93) == 32'h00000004);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_94 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_95 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_96);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_97 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_98,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_100,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_103}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_127 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_128) == 32'h00000024);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_129 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_130 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_131);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_132 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_133,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_135,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_138}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_19 = 32'h00006024;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_21 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00006014);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_22 = 32'h00000010;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_24 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00005064);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_25 = 32'h00000020;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_37 = 32'h00005018;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_39 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00005030);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_40 = 32'h0;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_42 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_43) == 32'h40000030);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_44 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_45 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_46);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_47 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_48,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_50,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_53}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_64 = 32'h00003030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_66 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00006050);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_67 = 32'h00004000;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_69 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_70) == 32'h00000040);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_71 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_72 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_73);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_74 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_75,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_77,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_80}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_93 = 32'h00000024;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_95 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00005040);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_96 = 32'h00001040;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_98 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_99) == 32'h00000008);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_100 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_101 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_102);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_103 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_104,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_106,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_109}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_128 = 32'h00000064;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_130 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00001044);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_131 = 32'h00000040;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_133 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_134) == 32'h40004020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_135 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_136 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_137);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_138 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_139,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_141,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_144}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_43 = 32'h40004034;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_45 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h0000502c);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_46 = 32'h00005000;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_48 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_49) == 32'h00001000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_50 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_51 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_52);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_53 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_54 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_55);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_70 = 32'h00003048;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_72 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00006064);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_73 = 32'h00006020;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_75 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_76) == 32'h00004010);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_77 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_78 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_79);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_80 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_81,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_83,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_86}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_99 = 32'h40001008;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_101 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00003020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_102 = 32'h0;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_104 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_105) == 32'h00007000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_106 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_107 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_108);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_109 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_110,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_112,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_115}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_134 = 32'h40004060;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_136 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_137 = 32'h00004020;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_139 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_140) == 32'h00000028);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_141 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_142 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_143);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_144 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_145,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_147,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_150}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_49 = 32'h0000302c;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_51 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007034);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_52 = 32'h00001030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_54 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h0000503c);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_55 = 32'h00000030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_76 = 32'h00005034;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_78 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00006034);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_79 = 32'h00002010;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_81 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_82) == 32'h00002000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_83 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_84 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_85);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_86 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_87 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_88);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_105 = 32'h00007010;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_107 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00005030);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_108 = 32'h00004010;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_110 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_111) == 32'h00002020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_112 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_113 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_114);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_115 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_116,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_117,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_119}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_140 = 32'h40004028;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_142 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h40004028);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_143 = 32'h00004008;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_145 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_146) == 32'h00005000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_147 = (_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_148 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_149);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_150 = {_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_151,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_152,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_154}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_82 = 32'h00007024;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_84 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h0000603c);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_85 = 32'h00000030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_87 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h40005034);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_88 = 32'h00000030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_111 = 32'h00006030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_113 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h40006020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_114 = 32'h40004000;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_116 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00006028) == 32'h00004000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_117 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_118) == 32'h00004000);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_119 = {(_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_120 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_121),{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_122,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_123}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_146 = 32'h00005050;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_148 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00006030);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_149 = 32'h00002000;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_151 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007060) == 32'h00007020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_152 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_153) == 32'h00002030);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_154 = {(_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_155 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_156),{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_157,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_158,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_159}}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_118 = 32'h00007010;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_120 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007024);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_121 = 32'h00002020;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_122 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007064) == 32'h00005020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_123 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h0000303c) == 32'h00001030);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_153 = 32'h00007030;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_155 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007024);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_156 = 32'h00003000;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_157 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00003070) == 32'h00001020);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_158 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007024) == 32'h0);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_159 = {((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00007034) == 32'h00006010),{((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_160) == 32'h40000010),{(_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_161 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_162),(_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_163 == _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_164)}}};
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_160 = 32'h4000601c;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_161 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h0000603c);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_162 = 32'h00000010;
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_163 = (coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h4000704c);
  assign _zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_164 = 32'h40005000;
  StreamFifo coreArea_fetch_fifo (
    .io_push_valid         (coreArea_fetch_io_readCmd_rsp_valid             ), //i
    .io_push_ready         (coreArea_fetch_fifo_io_push_ready               ), //o
    .io_push_payload_data  (coreArea_fetch_io_readCmd_rsp_payload_data[63:0]), //i
    .io_push_payload_epoch (coreArea_fetch_io_readCmd_rsp_payload_id[15:0]  ), //i
    .io_pop_valid          (coreArea_fetch_fifo_io_pop_valid                ), //o
    .io_pop_ready          (coreArea_fetch_fifo_io_pop_ready                ), //i
    .io_pop_payload_data   (coreArea_fetch_fifo_io_pop_payload_data[63:0]   ), //o
    .io_pop_payload_epoch  (coreArea_fetch_fifo_io_pop_payload_epoch[15:0]  ), //o
    .io_flush              (coreArea_fetch_io_flush                         ), //i
    .io_occupancy          (coreArea_fetch_fifo_io_occupancy[1:0]           ), //o
    .io_availability       (coreArea_fetch_fifo_io_availability[1:0]        ), //o
    .io_clk                (io_clk                                          ), //i
    .io_reset              (io_reset                                        ), //i
    .io_clkEnable          (io_clkEnable                                    )  //i
  );
  IntRegFile coreArea_srcPlugin_regfileread_regfile (
    .io_reads_0_valid    (coreArea_srcPlugin_rs1Reader_valid                          ), //i
    .io_reads_0_address  (coreArea_srcPlugin_rs1Reader_address[4:0]                   ), //i
    .io_reads_0_data     (coreArea_srcPlugin_regfileread_regfile_io_reads_0_data[63:0]), //o
    .io_reads_1_valid    (coreArea_srcPlugin_rs2Reader_valid                          ), //i
    .io_reads_1_address  (coreArea_srcPlugin_rs2Reader_address[4:0]                   ), //i
    .io_reads_1_data     (coreArea_srcPlugin_regfileread_regfile_io_reads_1_data[63:0]), //o
    .io_writes_0_valid   (coreArea_srcPlugin_regfileread_regfile_io_writes_0_valid    ), //i
    .io_writes_0_address (coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_address[4:0]   ), //i
    .io_writes_0_data    (coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_data[63:0]     ), //i
    .io_clk              (io_clk                                                      ), //i
    .io_reset            (io_reset                                                    ), //i
    .io_clkEnable        (io_clkEnable                                                )  //i
  );
  `ifndef SYNTHESIS
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_down_Decoder_MicroCode)
      MicroCode_uopNOP : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopNOP    ";
      MicroCode_uopLUI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLUI    ";
      MicroCode_uopAUIPC : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopAUIPC  ";
      MicroCode_uopJAL : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopJAL    ";
      MicroCode_uopJALR : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopJALR   ";
      MicroCode_uopBEQ : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopBEQ    ";
      MicroCode_uopBNE : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopBNE    ";
      MicroCode_uopBLT : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopBLT    ";
      MicroCode_uopBGE : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopBGE    ";
      MicroCode_uopBLTU : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopBLTU   ";
      MicroCode_uopBGEU : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopBGEU   ";
      MicroCode_uopLB : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLB     ";
      MicroCode_uopLH : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLH     ";
      MicroCode_uopLW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLW     ";
      MicroCode_uopLBU : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLBU    ";
      MicroCode_uopLHU : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLHU    ";
      MicroCode_uopSB : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSB     ";
      MicroCode_uopSH : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSH     ";
      MicroCode_uopSW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSW     ";
      MicroCode_uopADDI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopADDI   ";
      MicroCode_uopSLTI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLTI   ";
      MicroCode_uopSLTIU : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLTIU  ";
      MicroCode_uopXORI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopXORI   ";
      MicroCode_uopORI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopORI    ";
      MicroCode_uopANDI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopANDI   ";
      MicroCode_uopSLLI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLLI   ";
      MicroCode_uopSRLI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRLI   ";
      MicroCode_uopSRAI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRAI   ";
      MicroCode_uopADD : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopADD    ";
      MicroCode_uopSUB : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSUB    ";
      MicroCode_uopSLL : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLL    ";
      MicroCode_uopSLT : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLT    ";
      MicroCode_uopSLTU : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLTU   ";
      MicroCode_uopXOR : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopXOR    ";
      MicroCode_uopSRL : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRL    ";
      MicroCode_uopSRA : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRA    ";
      MicroCode_uopOR : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopOR     ";
      MicroCode_uopAND : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopAND    ";
      MicroCode_uopFENCE : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopFENCE_I";
      MicroCode_uopECALL : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopECALL  ";
      MicroCode_uopEBREAK : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopEBREAK ";
      MicroCode_uopCSRRW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopCSRRCI ";
      MicroCode_uopLWU : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLWU    ";
      MicroCode_uopLD : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopLD     ";
      MicroCode_uopSD : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSD     ";
      MicroCode_uopADDIW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopADDIW  ";
      MicroCode_uopSLLIW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRAIW  ";
      MicroCode_uopADDW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopADDW   ";
      MicroCode_uopSUBW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSUBW   ";
      MicroCode_uopSLLW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSLLW   ";
      MicroCode_uopSRLW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRLW   ";
      MicroCode_uopSRAW : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "uopSRAW   ";
      default : coreArea_pipeline_ctrl_5_down_Decoder_MicroCode_string = "??????????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_down_Decoder_LEGAL)
      YESNO_Y : coreArea_pipeline_ctrl_5_down_Decoder_LEGAL_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_5_down_Decoder_LEGAL_string = "N";
      default : coreArea_pipeline_ctrl_5_down_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_down_Decoder_MicroCode)
      MicroCode_uopNOP : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopNOP    ";
      MicroCode_uopLUI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLUI    ";
      MicroCode_uopAUIPC : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopAUIPC  ";
      MicroCode_uopJAL : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopJAL    ";
      MicroCode_uopJALR : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopJALR   ";
      MicroCode_uopBEQ : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopBEQ    ";
      MicroCode_uopBNE : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopBNE    ";
      MicroCode_uopBLT : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopBLT    ";
      MicroCode_uopBGE : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopBGE    ";
      MicroCode_uopBLTU : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopBLTU   ";
      MicroCode_uopBGEU : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopBGEU   ";
      MicroCode_uopLB : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLB     ";
      MicroCode_uopLH : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLH     ";
      MicroCode_uopLW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLW     ";
      MicroCode_uopLBU : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLBU    ";
      MicroCode_uopLHU : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLHU    ";
      MicroCode_uopSB : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSB     ";
      MicroCode_uopSH : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSH     ";
      MicroCode_uopSW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSW     ";
      MicroCode_uopADDI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopADDI   ";
      MicroCode_uopSLTI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLTI   ";
      MicroCode_uopSLTIU : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLTIU  ";
      MicroCode_uopXORI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopXORI   ";
      MicroCode_uopORI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopORI    ";
      MicroCode_uopANDI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopANDI   ";
      MicroCode_uopSLLI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLLI   ";
      MicroCode_uopSRLI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRLI   ";
      MicroCode_uopSRAI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRAI   ";
      MicroCode_uopADD : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopADD    ";
      MicroCode_uopSUB : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSUB    ";
      MicroCode_uopSLL : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLL    ";
      MicroCode_uopSLT : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLT    ";
      MicroCode_uopSLTU : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLTU   ";
      MicroCode_uopXOR : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopXOR    ";
      MicroCode_uopSRL : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRL    ";
      MicroCode_uopSRA : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRA    ";
      MicroCode_uopOR : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopOR     ";
      MicroCode_uopAND : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopAND    ";
      MicroCode_uopFENCE : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopFENCE_I";
      MicroCode_uopECALL : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopECALL  ";
      MicroCode_uopEBREAK : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopEBREAK ";
      MicroCode_uopCSRRW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopCSRRCI ";
      MicroCode_uopLWU : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLWU    ";
      MicroCode_uopLD : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopLD     ";
      MicroCode_uopSD : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSD     ";
      MicroCode_uopADDIW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopADDIW  ";
      MicroCode_uopSLLIW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRAIW  ";
      MicroCode_uopADDW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopADDW   ";
      MicroCode_uopSUBW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSUBW   ";
      MicroCode_uopSLLW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSLLW   ";
      MicroCode_uopSRLW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRLW   ";
      MicroCode_uopSRAW : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "uopSRAW   ";
      default : coreArea_pipeline_ctrl_4_down_Decoder_MicroCode_string = "??????????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL)
      Imm_Select_N_IMM : coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string = "N_IMM";
      Imm_Select_I_IMM : coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string = "I_IMM";
      Imm_Select_S_IMM : coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string = "S_IMM";
      Imm_Select_B_IMM : coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string = "B_IMM";
      Imm_Select_U_IMM : coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string = "U_IMM";
      Imm_Select_J_IMM : coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string = "J_IMM";
      default : coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL_string = "?????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_down_Decoder_LEGAL)
      YESNO_Y : coreArea_pipeline_ctrl_4_down_Decoder_LEGAL_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_4_down_Decoder_LEGAL_string = "N";
      default : coreArea_pipeline_ctrl_4_down_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_up_Decoder_MicroCode)
      MicroCode_uopNOP : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopNOP    ";
      MicroCode_uopLUI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLUI    ";
      MicroCode_uopAUIPC : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopAUIPC  ";
      MicroCode_uopJAL : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopJAL    ";
      MicroCode_uopJALR : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopJALR   ";
      MicroCode_uopBEQ : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopBEQ    ";
      MicroCode_uopBNE : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopBNE    ";
      MicroCode_uopBLT : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopBLT    ";
      MicroCode_uopBGE : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopBGE    ";
      MicroCode_uopBLTU : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopBLTU   ";
      MicroCode_uopBGEU : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopBGEU   ";
      MicroCode_uopLB : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLB     ";
      MicroCode_uopLH : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLH     ";
      MicroCode_uopLW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLW     ";
      MicroCode_uopLBU : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLBU    ";
      MicroCode_uopLHU : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLHU    ";
      MicroCode_uopSB : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSB     ";
      MicroCode_uopSH : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSH     ";
      MicroCode_uopSW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSW     ";
      MicroCode_uopADDI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopADDI   ";
      MicroCode_uopSLTI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLTI   ";
      MicroCode_uopSLTIU : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLTIU  ";
      MicroCode_uopXORI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopXORI   ";
      MicroCode_uopORI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopORI    ";
      MicroCode_uopANDI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopANDI   ";
      MicroCode_uopSLLI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLLI   ";
      MicroCode_uopSRLI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRLI   ";
      MicroCode_uopSRAI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRAI   ";
      MicroCode_uopADD : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopADD    ";
      MicroCode_uopSUB : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSUB    ";
      MicroCode_uopSLL : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLL    ";
      MicroCode_uopSLT : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLT    ";
      MicroCode_uopSLTU : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLTU   ";
      MicroCode_uopXOR : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopXOR    ";
      MicroCode_uopSRL : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRL    ";
      MicroCode_uopSRA : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRA    ";
      MicroCode_uopOR : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopOR     ";
      MicroCode_uopAND : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopAND    ";
      MicroCode_uopFENCE : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopFENCE_I";
      MicroCode_uopECALL : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopECALL  ";
      MicroCode_uopEBREAK : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopEBREAK ";
      MicroCode_uopCSRRW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopCSRRCI ";
      MicroCode_uopLWU : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLWU    ";
      MicroCode_uopLD : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopLD     ";
      MicroCode_uopSD : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSD     ";
      MicroCode_uopADDIW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopADDIW  ";
      MicroCode_uopSLLIW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRAIW  ";
      MicroCode_uopADDW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopADDW   ";
      MicroCode_uopSUBW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSUBW   ";
      MicroCode_uopSLLW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSLLW   ";
      MicroCode_uopSRLW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRLW   ";
      MicroCode_uopSRAW : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "uopSRAW   ";
      default : coreArea_pipeline_ctrl_5_up_Decoder_MicroCode_string = "??????????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_up_Decoder_LEGAL)
      YESNO_Y : coreArea_pipeline_ctrl_5_up_Decoder_LEGAL_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_5_up_Decoder_LEGAL_string = "N";
      default : coreArea_pipeline_ctrl_5_up_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_up_Decoder_MicroCode)
      MicroCode_uopNOP : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopNOP    ";
      MicroCode_uopLUI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLUI    ";
      MicroCode_uopAUIPC : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopAUIPC  ";
      MicroCode_uopJAL : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopJAL    ";
      MicroCode_uopJALR : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopJALR   ";
      MicroCode_uopBEQ : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopBEQ    ";
      MicroCode_uopBNE : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopBNE    ";
      MicroCode_uopBLT : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopBLT    ";
      MicroCode_uopBGE : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopBGE    ";
      MicroCode_uopBLTU : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopBLTU   ";
      MicroCode_uopBGEU : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopBGEU   ";
      MicroCode_uopLB : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLB     ";
      MicroCode_uopLH : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLH     ";
      MicroCode_uopLW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLW     ";
      MicroCode_uopLBU : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLBU    ";
      MicroCode_uopLHU : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLHU    ";
      MicroCode_uopSB : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSB     ";
      MicroCode_uopSH : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSH     ";
      MicroCode_uopSW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSW     ";
      MicroCode_uopADDI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopADDI   ";
      MicroCode_uopSLTI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLTI   ";
      MicroCode_uopSLTIU : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLTIU  ";
      MicroCode_uopXORI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopXORI   ";
      MicroCode_uopORI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopORI    ";
      MicroCode_uopANDI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopANDI   ";
      MicroCode_uopSLLI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLLI   ";
      MicroCode_uopSRLI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRLI   ";
      MicroCode_uopSRAI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRAI   ";
      MicroCode_uopADD : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopADD    ";
      MicroCode_uopSUB : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSUB    ";
      MicroCode_uopSLL : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLL    ";
      MicroCode_uopSLT : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLT    ";
      MicroCode_uopSLTU : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLTU   ";
      MicroCode_uopXOR : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopXOR    ";
      MicroCode_uopSRL : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRL    ";
      MicroCode_uopSRA : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRA    ";
      MicroCode_uopOR : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopOR     ";
      MicroCode_uopAND : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopAND    ";
      MicroCode_uopFENCE : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopFENCE_I";
      MicroCode_uopECALL : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopECALL  ";
      MicroCode_uopEBREAK : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopEBREAK ";
      MicroCode_uopCSRRW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopCSRRCI ";
      MicroCode_uopLWU : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLWU    ";
      MicroCode_uopLD : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopLD     ";
      MicroCode_uopSD : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSD     ";
      MicroCode_uopADDIW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopADDIW  ";
      MicroCode_uopSLLIW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRAIW  ";
      MicroCode_uopADDW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopADDW   ";
      MicroCode_uopSUBW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSUBW   ";
      MicroCode_uopSLLW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSLLW   ";
      MicroCode_uopSRLW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRLW   ";
      MicroCode_uopSRAW : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "uopSRAW   ";
      default : coreArea_pipeline_ctrl_4_up_Decoder_MicroCode_string = "??????????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL)
      Imm_Select_N_IMM : coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string = "N_IMM";
      Imm_Select_I_IMM : coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string = "I_IMM";
      Imm_Select_S_IMM : coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string = "S_IMM";
      Imm_Select_B_IMM : coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string = "B_IMM";
      Imm_Select_U_IMM : coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string = "U_IMM";
      Imm_Select_J_IMM : coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string = "J_IMM";
      default : coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL_string = "?????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_up_Decoder_LEGAL)
      YESNO_Y : coreArea_pipeline_ctrl_4_up_Decoder_LEGAL_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_4_up_Decoder_LEGAL_string = "N";
      default : coreArea_pipeline_ctrl_4_up_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_down_Decoder_LEGAL)
      YESNO_Y : coreArea_pipeline_ctrl_6_down_Decoder_LEGAL_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_6_down_Decoder_LEGAL_string = "N";
      default : coreArea_pipeline_ctrl_6_down_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_LEGAL)
      YESNO_Y : coreArea_pipeline_ctrl_6_up_Decoder_LEGAL_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_6_up_Decoder_LEGAL_string = "N";
      default : coreArea_pipeline_ctrl_6_up_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopNOP : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopNOP    ";
      MicroCode_uopLUI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLUI    ";
      MicroCode_uopAUIPC : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopAUIPC  ";
      MicroCode_uopJAL : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopJAL    ";
      MicroCode_uopJALR : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopJALR   ";
      MicroCode_uopBEQ : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopBEQ    ";
      MicroCode_uopBNE : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopBNE    ";
      MicroCode_uopBLT : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopBLT    ";
      MicroCode_uopBGE : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopBGE    ";
      MicroCode_uopBLTU : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopBLTU   ";
      MicroCode_uopBGEU : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopBGEU   ";
      MicroCode_uopLB : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLB     ";
      MicroCode_uopLH : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLH     ";
      MicroCode_uopLW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLW     ";
      MicroCode_uopLBU : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLBU    ";
      MicroCode_uopLHU : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLHU    ";
      MicroCode_uopSB : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSB     ";
      MicroCode_uopSH : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSH     ";
      MicroCode_uopSW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSW     ";
      MicroCode_uopADDI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopADDI   ";
      MicroCode_uopSLTI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLTI   ";
      MicroCode_uopSLTIU : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLTIU  ";
      MicroCode_uopXORI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopXORI   ";
      MicroCode_uopORI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopORI    ";
      MicroCode_uopANDI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopANDI   ";
      MicroCode_uopSLLI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLLI   ";
      MicroCode_uopSRLI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRLI   ";
      MicroCode_uopSRAI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRAI   ";
      MicroCode_uopADD : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopADD    ";
      MicroCode_uopSUB : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSUB    ";
      MicroCode_uopSLL : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLL    ";
      MicroCode_uopSLT : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLT    ";
      MicroCode_uopSLTU : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLTU   ";
      MicroCode_uopXOR : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopXOR    ";
      MicroCode_uopSRL : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRL    ";
      MicroCode_uopSRA : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRA    ";
      MicroCode_uopOR : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopOR     ";
      MicroCode_uopAND : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopAND    ";
      MicroCode_uopFENCE : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopFENCE_I";
      MicroCode_uopECALL : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopECALL  ";
      MicroCode_uopEBREAK : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopEBREAK ";
      MicroCode_uopCSRRW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopCSRRCI ";
      MicroCode_uopLWU : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLWU    ";
      MicroCode_uopLD : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopLD     ";
      MicroCode_uopSD : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSD     ";
      MicroCode_uopADDIW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopADDIW  ";
      MicroCode_uopSLLIW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRAIW  ";
      MicroCode_uopADDW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopADDW   ";
      MicroCode_uopSUBW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSUBW   ";
      MicroCode_uopSLLW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSLLW   ";
      MicroCode_uopSRLW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRLW   ";
      MicroCode_uopSRAW : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "uopSRAW   ";
      default : coreArea_pipeline_ctrl_6_up_Decoder_MicroCode_string = "??????????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL)
      Imm_Select_N_IMM : coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string = "N_IMM";
      Imm_Select_I_IMM : coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string = "I_IMM";
      Imm_Select_S_IMM : coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string = "S_IMM";
      Imm_Select_B_IMM : coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string = "B_IMM";
      Imm_Select_U_IMM : coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string = "U_IMM";
      Imm_Select_J_IMM : coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string = "J_IMM";
      default : coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL_string = "?????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT)
      ExecutionUnitEnum_ALU : coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT_string = "ALU";
      ExecutionUnitEnum_FPU : coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT_string = "FPU";
      ExecutionUnitEnum_AGU : coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT_string = "AGU";
      ExecutionUnitEnum_BR : coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT_string = "BR ";
      ExecutionUnitEnum_NA : coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT_string = "NA ";
      default : coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT_string = "???";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ)
      YESNO_Y : coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_string = "N";
      default : coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ)
      YESNO_Y : coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_string = "N";
      default : coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_IS_W)
      YESNO_Y : coreArea_pipeline_ctrl_3_down_Decoder_IS_W_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_3_down_Decoder_IS_W_string = "N";
      default : coreArea_pipeline_ctrl_3_down_Decoder_IS_W_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_IS_BR)
      YESNO_Y : coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string = "N";
      default : coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_MicroCode)
      MicroCode_uopNOP : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopNOP    ";
      MicroCode_uopLUI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLUI    ";
      MicroCode_uopAUIPC : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopAUIPC  ";
      MicroCode_uopJAL : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopJAL    ";
      MicroCode_uopJALR : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopJALR   ";
      MicroCode_uopBEQ : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopBEQ    ";
      MicroCode_uopBNE : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopBNE    ";
      MicroCode_uopBLT : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopBLT    ";
      MicroCode_uopBGE : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopBGE    ";
      MicroCode_uopBLTU : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopBLTU   ";
      MicroCode_uopBGEU : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopBGEU   ";
      MicroCode_uopLB : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLB     ";
      MicroCode_uopLH : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLH     ";
      MicroCode_uopLW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLW     ";
      MicroCode_uopLBU : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLBU    ";
      MicroCode_uopLHU : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLHU    ";
      MicroCode_uopSB : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSB     ";
      MicroCode_uopSH : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSH     ";
      MicroCode_uopSW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSW     ";
      MicroCode_uopADDI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopADDI   ";
      MicroCode_uopSLTI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLTI   ";
      MicroCode_uopSLTIU : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLTIU  ";
      MicroCode_uopXORI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopXORI   ";
      MicroCode_uopORI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopORI    ";
      MicroCode_uopANDI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopANDI   ";
      MicroCode_uopSLLI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLLI   ";
      MicroCode_uopSRLI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRLI   ";
      MicroCode_uopSRAI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRAI   ";
      MicroCode_uopADD : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopADD    ";
      MicroCode_uopSUB : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSUB    ";
      MicroCode_uopSLL : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLL    ";
      MicroCode_uopSLT : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLT    ";
      MicroCode_uopSLTU : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLTU   ";
      MicroCode_uopXOR : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopXOR    ";
      MicroCode_uopSRL : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRL    ";
      MicroCode_uopSRA : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRA    ";
      MicroCode_uopOR : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopOR     ";
      MicroCode_uopAND : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopAND    ";
      MicroCode_uopFENCE : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopFENCE_I";
      MicroCode_uopECALL : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopECALL  ";
      MicroCode_uopEBREAK : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopEBREAK ";
      MicroCode_uopCSRRW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopCSRRCI ";
      MicroCode_uopLWU : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLWU    ";
      MicroCode_uopLD : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopLD     ";
      MicroCode_uopSD : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSD     ";
      MicroCode_uopADDIW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopADDIW  ";
      MicroCode_uopSLLIW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRAIW  ";
      MicroCode_uopADDW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopADDW   ";
      MicroCode_uopSUBW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSUBW   ";
      MicroCode_uopSLLW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSLLW   ";
      MicroCode_uopSRLW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRLW   ";
      MicroCode_uopSRAW : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "uopSRAW   ";
      default : coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_string = "??????????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL)
      Imm_Select_N_IMM : coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string = "N_IMM";
      Imm_Select_I_IMM : coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string = "I_IMM";
      Imm_Select_S_IMM : coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string = "S_IMM";
      Imm_Select_B_IMM : coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string = "B_IMM";
      Imm_Select_U_IMM : coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string = "U_IMM";
      Imm_Select_J_IMM : coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string = "J_IMM";
      default : coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_string = "?????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN)
      YESNO_Y : coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string = "N";
      default : coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE)
      RSTYPE_RS_INT : coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_INT";
      RSTYPE_RS_FP : coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_VEC";
      RSTYPE_IMMED : coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "IMMED ";
      RSTYPE_RS_NA : coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_NA ";
      default : coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE)
      RDTYPE_RD_INT : coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_INT";
      RDTYPE_RD_FP : coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_FP ";
      RDTYPE_RD_VEC : coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_VEC";
      RDTYPE_RD_NA : coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_NA ";
      default : coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT)
      ExecutionUnitEnum_ALU : coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "ALU";
      ExecutionUnitEnum_FPU : coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "FPU";
      ExecutionUnitEnum_AGU : coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "AGU";
      ExecutionUnitEnum_BR : coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "BR ";
      ExecutionUnitEnum_NA : coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "NA ";
      default : coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "???";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_IS_FP)
      YESNO_Y : coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string = "N";
      default : coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string = "?";
    endcase
  end
  always @(*) begin
    case(coreArea_pipeline_ctrl_3_down_Decoder_LEGAL)
      YESNO_Y : coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string = "Y";
      YESNO_N : coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string = "N";
      default : coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT)
      ExecutionUnitEnum_ALU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "ALU";
      ExecutionUnitEnum_FPU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "FPU";
      ExecutionUnitEnum_AGU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "AGU";
      ExecutionUnitEnum_BR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "BR ";
      ExecutionUnitEnum_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_string = "???";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1)
      ExecutionUnitEnum_ALU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1_string = "ALU";
      ExecutionUnitEnum_FPU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1_string = "FPU";
      ExecutionUnitEnum_AGU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1_string = "AGU";
      ExecutionUnitEnum_BR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1_string = "BR ";
      ExecutionUnitEnum_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1_string = "NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1_string = "???";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2)
      ExecutionUnitEnum_ALU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2_string = "ALU";
      ExecutionUnitEnum_FPU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2_string = "FPU";
      ExecutionUnitEnum_AGU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2_string = "AGU";
      ExecutionUnitEnum_BR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2_string = "BR ";
      ExecutionUnitEnum_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2_string = "NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2_string = "???";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE)
      RDTYPE_RD_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_INT";
      RDTYPE_RD_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_FP ";
      RDTYPE_RD_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_VEC";
      RDTYPE_RD_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "RD_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1)
      RDTYPE_RD_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1_string = "RD_INT";
      RDTYPE_RD_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1_string = "RD_FP ";
      RDTYPE_RD_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1_string = "RD_VEC";
      RDTYPE_RD_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1_string = "RD_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2)
      RDTYPE_RD_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2_string = "RD_INT";
      RDTYPE_RD_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2_string = "RD_FP ";
      RDTYPE_RD_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2_string = "RD_VEC";
      RDTYPE_RD_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2_string = "RD_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE)
      RSTYPE_RS_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_INT";
      RSTYPE_RS_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_VEC";
      RSTYPE_IMMED : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "IMMED ";
      RSTYPE_RS_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "RS_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1)
      RSTYPE_RS_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1_string = "RS_INT";
      RSTYPE_RS_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1_string = "RS_FP ";
      RSTYPE_RS_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1_string = "RS_VEC";
      RSTYPE_IMMED : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1_string = "IMMED ";
      RSTYPE_RS_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1_string = "RS_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2)
      RSTYPE_RS_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2_string = "RS_INT";
      RSTYPE_RS_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2_string = "RS_FP ";
      RSTYPE_RS_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2_string = "RS_VEC";
      RSTYPE_IMMED : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2_string = "IMMED ";
      RSTYPE_RS_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2_string = "RS_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE)
      RSTYPE_RS_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_INT";
      RSTYPE_RS_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_FP ";
      RSTYPE_RS_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_VEC";
      RSTYPE_IMMED : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "IMMED ";
      RSTYPE_RS_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "RS_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1)
      RSTYPE_RS_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1_string = "RS_INT";
      RSTYPE_RS_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1_string = "RS_FP ";
      RSTYPE_RS_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1_string = "RS_VEC";
      RSTYPE_IMMED : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1_string = "IMMED ";
      RSTYPE_RS_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1_string = "RS_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2)
      RSTYPE_RS_INT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2_string = "RS_INT";
      RSTYPE_RS_FP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2_string = "RS_FP ";
      RSTYPE_RS_VEC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2_string = "RS_VEC";
      RSTYPE_IMMED : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2_string = "IMMED ";
      RSTYPE_RS_NA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2_string = "RS_NA ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2_string = "??????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2)
      Imm_Select_N_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string = "N_IMM";
      Imm_Select_I_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string = "I_IMM";
      Imm_Select_S_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string = "S_IMM";
      Imm_Select_B_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string = "B_IMM";
      Imm_Select_U_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string = "U_IMM";
      Imm_Select_J_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string = "J_IMM";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2_string = "?????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3)
      Imm_Select_N_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string = "N_IMM";
      Imm_Select_I_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string = "I_IMM";
      Imm_Select_S_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string = "S_IMM";
      Imm_Select_B_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string = "B_IMM";
      Imm_Select_U_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string = "U_IMM";
      Imm_Select_J_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string = "J_IMM";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3_string = "?????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4)
      Imm_Select_N_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string = "N_IMM";
      Imm_Select_I_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string = "I_IMM";
      Imm_Select_S_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string = "S_IMM";
      Imm_Select_B_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string = "B_IMM";
      Imm_Select_U_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string = "U_IMM";
      Imm_Select_J_IMM : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string = "J_IMM";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4_string = "?????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3)
      MicroCode_uopNOP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopNOP    ";
      MicroCode_uopLUI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLUI    ";
      MicroCode_uopAUIPC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopAUIPC  ";
      MicroCode_uopJAL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopJAL    ";
      MicroCode_uopJALR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopJALR   ";
      MicroCode_uopBEQ : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopBEQ    ";
      MicroCode_uopBNE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopBNE    ";
      MicroCode_uopBLT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopBLT    ";
      MicroCode_uopBGE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopBGE    ";
      MicroCode_uopBLTU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopBLTU   ";
      MicroCode_uopBGEU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopBGEU   ";
      MicroCode_uopLB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLB     ";
      MicroCode_uopLH : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLH     ";
      MicroCode_uopLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLW     ";
      MicroCode_uopLBU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLBU    ";
      MicroCode_uopLHU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLHU    ";
      MicroCode_uopSB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSB     ";
      MicroCode_uopSH : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSH     ";
      MicroCode_uopSW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSW     ";
      MicroCode_uopADDI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopADDI   ";
      MicroCode_uopSLTI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLTI   ";
      MicroCode_uopSLTIU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLTIU  ";
      MicroCode_uopXORI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopXORI   ";
      MicroCode_uopORI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopORI    ";
      MicroCode_uopANDI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopANDI   ";
      MicroCode_uopSLLI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLLI   ";
      MicroCode_uopSRLI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRLI   ";
      MicroCode_uopSRAI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRAI   ";
      MicroCode_uopADD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopADD    ";
      MicroCode_uopSUB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSUB    ";
      MicroCode_uopSLL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLL    ";
      MicroCode_uopSLT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLT    ";
      MicroCode_uopSLTU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLTU   ";
      MicroCode_uopXOR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopXOR    ";
      MicroCode_uopSRL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRL    ";
      MicroCode_uopSRA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRA    ";
      MicroCode_uopOR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopOR     ";
      MicroCode_uopAND : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopAND    ";
      MicroCode_uopFENCE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopFENCE_I";
      MicroCode_uopECALL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopECALL  ";
      MicroCode_uopEBREAK : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopEBREAK ";
      MicroCode_uopCSRRW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopCSRRCI ";
      MicroCode_uopLWU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLWU    ";
      MicroCode_uopLD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopLD     ";
      MicroCode_uopSD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSD     ";
      MicroCode_uopADDIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopADDIW  ";
      MicroCode_uopSLLIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRAIW  ";
      MicroCode_uopADDW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopADDW   ";
      MicroCode_uopSUBW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSUBW   ";
      MicroCode_uopSLLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSLLW   ";
      MicroCode_uopSRLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRLW   ";
      MicroCode_uopSRAW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "uopSRAW   ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3_string = "??????????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4)
      MicroCode_uopNOP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopNOP    ";
      MicroCode_uopLUI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLUI    ";
      MicroCode_uopAUIPC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopAUIPC  ";
      MicroCode_uopJAL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopJAL    ";
      MicroCode_uopJALR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopJALR   ";
      MicroCode_uopBEQ : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopBEQ    ";
      MicroCode_uopBNE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopBNE    ";
      MicroCode_uopBLT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopBLT    ";
      MicroCode_uopBGE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopBGE    ";
      MicroCode_uopBLTU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopBLTU   ";
      MicroCode_uopBGEU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopBGEU   ";
      MicroCode_uopLB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLB     ";
      MicroCode_uopLH : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLH     ";
      MicroCode_uopLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLW     ";
      MicroCode_uopLBU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLBU    ";
      MicroCode_uopLHU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLHU    ";
      MicroCode_uopSB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSB     ";
      MicroCode_uopSH : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSH     ";
      MicroCode_uopSW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSW     ";
      MicroCode_uopADDI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopADDI   ";
      MicroCode_uopSLTI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLTI   ";
      MicroCode_uopSLTIU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLTIU  ";
      MicroCode_uopXORI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopXORI   ";
      MicroCode_uopORI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopORI    ";
      MicroCode_uopANDI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopANDI   ";
      MicroCode_uopSLLI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLLI   ";
      MicroCode_uopSRLI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRLI   ";
      MicroCode_uopSRAI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRAI   ";
      MicroCode_uopADD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopADD    ";
      MicroCode_uopSUB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSUB    ";
      MicroCode_uopSLL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLL    ";
      MicroCode_uopSLT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLT    ";
      MicroCode_uopSLTU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLTU   ";
      MicroCode_uopXOR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopXOR    ";
      MicroCode_uopSRL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRL    ";
      MicroCode_uopSRA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRA    ";
      MicroCode_uopOR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopOR     ";
      MicroCode_uopAND : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopAND    ";
      MicroCode_uopFENCE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopFENCE_I";
      MicroCode_uopECALL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopECALL  ";
      MicroCode_uopEBREAK : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopEBREAK ";
      MicroCode_uopCSRRW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopCSRRCI ";
      MicroCode_uopLWU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLWU    ";
      MicroCode_uopLD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopLD     ";
      MicroCode_uopSD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSD     ";
      MicroCode_uopADDIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopADDIW  ";
      MicroCode_uopSLLIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRAIW  ";
      MicroCode_uopADDW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopADDW   ";
      MicroCode_uopSUBW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSUBW   ";
      MicroCode_uopSLLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSLLW   ";
      MicroCode_uopSRLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRLW   ";
      MicroCode_uopSRAW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "uopSRAW   ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_string = "??????????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5)
      MicroCode_uopNOP : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopNOP    ";
      MicroCode_uopLUI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLUI    ";
      MicroCode_uopAUIPC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopAUIPC  ";
      MicroCode_uopJAL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopJAL    ";
      MicroCode_uopJALR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopJALR   ";
      MicroCode_uopBEQ : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopBEQ    ";
      MicroCode_uopBNE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopBNE    ";
      MicroCode_uopBLT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopBLT    ";
      MicroCode_uopBGE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopBGE    ";
      MicroCode_uopBLTU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopBLTU   ";
      MicroCode_uopBGEU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopBGEU   ";
      MicroCode_uopLB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLB     ";
      MicroCode_uopLH : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLH     ";
      MicroCode_uopLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLW     ";
      MicroCode_uopLBU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLBU    ";
      MicroCode_uopLHU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLHU    ";
      MicroCode_uopSB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSB     ";
      MicroCode_uopSH : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSH     ";
      MicroCode_uopSW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSW     ";
      MicroCode_uopADDI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopADDI   ";
      MicroCode_uopSLTI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLTI   ";
      MicroCode_uopSLTIU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLTIU  ";
      MicroCode_uopXORI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopXORI   ";
      MicroCode_uopORI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopORI    ";
      MicroCode_uopANDI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopANDI   ";
      MicroCode_uopSLLI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLLI   ";
      MicroCode_uopSRLI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRLI   ";
      MicroCode_uopSRAI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRAI   ";
      MicroCode_uopADD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopADD    ";
      MicroCode_uopSUB : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSUB    ";
      MicroCode_uopSLL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLL    ";
      MicroCode_uopSLT : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLT    ";
      MicroCode_uopSLTU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLTU   ";
      MicroCode_uopXOR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopXOR    ";
      MicroCode_uopSRL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRL    ";
      MicroCode_uopSRA : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRA    ";
      MicroCode_uopOR : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopOR     ";
      MicroCode_uopAND : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopAND    ";
      MicroCode_uopFENCE : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopFENCE  ";
      MicroCode_uopFENCE_I : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopFENCE_I";
      MicroCode_uopECALL : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopECALL  ";
      MicroCode_uopEBREAK : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopEBREAK ";
      MicroCode_uopCSRRW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopCSRRW  ";
      MicroCode_uopCSRRS : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopCSRRS  ";
      MicroCode_uopCSRRC : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopCSRRC  ";
      MicroCode_uopCSRRWI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopCSRRWI ";
      MicroCode_uopCSRRSI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopCSRRSI ";
      MicroCode_uopCSRRCI : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopCSRRCI ";
      MicroCode_uopLWU : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLWU    ";
      MicroCode_uopLD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopLD     ";
      MicroCode_uopSD : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSD     ";
      MicroCode_uopADDIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopADDIW  ";
      MicroCode_uopSLLIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLLIW  ";
      MicroCode_uopSRLIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRLIW  ";
      MicroCode_uopSRAIW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRAIW  ";
      MicroCode_uopADDW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopADDW   ";
      MicroCode_uopSUBW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSUBW   ";
      MicroCode_uopSLLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSLLW   ";
      MicroCode_uopSRLW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRLW   ";
      MicroCode_uopSRAW : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "uopSRAW   ";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5_string = "??????????";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3_string = "?";
    endcase
  end
  always @(*) begin
    case(_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4)
      YESNO_Y : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4_string = "Y";
      YESNO_N : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4_string = "N";
      default : _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4_string = "?";
    endcase
  end
  `endif

  always @(*) begin
    _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l168 = 1'b0;
    if(when_Lsu_l151) begin
      if(!when_Lsu_l152) begin
        if(!coreArea_lsu_logic_responseArriving) begin
          _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l168 = 1'b1;
        end
      end
    end
  end

  always @(*) begin
    _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l159 = 1'b0;
    if(when_Lsu_l151) begin
      if(when_Lsu_l152) begin
        if(!when_Lsu_l153) begin
          _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l159 = 1'b1;
        end
      end
    end
  end

  always @(*) begin
    _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l157 = 1'b0;
    if(when_Lsu_l151) begin
      if(when_Lsu_l152) begin
        if(when_Lsu_l153) begin
          _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l157 = 1'b1;
        end
      end
    end
  end

  assign coreArea_pipeline_ctrl_0_up_valid = 1'b1;
  assign coreArea_pipeline_ctrl_0_down_PC_PC = coreArea_pc_PC_cur;
  assign coreArea_pc_exception_valid = 1'b0;
  assign coreArea_pc_exception_payload_vector = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
  assign coreArea_pc_flush_valid = 1'b0;
  assign coreArea_pc_flush_payload_address = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
  assign coreArea_fetch_cmdFire = (coreArea_fetch_io_readCmd_cmd_valid && coreArea_fetch_io_readCmd_cmd_ready);
  assign coreArea_fetch_io_readCmd_cmd_valid = (((coreArea_pipeline_ctrl_1_up_isValid && (! coreArea_fetch_cmdArea_reqSent)) && (coreArea_fetch_inflight < _zz_coreArea_fetch_io_readCmd_cmd_valid)) && (! coreArea_fetch_io_flush));
  assign coreArea_fetch_io_readCmd_cmd_payload_address = coreArea_pipeline_ctrl_1_down_PC_PC;
  assign coreArea_fetch_io_readCmd_cmd_payload_id = coreArea_fetch_epoch;
  assign coreArea_pipeline_ctrl_1_haltRequest_Fetch_l80 = ((! coreArea_fetch_cmdArea_reqSent) && (! coreArea_fetch_cmdFire));
  assign coreArea_fetch_rspArea_epochMatch = (coreArea_fetch_fifo_io_pop_payload_epoch == coreArea_fetch_epoch);
  assign coreArea_fetch_rspArea_stalePacket = (coreArea_fetch_fifo_io_pop_valid && (! coreArea_fetch_rspArea_epochMatch));
  assign coreArea_pipeline_ctrl_2_throwWhen_Fetch_l92 = coreArea_fetch_rspArea_stalePacket;
  assign coreArea_pipeline_ctrl_2_haltRequest_Fetch_l95 = (! coreArea_fetch_fifo_io_pop_valid);
  assign coreArea_pipeline_ctrl_2_down_Decoder_INSTRUCTION = coreArea_fetch_fifo_io_pop_payload_data[31 : 0];
  assign coreArea_pipeline_ctrl_2_down_Common_SPEC_EPOCH = coreArea_fetch_io_currentEpoch;
  assign coreArea_fetch_fifo_io_pop_ready = (coreArea_pipeline_ctrl_2_down_isFiring || coreArea_fetch_rspArea_stalePacket);
  assign coreArea_pipeline_ctrl_3_down_Decoder_VALID = (|{((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h0000005f) == 32'h00000017),{((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & 32'h0000007f) == 32'h0000006f),{((coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION & _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID) == 32'h00000003),{(_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_1 == _zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_2),{_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_3,{_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_4,_zz_coreArea_pipeline_ctrl_3_down_Decoder_VALID_5}}}}}});
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1 = 1'b0;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL = _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL;
  assign coreArea_pipeline_ctrl_3_down_Decoder_LEGAL = _zz_coreArea_pipeline_ctrl_3_down_Decoder_LEGAL_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h0) == 32'h0);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1 = (|_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP;
  assign coreArea_pipeline_ctrl_3_down_Decoder_IS_FP = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_FP_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000040) == 32'h00000040);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000010) == 32'h0);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1 = {1'b0,{(|_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W),(|_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ)}};
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT = _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT;
  assign coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT = _zz_coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000070) == 32'h00000020);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000044) == 32'h00000040);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1 = {(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL,_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode}),(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL,_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode})};
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE;
  assign coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RDTYPE_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_1 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000044) == 32'h00000004);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_1 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000018) == 32'h00000008);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1 = {(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_1,_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_1}),{1'b0,1'b0}};
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE;
  assign coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_1 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000020) == 32'h0);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1 = {(|((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000014) == 32'h00000004)),{(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_1,_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_1}),(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_1,_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_1})}};
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE;
  assign coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE = _zz_coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1 = (|_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN = _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN;
  assign coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN = _zz_coreArea_pipeline_ctrl_3_down_Decoder_FSR3EN_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3 = {(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_1,_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_1}),{(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL,_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode}),(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ,((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000024) == 32'h0)})}};
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_3;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_2;
  assign coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL_4;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_2 = ((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000048) == 32'h00000008);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4 = {(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_2,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_2,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_5}}}),{(|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_2,{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_10,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_11}}),{(|{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_26,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_29}),{(|_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_56),{_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_89,_zz__zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4_124}}}}};
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_4;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_3;
  assign coreArea_pipeline_ctrl_3_down_Decoder_MicroCode = _zz_coreArea_pipeline_ctrl_3_down_Decoder_MicroCode_5;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1 = (|((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000040) == 32'h0));
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_1;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR;
  assign coreArea_pipeline_ctrl_3_down_Decoder_IS_BR = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_BR_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2 = (|{_zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W,((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000008) == 32'h0)});
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_1;
  assign coreArea_pipeline_ctrl_3_down_Decoder_IS_W = _zz_coreArea_pipeline_ctrl_3_down_Decoder_IS_W_3;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2 = (|_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ);
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_2;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_1;
  assign coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ = _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_LDQ_3;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3 = (|{((coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION & 32'h00000010) == 32'h00000010),{_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ,_zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_1}});
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_3;
  assign _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4 = _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_2;
  assign coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ = _zz_coreArea_pipeline_ctrl_3_down_Decoder_USE_STQ_4;
  assign coreArea_pipeline_ctrl_3_down_Decoder_RD_ADDR = coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION[11 : 7];
  assign coreArea_pipeline_ctrl_3_down_Decoder_RS1_ADDR = coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION[19 : 15];
  assign coreArea_pipeline_ctrl_3_down_Decoder_RS2_ADDR = coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION[24 : 20];
  always @(*) begin
    coreArea_pipeline_ctrl_4_down_Common_LANE_SEL = 1'b0;
    if(when_scheduler_l144) begin
      coreArea_pipeline_ctrl_4_down_Common_LANE_SEL = coreArea_pipeline_ctrl_4_up_isFiring;
    end
    if(when_scheduler_l148) begin
      coreArea_pipeline_ctrl_4_down_Common_LANE_SEL = coreArea_pipeline_ctrl_4_up_isFiring;
    end
    if(when_scheduler_l152) begin
      coreArea_pipeline_ctrl_4_down_Common_LANE_SEL = coreArea_pipeline_ctrl_4_up_isFiring;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOALU = 1'b0;
    if(when_scheduler_l144) begin
      coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOALU = 1'b1;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOBRANCH = 1'b0;
    if(when_scheduler_l148) begin
      coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOBRANCH = 1'b1;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOAGU = 1'b0;
    if(when_scheduler_l152) begin
      coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOAGU = 1'b1;
    end
  end

  assign when_scheduler_l144 = (coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT == ExecutionUnitEnum_ALU);
  assign when_scheduler_l148 = (coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT == ExecutionUnitEnum_BR);
  assign when_scheduler_l152 = (coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT == ExecutionUnitEnum_AGU);
  assign when_scheduler_l187 = (coreArea_pipeline_ctrl_4_up_isFiring && (coreArea_pipeline_ctrl_4_up_Decoder_RD_ADDR != 5'h0));
  assign when_scheduler_l196 = ((coreArea_pipeline_ctrl_7_down_isFiring && coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_valid) && (coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_address != 5'h0));
  assign when_scheduler_l200 = (coreArea_pipeline_ctrl_4_up_isFiring && (coreArea_pipeline_ctrl_4_up_Decoder_RD_ADDR != 5'h0));
  assign coreArea_dispatcher_hcs_writes_rs1Busy = coreArea_dispatcher_hcs_regBusy[coreArea_pipeline_ctrl_4_up_Decoder_RS1_ADDR];
  assign coreArea_dispatcher_hcs_writes_rs2Busy = coreArea_dispatcher_hcs_regBusy[coreArea_pipeline_ctrl_4_up_Decoder_RS2_ADDR];
  assign coreArea_dispatcher_hcs_writes_hazard = (coreArea_pipeline_ctrl_4_up_Decoder_VALID && (coreArea_dispatcher_hcs_writes_rs1Busy || coreArea_dispatcher_hcs_writes_rs2Busy));
  assign coreArea_pipeline_ctrl_4_haltRequest_scheduler_l214 = coreArea_dispatcher_hcs_writes_hazard;
  always @(*) begin
    coreArea_dispatcher_hcs_hazards = _zz_coreArea_dispatcher_hcs_hazards;
    coreArea_dispatcher_hcs_hazards = 4'b0000;
  end

  always @(*) begin
    coreArea_dispatcher_hcs_init_willIncrement = 1'b0;
    if(when_scheduler_l250) begin
      coreArea_dispatcher_hcs_init_willIncrement = 1'b1;
    end
  end

  assign coreArea_dispatcher_hcs_init_willClear = 1'b0;
  assign coreArea_dispatcher_hcs_init_willOverflowIfInc = (coreArea_dispatcher_hcs_init_value == 3'b101);
  assign coreArea_dispatcher_hcs_init_willOverflow = (coreArea_dispatcher_hcs_init_willOverflowIfInc && coreArea_dispatcher_hcs_init_willIncrement);
  always @(*) begin
    if(coreArea_dispatcher_hcs_init_willOverflow) begin
      coreArea_dispatcher_hcs_init_valueNext = 3'b001;
    end else begin
      coreArea_dispatcher_hcs_init_valueNext = (coreArea_dispatcher_hcs_init_value + _zz_coreArea_dispatcher_hcs_init_valueNext);
    end
    if(coreArea_dispatcher_hcs_init_willClear) begin
      coreArea_dispatcher_hcs_init_valueNext = 3'b001;
    end
  end

  assign when_scheduler_l250 = (coreArea_dispatcher_hcs_init_value != 3'b101);
  assign coreArea_srcPlugin_wasReset = 1'b0;
  always @(*) begin
    coreArea_srcPlugin_immsel_sext = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
    coreArea_srcPlugin_immsel_sext = _zz_coreArea_srcPlugin_immsel_sext_1;
  end

  assign _zz_coreArea_srcPlugin_immsel_sext = {{32{_zz__zz_coreArea_srcPlugin_immsel_sext[31]}}, _zz__zz_coreArea_srcPlugin_immsel_sext};
  always @(*) begin
    _zz_coreArea_srcPlugin_immsel_sext_1 = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
    case(coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL)
      Imm_Select_I_IMM : begin
        _zz_coreArea_srcPlugin_immsel_sext_1 = {{52{_zz__zz_coreArea_srcPlugin_immsel_sext_1[11]}}, _zz__zz_coreArea_srcPlugin_immsel_sext_1};
      end
      Imm_Select_S_IMM : begin
        _zz_coreArea_srcPlugin_immsel_sext_1 = {{52{_zz__zz_coreArea_srcPlugin_immsel_sext_1_1[11]}}, _zz__zz_coreArea_srcPlugin_immsel_sext_1_1};
      end
      Imm_Select_B_IMM : begin
        _zz_coreArea_srcPlugin_immsel_sext_1 = {{51{_zz__zz_coreArea_srcPlugin_immsel_sext_1_2[12]}}, _zz__zz_coreArea_srcPlugin_immsel_sext_1_2};
      end
      Imm_Select_U_IMM : begin
        _zz_coreArea_srcPlugin_immsel_sext_1 = _zz_coreArea_srcPlugin_immsel_sext;
      end
      Imm_Select_J_IMM : begin
        _zz_coreArea_srcPlugin_immsel_sext_1 = {{43{_zz__zz_coreArea_srcPlugin_immsel_sext_1_3[20]}}, _zz__zz_coreArea_srcPlugin_immsel_sext_1_3};
      end
      default : begin
      end
    endcase
  end

  assign coreArea_srcPlugin_rs1Reader_valid = ((coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE == RSTYPE_RS_INT) && (coreArea_pipeline_ctrl_5_up_Decoder_VALID == 1'b1));
  assign coreArea_srcPlugin_rs2Reader_valid = ((coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE == RSTYPE_RS_INT) && (coreArea_pipeline_ctrl_5_up_Decoder_VALID == 1'b1));
  assign coreArea_srcPlugin_rs1Reader_address = coreArea_pipeline_ctrl_5_up_Decoder_RS1_ADDR;
  assign coreArea_srcPlugin_rs2Reader_address = coreArea_pipeline_ctrl_5_up_Decoder_RS2_ADDR;
  assign coreArea_srcPlugin_rs1Reader_data = ((coreArea_srcPlugin_rs1Reader_address == 5'h0) ? 64'h0 : coreArea_srcPlugin_regfileread_regfile_io_reads_0_data);
  assign coreArea_srcPlugin_rs2Reader_data = ((coreArea_srcPlugin_rs2Reader_address == 5'h0) ? 64'h0 : coreArea_srcPlugin_regfileread_regfile_io_reads_1_data);
  always @(*) begin
    coreArea_pipeline_ctrl_5_down_SrcPlugin_RS1 = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
    coreArea_pipeline_ctrl_5_down_SrcPlugin_RS1 = coreArea_srcPlugin_rs_rs1Data;
  end

  always @(*) begin
    coreArea_pipeline_ctrl_5_down_SrcPlugin_RS2 = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
    coreArea_pipeline_ctrl_5_down_SrcPlugin_RS2 = coreArea_srcPlugin_rs_rs2Data;
  end

  always @(*) begin
    coreArea_pipeline_ctrl_5_down_SrcPlugin_IMMED = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
    coreArea_pipeline_ctrl_5_down_SrcPlugin_IMMED = coreArea_srcPlugin_immsel_sext;
  end

  assign coreArea_srcPlugin_rs_rs1Data = ((coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE == RSTYPE_RS_INT) ? coreArea_srcPlugin_rs1Reader_data : 64'h0);
  assign coreArea_srcPlugin_rs_rs2Data = ((coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE == RSTYPE_RS_INT) ? coreArea_srcPlugin_rs2Reader_data : 64'h0);
  always @(*) begin
    IntAlu_aluNodeStage_result = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
    if(when_IntAlu_l40) begin
      IntAlu_aluNodeStage_result = _zz_IntAlu_aluNodeStage_result;
    end
  end

  assign when_IntAlu_l40 = (coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOALU == 1'b1);
  always @(*) begin
    _zz_IntAlu_aluNodeStage_result = 64'bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopXORI : begin
        _zz_IntAlu_aluNodeStage_result = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 ^ coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED);
      end
      MicroCode_uopORI : begin
        _zz_IntAlu_aluNodeStage_result = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 | coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED);
      end
      MicroCode_uopANDI : begin
        _zz_IntAlu_aluNodeStage_result = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 & coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED);
      end
      MicroCode_uopADDI : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result;
      end
      MicroCode_uopSLTI : begin
        _zz_IntAlu_aluNodeStage_result = {63'd0, _zz__zz_IntAlu_aluNodeStage_result_3};
      end
      MicroCode_uopSLTIU : begin
        _zz_IntAlu_aluNodeStage_result = {63'd0, _zz__zz_IntAlu_aluNodeStage_result_6};
      end
      MicroCode_uopSLLI : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_7;
      end
      MicroCode_uopSRLI : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_8;
      end
      MicroCode_uopSRAI : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_9;
      end
      MicroCode_uopXOR : begin
        _zz_IntAlu_aluNodeStage_result = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 ^ coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2);
      end
      MicroCode_uopOR : begin
        _zz_IntAlu_aluNodeStage_result = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 | coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2);
      end
      MicroCode_uopAND : begin
        _zz_IntAlu_aluNodeStage_result = (coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 & coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2);
      end
      MicroCode_uopADD : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_11;
      end
      MicroCode_uopSLL : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_14;
      end
      MicroCode_uopSRL : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_15;
      end
      MicroCode_uopSRA : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_16;
      end
      MicroCode_uopSUB : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_18;
      end
      MicroCode_uopSLT : begin
        _zz_IntAlu_aluNodeStage_result = {63'd0, _zz__zz_IntAlu_aluNodeStage_result_21};
      end
      MicroCode_uopSLTU : begin
        _zz_IntAlu_aluNodeStage_result = {63'd0, _zz__zz_IntAlu_aluNodeStage_result_24};
      end
      MicroCode_uopADDW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_25;
      end
      MicroCode_uopSUBW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_29;
      end
      MicroCode_uopADDIW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_33;
      end
      MicroCode_uopSLLW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_37;
      end
      MicroCode_uopSRLW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_40;
      end
      MicroCode_uopSRAW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_43;
      end
      MicroCode_uopSLLIW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_46;
      end
      MicroCode_uopSRLIW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_49;
      end
      MicroCode_uopSRAIW : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_52;
      end
      MicroCode_uopLUI : begin
        _zz_IntAlu_aluNodeStage_result = coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED;
      end
      MicroCode_uopAUIPC : begin
        _zz_IntAlu_aluNodeStage_result = _zz__zz_IntAlu_aluNodeStage_result_55;
      end
      default : begin
      end
    endcase
  end

  always @(*) begin
    coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_address = 5'h0;
    if(when_IntAlu_l83) begin
      coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_address = coreArea_pipeline_ctrl_6_up_Decoder_RD_ADDR;
    end
    if(when_branch_l95) begin
      if(coreArea_branch_logic_isJump) begin
        coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_address = coreArea_pipeline_ctrl_6_up_Decoder_RD_ADDR;
      end
    end
    if(LSU_isLoad) begin
      coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_address = coreArea_lsu_logic_rdAddr;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data = 64'h0;
    if(when_IntAlu_l83) begin
      coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data = ((coreArea_pipeline_ctrl_6_up_Decoder_RD_ADDR == 5'h0) ? 64'h0 : IntAlu_aluNodeStage_result);
    end
    if(when_branch_l95) begin
      if(coreArea_branch_logic_isJump) begin
        coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data = ((coreArea_pipeline_ctrl_6_up_Decoder_RD_ADDR == 5'h0) ? 64'h0 : _zz_coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data);
      end
    end
    if(LSU_isLoad) begin
      coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data = coreArea_lsu_logic_maskedLoadResult;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_valid = 1'b0;
    if(when_IntAlu_l83) begin
      coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_valid = ((coreArea_pipeline_ctrl_6_up_Decoder_LEGAL == YESNO_Y) && coreArea_pipeline_ctrl_6_up_Decoder_VALID);
    end
    if(when_branch_l95) begin
      if(coreArea_branch_logic_isJump) begin
        coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_valid = (((coreArea_pipeline_ctrl_6_down_Decoder_LEGAL == YESNO_Y) && coreArea_pipeline_ctrl_6_up_Decoder_VALID) && (! coreArea_branch_logic_willTrap));
      end
    end
    if(LSU_isLoad) begin
      coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_valid = 1'b1;
    end
  end

  assign when_IntAlu_l83 = ((coreArea_pipeline_ctrl_6_up_Decoder_VALID == 1'b1) && coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOALU);
  assign coreArea_branch_logic_src1 = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1;
  assign coreArea_branch_logic_src2 = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2;
  assign coreArea_branch_logic_src1U = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1;
  assign coreArea_branch_logic_src2U = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2;
  assign coreArea_branch_logic_imm = coreArea_pipeline_ctrl_6_up_SrcPlugin_IMMED;
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopBEQ : begin
        coreArea_branch_logic_condition = ($signed(coreArea_branch_logic_src1) == $signed(coreArea_branch_logic_src2));
      end
      MicroCode_uopBNE : begin
        coreArea_branch_logic_condition = ($signed(coreArea_branch_logic_src1) != $signed(coreArea_branch_logic_src2));
      end
      MicroCode_uopBLT : begin
        coreArea_branch_logic_condition = ($signed(coreArea_branch_logic_src1) < $signed(coreArea_branch_logic_src2));
      end
      MicroCode_uopBGE : begin
        coreArea_branch_logic_condition = ($signed(coreArea_branch_logic_src2) <= $signed(coreArea_branch_logic_src1));
      end
      MicroCode_uopBLTU : begin
        coreArea_branch_logic_condition = (coreArea_branch_logic_src1U < coreArea_branch_logic_src2U);
      end
      MicroCode_uopBGEU : begin
        coreArea_branch_logic_condition = (coreArea_branch_logic_src2U <= coreArea_branch_logic_src1U);
      end
      default : begin
        coreArea_branch_logic_condition = 1'b0;
      end
    endcase
  end

  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopJALR : begin
        coreArea_branch_logic_target = _zz_coreArea_branch_logic_target;
        coreArea_branch_logic_target[0] = 1'b0;
      end
      default : begin
        coreArea_branch_logic_target = _zz_coreArea_branch_logic_target_3;
      end
    endcase
  end

  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopJAL : begin
        coreArea_branch_logic_isJump = 1'b1;
      end
      MicroCode_uopJALR : begin
        coreArea_branch_logic_isJump = 1'b1;
      end
      default : begin
        coreArea_branch_logic_isJump = 1'b0;
      end
    endcase
  end

  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopBEQ : begin
        coreArea_branch_logic_isBranch = 1'b1;
      end
      MicroCode_uopBNE : begin
        coreArea_branch_logic_isBranch = 1'b1;
      end
      MicroCode_uopBLT : begin
        coreArea_branch_logic_isBranch = 1'b1;
      end
      MicroCode_uopBGE : begin
        coreArea_branch_logic_isBranch = 1'b1;
      end
      MicroCode_uopBLTU : begin
        coreArea_branch_logic_isBranch = 1'b1;
      end
      MicroCode_uopBGEU : begin
        coreArea_branch_logic_isBranch = 1'b1;
      end
      default : begin
        coreArea_branch_logic_isBranch = 1'b0;
      end
    endcase
  end

  assign coreArea_branch_logic_doJump = (((coreArea_branch_logic_isJump || (coreArea_branch_logic_isBranch && coreArea_branch_logic_condition)) && coreArea_pipeline_ctrl_6_up_Common_LANE_SEL) && coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOBRANCH);
  assign coreArea_branch_logic_misaligned = (coreArea_branch_logic_target[1 : 0] != 2'b00);
  assign coreArea_branch_logic_willTrap = (coreArea_branch_logic_doJump && coreArea_branch_logic_misaligned);
  assign coreArea_pipeline_ctrl_6_down_Branch_BRANCH_TAKEN = (coreArea_branch_logic_doJump && (! coreArea_branch_logic_willTrap));
  assign coreArea_pipeline_ctrl_6_down_Branch_BRANCH_TARGET = coreArea_branch_logic_target;
  assign coreArea_branch_branchResolved = ((((coreArea_branch_logic_isJump || coreArea_branch_logic_isBranch) && coreArea_pipeline_ctrl_6_up_Common_LANE_SEL) && coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOBRANCH) && coreArea_pipeline_ctrl_6_down_isFiring);
  assign coreArea_branch_logic_jumpCmd_valid = (coreArea_branch_logic_doJump && (! coreArea_branch_logic_willTrap));
  assign coreArea_branch_logic_jumpCmd_payload_target = coreArea_branch_logic_target;
  assign coreArea_branch_logic_jumpCmd_payload_is_jump = coreArea_branch_logic_isJump;
  assign coreArea_branch_logic_jumpCmd_payload_is_branch = coreArea_branch_logic_isBranch;
  assign when_branch_l95 = (coreArea_pipeline_ctrl_6_up_Common_LANE_SEL && coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOBRANCH);
  assign coreArea_lsu_logic_effectiveAddr = _zz_coreArea_lsu_logic_effectiveAddr;
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopSB : begin
        LSU_isStore = 1'b1;
      end
      MicroCode_uopSH : begin
        LSU_isStore = 1'b1;
      end
      MicroCode_uopSW : begin
        LSU_isStore = 1'b1;
      end
      MicroCode_uopSD : begin
        LSU_isStore = 1'b1;
      end
      default : begin
        LSU_isStore = 1'b0;
      end
    endcase
  end

  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopSH : begin
        _zz_coreArea_lsu_logic_misaligned = (coreArea_lsu_logic_effectiveAddr[0] != 1'b0);
      end
      MicroCode_uopSW : begin
        _zz_coreArea_lsu_logic_misaligned = (coreArea_lsu_logic_effectiveAddr[1 : 0] != 2'b00);
      end
      MicroCode_uopSD : begin
        _zz_coreArea_lsu_logic_misaligned = (coreArea_lsu_logic_effectiveAddr[2 : 0] != 3'b000);
      end
      default : begin
        _zz_coreArea_lsu_logic_misaligned = 1'b0;
      end
    endcase
  end

  assign coreArea_lsu_logic_misaligned = _zz_coreArea_lsu_logic_misaligned;
  assign coreArea_lsu_logic_localTrap = (coreArea_lsu_logic_misaligned && LSU_isStore);
  assign coreArea_lsu_logic_byteOffset = coreArea_lsu_logic_effectiveAddr[2 : 0];
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopSB : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h01;
      end
      MicroCode_uopSH : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h03;
      end
      MicroCode_uopSW : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h0f;
      end
      MicroCode_uopSD : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'hff;
      end
      MicroCode_uopLB : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h01;
      end
      MicroCode_uopLBU : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h01;
      end
      MicroCode_uopLH : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h03;
      end
      MicroCode_uopLHU : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h03;
      end
      MicroCode_uopLW : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h0f;
      end
      MicroCode_uopLWU : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h0f;
      end
      MicroCode_uopLD : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'hff;
      end
      default : begin
        _zz_coreArea_lsu_logic_accessSizeMask = 8'h0;
      end
    endcase
  end

  assign coreArea_lsu_logic_accessSizeMask = _zz_coreArea_lsu_logic_accessSizeMask;
  assign coreArea_lsu_logic_writeMask = (coreArea_lsu_logic_accessSizeMask <<< coreArea_lsu_logic_byteOffset);
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopSB : begin
        _zz_coreArea_lsu_logic_rawStoreData = {56'd0, _zz__zz_coreArea_lsu_logic_rawStoreData};
      end
      MicroCode_uopSH : begin
        _zz_coreArea_lsu_logic_rawStoreData = {48'd0, _zz__zz_coreArea_lsu_logic_rawStoreData_1};
      end
      MicroCode_uopSW : begin
        _zz_coreArea_lsu_logic_rawStoreData = {32'd0, _zz__zz_coreArea_lsu_logic_rawStoreData_2};
      end
      MicroCode_uopSD : begin
        _zz_coreArea_lsu_logic_rawStoreData = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2;
      end
      default : begin
        _zz_coreArea_lsu_logic_rawStoreData = 64'h0;
      end
    endcase
  end

  assign coreArea_lsu_logic_rawStoreData = _zz_coreArea_lsu_logic_rawStoreData;
  assign coreArea_lsu_logic_storeData = (coreArea_lsu_logic_rawStoreData <<< _zz_coreArea_lsu_logic_storeData);
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopLB : begin
        LSU_isLoad = 1'b1;
      end
      MicroCode_uopLH : begin
        LSU_isLoad = 1'b1;
      end
      MicroCode_uopLW : begin
        LSU_isLoad = 1'b1;
      end
      MicroCode_uopLD : begin
        LSU_isLoad = 1'b1;
      end
      MicroCode_uopLBU : begin
        LSU_isLoad = 1'b1;
      end
      MicroCode_uopLHU : begin
        LSU_isLoad = 1'b1;
      end
      MicroCode_uopLWU : begin
        LSU_isLoad = 1'b1;
      end
      default : begin
        LSU_isLoad = 1'b0;
      end
    endcase
  end

  assign coreArea_lsu_logic_fireLoad = ((LSU_isLoad && coreArea_pipeline_ctrl_6_up_Decoder_VALID) && (! coreArea_lsu_logic_waitingResponse));
  assign coreArea_lsu_io_dBus_cmd_valid = ((((LSU_isStore || coreArea_lsu_logic_fireLoad) && coreArea_pipeline_ctrl_6_up_Decoder_VALID) && coreArea_pipeline_ctrl_6_up_Common_LANE_SEL) && (! coreArea_lsu_logic_misaligned));
  assign coreArea_lsu_io_dBus_cmd_payload_address = coreArea_lsu_logic_effectiveAddr;
  assign coreArea_lsu_io_dBus_cmd_payload_data = coreArea_lsu_logic_storeData;
  assign coreArea_lsu_io_dBus_cmd_payload_mask = coreArea_lsu_logic_writeMask;
  assign coreArea_lsu_io_dBus_cmd_payload_id = (LSU_isStore ? 16'h0 : coreArea_lsu_logic_nextId);
  assign coreArea_lsu_io_dBus_cmd_payload_write = LSU_isStore;
  assign coreArea_lsu_logic_responseArriving = ((((LSU_isLoad && coreArea_pipeline_ctrl_6_up_Decoder_VALID) && coreArea_lsu_logic_waitingResponse) && coreArea_lsu_io_dBus_rsp_valid) && (coreArea_lsu_io_dBus_rsp_payload_id == coreArea_lsu_logic_waitId));
  assign when_Lsu_l151 = (LSU_isLoad && coreArea_pipeline_ctrl_6_up_Decoder_VALID);
  assign when_Lsu_l152 = (! coreArea_lsu_logic_waitingResponse);
  assign when_Lsu_l153 = ((coreArea_lsu_io_dBus_cmd_ready && (! coreArea_lsu_logic_misaligned)) && coreArea_pipeline_ctrl_6_up_Common_LANE_SEL);
  assign coreArea_pipeline_ctrl_6_haltRequest_Lsu_l157 = _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l157;
  assign coreArea_pipeline_ctrl_6_haltRequest_Lsu_l159 = _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l159;
  assign coreArea_pipeline_ctrl_6_haltRequest_Lsu_l168 = _zz_coreArea_pipeline_ctrl_6_haltRequest_Lsu_l168;
  assign coreArea_lsu_logic_rspData = (coreArea_lsu_logic_responseArriving ? coreArea_lsu_io_dBus_rsp_payload_data : coreArea_lsu_logic_latchedRspData);
  assign coreArea_lsu_logic_shiftedLoadData = (coreArea_lsu_logic_rspData >>> _zz_coreArea_lsu_logic_shiftedLoadData);
  always @(*) begin
    case(coreArea_pipeline_ctrl_6_up_Decoder_MicroCode)
      MicroCode_uopLB : begin
        _zz_coreArea_lsu_logic_loadResult = _zz__zz_coreArea_lsu_logic_loadResult;
      end
      MicroCode_uopLBU : begin
        _zz_coreArea_lsu_logic_loadResult = {56'd0, _zz__zz_coreArea_lsu_logic_loadResult_2};
      end
      MicroCode_uopLH : begin
        _zz_coreArea_lsu_logic_loadResult = _zz__zz_coreArea_lsu_logic_loadResult_3;
      end
      MicroCode_uopLHU : begin
        _zz_coreArea_lsu_logic_loadResult = {48'd0, _zz__zz_coreArea_lsu_logic_loadResult_5};
      end
      MicroCode_uopLW : begin
        _zz_coreArea_lsu_logic_loadResult = _zz__zz_coreArea_lsu_logic_loadResult_6;
      end
      MicroCode_uopLWU : begin
        _zz_coreArea_lsu_logic_loadResult = {32'd0, _zz__zz_coreArea_lsu_logic_loadResult_8};
      end
      MicroCode_uopLD : begin
        _zz_coreArea_lsu_logic_loadResult = coreArea_lsu_logic_shiftedLoadData;
      end
      default : begin
        _zz_coreArea_lsu_logic_loadResult = 64'h0;
      end
    endcase
  end

  assign coreArea_lsu_logic_loadResult = _zz_coreArea_lsu_logic_loadResult;
  assign coreArea_lsu_logic_rdAddr = coreArea_pipeline_ctrl_6_up_Decoder_RD_ADDR;
  assign coreArea_lsu_logic_isX0 = (coreArea_lsu_logic_rdAddr == 5'h0);
  assign coreArea_lsu_logic_maskedLoadResult = (coreArea_lsu_logic_isX0 ? 64'h0 : coreArea_lsu_logic_loadResult);
  assign coreArea_pipeline_ctrl_6_down_LSU_MEM_ADDR = ((coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOAGU && (LSU_isStore || LSU_isLoad)) ? coreArea_lsu_logic_effectiveAddr : 64'h0);
  assign coreArea_pipeline_ctrl_6_down_LSU_MEM_WMASK = ((LSU_isStore && (! coreArea_lsu_logic_misaligned)) ? coreArea_lsu_logic_accessSizeMask : 8'h0);
  assign coreArea_pipeline_ctrl_6_down_LSU_MEM_WDATA = ((LSU_isStore && (! coreArea_lsu_logic_misaligned)) ? coreArea_lsu_logic_rawStoreData : 64'h0);
  assign coreArea_lsu_logic_readMaskShifted = (coreArea_lsu_logic_accessSizeMask <<< coreArea_lsu_logic_byteOffset);
  assign coreArea_pipeline_ctrl_6_down_LSU_MEM_RMASK = ((LSU_isLoad && (! coreArea_lsu_logic_misaligned)) ? coreArea_lsu_logic_accessSizeMask : 8'h0);
  assign coreArea_pipeline_ctrl_6_down_LSU_MEM_RDATA = ((LSU_isLoad && (! coreArea_lsu_logic_misaligned)) ? coreArea_lsu_logic_shiftedLoadData : 64'h0);
  assign io_dBus_cmd_valid = coreArea_lsu_io_dBus_cmd_valid;
  assign coreArea_lsu_io_dBus_cmd_ready = io_dBus_cmd_ready;
  assign io_dBus_cmd_payload_address = coreArea_lsu_io_dBus_cmd_payload_address;
  assign io_dBus_cmd_payload_data = coreArea_lsu_io_dBus_cmd_payload_data;
  assign io_dBus_cmd_payload_mask = coreArea_lsu_io_dBus_cmd_payload_mask;
  assign io_dBus_cmd_payload_id = coreArea_lsu_io_dBus_cmd_payload_id;
  assign io_dBus_cmd_payload_write = coreArea_lsu_io_dBus_cmd_payload_write;
  assign coreArea_lsu_io_dBus_rsp_valid = io_dBus_rsp_valid;
  assign coreArea_lsu_io_dBus_rsp_payload_data = io_dBus_rsp_payload_data;
  assign coreArea_lsu_io_dBus_rsp_payload_id = io_dBus_rsp_payload_id;
  assign coreArea_pipeline_ctrl_6_down_Common_TRAP = (coreArea_branch_logic_willTrap || coreArea_lsu_logic_localTrap);
  assign coreArea_decode_branchResolved = coreArea_branch_branchResolved;
  assign coreArea_pc_jump_valid = coreArea_branch_logic_jumpCmd_valid;
  assign coreArea_pc_jump_payload_target = coreArea_branch_logic_jumpCmd_payload_target;
  assign coreArea_pc_jump_payload_is_jump = coreArea_branch_logic_jumpCmd_payload_is_jump;
  assign coreArea_pc_jump_payload_is_branch = coreArea_branch_logic_jumpCmd_payload_is_branch;
  assign coreArea_fetch_io_flush = coreArea_branch_logic_jumpCmd_valid;
  assign coreArea_fetch_io_currentEpoch = coreArea_currentEpoch;
  assign coreArea_pipeline_ctrl_3_throwWhen_CPU_l152 = (coreArea_branch_logic_jumpCmd_valid && (coreArea_pipeline_ctrl_3_down_Common_SPEC_EPOCH == coreArea_currentEpoch));
  assign coreArea_pipeline_ctrl_4_throwWhen_CPU_l152 = (coreArea_branch_logic_jumpCmd_valid && (coreArea_pipeline_ctrl_4_down_Common_SPEC_EPOCH == coreArea_currentEpoch));
  assign coreArea_pipeline_ctrl_5_throwWhen_CPU_l152 = (coreArea_branch_logic_jumpCmd_valid && (coreArea_pipeline_ctrl_5_down_Common_SPEC_EPOCH == coreArea_currentEpoch));
  assign coreArea_pipeline_ctrl_1_throwWhen_CPU_l158 = coreArea_branch_logic_jumpCmd_valid;
  assign coreArea_pipeline_ctrl_2_throwWhen_CPU_l158 = coreArea_branch_logic_jumpCmd_valid;
  assign coreArea_rvfiPlugin_io_rvfi_valid = coreArea_pipeline_ctrl_7_up_Common_COMMIT;
  assign coreArea_rvfiPlugin_io_rvfi_order = coreArea_rvfiPlugin_order;
  assign coreArea_rvfiPlugin_io_rvfi_insn = coreArea_pipeline_ctrl_7_up_Decoder_INSTRUCTION;
  assign coreArea_rvfiPlugin_io_rvfi_trap = coreArea_pipeline_ctrl_7_up_Common_TRAP;
  assign coreArea_rvfiPlugin_io_rvfi_halt = 1'b0;
  assign coreArea_rvfiPlugin_io_rvfi_intr = 1'b0;
  assign coreArea_rvfiPlugin_io_rvfi_mode = 2'b11;
  assign coreArea_rvfiPlugin_io_rvfi_ixl = 2'b10;
  assign coreArea_rvfiPlugin_io_rvfi_rs1_addr = coreArea_pipeline_ctrl_7_up_Decoder_RS1_ADDR;
  assign coreArea_rvfiPlugin_io_rvfi_rs2_addr = coreArea_pipeline_ctrl_7_up_Decoder_RS2_ADDR;
  assign coreArea_rvfiPlugin_io_rvfi_rs1_rdata = coreArea_pipeline_ctrl_7_up_SrcPlugin_RS1;
  assign coreArea_rvfiPlugin_io_rvfi_rs2_rdata = coreArea_pipeline_ctrl_7_up_SrcPlugin_RS2;
  assign coreArea_rvfiPlugin_io_rvfi_rd_addr = coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_address;
  assign coreArea_rvfiPlugin_io_rvfi_rd_wdata = coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_data;
  assign coreArea_rvfiPlugin_io_rvfi_pc_rdata = coreArea_pipeline_ctrl_7_up_PC_PC;
  assign coreArea_rvfiPlugin_io_rvfi_pc_wdata = (coreArea_pipeline_ctrl_7_up_Branch_BRANCH_TAKEN ? coreArea_pipeline_ctrl_7_up_Branch_BRANCH_TARGET : _zz_coreArea_rvfiPlugin_io_rvfi_pc_wdata);
  assign coreArea_rvfiPlugin_io_rvfi_mem_addr = coreArea_pipeline_ctrl_7_up_LSU_MEM_ADDR;
  assign coreArea_rvfiPlugin_io_rvfi_mem_rmask = coreArea_pipeline_ctrl_7_up_LSU_MEM_RMASK;
  assign coreArea_rvfiPlugin_io_rvfi_mem_wmask = coreArea_pipeline_ctrl_7_up_LSU_MEM_WMASK;
  assign coreArea_rvfiPlugin_io_rvfi_mem_rdata = coreArea_pipeline_ctrl_7_up_LSU_MEM_RDATA;
  assign coreArea_rvfiPlugin_io_rvfi_mem_wdata = coreArea_pipeline_ctrl_7_up_LSU_MEM_WDATA;
  assign io_rvfi_valid = coreArea_rvfiPlugin_io_rvfi_valid;
  assign io_rvfi_order = coreArea_rvfiPlugin_io_rvfi_order;
  assign io_rvfi_insn = coreArea_rvfiPlugin_io_rvfi_insn;
  assign io_rvfi_trap = coreArea_rvfiPlugin_io_rvfi_trap;
  assign io_rvfi_halt = coreArea_rvfiPlugin_io_rvfi_halt;
  assign io_rvfi_intr = coreArea_rvfiPlugin_io_rvfi_intr;
  assign io_rvfi_mode = coreArea_rvfiPlugin_io_rvfi_mode;
  assign io_rvfi_ixl = coreArea_rvfiPlugin_io_rvfi_ixl;
  assign io_rvfi_rs1_addr = coreArea_rvfiPlugin_io_rvfi_rs1_addr;
  assign io_rvfi_rs2_addr = coreArea_rvfiPlugin_io_rvfi_rs2_addr;
  assign io_rvfi_rs1_rdata = coreArea_rvfiPlugin_io_rvfi_rs1_rdata;
  assign io_rvfi_rs2_rdata = coreArea_rvfiPlugin_io_rvfi_rs2_rdata;
  assign io_rvfi_rd_addr = coreArea_rvfiPlugin_io_rvfi_rd_addr;
  assign io_rvfi_rd_wdata = coreArea_rvfiPlugin_io_rvfi_rd_wdata;
  assign io_rvfi_pc_rdata = coreArea_rvfiPlugin_io_rvfi_pc_rdata;
  assign io_rvfi_pc_wdata = coreArea_rvfiPlugin_io_rvfi_pc_wdata;
  assign io_rvfi_mem_addr = coreArea_rvfiPlugin_io_rvfi_mem_addr;
  assign io_rvfi_mem_rmask = coreArea_rvfiPlugin_io_rvfi_mem_rmask;
  assign io_rvfi_mem_wmask = coreArea_rvfiPlugin_io_rvfi_mem_wmask;
  assign io_rvfi_mem_rdata = coreArea_rvfiPlugin_io_rvfi_mem_rdata;
  assign io_rvfi_mem_wdata = coreArea_rvfiPlugin_io_rvfi_mem_wdata;
  assign coreArea_debugPlugin_io_dbg_commitValid = coreArea_pipeline_ctrl_7_up_Common_COMMIT;
  assign coreArea_debugPlugin_io_dbg_commitPc = coreArea_pipeline_ctrl_7_up_PC_PC;
  assign coreArea_debugPlugin_io_dbg_commitInsn = coreArea_pipeline_ctrl_7_up_Decoder_INSTRUCTION;
  assign coreArea_debugPlugin_io_dbg_commitRd = (coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_valid ? coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_address : 5'h0);
  assign coreArea_debugPlugin_io_dbg_commitWe = (coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_valid && coreArea_pipeline_ctrl_7_up_Common_COMMIT);
  assign coreArea_debugPlugin_io_dbg_commitWdata = (coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_valid ? coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_data : 64'h0);
  assign coreArea_debugPlugin_io_dbg_squashed = ((! coreArea_pipeline_ctrl_7_up_Common_LANE_SEL) || coreArea_pipeline_ctrl_7_up_Common_TRAP);
  assign coreArea_debugPlugin_io_dbg_wb_pc = coreArea_pipeline_ctrl_7_up_PC_PC;
  assign io_dbg_commitValid = coreArea_debugPlugin_io_dbg_commitValid;
  assign io_dbg_commitPc = coreArea_debugPlugin_io_dbg_commitPc;
  assign io_dbg_commitInsn = coreArea_debugPlugin_io_dbg_commitInsn;
  assign io_dbg_commitRd = coreArea_debugPlugin_io_dbg_commitRd;
  assign io_dbg_commitWe = coreArea_debugPlugin_io_dbg_commitWe;
  assign io_dbg_commitWdata = coreArea_debugPlugin_io_dbg_commitWdata;
  assign io_dbg_squashed = coreArea_debugPlugin_io_dbg_squashed;
  assign io_dbg_f_pc = coreArea_debugPlugin_io_dbg_f_pc;
  assign io_dbg_d_pc = coreArea_debugPlugin_io_dbg_d_pc;
  assign io_dbg_x_pc = coreArea_debugPlugin_io_dbg_x_pc;
  assign io_dbg_wb_pc = coreArea_debugPlugin_io_dbg_wb_pc;
  assign coreArea_debugPlugin_io_dbg_f_pc = coreArea_pipeline_ctrl_2_down_PC_PC;
  assign coreArea_debugPlugin_io_dbg_d_pc = coreArea_pipeline_ctrl_3_down_PC_PC;
  assign coreArea_debugPlugin_io_dbg_x_pc = coreArea_pipeline_ctrl_6_down_PC_PC;
  assign coreArea_perfCounters_counters_cycles = coreArea_perfCounters_cycles;
  assign coreArea_perfCounters_counters_instret = coreArea_perfCounters_instret;
  assign coreArea_perfCounters_counters_stallsHazard = coreArea_perfCounters_stallsHazard;
  assign coreArea_perfCounters_counters_stallsFetch = coreArea_perfCounters_stallsFetch;
  assign coreArea_perfCounters_counters_stallsMem = coreArea_perfCounters_stallsMem;
  assign coreArea_perfCounters_counters_branches = coreArea_perfCounters_branches;
  assign coreArea_perfCounters_counters_branchesTaken = coreArea_perfCounters_branchesTaken;
  assign coreArea_perfCounters_counters_flushes = coreArea_perfCounters_flushes;
  assign io_perf_cycles = coreArea_perfCounters_counters_cycles;
  assign io_perf_instret = coreArea_perfCounters_counters_instret;
  assign io_perf_stallsHazard = coreArea_perfCounters_counters_stallsHazard;
  assign io_perf_stallsFetch = coreArea_perfCounters_counters_stallsFetch;
  assign io_perf_stallsMem = coreArea_perfCounters_counters_stallsMem;
  assign io_perf_branches = coreArea_perfCounters_counters_branches;
  assign io_perf_branchesTaken = coreArea_perfCounters_counters_branchesTaken;
  assign io_perf_flushes = coreArea_perfCounters_counters_flushes;
  assign coreArea_perfCounters_hazardStall = coreArea_dispatcher_hcs_writes_hazard;
  assign coreArea_perfCounters_fetchStall = (! coreArea_fetch_fifo_io_pop_valid);
  assign coreArea_perfCounters_memStall = coreArea_lsu_logic_waitingResponse;
  assign coreArea_perfCounters_branchExecuted = (coreArea_branch_logic_isBranch && coreArea_pipeline_ctrl_6_up_Common_LANE_SEL);
  assign coreArea_perfCounters_branchTaken = coreArea_branch_logic_doJump;
  assign coreArea_perfCounters_pipelineFlush = coreArea_branch_logic_jumpCmd_valid;
  assign coreArea_pipeline_ctrl_7_up_Common_COMMIT = ((! coreArea_pipeline_ctrl_7_up_Common_TRAP) && coreArea_pipeline_ctrl_7_up_Common_LANE_SEL);
  assign coreArea_srcPlugin_regfileread_regfile_io_writes_0_valid = ((coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_valid && coreArea_pipeline_ctrl_7_down_isFiring) && coreArea_pipeline_ctrl_7_up_Common_COMMIT);
  assign coreArea_pipeline_ctrl_7_down_ready = 1'b1;
  assign io_iBus_cmd_valid = coreArea_fetch_io_readCmd_cmd_valid;
  assign coreArea_fetch_io_readCmd_cmd_ready = io_iBus_cmd_ready;
  assign io_iBus_cmd_payload_address = coreArea_fetch_io_readCmd_cmd_payload_address;
  assign io_iBus_cmd_payload_id = coreArea_fetch_io_readCmd_cmd_payload_id;
  assign coreArea_fetch_io_readCmd_rsp_valid = io_iBus_rsp_valid;
  assign coreArea_fetch_io_readCmd_rsp_payload_data = io_iBus_rsp_payload_data;
  assign coreArea_fetch_io_readCmd_rsp_payload_address = io_iBus_rsp_payload_address;
  assign coreArea_fetch_io_readCmd_rsp_payload_id = io_iBus_rsp_payload_id;
  assign coreArea_pipeline_ctrl_5_up_forgetOne = (|coreArea_pipeline_ctrl_5_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_5_up_cancel = (|coreArea_pipeline_ctrl_5_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_4_up_forgetOne = (|coreArea_pipeline_ctrl_4_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_4_up_cancel = (|coreArea_pipeline_ctrl_4_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_3_up_forgetOne = (|coreArea_pipeline_ctrl_3_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_3_up_cancel = (|coreArea_pipeline_ctrl_3_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_2_up_forgetOne = (|{coreArea_pipeline_ctrl_2_throwWhen_CPU_l158,coreArea_pipeline_ctrl_2_throwWhen_Fetch_l92});
  assign coreArea_pipeline_ctrl_2_up_cancel = (|{coreArea_pipeline_ctrl_2_throwWhen_CPU_l158,coreArea_pipeline_ctrl_2_throwWhen_Fetch_l92});
  assign coreArea_pipeline_ctrl_1_up_forgetOne = (|coreArea_pipeline_ctrl_1_throwWhen_CPU_l158);
  assign coreArea_pipeline_ctrl_1_up_cancel = (|coreArea_pipeline_ctrl_1_throwWhen_CPU_l158);
  always @(*) begin
    coreArea_pipeline_ctrl_0_down_ready = coreArea_pipeline_ctrl_1_up_ready;
    if(when_StageLink_l71) begin
      coreArea_pipeline_ctrl_0_down_ready = 1'b1;
    end
  end

  assign when_StageLink_l71 = (! coreArea_pipeline_ctrl_1_up_isValid);
  always @(*) begin
    coreArea_pipeline_ctrl_1_down_ready = coreArea_pipeline_ctrl_2_up_ready;
    if(when_StageLink_l71_1) begin
      coreArea_pipeline_ctrl_1_down_ready = 1'b1;
    end
  end

  assign when_StageLink_l71_1 = (! coreArea_pipeline_ctrl_2_up_isValid);
  always @(*) begin
    coreArea_pipeline_ctrl_2_down_ready = coreArea_pipeline_ctrl_3_up_ready;
    if(when_StageLink_l71_2) begin
      coreArea_pipeline_ctrl_2_down_ready = 1'b1;
    end
  end

  assign when_StageLink_l71_2 = (! coreArea_pipeline_ctrl_3_up_isValid);
  always @(*) begin
    coreArea_pipeline_ctrl_3_down_ready = coreArea_pipeline_ctrl_4_up_ready;
    if(when_StageLink_l71_3) begin
      coreArea_pipeline_ctrl_3_down_ready = 1'b1;
    end
  end

  assign when_StageLink_l71_3 = (! coreArea_pipeline_ctrl_4_up_isValid);
  always @(*) begin
    coreArea_pipeline_ctrl_4_down_ready = coreArea_pipeline_ctrl_5_up_ready;
    if(when_StageLink_l71_4) begin
      coreArea_pipeline_ctrl_4_down_ready = 1'b1;
    end
  end

  assign when_StageLink_l71_4 = (! coreArea_pipeline_ctrl_5_up_isValid);
  always @(*) begin
    coreArea_pipeline_ctrl_5_down_ready = coreArea_pipeline_ctrl_6_up_ready;
    if(when_StageLink_l71_5) begin
      coreArea_pipeline_ctrl_5_down_ready = 1'b1;
    end
  end

  assign when_StageLink_l71_5 = (! coreArea_pipeline_ctrl_6_up_isValid);
  always @(*) begin
    coreArea_pipeline_ctrl_6_down_ready = coreArea_pipeline_ctrl_7_up_ready;
    if(when_StageLink_l71_6) begin
      coreArea_pipeline_ctrl_6_down_ready = 1'b1;
    end
  end

  assign when_StageLink_l71_6 = (! coreArea_pipeline_ctrl_7_up_isValid);
  assign coreArea_pipeline_ctrl_0_down_valid = coreArea_pipeline_ctrl_0_up_valid;
  assign coreArea_pipeline_ctrl_0_up_ready = coreArea_pipeline_ctrl_0_down_isReady;
  always @(*) begin
    coreArea_pipeline_ctrl_1_down_valid = coreArea_pipeline_ctrl_1_up_valid;
    if(when_CtrlLink_l191) begin
      coreArea_pipeline_ctrl_1_down_valid = 1'b0;
    end
    if(when_CtrlLink_l198) begin
      coreArea_pipeline_ctrl_1_down_valid = 1'b0;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_1_up_ready = coreArea_pipeline_ctrl_1_down_isReady;
    if(when_CtrlLink_l191) begin
      coreArea_pipeline_ctrl_1_up_ready = 1'b0;
    end
  end

  assign when_CtrlLink_l191 = (|coreArea_pipeline_ctrl_1_haltRequest_Fetch_l80);
  assign when_CtrlLink_l198 = (|coreArea_pipeline_ctrl_1_throwWhen_CPU_l158);
  assign coreArea_pipeline_ctrl_1_down_PC_PC = coreArea_pipeline_ctrl_1_up_PC_PC;
  always @(*) begin
    coreArea_pipeline_ctrl_2_down_valid = coreArea_pipeline_ctrl_2_up_valid;
    if(when_CtrlLink_l191_1) begin
      coreArea_pipeline_ctrl_2_down_valid = 1'b0;
    end
    if(when_CtrlLink_l198_1) begin
      coreArea_pipeline_ctrl_2_down_valid = 1'b0;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_2_up_ready = coreArea_pipeline_ctrl_2_down_isReady;
    if(when_CtrlLink_l191_1) begin
      coreArea_pipeline_ctrl_2_up_ready = 1'b0;
    end
  end

  assign when_CtrlLink_l191_1 = (|coreArea_pipeline_ctrl_2_haltRequest_Fetch_l95);
  assign when_CtrlLink_l198_1 = (|{coreArea_pipeline_ctrl_2_throwWhen_CPU_l158,coreArea_pipeline_ctrl_2_throwWhen_Fetch_l92});
  assign coreArea_pipeline_ctrl_2_down_PC_PC = coreArea_pipeline_ctrl_2_up_PC_PC;
  always @(*) begin
    coreArea_pipeline_ctrl_3_down_valid = coreArea_pipeline_ctrl_3_up_valid;
    if(when_CtrlLink_l198_2) begin
      coreArea_pipeline_ctrl_3_down_valid = 1'b0;
    end
  end

  assign coreArea_pipeline_ctrl_3_up_ready = coreArea_pipeline_ctrl_3_down_isReady;
  assign when_CtrlLink_l198_2 = (|coreArea_pipeline_ctrl_3_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_3_down_PC_PC = coreArea_pipeline_ctrl_3_up_PC_PC;
  assign coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION = coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION;
  assign coreArea_pipeline_ctrl_3_down_Common_SPEC_EPOCH = coreArea_pipeline_ctrl_3_up_Common_SPEC_EPOCH;
  always @(*) begin
    coreArea_pipeline_ctrl_4_down_valid = coreArea_pipeline_ctrl_4_up_valid;
    if(when_CtrlLink_l191_2) begin
      coreArea_pipeline_ctrl_4_down_valid = 1'b0;
    end
    if(when_CtrlLink_l198_3) begin
      coreArea_pipeline_ctrl_4_down_valid = 1'b0;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_4_up_ready = coreArea_pipeline_ctrl_4_down_isReady;
    if(when_CtrlLink_l191_2) begin
      coreArea_pipeline_ctrl_4_up_ready = 1'b0;
    end
  end

  assign when_CtrlLink_l191_2 = (|coreArea_pipeline_ctrl_4_haltRequest_scheduler_l214);
  assign when_CtrlLink_l198_3 = (|coreArea_pipeline_ctrl_4_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_4_down_PC_PC = coreArea_pipeline_ctrl_4_up_PC_PC;
  assign coreArea_pipeline_ctrl_4_down_Decoder_INSTRUCTION = coreArea_pipeline_ctrl_4_up_Decoder_INSTRUCTION;
  assign coreArea_pipeline_ctrl_4_down_Common_SPEC_EPOCH = coreArea_pipeline_ctrl_4_up_Common_SPEC_EPOCH;
  assign coreArea_pipeline_ctrl_4_down_Decoder_VALID = coreArea_pipeline_ctrl_4_up_Decoder_VALID;
  assign coreArea_pipeline_ctrl_4_down_Decoder_LEGAL = coreArea_pipeline_ctrl_4_up_Decoder_LEGAL;
  assign coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE = coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE;
  assign coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE = coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE;
  assign coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL = coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL;
  assign coreArea_pipeline_ctrl_4_down_Decoder_MicroCode = coreArea_pipeline_ctrl_4_up_Decoder_MicroCode;
  assign coreArea_pipeline_ctrl_4_down_Decoder_RD_ADDR = coreArea_pipeline_ctrl_4_up_Decoder_RD_ADDR;
  assign coreArea_pipeline_ctrl_4_down_Decoder_RS1_ADDR = coreArea_pipeline_ctrl_4_up_Decoder_RS1_ADDR;
  assign coreArea_pipeline_ctrl_4_down_Decoder_RS2_ADDR = coreArea_pipeline_ctrl_4_up_Decoder_RS2_ADDR;
  always @(*) begin
    coreArea_pipeline_ctrl_5_down_valid = coreArea_pipeline_ctrl_5_up_valid;
    if(when_CtrlLink_l198_4) begin
      coreArea_pipeline_ctrl_5_down_valid = 1'b0;
    end
  end

  assign coreArea_pipeline_ctrl_5_up_ready = coreArea_pipeline_ctrl_5_down_isReady;
  assign when_CtrlLink_l198_4 = (|coreArea_pipeline_ctrl_5_throwWhen_CPU_l152);
  assign coreArea_pipeline_ctrl_5_down_PC_PC = coreArea_pipeline_ctrl_5_up_PC_PC;
  assign coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION = coreArea_pipeline_ctrl_5_up_Decoder_INSTRUCTION;
  assign coreArea_pipeline_ctrl_5_down_Common_SPEC_EPOCH = coreArea_pipeline_ctrl_5_up_Common_SPEC_EPOCH;
  assign coreArea_pipeline_ctrl_5_down_Decoder_VALID = coreArea_pipeline_ctrl_5_up_Decoder_VALID;
  assign coreArea_pipeline_ctrl_5_down_Decoder_LEGAL = coreArea_pipeline_ctrl_5_up_Decoder_LEGAL;
  assign coreArea_pipeline_ctrl_5_down_Decoder_RS1TYPE = coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE;
  assign coreArea_pipeline_ctrl_5_down_Decoder_RS2TYPE = coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE;
  assign coreArea_pipeline_ctrl_5_down_Decoder_MicroCode = coreArea_pipeline_ctrl_5_up_Decoder_MicroCode;
  assign coreArea_pipeline_ctrl_5_down_Decoder_RD_ADDR = coreArea_pipeline_ctrl_5_up_Decoder_RD_ADDR;
  assign coreArea_pipeline_ctrl_5_down_Decoder_RS1_ADDR = coreArea_pipeline_ctrl_5_up_Decoder_RS1_ADDR;
  assign coreArea_pipeline_ctrl_5_down_Decoder_RS2_ADDR = coreArea_pipeline_ctrl_5_up_Decoder_RS2_ADDR;
  assign coreArea_pipeline_ctrl_5_down_Common_LANE_SEL = coreArea_pipeline_ctrl_5_up_Common_LANE_SEL;
  assign coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOALU = coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOALU;
  assign coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOBRANCH = coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOBRANCH;
  assign coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOAGU = coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOAGU;
  always @(*) begin
    coreArea_pipeline_ctrl_6_down_valid = coreArea_pipeline_ctrl_6_up_valid;
    if(when_CtrlLink_l191_3) begin
      coreArea_pipeline_ctrl_6_down_valid = 1'b0;
    end
  end

  always @(*) begin
    coreArea_pipeline_ctrl_6_up_ready = coreArea_pipeline_ctrl_6_down_isReady;
    if(when_CtrlLink_l191_3) begin
      coreArea_pipeline_ctrl_6_up_ready = 1'b0;
    end
  end

  assign when_CtrlLink_l191_3 = (|{coreArea_pipeline_ctrl_6_haltRequest_Lsu_l168,{coreArea_pipeline_ctrl_6_haltRequest_Lsu_l159,coreArea_pipeline_ctrl_6_haltRequest_Lsu_l157}});
  assign coreArea_pipeline_ctrl_6_down_PC_PC = coreArea_pipeline_ctrl_6_up_PC_PC;
  assign coreArea_pipeline_ctrl_6_down_Decoder_INSTRUCTION = coreArea_pipeline_ctrl_6_up_Decoder_INSTRUCTION;
  assign coreArea_pipeline_ctrl_6_down_Common_SPEC_EPOCH = coreArea_pipeline_ctrl_6_up_Common_SPEC_EPOCH;
  assign coreArea_pipeline_ctrl_6_down_Decoder_VALID = coreArea_pipeline_ctrl_6_up_Decoder_VALID;
  assign coreArea_pipeline_ctrl_6_down_Decoder_LEGAL = coreArea_pipeline_ctrl_6_up_Decoder_LEGAL;
  assign coreArea_pipeline_ctrl_6_down_Decoder_RS1_ADDR = coreArea_pipeline_ctrl_6_up_Decoder_RS1_ADDR;
  assign coreArea_pipeline_ctrl_6_down_Decoder_RS2_ADDR = coreArea_pipeline_ctrl_6_up_Decoder_RS2_ADDR;
  assign coreArea_pipeline_ctrl_6_down_Common_LANE_SEL = coreArea_pipeline_ctrl_6_up_Common_LANE_SEL;
  assign coreArea_pipeline_ctrl_6_down_Dispatch_SENDTOALU = coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOALU;
  assign coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1 = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1;
  assign coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2 = coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2;
  assign coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED = coreArea_pipeline_ctrl_6_up_SrcPlugin_IMMED;
  assign coreArea_pipeline_ctrl_7_down_valid = coreArea_pipeline_ctrl_7_up_valid;
  assign coreArea_pipeline_ctrl_7_up_ready = coreArea_pipeline_ctrl_7_down_isReady;
  assign coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_valid = coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_valid;
  assign coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_address = coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_address;
  assign coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_data = coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_data;
  assign coreArea_pipeline_ctrl_0_down_isFiring = (coreArea_pipeline_ctrl_0_down_isValid && coreArea_pipeline_ctrl_0_down_isReady);
  assign coreArea_pipeline_ctrl_0_down_isValid = coreArea_pipeline_ctrl_0_down_valid;
  assign coreArea_pipeline_ctrl_0_down_isReady = coreArea_pipeline_ctrl_0_down_ready;
  assign coreArea_pipeline_ctrl_1_up_isValid = coreArea_pipeline_ctrl_1_up_valid;
  assign coreArea_pipeline_ctrl_1_down_isFiring = (coreArea_pipeline_ctrl_1_down_isValid && coreArea_pipeline_ctrl_1_down_isReady);
  assign coreArea_pipeline_ctrl_1_down_isValid = coreArea_pipeline_ctrl_1_down_valid;
  assign coreArea_pipeline_ctrl_1_down_isReady = coreArea_pipeline_ctrl_1_down_ready;
  assign coreArea_pipeline_ctrl_2_up_isValid = coreArea_pipeline_ctrl_2_up_valid;
  assign coreArea_pipeline_ctrl_2_down_isFiring = (coreArea_pipeline_ctrl_2_down_isValid && coreArea_pipeline_ctrl_2_down_isReady);
  assign coreArea_pipeline_ctrl_2_down_isValid = coreArea_pipeline_ctrl_2_down_valid;
  assign coreArea_pipeline_ctrl_2_down_isReady = coreArea_pipeline_ctrl_2_down_ready;
  assign coreArea_pipeline_ctrl_3_up_isValid = coreArea_pipeline_ctrl_3_up_valid;
  assign coreArea_pipeline_ctrl_3_down_isFiring = (coreArea_pipeline_ctrl_3_down_isValid && coreArea_pipeline_ctrl_3_down_isReady);
  assign coreArea_pipeline_ctrl_3_down_isValid = coreArea_pipeline_ctrl_3_down_valid;
  assign coreArea_pipeline_ctrl_3_down_isReady = coreArea_pipeline_ctrl_3_down_ready;
  assign coreArea_pipeline_ctrl_4_up_isFiring = ((coreArea_pipeline_ctrl_4_up_isValid && coreArea_pipeline_ctrl_4_up_isReady) && (! coreArea_pipeline_ctrl_4_up_isCancel));
  assign coreArea_pipeline_ctrl_4_up_isValid = coreArea_pipeline_ctrl_4_up_valid;
  assign coreArea_pipeline_ctrl_4_up_isReady = coreArea_pipeline_ctrl_4_up_ready;
  assign coreArea_pipeline_ctrl_4_up_isCancel = coreArea_pipeline_ctrl_4_up_cancel;
  assign coreArea_pipeline_ctrl_4_down_isFiring = (coreArea_pipeline_ctrl_4_down_isValid && coreArea_pipeline_ctrl_4_down_isReady);
  assign coreArea_pipeline_ctrl_4_down_isValid = coreArea_pipeline_ctrl_4_down_valid;
  assign coreArea_pipeline_ctrl_4_down_isReady = coreArea_pipeline_ctrl_4_down_ready;
  assign coreArea_pipeline_ctrl_5_up_isValid = coreArea_pipeline_ctrl_5_up_valid;
  assign coreArea_pipeline_ctrl_5_down_isFiring = (coreArea_pipeline_ctrl_5_down_isValid && coreArea_pipeline_ctrl_5_down_isReady);
  assign coreArea_pipeline_ctrl_5_down_isValid = coreArea_pipeline_ctrl_5_down_valid;
  assign coreArea_pipeline_ctrl_5_down_isReady = coreArea_pipeline_ctrl_5_down_ready;
  assign coreArea_pipeline_ctrl_6_up_isValid = coreArea_pipeline_ctrl_6_up_valid;
  assign coreArea_pipeline_ctrl_6_down_isFiring = (coreArea_pipeline_ctrl_6_down_isValid && coreArea_pipeline_ctrl_6_down_isReady);
  assign coreArea_pipeline_ctrl_6_down_isValid = coreArea_pipeline_ctrl_6_down_valid;
  assign coreArea_pipeline_ctrl_6_down_isReady = coreArea_pipeline_ctrl_6_down_ready;
  assign coreArea_pipeline_ctrl_7_up_isValid = coreArea_pipeline_ctrl_7_up_valid;
  assign coreArea_pipeline_ctrl_0_up_isValid = coreArea_pipeline_ctrl_0_up_valid;
  assign coreArea_pipeline_ctrl_7_down_isFiring = (coreArea_pipeline_ctrl_7_down_isValid && coreArea_pipeline_ctrl_7_down_isReady);
  assign coreArea_pipeline_ctrl_7_down_isValid = coreArea_pipeline_ctrl_7_down_valid;
  assign coreArea_pipeline_ctrl_7_down_isReady = coreArea_pipeline_ctrl_7_down_ready;
  always @(posedge io_clk) begin
    if(io_clkEnable) begin
      if(io_reset) begin
      coreArea_pc_PC_cur <= 64'h0;
      coreArea_fetch_inflight <= 4'b0000;
      coreArea_fetch_epoch <= 16'h0;
      coreArea_fetch_flushPending <= 1'b0;
      coreArea_fetch_cmdArea_reqSent <= 1'b0;
      coreArea_dispatcher_hcs_regBusy <= 32'h0;
      coreArea_dispatcher_hcs_init_value <= 3'b001;
      coreArea_lsu_logic_waitingResponse <= 1'b0;
      coreArea_lsu_logic_nextId <= 16'h0001;
      coreArea_currentEpoch <= 4'b0000;
      coreArea_rvfiPlugin_order <= 64'h0;
      coreArea_perfCounters_cycles <= 64'h0;
      coreArea_perfCounters_instret <= 64'h0;
      coreArea_perfCounters_stallsHazard <= 64'h0;
      coreArea_perfCounters_stallsFetch <= 64'h0;
      coreArea_perfCounters_stallsMem <= 64'h0;
      coreArea_perfCounters_branches <= 64'h0;
      coreArea_perfCounters_branchesTaken <= 64'h0;
      coreArea_perfCounters_flushes <= 64'h0;
      coreArea_pipeline_ctrl_1_up_valid <= 1'b0;
      coreArea_pipeline_ctrl_2_up_valid <= 1'b0;
      coreArea_pipeline_ctrl_3_up_valid <= 1'b0;
      coreArea_pipeline_ctrl_4_up_valid <= 1'b0;
      coreArea_pipeline_ctrl_5_up_valid <= 1'b0;
      coreArea_pipeline_ctrl_6_up_valid <= 1'b0;
      coreArea_pipeline_ctrl_7_up_valid <= 1'b0;
      end else begin
        if(coreArea_pc_exception_valid) begin
          coreArea_pc_PC_cur <= coreArea_pc_exception_payload_vector;
        end else begin
          if(coreArea_pc_flush_valid) begin
            coreArea_pc_PC_cur <= coreArea_pc_flush_payload_address;
          end else begin
            if(coreArea_pc_jump_valid) begin
              coreArea_pc_PC_cur <= coreArea_pc_jump_payload_target;
            end else begin
              if(coreArea_pipeline_ctrl_0_down_isReady) begin
                coreArea_pc_PC_cur <= (coreArea_pc_PC_cur + 64'h0000000000000004);
              end
            end
          end
        end
        coreArea_fetch_inflight <= (_zz_coreArea_fetch_inflight - _zz_coreArea_fetch_inflight_3);
        coreArea_fetch_flushPending <= coreArea_fetch_io_flush;
        if(coreArea_fetch_flushPending) begin
          coreArea_fetch_epoch <= (coreArea_fetch_epoch + 16'h0001);
        end
        if(coreArea_fetch_cmdFire) begin
          coreArea_fetch_cmdArea_reqSent <= 1'b1;
        end
        if(coreArea_pipeline_ctrl_1_down_isFiring) begin
          coreArea_fetch_cmdArea_reqSent <= 1'b0;
        end
        if(coreArea_fetch_io_flush) begin
          coreArea_fetch_cmdArea_reqSent <= 1'b0;
        end
        if(when_scheduler_l187) begin
          coreArea_dispatcher_hcs_regBusy[coreArea_pipeline_ctrl_4_up_Decoder_RD_ADDR] <= 1'b1;
        end
        if(when_scheduler_l196) begin
          coreArea_dispatcher_hcs_regBusy[coreArea_pipeline_ctrl_7_down_WriteBack_RESULT_address] <= 1'b0;
        end
        if(when_scheduler_l200) begin
          coreArea_dispatcher_hcs_regBusy[coreArea_pipeline_ctrl_4_up_Decoder_RD_ADDR] <= 1'b1;
        end
        coreArea_dispatcher_hcs_init_value <= coreArea_dispatcher_hcs_init_valueNext;
        if(when_scheduler_l250) begin
          coreArea_dispatcher_hcs_regBusy <= 32'h0;
        end
        if(when_Lsu_l151) begin
          if(when_Lsu_l152) begin
            if(when_Lsu_l153) begin
              coreArea_lsu_logic_waitingResponse <= 1'b1;
              coreArea_lsu_logic_nextId <= (coreArea_lsu_logic_nextId + 16'h0001);
            end
          end else begin
            if(coreArea_lsu_logic_responseArriving) begin
              coreArea_lsu_logic_waitingResponse <= 1'b0;
            end
          end
        end
        if(coreArea_branch_logic_jumpCmd_valid) begin
          coreArea_currentEpoch <= (coreArea_currentEpoch + 4'b0001);
        end
        if(coreArea_pipeline_ctrl_7_up_Common_COMMIT) begin
          coreArea_rvfiPlugin_order <= (coreArea_rvfiPlugin_order + 64'h0000000000000001);
        end
        coreArea_perfCounters_cycles <= (coreArea_perfCounters_cycles + 64'h0000000000000001);
        if(coreArea_pipeline_ctrl_7_up_Common_COMMIT) begin
          coreArea_perfCounters_instret <= (coreArea_perfCounters_instret + 64'h0000000000000001);
        end
        if(coreArea_perfCounters_hazardStall) begin
          coreArea_perfCounters_stallsHazard <= (coreArea_perfCounters_stallsHazard + 64'h0000000000000001);
        end
        if(coreArea_perfCounters_fetchStall) begin
          coreArea_perfCounters_stallsFetch <= (coreArea_perfCounters_stallsFetch + 64'h0000000000000001);
        end
        if(coreArea_perfCounters_memStall) begin
          coreArea_perfCounters_stallsMem <= (coreArea_perfCounters_stallsMem + 64'h0000000000000001);
        end
        if(coreArea_perfCounters_branchExecuted) begin
          coreArea_perfCounters_branches <= (coreArea_perfCounters_branches + 64'h0000000000000001);
        end
        if(coreArea_perfCounters_branchTaken) begin
          coreArea_perfCounters_branchesTaken <= (coreArea_perfCounters_branchesTaken + 64'h0000000000000001);
        end
        if(coreArea_perfCounters_pipelineFlush) begin
          coreArea_perfCounters_flushes <= (coreArea_perfCounters_flushes + 64'h0000000000000001);
        end
        if(coreArea_pipeline_ctrl_1_up_forgetOne) begin
          coreArea_pipeline_ctrl_1_up_valid <= 1'b0;
        end
        if(coreArea_pipeline_ctrl_0_down_isReady) begin
          coreArea_pipeline_ctrl_1_up_valid <= coreArea_pipeline_ctrl_0_down_isValid;
        end
        if(coreArea_pipeline_ctrl_2_up_forgetOne) begin
          coreArea_pipeline_ctrl_2_up_valid <= 1'b0;
        end
        if(coreArea_pipeline_ctrl_1_down_isReady) begin
          coreArea_pipeline_ctrl_2_up_valid <= coreArea_pipeline_ctrl_1_down_isValid;
        end
        if(coreArea_pipeline_ctrl_3_up_forgetOne) begin
          coreArea_pipeline_ctrl_3_up_valid <= 1'b0;
        end
        if(coreArea_pipeline_ctrl_2_down_isReady) begin
          coreArea_pipeline_ctrl_3_up_valid <= coreArea_pipeline_ctrl_2_down_isValid;
        end
        if(coreArea_pipeline_ctrl_4_up_forgetOne) begin
          coreArea_pipeline_ctrl_4_up_valid <= 1'b0;
        end
        if(coreArea_pipeline_ctrl_3_down_isReady) begin
          coreArea_pipeline_ctrl_4_up_valid <= coreArea_pipeline_ctrl_3_down_isValid;
        end
        if(coreArea_pipeline_ctrl_5_up_forgetOne) begin
          coreArea_pipeline_ctrl_5_up_valid <= 1'b0;
        end
        if(coreArea_pipeline_ctrl_4_down_isReady) begin
          coreArea_pipeline_ctrl_5_up_valid <= coreArea_pipeline_ctrl_4_down_isValid;
        end
        if(coreArea_pipeline_ctrl_5_down_isReady) begin
          coreArea_pipeline_ctrl_6_up_valid <= coreArea_pipeline_ctrl_5_down_isValid;
        end
        if(coreArea_pipeline_ctrl_6_down_isReady) begin
          coreArea_pipeline_ctrl_7_up_valid <= coreArea_pipeline_ctrl_6_down_isValid;
        end
      end
    end
  end

  always @(posedge io_clk) begin
    if(io_clkEnable) begin
      if(when_Lsu_l151) begin
        if(when_Lsu_l152) begin
          if(when_Lsu_l153) begin
            coreArea_lsu_logic_waitId <= coreArea_lsu_logic_nextId;
          end
        end else begin
          if(coreArea_lsu_logic_responseArriving) begin
            coreArea_lsu_logic_latchedRspData <= coreArea_lsu_io_dBus_rsp_payload_data;
          end
        end
      end
      coreArea_pipeline_ctrl_7_up_Common_TRAP <= 1'b0;
      if(coreArea_pipeline_ctrl_0_down_isReady) begin
        coreArea_pipeline_ctrl_1_up_PC_PC <= coreArea_pipeline_ctrl_0_down_PC_PC;
      end
      if(coreArea_pipeline_ctrl_1_down_isReady) begin
        coreArea_pipeline_ctrl_2_up_PC_PC <= coreArea_pipeline_ctrl_1_down_PC_PC;
      end
      if(coreArea_pipeline_ctrl_2_down_isReady) begin
        coreArea_pipeline_ctrl_3_up_PC_PC <= coreArea_pipeline_ctrl_2_down_PC_PC;
        coreArea_pipeline_ctrl_3_up_Decoder_INSTRUCTION <= coreArea_pipeline_ctrl_2_down_Decoder_INSTRUCTION;
        coreArea_pipeline_ctrl_3_up_Common_SPEC_EPOCH <= coreArea_pipeline_ctrl_2_down_Common_SPEC_EPOCH;
      end
      if(coreArea_pipeline_ctrl_3_down_isReady) begin
        coreArea_pipeline_ctrl_4_up_PC_PC <= coreArea_pipeline_ctrl_3_down_PC_PC;
        coreArea_pipeline_ctrl_4_up_Decoder_INSTRUCTION <= coreArea_pipeline_ctrl_3_down_Decoder_INSTRUCTION;
        coreArea_pipeline_ctrl_4_up_Common_SPEC_EPOCH <= coreArea_pipeline_ctrl_3_down_Common_SPEC_EPOCH;
        coreArea_pipeline_ctrl_4_up_Decoder_VALID <= coreArea_pipeline_ctrl_3_down_Decoder_VALID;
        coreArea_pipeline_ctrl_4_up_Decoder_LEGAL <= coreArea_pipeline_ctrl_3_down_Decoder_LEGAL;
        coreArea_pipeline_ctrl_4_up_Decoder_EXECUTION_UNIT <= coreArea_pipeline_ctrl_3_down_Decoder_EXECUTION_UNIT;
        coreArea_pipeline_ctrl_4_up_Decoder_RS1TYPE <= coreArea_pipeline_ctrl_3_down_Decoder_RS1TYPE;
        coreArea_pipeline_ctrl_4_up_Decoder_RS2TYPE <= coreArea_pipeline_ctrl_3_down_Decoder_RS2TYPE;
        coreArea_pipeline_ctrl_4_up_Decoder_IMMSEL <= coreArea_pipeline_ctrl_3_down_Decoder_IMMSEL;
        coreArea_pipeline_ctrl_4_up_Decoder_MicroCode <= coreArea_pipeline_ctrl_3_down_Decoder_MicroCode;
        coreArea_pipeline_ctrl_4_up_Decoder_RD_ADDR <= coreArea_pipeline_ctrl_3_down_Decoder_RD_ADDR;
        coreArea_pipeline_ctrl_4_up_Decoder_RS1_ADDR <= coreArea_pipeline_ctrl_3_down_Decoder_RS1_ADDR;
        coreArea_pipeline_ctrl_4_up_Decoder_RS2_ADDR <= coreArea_pipeline_ctrl_3_down_Decoder_RS2_ADDR;
      end
      if(coreArea_pipeline_ctrl_4_down_isReady) begin
        coreArea_pipeline_ctrl_5_up_PC_PC <= coreArea_pipeline_ctrl_4_down_PC_PC;
        coreArea_pipeline_ctrl_5_up_Decoder_INSTRUCTION <= coreArea_pipeline_ctrl_4_down_Decoder_INSTRUCTION;
        coreArea_pipeline_ctrl_5_up_Common_SPEC_EPOCH <= coreArea_pipeline_ctrl_4_down_Common_SPEC_EPOCH;
        coreArea_pipeline_ctrl_5_up_Decoder_VALID <= coreArea_pipeline_ctrl_4_down_Decoder_VALID;
        coreArea_pipeline_ctrl_5_up_Decoder_LEGAL <= coreArea_pipeline_ctrl_4_down_Decoder_LEGAL;
        coreArea_pipeline_ctrl_5_up_Decoder_RS1TYPE <= coreArea_pipeline_ctrl_4_down_Decoder_RS1TYPE;
        coreArea_pipeline_ctrl_5_up_Decoder_RS2TYPE <= coreArea_pipeline_ctrl_4_down_Decoder_RS2TYPE;
        coreArea_pipeline_ctrl_5_up_Decoder_IMMSEL <= coreArea_pipeline_ctrl_4_down_Decoder_IMMSEL;
        coreArea_pipeline_ctrl_5_up_Decoder_MicroCode <= coreArea_pipeline_ctrl_4_down_Decoder_MicroCode;
        coreArea_pipeline_ctrl_5_up_Decoder_RD_ADDR <= coreArea_pipeline_ctrl_4_down_Decoder_RD_ADDR;
        coreArea_pipeline_ctrl_5_up_Decoder_RS1_ADDR <= coreArea_pipeline_ctrl_4_down_Decoder_RS1_ADDR;
        coreArea_pipeline_ctrl_5_up_Decoder_RS2_ADDR <= coreArea_pipeline_ctrl_4_down_Decoder_RS2_ADDR;
        coreArea_pipeline_ctrl_5_up_Common_LANE_SEL <= coreArea_pipeline_ctrl_4_down_Common_LANE_SEL;
        coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOALU <= coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOALU;
        coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOBRANCH <= coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOBRANCH;
        coreArea_pipeline_ctrl_5_up_Dispatch_SENDTOAGU <= coreArea_pipeline_ctrl_4_down_Dispatch_SENDTOAGU;
      end
      if(coreArea_pipeline_ctrl_5_down_isReady) begin
        coreArea_pipeline_ctrl_6_up_PC_PC <= coreArea_pipeline_ctrl_5_down_PC_PC;
        coreArea_pipeline_ctrl_6_up_Decoder_INSTRUCTION <= coreArea_pipeline_ctrl_5_down_Decoder_INSTRUCTION;
        coreArea_pipeline_ctrl_6_up_Common_SPEC_EPOCH <= coreArea_pipeline_ctrl_5_down_Common_SPEC_EPOCH;
        coreArea_pipeline_ctrl_6_up_Decoder_VALID <= coreArea_pipeline_ctrl_5_down_Decoder_VALID;
        coreArea_pipeline_ctrl_6_up_Decoder_LEGAL <= coreArea_pipeline_ctrl_5_down_Decoder_LEGAL;
        coreArea_pipeline_ctrl_6_up_Decoder_MicroCode <= coreArea_pipeline_ctrl_5_down_Decoder_MicroCode;
        coreArea_pipeline_ctrl_6_up_Decoder_RD_ADDR <= coreArea_pipeline_ctrl_5_down_Decoder_RD_ADDR;
        coreArea_pipeline_ctrl_6_up_Decoder_RS1_ADDR <= coreArea_pipeline_ctrl_5_down_Decoder_RS1_ADDR;
        coreArea_pipeline_ctrl_6_up_Decoder_RS2_ADDR <= coreArea_pipeline_ctrl_5_down_Decoder_RS2_ADDR;
        coreArea_pipeline_ctrl_6_up_Common_LANE_SEL <= coreArea_pipeline_ctrl_5_down_Common_LANE_SEL;
        coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOALU <= coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOALU;
        coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOBRANCH <= coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOBRANCH;
        coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOAGU <= coreArea_pipeline_ctrl_5_down_Dispatch_SENDTOAGU;
        coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1 <= coreArea_pipeline_ctrl_5_down_SrcPlugin_RS1;
        coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2 <= coreArea_pipeline_ctrl_5_down_SrcPlugin_RS2;
        coreArea_pipeline_ctrl_6_up_SrcPlugin_IMMED <= coreArea_pipeline_ctrl_5_down_SrcPlugin_IMMED;
      end
      if(coreArea_pipeline_ctrl_6_down_isReady) begin
        coreArea_pipeline_ctrl_7_up_PC_PC <= coreArea_pipeline_ctrl_6_down_PC_PC;
        coreArea_pipeline_ctrl_7_up_Decoder_INSTRUCTION <= coreArea_pipeline_ctrl_6_down_Decoder_INSTRUCTION;
        coreArea_pipeline_ctrl_7_up_Common_SPEC_EPOCH <= coreArea_pipeline_ctrl_6_down_Common_SPEC_EPOCH;
        coreArea_pipeline_ctrl_7_up_Decoder_VALID <= coreArea_pipeline_ctrl_6_down_Decoder_VALID;
        coreArea_pipeline_ctrl_7_up_Decoder_RS1_ADDR <= coreArea_pipeline_ctrl_6_down_Decoder_RS1_ADDR;
        coreArea_pipeline_ctrl_7_up_Decoder_RS2_ADDR <= coreArea_pipeline_ctrl_6_down_Decoder_RS2_ADDR;
        coreArea_pipeline_ctrl_7_up_Common_LANE_SEL <= coreArea_pipeline_ctrl_6_down_Common_LANE_SEL;
        coreArea_pipeline_ctrl_7_up_Dispatch_SENDTOALU <= coreArea_pipeline_ctrl_6_down_Dispatch_SENDTOALU;
        coreArea_pipeline_ctrl_7_up_SrcPlugin_RS1 <= coreArea_pipeline_ctrl_6_down_SrcPlugin_RS1;
        coreArea_pipeline_ctrl_7_up_SrcPlugin_RS2 <= coreArea_pipeline_ctrl_6_down_SrcPlugin_RS2;
        coreArea_pipeline_ctrl_7_up_SrcPlugin_IMMED <= coreArea_pipeline_ctrl_6_down_SrcPlugin_IMMED;
        coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_valid <= coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_valid;
        coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_address <= coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_address;
        coreArea_pipeline_ctrl_7_up_WriteBack_RESULT_data <= coreArea_pipeline_ctrl_6_down_WriteBack_RESULT_data;
        coreArea_pipeline_ctrl_7_up_Branch_BRANCH_TAKEN <= coreArea_pipeline_ctrl_6_down_Branch_BRANCH_TAKEN;
        coreArea_pipeline_ctrl_7_up_Branch_BRANCH_TARGET <= coreArea_pipeline_ctrl_6_down_Branch_BRANCH_TARGET;
        coreArea_pipeline_ctrl_7_up_LSU_MEM_ADDR <= coreArea_pipeline_ctrl_6_down_LSU_MEM_ADDR;
        coreArea_pipeline_ctrl_7_up_LSU_MEM_WMASK <= coreArea_pipeline_ctrl_6_down_LSU_MEM_WMASK;
        coreArea_pipeline_ctrl_7_up_LSU_MEM_WDATA <= coreArea_pipeline_ctrl_6_down_LSU_MEM_WDATA;
        coreArea_pipeline_ctrl_7_up_LSU_MEM_RMASK <= coreArea_pipeline_ctrl_6_down_LSU_MEM_RMASK;
        coreArea_pipeline_ctrl_7_up_LSU_MEM_RDATA <= coreArea_pipeline_ctrl_6_down_LSU_MEM_RDATA;
        coreArea_pipeline_ctrl_7_up_Common_TRAP <= coreArea_pipeline_ctrl_6_down_Common_TRAP;
      end
    end
  end


endmodule

module IntRegFile (
  input  wire          io_reads_0_valid,
  input  wire [4:0]    io_reads_0_address,
  output wire [63:0]   io_reads_0_data,
  input  wire          io_reads_1_valid,
  input  wire [4:0]    io_reads_1_address,
  output wire [63:0]   io_reads_1_data,
  input  wire          io_writes_0_valid,
  input  wire [4:0]    io_writes_0_address,
  input  wire [63:0]   io_writes_0_data,
  input  wire          io_clk,
  input  wire          io_reset,
  input  wire          io_clkEnable
);

  wire       [63:0]   mem_spinal_port0;
  wire       [63:0]   mem_spinal_port1;
  reg                 _zz_1;
  wire                when_regFile_l66;
  (* ram_style = "distributed" *) reg [63:0] mem [0:31];

  assign mem_spinal_port0 = mem[io_reads_0_address];
  assign mem_spinal_port1 = mem[io_reads_1_address];
  always @(posedge io_clk) begin
    if(io_clkEnable) begin
      if(_zz_1) begin
        mem[io_writes_0_address] <= io_writes_0_data;
      end
    end
  end

  always @(*) begin
    _zz_1 = 1'b0;
    if(when_regFile_l66) begin
      _zz_1 = 1'b1;
    end
  end

  assign io_reads_0_data = ((io_reads_0_address == 5'h0) ? 64'h0 : mem_spinal_port0);
  assign io_reads_1_data = ((io_reads_1_address == 5'h0) ? 64'h0 : mem_spinal_port1);
  assign when_regFile_l66 = (io_writes_0_valid && (io_writes_0_address != 5'h0));

endmodule

module StreamFifo (
  input  wire          io_push_valid,
  output wire          io_push_ready,
  input  wire [63:0]   io_push_payload_data,
  input  wire [15:0]   io_push_payload_epoch,
  output wire          io_pop_valid,
  input  wire          io_pop_ready,
  output wire [63:0]   io_pop_payload_data,
  output wire [15:0]   io_pop_payload_epoch,
  input  wire          io_flush,
  output wire [1:0]    io_occupancy,
  output wire [1:0]    io_availability,
  input  wire          io_clk,
  input  wire          io_reset,
  input  wire          io_clkEnable
);

  reg        [79:0]   logic_ram_spinal_port1;
  wire       [79:0]   _zz_logic_ram_port;
  reg                 _zz_1;
  wire                logic_ptr_doPush;
  wire                logic_ptr_doPop;
  wire                logic_ptr_full;
  wire                logic_ptr_empty;
  reg        [1:0]    logic_ptr_push;
  reg        [1:0]    logic_ptr_pop;
  wire       [1:0]    logic_ptr_occupancy;
  wire       [1:0]    logic_ptr_popOnIo;
  wire                when_Stream_l1455;
  reg                 logic_ptr_wentUp;
  wire                io_push_fire;
  wire                logic_push_onRam_write_valid;
  wire       [0:0]    logic_push_onRam_write_payload_address;
  wire       [63:0]   logic_push_onRam_write_payload_data_data;
  wire       [15:0]   logic_push_onRam_write_payload_data_epoch;
  wire                logic_pop_addressGen_valid;
  reg                 logic_pop_addressGen_ready;
  wire       [0:0]    logic_pop_addressGen_payload;
  wire                logic_pop_addressGen_fire;
  wire                logic_pop_sync_readArbitation_valid;
  wire                logic_pop_sync_readArbitation_ready;
  wire       [0:0]    logic_pop_sync_readArbitation_payload;
  reg                 logic_pop_addressGen_rValid;
  reg        [0:0]    logic_pop_addressGen_rData;
  wire                when_Stream_l477;
  wire                logic_pop_sync_readPort_cmd_valid;
  wire       [0:0]    logic_pop_sync_readPort_cmd_payload;
  wire       [63:0]   logic_pop_sync_readPort_rsp_data;
  wire       [15:0]   logic_pop_sync_readPort_rsp_epoch;
  wire       [79:0]   _zz_logic_pop_sync_readPort_rsp_data;
  wire                logic_pop_addressGen_toFlowFire_valid;
  wire       [0:0]    logic_pop_addressGen_toFlowFire_payload;
  wire                logic_pop_sync_readArbitation_translated_valid;
  wire                logic_pop_sync_readArbitation_translated_ready;
  wire       [63:0]   logic_pop_sync_readArbitation_translated_payload_data;
  wire       [15:0]   logic_pop_sync_readArbitation_translated_payload_epoch;
  wire                logic_pop_sync_readArbitation_fire;
  reg        [1:0]    logic_pop_sync_popReg;
  reg [79:0] logic_ram [0:1];

  assign _zz_logic_ram_port = {logic_push_onRam_write_payload_data_epoch,logic_push_onRam_write_payload_data_data};
  always @(posedge io_clk) begin
    if(io_clkEnable) begin
      if(_zz_1) begin
        logic_ram[logic_push_onRam_write_payload_address] <= _zz_logic_ram_port;
      end
    end
  end

  always @(posedge io_clk) begin
    if(io_clkEnable) begin
      if(logic_pop_sync_readPort_cmd_valid) begin
        logic_ram_spinal_port1 <= logic_ram[logic_pop_sync_readPort_cmd_payload];
      end
    end
  end

  always @(*) begin
    _zz_1 = 1'b0;
    if(logic_push_onRam_write_valid) begin
      _zz_1 = 1'b1;
    end
  end

  assign when_Stream_l1455 = (logic_ptr_doPush != logic_ptr_doPop);
  assign logic_ptr_full = (((logic_ptr_push ^ logic_ptr_popOnIo) ^ 2'b10) == 2'b00);
  assign logic_ptr_empty = (logic_ptr_push == logic_ptr_pop);
  assign logic_ptr_occupancy = (logic_ptr_push - logic_ptr_popOnIo);
  assign io_push_ready = (! logic_ptr_full);
  assign io_push_fire = (io_push_valid && io_push_ready);
  assign logic_ptr_doPush = io_push_fire;
  assign logic_push_onRam_write_valid = io_push_fire;
  assign logic_push_onRam_write_payload_address = logic_ptr_push[0:0];
  assign logic_push_onRam_write_payload_data_data = io_push_payload_data;
  assign logic_push_onRam_write_payload_data_epoch = io_push_payload_epoch;
  assign logic_pop_addressGen_valid = (! logic_ptr_empty);
  assign logic_pop_addressGen_payload = logic_ptr_pop[0:0];
  assign logic_pop_addressGen_fire = (logic_pop_addressGen_valid && logic_pop_addressGen_ready);
  assign logic_ptr_doPop = logic_pop_addressGen_fire;
  always @(*) begin
    logic_pop_addressGen_ready = logic_pop_sync_readArbitation_ready;
    if(when_Stream_l477) begin
      logic_pop_addressGen_ready = 1'b1;
    end
  end

  assign when_Stream_l477 = (! logic_pop_sync_readArbitation_valid);
  assign logic_pop_sync_readArbitation_valid = logic_pop_addressGen_rValid;
  assign logic_pop_sync_readArbitation_payload = logic_pop_addressGen_rData;
  assign _zz_logic_pop_sync_readPort_rsp_data = logic_ram_spinal_port1;
  assign logic_pop_sync_readPort_rsp_data = _zz_logic_pop_sync_readPort_rsp_data[63 : 0];
  assign logic_pop_sync_readPort_rsp_epoch = _zz_logic_pop_sync_readPort_rsp_data[79 : 64];
  assign logic_pop_addressGen_toFlowFire_valid = logic_pop_addressGen_fire;
  assign logic_pop_addressGen_toFlowFire_payload = logic_pop_addressGen_payload;
  assign logic_pop_sync_readPort_cmd_valid = logic_pop_addressGen_toFlowFire_valid;
  assign logic_pop_sync_readPort_cmd_payload = logic_pop_addressGen_toFlowFire_payload;
  assign logic_pop_sync_readArbitation_translated_valid = logic_pop_sync_readArbitation_valid;
  assign logic_pop_sync_readArbitation_ready = logic_pop_sync_readArbitation_translated_ready;
  assign logic_pop_sync_readArbitation_translated_payload_data = logic_pop_sync_readPort_rsp_data;
  assign logic_pop_sync_readArbitation_translated_payload_epoch = logic_pop_sync_readPort_rsp_epoch;
  assign io_pop_valid = logic_pop_sync_readArbitation_translated_valid;
  assign logic_pop_sync_readArbitation_translated_ready = io_pop_ready;
  assign io_pop_payload_data = logic_pop_sync_readArbitation_translated_payload_data;
  assign io_pop_payload_epoch = logic_pop_sync_readArbitation_translated_payload_epoch;
  assign logic_pop_sync_readArbitation_fire = (logic_pop_sync_readArbitation_valid && logic_pop_sync_readArbitation_ready);
  assign logic_ptr_popOnIo = logic_pop_sync_popReg;
  assign io_occupancy = logic_ptr_occupancy;
  assign io_availability = (2'b10 - logic_ptr_occupancy);
  always @(posedge io_clk) begin
    if(io_clkEnable) begin
      if(io_reset) begin
      logic_ptr_push <= 2'b00;
      logic_ptr_pop <= 2'b00;
      logic_ptr_wentUp <= 1'b0;
      logic_pop_addressGen_rValid <= 1'b0;
      logic_pop_sync_popReg <= 2'b00;
      end else begin
        if(when_Stream_l1455) begin
          logic_ptr_wentUp <= logic_ptr_doPush;
        end
        if(io_flush) begin
          logic_ptr_wentUp <= 1'b0;
        end
        if(logic_ptr_doPush) begin
          logic_ptr_push <= (logic_ptr_push + 2'b01);
        end
        if(logic_ptr_doPop) begin
          logic_ptr_pop <= (logic_ptr_pop + 2'b01);
        end
        if(io_flush) begin
          logic_ptr_push <= 2'b00;
          logic_ptr_pop <= 2'b00;
        end
        if(logic_pop_addressGen_ready) begin
          logic_pop_addressGen_rValid <= logic_pop_addressGen_valid;
        end
        if(io_flush) begin
          logic_pop_addressGen_rValid <= 1'b0;
        end
        if(logic_pop_sync_readArbitation_fire) begin
          logic_pop_sync_popReg <= logic_ptr_pop;
        end
        if(io_flush) begin
          logic_pop_sync_popReg <= 2'b00;
        end
      end
    end
  end

  always @(posedge io_clk) begin
    if(io_clkEnable) begin
      if(logic_pop_addressGen_ready) begin
        logic_pop_addressGen_rData <= logic_pop_addressGen_payload;
      end
    end
  end


endmodule
