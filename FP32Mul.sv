// Generator : SpinalHDL v1.12.3    git head : 591e64062329e5e2e2b81f4d52422948053edb97
// Component : FP32Mul
// Git hash  : f71ecfd8a04b521a80486258b4c2c43a645acfc9

`timescale 1ns/1ps

module FP32Mul (
  input  wire          io_input_valid,
  input  wire [22:0]   io_input_payload_a_mantissa,
  input  wire [7:0]    io_input_payload_a_exponent,
  input  wire          io_input_payload_a_sign,
  input  wire [22:0]   io_input_payload_b_mantissa,
  input  wire [7:0]    io_input_payload_b_exponent,
  input  wire          io_input_payload_b_sign,
  output wire          io_result_valid,
  input  wire          io_result_ready,
  output wire [22:0]   io_result_payload_mantissa,
  output wire [7:0]    io_result_payload_exponent,
  output wire          io_result_payload_sign,
  input  wire          clk,
  input  wire          reset
);

  wire       [23:0]   fixTo_1_dout;
  wire       [8:0]    _zz_n1_exp_mul;
  wire       [48:0]   _zz_n2_mant_mul_adj;
  wire       [8:0]    _zz_n2_exp_mul_adj;
  wire       [8:0]    _zz_n2_exp_mul_adj_1;
  wire       [1:0]    _zz_n2_exp_mul_adj_2;
  wire       [8:0]    _zz_n2_exp_mul_adj_3;
  wire       [0:0]    _zz_n2_exp_mul_adj_4;
  wire       [8:0]    _zz_io_result_payload_exponent;
  wire       [23:0]   _zz_io_result_payload_mantissa;
  reg        [23:0]   pipeline_ctrl_1_up_n0_mant_b;
  reg        [23:0]   pipeline_ctrl_1_up_n0_mant_a;
  reg        [22:0]   pipeline_ctrl_1_up_n0_b_mantissa;
  reg        [7:0]    pipeline_ctrl_1_up_n0_b_exponent;
  reg                 pipeline_ctrl_1_up_n0_b_sign;
  reg        [22:0]   pipeline_ctrl_1_up_n0_a_mantissa;
  reg        [7:0]    pipeline_ctrl_1_up_n0_a_exponent;
  reg                 pipeline_ctrl_1_up_n0_a_sign;
  wire                n2_ready;
  wire                n2_isValid;
  wire       [23:0]   pipeline_ctrl_1_down_n0_mant_b;
  wire       [23:0]   pipeline_ctrl_1_down_n0_mant_a;
  wire       [22:0]   pipeline_ctrl_1_down_n0_b_mantissa;
  wire       [7:0]    pipeline_ctrl_1_down_n0_b_exponent;
  wire                pipeline_ctrl_1_down_n0_b_sign;
  wire       [22:0]   pipeline_ctrl_1_down_n0_a_mantissa;
  wire       [7:0]    pipeline_ctrl_1_down_n0_a_exponent;
  wire                pipeline_ctrl_1_down_n0_a_sign;
  wire       [23:0]   pipeline_ctrl_0_down_n0_mant_b;
  wire       [23:0]   pipeline_ctrl_0_down_n0_mant_a;
  wire                pipeline_ctrl_0_down_n0_is_inf;
  wire                pipeline_ctrl_0_down_n0_is_nan;
  wire       [22:0]   pipeline_ctrl_0_down_n0_b_mantissa;
  wire       [7:0]    pipeline_ctrl_0_down_n0_b_exponent;
  wire                pipeline_ctrl_0_down_n0_b_sign;
  wire       [22:0]   pipeline_ctrl_0_down_n0_a_mantissa;
  wire       [7:0]    pipeline_ctrl_0_down_n0_a_exponent;
  wire                pipeline_ctrl_0_down_n0_a_sign;
  wire                _zz_pipeline_ctrl_0_down_n0_is_nan;
  wire                _zz_pipeline_ctrl_0_down_n0_is_inf;
  wire                n0_sign_mul;
  wire       [8:0]    n1_exp_mul;
  wire       [47:0]   n1_mant_mul;
  wire       [46:0]   n2_mant_mul_adj;
  wire       [8:0]    n2_exp_mul_adj;

  assign _zz_n1_exp_mul = ({1'b0,pipeline_ctrl_1_down_n0_a_exponent} + {1'b0,pipeline_ctrl_1_down_n0_b_exponent});
  assign _zz_n2_mant_mul_adj = ({n1_mant_mul,1'b0} >>> n1_mant_mul[47]);
  assign _zz_n2_exp_mul_adj = ($signed(n1_exp_mul) + $signed(_zz_n2_exp_mul_adj_1));
  assign _zz_n2_exp_mul_adj_2 = {1'b0,n1_mant_mul[47]};
  assign _zz_n2_exp_mul_adj_1 = {{7{_zz_n2_exp_mul_adj_2[1]}}, _zz_n2_exp_mul_adj_2};
  assign _zz_n2_exp_mul_adj_4 = fixTo_1_dout[23];
  assign _zz_n2_exp_mul_adj_3 = {{8{_zz_n2_exp_mul_adj_4[0]}}, _zz_n2_exp_mul_adj_4};
  assign _zz_io_result_payload_exponent = n2_exp_mul_adj;
  assign _zz_io_result_payload_mantissa = fixTo_1_dout;
  fixTo fixTo_1 (
    .din  (n2_mant_mul_adj[46:0]), //i
    .dout (fixTo_1_dout[23:0]   )  //o
  );
  assign pipeline_ctrl_0_down_n0_a_mantissa = io_input_payload_a_mantissa;
  assign pipeline_ctrl_0_down_n0_a_exponent = io_input_payload_a_exponent;
  assign pipeline_ctrl_0_down_n0_a_sign = io_input_payload_a_sign;
  assign pipeline_ctrl_0_down_n0_b_mantissa = io_input_payload_b_mantissa;
  assign pipeline_ctrl_0_down_n0_b_exponent = io_input_payload_b_exponent;
  assign pipeline_ctrl_0_down_n0_b_sign = io_input_payload_b_sign;
  assign pipeline_ctrl_0_down_n0_is_nan = _zz_pipeline_ctrl_0_down_n0_is_nan;
  assign pipeline_ctrl_0_down_n0_is_inf = _zz_pipeline_ctrl_0_down_n0_is_inf;
  assign pipeline_ctrl_0_down_n0_mant_a = {1'b1,pipeline_ctrl_0_down_n0_a_mantissa};
  assign pipeline_ctrl_0_down_n0_mant_b = {1'b1,pipeline_ctrl_0_down_n0_b_mantissa};
  assign n0_sign_mul = (pipeline_ctrl_0_down_n0_a_sign ^ pipeline_ctrl_0_down_n0_b_sign);
  assign n1_exp_mul = _zz_n1_exp_mul;
  assign n1_mant_mul = (pipeline_ctrl_1_down_n0_mant_a * pipeline_ctrl_1_down_n0_mant_b);
  assign io_result_valid = n2_isValid;
  assign n2_ready = io_result_ready;
  assign n2_mant_mul_adj = _zz_n2_mant_mul_adj[46 : 0];
  assign n2_exp_mul_adj = ($signed(_zz_n2_exp_mul_adj) + $signed(_zz_n2_exp_mul_adj_3));
  assign io_result_payload_sign = n0_sign_mul;
  assign io_result_payload_exponent = _zz_io_result_payload_exponent[7:0];
  assign io_result_payload_mantissa = _zz_io_result_payload_mantissa[22:0];
  assign n2_isValid = 1'b1;
  assign pipeline_ctrl_1_down_n0_a_mantissa = pipeline_ctrl_1_up_n0_a_mantissa;
  assign pipeline_ctrl_1_down_n0_a_exponent = pipeline_ctrl_1_up_n0_a_exponent;
  assign pipeline_ctrl_1_down_n0_a_sign = pipeline_ctrl_1_up_n0_a_sign;
  assign pipeline_ctrl_1_down_n0_b_mantissa = pipeline_ctrl_1_up_n0_b_mantissa;
  assign pipeline_ctrl_1_down_n0_b_exponent = pipeline_ctrl_1_up_n0_b_exponent;
  assign pipeline_ctrl_1_down_n0_b_sign = pipeline_ctrl_1_up_n0_b_sign;
  assign pipeline_ctrl_1_down_n0_mant_a = pipeline_ctrl_1_up_n0_mant_a;
  assign pipeline_ctrl_1_down_n0_mant_b = pipeline_ctrl_1_up_n0_mant_b;
  always @(posedge clk) begin
    pipeline_ctrl_1_up_n0_a_mantissa <= pipeline_ctrl_0_down_n0_a_mantissa;
    pipeline_ctrl_1_up_n0_a_exponent <= pipeline_ctrl_0_down_n0_a_exponent;
    pipeline_ctrl_1_up_n0_a_sign <= pipeline_ctrl_0_down_n0_a_sign;
    pipeline_ctrl_1_up_n0_b_mantissa <= pipeline_ctrl_0_down_n0_b_mantissa;
    pipeline_ctrl_1_up_n0_b_exponent <= pipeline_ctrl_0_down_n0_b_exponent;
    pipeline_ctrl_1_up_n0_b_sign <= pipeline_ctrl_0_down_n0_b_sign;
    pipeline_ctrl_1_up_n0_mant_a <= pipeline_ctrl_0_down_n0_mant_a;
    pipeline_ctrl_1_up_n0_mant_b <= pipeline_ctrl_0_down_n0_mant_b;
  end


endmodule

module fixTo (
  input  wire [46:0]   din,
  output wire [23:0]   dout
);

  wire       [24:0]   _zz_dout;

  assign _zz_dout = ({1'b0,din[46 : 23]} + {1'b0,{23'h0,1'b1}});
  assign dout = (_zz_dout >>> 1'd1);

endmodule
