`include "rvfi_macros.vh"

module rvfi_wrapper (
    input clock,
    input reset,
    `RVFI_OUTPUTS
);

    // Instantiate the SpinalHDL generated CPU
    // The CPU module exposes:
    // io_clk, io_reset
    // io_iBus_* (ignored for now as we focus on RVFI)
    // io_rvfi_* (signals to map to RVFI_OUTPUTS)
    
    // Wire 1:1 mapping from CPU to RVFI Macros

    (* keep *) `rvformal_rand_reg        ibus_resp_valid;
    (* keep *) `rvformal_rand_reg [63:0] ibus_resp_data;
    (* keep *) `rvformal_rand_reg [63:0] ibus_resp_addr;
    (* keep *) `rvformal_rand_reg [15:0] ibus_resp_id;

    (* keep *) `rvformal_rand_reg        ibus_cmd_ready;
    (* keep *) `rvformal_rand_reg        dbus_cmd_ready;
    (* keep *) `rvformal_rand_reg        dbus_rsp_valid;
    (* keep *) `rvformal_rand_reg [63:0] dbus_rsp_data;
    
    // Capture the command ID when a load fires, so response ID always matches
    // This removes ID mismatch complexity from formal verification
    wire        dbus_cmd_valid_internal;
    wire [15:0] dbus_cmd_id_internal;
    wire        dbus_cmd_write_internal;
    reg  [15:0] dbus_pending_id;
    
    always @(posedge clock) begin
        if (reset) begin
            dbus_pending_id <= 16'd0;
        end else if (dbus_cmd_valid_internal && dbus_cmd_ready && !dbus_cmd_write_internal) begin
            // Capture ID when a load command is accepted
            dbus_pending_id <= dbus_cmd_id_internal;
        end
    end
    
    // Response ID is always the pending ID (no random mismatch)
    wire [15:0] dbus_rsp_id = dbus_pending_id;

    
    CPU cpu (
        .io_clk       (clock),
        .io_reset     (reset),
        .io_clkEnable (1'b1),
        
        .io_rvfi_valid      (rvfi_valid),
        .io_rvfi_order      (rvfi_order),
        .io_rvfi_insn       (rvfi_insn),
        .io_rvfi_trap       (rvfi_trap),
        .io_rvfi_halt       (rvfi_halt),
        .io_rvfi_intr       (rvfi_intr),
        .io_rvfi_mode       (rvfi_mode),
        .io_rvfi_ixl        (rvfi_ixl),
        
        .io_rvfi_rs1_addr   (rvfi_rs1_addr),
        .io_rvfi_rs2_addr   (rvfi_rs2_addr),
        .io_rvfi_rs1_rdata  (rvfi_rs1_rdata),
        .io_rvfi_rs2_rdata  (rvfi_rs2_rdata),
        
        .io_rvfi_rd_addr    (rvfi_rd_addr),
        .io_rvfi_rd_wdata   (rvfi_rd_wdata),
        
        .io_rvfi_pc_rdata   (rvfi_pc_rdata),
        .io_rvfi_pc_wdata   (rvfi_pc_wdata),
        
        .io_rvfi_mem_addr   (rvfi_mem_addr),
        .io_rvfi_mem_rmask  (rvfi_mem_rmask),
        .io_rvfi_mem_wmask  (rvfi_mem_wmask),
        .io_rvfi_mem_rdata  (rvfi_mem_rdata),
        .io_rvfi_mem_wdata  (rvfi_mem_wdata),
        
        // Ignored IOs
        .io_iBus_cmd_valid  (),
        .io_iBus_cmd_ready  (ibus_cmd_ready),
        .io_iBus_cmd_payload_address (),
        .io_iBus_cmd_payload_id      (),
        .io_iBus_rsp_valid  (ibus_resp_valid),
        .io_iBus_rsp_payload_data   (ibus_resp_data),
        .io_iBus_rsp_payload_address (ibus_resp_addr),
        .io_iBus_rsp_payload_id     (ibus_resp_id),

        // Data Bus (dBus) - ID tracking for guaranteed response matching
        .io_dBus_cmd_valid          (dbus_cmd_valid_internal),
        .io_dBus_cmd_ready          (dbus_cmd_ready),
        .io_dBus_cmd_payload_address(),
        .io_dBus_cmd_payload_data   (),
        .io_dBus_cmd_payload_mask   (),
        .io_dBus_cmd_payload_write  (dbus_cmd_write_internal),
        .io_dBus_cmd_payload_id     (dbus_cmd_id_internal),
        .io_dBus_rsp_valid          (dbus_rsp_valid),
        .io_dBus_rsp_payload_data   (dbus_rsp_data),
        .io_dBus_rsp_payload_id     (dbus_rsp_id),
        
        .io_dbg_commitValid (),
        .io_dbg_commitPc    (),
        .io_dbg_commitInsn  (),
        .io_dbg_commitRd    (),
        .io_dbg_commitWe    (),
        .io_dbg_commitWdata (),
        .io_dbg_squashed    (),
        .io_dbg_f_pc        (),
        .io_dbg_d_pc        (),
        .io_dbg_x_pc        (),
        .io_dbg_wb_pc       ()
    );

endmodule
