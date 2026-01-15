module BorbRvfiWrapper (
    input clock,
    input reset,
    `RVFI_OUTPUTS
);

    // Instantiate the SpinalHDL generated CPU
    // We assume the CPU module is named 'CPU' and has standard io_* ports
    
    CPU cpu (
        .io_clk       (clock),
        .io_reset     (reset),
        .io_clkEnable (1'b1),
        
        // Connect RVFI signals directly
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
        
        // Terminate other IOs
        .io_iBus_cmd_valid  (),
        .io_iBus_cmd_ready  (1'b1), 
        .io_iBus_cmd_payload_address (),
        .io_iBus_cmd_payload_id      (),
        .io_iBus_rsp_valid  (1'b0), // No memory connected
        .io_iBus_rsp_data   (64'b0),
        .io_iBus_rsp_id     (16'b0),
        .io_iBus_rsp_error  (1'b0),
        
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
