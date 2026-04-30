`include "rvfi_macros.vh"

module rvfi_wrapper (
    input clock,
    input reset,
    `RVFI_OUTPUTS
);

    (* keep *) `rvformal_rand_reg [63:0] i_axi_r_data;
    (* keep *) `rvformal_rand_reg [63:0] d_axi_r_data;

    reg        i_pending_valid;
    reg [15:0] i_pending_id;
    reg [7:0]  i_pending_beats_left;

    reg        d_read_pending_valid;
    reg [15:0] d_read_pending_id;

    reg        d_write_addr_seen;
    reg [15:0] d_write_pending_id;
    reg        d_write_data_seen;
    reg        d_write_resp_valid;

    wire i_axi_arw_valid;
    wire [15:0] i_axi_arw_id;
    wire [7:0] i_axi_arw_len;
    wire [2:0] i_axi_arw_size;
    wire [1:0] i_axi_arw_burst;
    wire i_axi_arw_write;

    wire d_axi_arw_valid;
    wire [15:0] d_axi_arw_id;
    wire [7:0] d_axi_arw_len;
    wire [2:0] d_axi_arw_size;
    wire [1:0] d_axi_arw_burst;
    wire d_axi_arw_write;
    wire d_axi_w_valid;
    wire d_axi_w_last;

    wire i_axi_arw_ready = !i_pending_valid;
    wire i_axi_r_ready;
    wire i_axi_r_valid = i_pending_valid;
    wire [15:0] i_axi_r_id = i_pending_id;
    wire i_axi_r_last = (i_pending_beats_left == 8'd1);

    wire d_axi_arw_ready = !d_read_pending_valid && !d_write_addr_seen;
    wire d_axi_w_ready = !d_write_data_seen;
    wire d_axi_b_ready;
    wire d_axi_b_valid = d_write_resp_valid;
    wire [15:0] d_axi_b_id = d_write_pending_id;
    wire d_axi_r_ready;
    wire d_axi_r_valid = d_read_pending_valid;
    wire [15:0] d_axi_r_id = d_read_pending_id;

    always @(posedge clock) begin
        if (reset) begin
            i_pending_valid <= 1'b0;
            i_pending_id <= 16'd0;
            i_pending_beats_left <= 8'd0;
            d_read_pending_valid <= 1'b0;
            d_read_pending_id <= 16'd0;
            d_write_addr_seen <= 1'b0;
            d_write_pending_id <= 16'd0;
            d_write_data_seen <= 1'b0;
            d_write_resp_valid <= 1'b0;
        end else begin
            if (i_pending_valid && i_axi_r_ready) begin
                if (i_pending_beats_left == 8'd1) begin
                    i_pending_valid <= 1'b0;
                    i_pending_beats_left <= 8'd0;
                end else begin
                    i_pending_beats_left <= i_pending_beats_left - 8'd1;
                end
            end
            if (i_axi_arw_valid && i_axi_arw_ready) begin
                i_pending_valid <= 1'b1;
                i_pending_id <= i_axi_arw_id;
                i_pending_beats_left <= i_axi_arw_len + 8'd1;
            end

            if (d_read_pending_valid && d_axi_r_ready) begin
                d_read_pending_valid <= 1'b0;
            end
            if (d_axi_arw_valid && d_axi_arw_ready && !d_axi_arw_write) begin
                d_read_pending_valid <= 1'b1;
                d_read_pending_id <= d_axi_arw_id;
            end

            if (d_axi_arw_valid && d_axi_arw_ready && d_axi_arw_write) begin
                d_write_addr_seen <= 1'b1;
                d_write_pending_id <= d_axi_arw_id;
            end
            if (d_axi_w_valid && d_axi_w_ready && d_axi_w_last) begin
                d_write_data_seen <= 1'b1;
            end

            if (d_write_addr_seen && d_write_data_seen) begin
                d_write_resp_valid <= 1'b1;
                d_write_addr_seen <= 1'b0;
                d_write_data_seen <= 1'b0;
            end

            if (d_write_resp_valid && d_axi_b_ready) begin
                d_write_resp_valid <= 1'b0;
            end
        end
    end

    CPU cpu (
        .io_clk(clock),
        .io_clkEnable(1'b1),
        .io_reset(reset),

        .io_iAxi_arw_valid(i_axi_arw_valid),
        .io_iAxi_arw_ready(i_axi_arw_ready),
        .io_iAxi_arw_payload_addr(),
        .io_iAxi_arw_payload_id(i_axi_arw_id),
        .io_iAxi_arw_payload_len(i_axi_arw_len),
        .io_iAxi_arw_payload_size(i_axi_arw_size),
        .io_iAxi_arw_payload_burst(i_axi_arw_burst),
        .io_iAxi_arw_payload_write(i_axi_arw_write),
        .io_iAxi_w_valid(),
        .io_iAxi_w_ready(1'b1),
        .io_iAxi_w_payload_data(),
        .io_iAxi_w_payload_strb(),
        .io_iAxi_w_payload_last(),
        .io_iAxi_b_valid(1'b0),
        .io_iAxi_b_ready(),
        .io_iAxi_b_payload_id(16'd0),
        .io_iAxi_b_payload_resp(2'b00),
        .io_iAxi_r_valid(i_axi_r_valid),
        .io_iAxi_r_ready(i_axi_r_ready),
        .io_iAxi_r_payload_data(i_axi_r_data),
        .io_iAxi_r_payload_id(i_axi_r_id),
        .io_iAxi_r_payload_resp(2'b00),
        .io_iAxi_r_payload_last(i_axi_r_last),

        .io_dAxi_arw_valid(d_axi_arw_valid),
        .io_dAxi_arw_ready(d_axi_arw_ready),
        .io_dAxi_arw_payload_addr(),
        .io_dAxi_arw_payload_id(d_axi_arw_id),
        .io_dAxi_arw_payload_len(d_axi_arw_len),
        .io_dAxi_arw_payload_size(d_axi_arw_size),
        .io_dAxi_arw_payload_burst(d_axi_arw_burst),
        .io_dAxi_arw_payload_write(d_axi_arw_write),
        .io_dAxi_w_valid(d_axi_w_valid),
        .io_dAxi_w_ready(d_axi_w_ready),
        .io_dAxi_w_payload_data(),
        .io_dAxi_w_payload_strb(),
        .io_dAxi_w_payload_last(d_axi_w_last),
        .io_dAxi_b_valid(d_axi_b_valid),
        .io_dAxi_b_ready(d_axi_b_ready),
        .io_dAxi_b_payload_id(d_axi_b_id),
        .io_dAxi_b_payload_resp(2'b00),
        .io_dAxi_r_valid(d_axi_r_valid),
        .io_dAxi_r_ready(d_axi_r_ready),
        .io_dAxi_r_payload_data(d_axi_r_data),
        .io_dAxi_r_payload_id(d_axi_r_id),
        .io_dAxi_r_payload_resp(2'b00),
        .io_dAxi_r_payload_last(1'b1),

        .io_rvfi_valid(rvfi_valid),
        .io_rvfi_order(rvfi_order),
        .io_rvfi_insn(rvfi_insn),
        .io_rvfi_trap(rvfi_trap),
        .io_rvfi_halt(rvfi_halt),
        .io_rvfi_intr(rvfi_intr),
        .io_rvfi_mode(rvfi_mode),
        .io_rvfi_ixl(rvfi_ixl),
        .io_rvfi_rs1_addr(rvfi_rs1_addr),
        .io_rvfi_rs2_addr(rvfi_rs2_addr),
        .io_rvfi_rs1_rdata(rvfi_rs1_rdata),
        .io_rvfi_rs2_rdata(rvfi_rs2_rdata),
        .io_rvfi_rd_addr(rvfi_rd_addr),
        .io_rvfi_rd_wdata(rvfi_rd_wdata),
        .io_rvfi_pc_rdata(rvfi_pc_rdata),
        .io_rvfi_pc_wdata(rvfi_pc_wdata),
        .io_rvfi_mem_addr(rvfi_mem_addr),
        .io_rvfi_mem_rmask(rvfi_mem_rmask),
        .io_rvfi_mem_wmask(rvfi_mem_wmask),
        .io_rvfi_mem_rdata(rvfi_mem_rdata),
        .io_rvfi_mem_wdata(rvfi_mem_wdata),

        .io_dbg_commitValid(),
        .io_dbg_commitOrder(),
        .io_dbg_commitSeq(),
        .io_dbg_commitPulse(),
        .io_dbg_duplicateRetire(),
        .io_dbg_commitPc(),
        .io_dbg_commitInsn(),
        .io_dbg_commitRs1(),
        .io_dbg_commitRs2(),
        .io_dbg_commitRs1Data(),
        .io_dbg_commitRs2Data(),
        .io_dbg_commitRd(),
        .io_dbg_commitWe(),
        .io_dbg_commitWdata(),
        .io_dbg_commitTrap(),
        .io_dbg_commitTrapCause(),
        .io_dbg_commitTrapTval(),
        .io_dbg_redirectBranch(),
        .io_dbg_redirectTrap(),
        .io_dbg_redirectMret(),
        .io_dbg_redirectAny(),
        .io_dbg_redirectExecEpochMatches(),
        .io_dbg_redirectPcJumpValid(),
        .io_dbg_redirectPcJumpTarget(),
        .io_dbg_redirectPcExceptionValid(),
        .io_dbg_redirectPcExceptionTarget(),
        .io_dbg_liveTrapCause(),
        .io_dbg_liveTrapTval(),
        .io_dbg_squashed(),
        .io_dbg_f_pc(),
        .io_dbg_d_pc(),
        .io_dbg_x_pc(),
        .io_dbg_wb_pc(),
        .io_dbg_s4_valid(),
        .io_dbg_s4_fire(),
        .io_dbg_s4_seq(),
        .io_dbg_s5_valid(),
        .io_dbg_s5_fire(),
        .io_dbg_s5_lane(),
        .io_dbg_s5_seq(),
        .io_dbg_s6_valid(),
        .io_dbg_s6_fire(),
        .io_dbg_s6_lane(),
        .io_dbg_s6_seq(),
        .io_dbg_s7_valid(),
        .io_dbg_s7_fire(),
        .io_dbg_s7_lane(),
        .io_dbg_s7_seq(),
        .io_dbg_memAddr(),
        .io_dbg_memRmask(),
        .io_dbg_memWmask(),
        .io_dbg_memRdata(),
        .io_dbg_memWdata(),
        .io_perf_cycles(),
        .io_perf_instret(),
        .io_perf_stallsHazard(),
        .io_perf_stallsFetch(),
        .io_perf_stallsMem(),
        .io_perf_stallsBackend(),
        .io_perf_stallsWriteback(),
        .io_perf_stallsCommit(),
        .io_perf_stallsMulDivBusy(),
        .io_perf_stallsLsuReplayOrWait(),
        .io_perf_stallsDispatchToSrc(),
        .io_perf_stallsSrcToExec(),
        .io_perf_stallsExecToWrite(),
        .io_perf_cyclesDispatchValid(),
        .io_perf_cyclesSrcValid(),
        .io_perf_cyclesExecValid(),
        .io_perf_cyclesWriteValid(),
        .io_perf_cyclesDispatchFire(),
        .io_perf_cyclesSrcFire(),
        .io_perf_cyclesExecFire(),
        .io_perf_cyclesWriteFire(),
        .io_perf_frontendPendingReqCycles(),
        .io_perf_frontendBeat0ValidCycles(),
        .io_perf_frontendBeat1ValidCycles(),
        .io_perf_frontendReqIssued(),
        .io_perf_frontendRspAccepted(),
        .io_perf_frontendNeedCurrentReq(),
        .io_perf_frontendNeedNextReq(),
        .io_perf_frontendPrefetchReq(),
        .io_perf_frontendWaitCurBeat(),
        .io_perf_frontendWaitNextBeat(),
        .io_perf_frontendTakeInsn(),
        .io_perf_frontendCurBeatHit(),
        .io_perf_frontendNextBeatHit(),
        .io_perf_frontendCmdValidCycles(),
        .io_perf_frontendPrefetchWindow(),
        .io_perf_frontendPrefetchBlockedNoCmd(),
        .io_perf_frontendPrefetchBlockedPending(),
        .io_perf_frontendPrefetchBlockedNextHit(),
        .io_perf_frontendLoopPredictUsed(),
        .io_perf_frontendLoopPredictHit(),
        .io_perf_frontendFastPredictHit(),
        .io_perf_frontendMainPredictHit(),
        .io_perf_frontendIndirectPredictHit(),
        .io_perf_frontendRasUse(),
        .io_perf_frontendRasRepair(),
        .io_perf_frontendFtqAlloc(),
        .io_perf_frontendFtqRestore(),
        .io_perf_frontendPredictedRedirect(),
        .io_perf_frontendMissCurrentBlock(),
        .io_perf_frontendMissNextBlock(),
        .io_perf_frontendMissPrefetch(),
        .io_perf_frontendReqBlockedOutstanding(),
        .io_perf_frontendPacketQueueFullCycles(),
        .io_perf_frontendStraddlePackets(),
        .io_perf_frontendSecondBlockUsed(),
        .io_perf_frontendSecondBlockLate(),
        .io_perf_frontendWrongPathBeats(),
        .io_perf_frontendWrongPathInsns(),
        .io_perf_l1iBankConflictCycles(),
        .io_perf_l1iBankBusyCycles(),
        .io_perf_l1iCrossBankDualFetchSuccess(),
        .io_perf_backendOccupancy0(),
        .io_perf_backendOccupancy1(),
        .io_perf_backendOccupancy2(),
        .io_perf_backendOccupancy3(),
        .io_perf_backendOccupancy4(),
        .io_perf_backendOverlapDispatchSrc(),
        .io_perf_backendOverlapSrcExec(),
        .io_perf_backendOverlapExecWrite(),
        .io_perf_branches(),
        .io_perf_branchesTaken(),
        .io_perf_flushes(),
        .io_perf_loads(),
        .io_perf_stores(),
        .io_perf_jumps(),
        .io_perf_csrOps(),
        .io_perf_mulDivOps(),
        .io_perf_trapCommits(),
        .coreArea_rvfiPlugin_io_rvfi_valid(),
        .coreArea_rvfiPlugin_io_rvfi_order(),
        .coreArea_rvfiPlugin_io_rvfi_insn(),
        .coreArea_rvfiPlugin_io_rvfi_trap(),
        .coreArea_rvfiPlugin_io_rvfi_halt(),
        .coreArea_rvfiPlugin_io_rvfi_intr(),
        .coreArea_rvfiPlugin_io_rvfi_mode(),
        .coreArea_rvfiPlugin_io_rvfi_ixl(),
        .coreArea_rvfiPlugin_io_rvfi_rs1_addr(),
        .coreArea_rvfiPlugin_io_rvfi_rs2_addr(),
        .coreArea_rvfiPlugin_io_rvfi_rs1_rdata(),
        .coreArea_rvfiPlugin_io_rvfi_rs2_rdata()
    );

`ifdef BORB_FORMAL_FAIRNESS
    reg [2:0] i_req_wait;
    reg [2:0] i_rsp_wait;
    reg [2:0] d_read_rsp_wait;
    reg [2:0] d_write_rsp_wait;

    always @(posedge clock) begin
        if (reset) begin
            i_req_wait <= 3'd0;
            i_rsp_wait <= 3'd0;
            d_read_rsp_wait <= 3'd0;
            d_write_rsp_wait <= 3'd0;
        end else begin
            i_req_wait <= i_axi_arw_valid ? (i_req_wait + 3'd1) : 3'd0;
            i_rsp_wait <= i_pending_valid ? (i_rsp_wait + 3'd1) : 3'd0;
            d_read_rsp_wait <= d_read_pending_valid ? (d_read_rsp_wait + 3'd1) : 3'd0;
            d_write_rsp_wait <= d_write_pending_valid ? (d_write_rsp_wait + 3'd1) : 3'd0;
        end

        restrict property(i_req_wait < 3'd4);
        restrict property(i_rsp_wait < 3'd4);
        restrict property(d_read_rsp_wait < 3'd4);
        restrict property(d_write_rsp_wait < 3'd4);
    end
`endif

    always @(*) begin
        assume(!i_axi_arw_write);
    end

endmodule
