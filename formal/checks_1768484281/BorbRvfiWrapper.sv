module BorbRvfiWrapper (
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
    // riscv-formal macros might produce vectors if nret > 1.
    // For nret=1, they are usually scalar (or [0:0] depending on version).
    // The safest way is to assign them explicitly.
    // Checks standard macro usage: rvfi_valid, rvfi_order, etc.
    
    // We assume the macros define output ports like:
    // output [NRET-1:0] rvfi_valid;
    // output [NRET*64-1:0] rvfi_order;
    // ...
    // Since NRET=1, these are just scalars or single-element vectors.
    // We connect them directly to the scalar outputs of our CPU.
    
    CPU cpu (
        .io_clk       (clock),
        .io_reset     (reset),
        .io_clkEnable (1'b1),
        
        // Connect RVFI signals
        // Note: bit slicing [0] is used in case macro declares vector [0:0]
        // If macro declares scalar, [0] is invalid. 
        // riscv-formal `rvfi_macros.vh` usually declares `output [NRET-1:0] rvfi_valid`.
        // If NRET=1, it is `output [0:0] rvfi_valid`.
        // So we MUST use [0] index or assign to the wire.
        
        // Let's rely on Verilog's auto-width matching or explicit assignment if implicitly declared.
        // Actually, better to use `RVFI_CONN` if we had an internal interface, but we are connecting ports.
        // We will assign to the macro-generated ports.
        
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
        .io_iBus_cmd_ready  (1'b1),
        .io_iBus_cmd_payload_address (),
        .io_iBus_cmd_payload_id      (),
        .io_iBus_rsp_valid  (1'b0),
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
