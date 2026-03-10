#include <verilated.h>
#if VM_TRACE_FST
#include <verilated_fst_c.h>
#endif
#include "VSoC.h"
#include "VSoC___024root.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <string>
#include <vector>

// Minimal ELF definitions (portable on macOS where <elf.h> is unavailable).
static constexpr uint8_t EI_NIDENT = 16;
static constexpr uint8_t EI_MAG0 = 0;
static constexpr uint8_t EI_MAG1 = 1;
static constexpr uint8_t EI_MAG2 = 2;
static constexpr uint8_t EI_MAG3 = 3;
static constexpr uint8_t EI_CLASS = 4;
static constexpr uint8_t EI_DATA = 5;
static constexpr uint8_t ELFMAG0 = 0x7f;
static constexpr uint8_t ELFMAG1 = 'E';
static constexpr uint8_t ELFMAG2 = 'L';
static constexpr uint8_t ELFMAG3 = 'F';
static constexpr uint8_t ELFCLASS32 = 1;
static constexpr uint8_t ELFCLASS64 = 2;
static constexpr uint8_t ELFDATA2LSB = 1;
static constexpr uint32_t PT_LOAD = 1;

struct Elf64_Ehdr {
  unsigned char e_ident[EI_NIDENT];
  uint16_t e_type;
  uint16_t e_machine;
  uint32_t e_version;
  uint64_t e_entry;
  uint64_t e_phoff;
  uint64_t e_shoff;
  uint32_t e_flags;
  uint16_t e_ehsize;
  uint16_t e_phentsize;
  uint16_t e_phnum;
  uint16_t e_shentsize;
  uint16_t e_shnum;
  uint16_t e_shstrndx;
};

struct Elf64_Phdr {
  uint32_t p_type;
  uint32_t p_flags;
  uint64_t p_offset;
  uint64_t p_vaddr;
  uint64_t p_paddr;
  uint64_t p_filesz;
  uint64_t p_memsz;
  uint64_t p_align;
};

struct Elf32_Ehdr {
  unsigned char e_ident[EI_NIDENT];
  uint16_t e_type;
  uint16_t e_machine;
  uint32_t e_version;
  uint32_t e_entry;
  uint32_t e_phoff;
  uint32_t e_shoff;
  uint32_t e_flags;
  uint16_t e_ehsize;
  uint16_t e_phentsize;
  uint16_t e_phnum;
  uint16_t e_shentsize;
  uint16_t e_shnum;
  uint16_t e_shstrndx;
};

struct Elf32_Phdr {
  uint32_t p_type;
  uint32_t p_offset;
  uint32_t p_vaddr;
  uint32_t p_paddr;
  uint32_t p_filesz;
  uint32_t p_memsz;
  uint32_t p_flags;
  uint32_t p_align;
};

struct Options {
  std::string bin_path;
  std::string elf_path;
  std::string signature_path;
  uint64_t sig_begin = 0;
  uint64_t sig_end = 0;
  uint64_t tohost = 0;
  uint64_t max_cycles = 200000;
  std::string fst_path;
  bool trace = false;
  std::string commit_trace_path;
  bool commit_trace = false;
  std::string tohost_report_path;
  bool report_tohost = false;
  std::string perf_report_path;
  bool report_perf = false;
};

static void usage(const char* prog) {
  std::cerr << "Usage: " << prog << " (--bin <path> | --elf <path>) --sig-begin <hex> --sig-end <hex> --tohost <hex> --signature <path> [--max-cycles <n>] [--fst <path>] [--trace-commit <path>] [--report-tohost <path>] [--report-perf <path>]" << std::endl;
}

static bool parse_args(int argc, char** argv, Options& opt) {
  for (int i = 1; i < argc; i++) {
    std::string a = argv[i];
    auto need = [&](const char* flag) -> const char* {
      if (i + 1 >= argc) {
        std::cerr << "Missing value for " << flag << std::endl;
        return nullptr;
      }
      return argv[++i];
    };

    if (a == "--bin") {
      const char* v = need("--bin");
      if (!v) return false;
      opt.bin_path = v;
    } else if (a == "--elf") {
      const char* v = need("--elf");
      if (!v) return false;
      opt.elf_path = v;
    } else if (a == "--sig-begin") {
      const char* v = need("--sig-begin");
      if (!v) return false;
      opt.sig_begin = std::stoull(v, nullptr, 0);
    } else if (a == "--sig-end") {
      const char* v = need("--sig-end");
      if (!v) return false;
      opt.sig_end = std::stoull(v, nullptr, 0);
    } else if (a == "--tohost") {
      const char* v = need("--tohost");
      if (!v) return false;
      opt.tohost = std::stoull(v, nullptr, 0);
    } else if (a == "--signature") {
      const char* v = need("--signature");
      if (!v) return false;
      opt.signature_path = v;
    } else if (a == "--max-cycles") {
      const char* v = need("--max-cycles");
      if (!v) return false;
      opt.max_cycles = std::stoull(v, nullptr, 0);
    } else if (a == "--fst") {
      const char* v = need("--fst");
      if (!v) return false;
      opt.fst_path = v;
      opt.trace = true;
    } else if (a == "--vcd") {
      // Backward-compatible alias. Output format is still FST.
      const char* v = need("--vcd");
      if (!v) return false;
      opt.fst_path = v;
      opt.trace = true;
    } else if (a == "--trace-commit") {
      const char* v = need("--trace-commit");
      if (!v) return false;
      opt.commit_trace_path = v;
      opt.commit_trace = true;
    } else if (a == "--report-tohost") {
      const char* v = need("--report-tohost");
      if (!v) return false;
      opt.tohost_report_path = v;
      opt.report_tohost = true;
    } else if (a == "--report-perf") {
      const char* v = need("--report-perf");
      if (!v) return false;
      opt.perf_report_path = v;
      opt.report_perf = true;
    } else {
      std::cerr << "Unknown arg: " << a << std::endl;
      return false;
    }
  }

  const bool has_bin = !opt.bin_path.empty();
  const bool has_elf = !opt.elf_path.empty();
  if ((has_bin == has_elf) || opt.signature_path.empty() || opt.sig_begin == 0 || opt.sig_end == 0) {
    return false;
  }
  return true;
}

static std::vector<uint8_t> read_file(const std::string& path) {
  std::ifstream ifs(path, std::ios::binary);
  if (!ifs) {
    throw std::runtime_error("Failed to open file: " + path);
  }
  return std::vector<uint8_t>(std::istreambuf_iterator<char>(ifs), std::istreambuf_iterator<char>());
}

static int popcount8(uint8_t v) {
  int c = 0;
  for (int i = 0; i < 8; i++) {
    c += (v >> i) & 1;
  }
  return c;
}

template <typename WriteByteFn>
static bool load_elf_image(const std::vector<uint8_t>& img, WriteByteFn write_byte) {
  if (img.size() < EI_NIDENT) return false;
  const unsigned char* ident = reinterpret_cast<const unsigned char*>(img.data());
  if (ident[EI_MAG0] != ELFMAG0 || ident[EI_MAG1] != ELFMAG1 ||
      ident[EI_MAG2] != ELFMAG2 || ident[EI_MAG3] != ELFMAG3) {
    return false;
  }
  if (ident[EI_DATA] != ELFDATA2LSB) return false;

  if (ident[EI_CLASS] == ELFCLASS64) {
    if (img.size() < sizeof(Elf64_Ehdr)) return false;
    const auto* eh = reinterpret_cast<const Elf64_Ehdr*>(img.data());
    if (eh->e_phoff + (uint64_t)eh->e_phnum * eh->e_phentsize > img.size()) return false;

    for (uint16_t i = 0; i < eh->e_phnum; i++) {
      const uint64_t off = eh->e_phoff + (uint64_t)i * eh->e_phentsize;
      const auto* ph = reinterpret_cast<const Elf64_Phdr*>(img.data() + off);
      if (ph->p_type != PT_LOAD || ph->p_filesz == 0) continue;
      if (ph->p_offset + ph->p_filesz > img.size()) return false;
      const uint64_t base = (ph->p_paddr != 0) ? ph->p_paddr : ph->p_vaddr;
      for (uint64_t j = 0; j < ph->p_filesz; j++) {
        write_byte(base + j, img[ph->p_offset + j]);
      }
    }
    return true;
  }

  if (ident[EI_CLASS] == ELFCLASS32) {
    if (img.size() < sizeof(Elf32_Ehdr)) return false;
    const auto* eh = reinterpret_cast<const Elf32_Ehdr*>(img.data());
    if (eh->e_phoff + (uint64_t)eh->e_phnum * eh->e_phentsize > img.size()) return false;

    for (uint16_t i = 0; i < eh->e_phnum; i++) {
      const uint64_t off = eh->e_phoff + (uint64_t)i * eh->e_phentsize;
      const auto* ph = reinterpret_cast<const Elf32_Phdr*>(img.data() + off);
      if (ph->p_type != PT_LOAD || ph->p_filesz == 0) continue;
      if ((uint64_t)ph->p_offset + (uint64_t)ph->p_filesz > img.size()) return false;
      const uint64_t base = (ph->p_paddr != 0) ? (uint64_t)ph->p_paddr : (uint64_t)ph->p_vaddr;
      for (uint64_t j = 0; j < ph->p_filesz; j++) {
        write_byte(base + j, img[(uint64_t)ph->p_offset + j]);
      }
    }
    return true;
  }

  return false;
}

int main(int argc, char** argv) {
  Options opt;
  if (!parse_args(argc, argv, opt)) {
    usage(argv[0]);
    return 1;
  }

  Verilated::commandArgs(argc, argv);
  if (opt.trace) {
#if VM_TRACE_FST
    Verilated::traceEverOn(true);
#else
    std::cerr << "--fst/--vcd requested, but simulator was built without FST support."
              << " Rebuild without FAST=1 or with TRACE=1." << std::endl;
    return 2;
#endif
  }

  const uint64_t mem_bytes = 8ULL * 1024ULL * 1024ULL;
  const uint64_t word_count = mem_bytes / 8;

  VSoC* top = new VSoC();
#if VM_TRACE_FST
  VerilatedFstC* fst = nullptr;
#endif
  std::ofstream commit_trace;
  if (opt.trace) {
#if VM_TRACE_FST
    fst = new VerilatedFstC();
    top->trace(fst, 99);
    fst->open(opt.fst_path.c_str());
#endif
  }
  if (opt.commit_trace) {
    commit_trace.open(opt.commit_trace_path);
    if (!commit_trace) {
      std::cerr << "Failed to open commit trace file: " << opt.commit_trace_path << std::endl;
      return 1;
    }
  }

  auto tick = [&](uint64_t time) {
    top->io_clk = 0;
    top->eval();
#if VM_TRACE_FST
    if (fst) fst->dump(time);
#endif
    top->io_clk = 1;
    top->eval();
#if VM_TRACE_FST
    if (fst) fst->dump(time + 1);
#endif
  };

  top->io_clkEnable = 1;
  top->io_reset = 1;
  for (int i = 0; i < 10; i++) tick(i * 2);
  top->io_reset = 0;

  auto* rootp = top->rootp;

  auto write_byte = [&](uint64_t addr, uint8_t val) {
    uint64_t phys = addr & (mem_bytes - 1);
    uint64_t word_index = (phys >> 3) & (word_count - 1);
    uint64_t lane = phys & 0x7;
    switch (lane) {
      case 0: rootp->SoC__DOT__area_ram__DOT__ram_symbol0[word_index] = val; break;
      case 1: rootp->SoC__DOT__area_ram__DOT__ram_symbol1[word_index] = val; break;
      case 2: rootp->SoC__DOT__area_ram__DOT__ram_symbol2[word_index] = val; break;
      case 3: rootp->SoC__DOT__area_ram__DOT__ram_symbol3[word_index] = val; break;
      case 4: rootp->SoC__DOT__area_ram__DOT__ram_symbol4[word_index] = val; break;
      case 5: rootp->SoC__DOT__area_ram__DOT__ram_symbol5[word_index] = val; break;
      case 6: rootp->SoC__DOT__area_ram__DOT__ram_symbol6[word_index] = val; break;
      case 7: rootp->SoC__DOT__area_ram__DOT__ram_symbol7[word_index] = val; break;
    }
  };

  auto read_byte = [&](uint64_t addr) -> uint8_t {
    uint64_t phys = addr & (mem_bytes - 1);
    uint64_t word_index = (phys >> 3) & (word_count - 1);
    uint64_t lane = phys & 0x7;
    switch (lane) {
      case 0: return rootp->SoC__DOT__area_ram__DOT__ram_symbol0[word_index];
      case 1: return rootp->SoC__DOT__area_ram__DOT__ram_symbol1[word_index];
      case 2: return rootp->SoC__DOT__area_ram__DOT__ram_symbol2[word_index];
      case 3: return rootp->SoC__DOT__area_ram__DOT__ram_symbol3[word_index];
      case 4: return rootp->SoC__DOT__area_ram__DOT__ram_symbol4[word_index];
      case 5: return rootp->SoC__DOT__area_ram__DOT__ram_symbol5[word_index];
      case 6: return rootp->SoC__DOT__area_ram__DOT__ram_symbol6[word_index];
      case 7: return rootp->SoC__DOT__area_ram__DOT__ram_symbol7[word_index];
      default: return 0;
    }
  };

  if (!opt.bin_path.empty()) {
    // Legacy path: load flat binary at address 0.
    auto bin = read_file(opt.bin_path);
    for (uint64_t i = 0; i < bin.size(); i++) {
      write_byte(i, bin[i]);
    }
  } else {
    // Preferred path for sparse/high-address tests.
    auto elf = read_file(opt.elf_path);
    if (!load_elf_image(elf, write_byte)) {
      std::cerr << "Failed to load ELF image: " << opt.elf_path << std::endl;
      return 1;
    }
  }

  uint64_t cycles = 0;
  uint64_t stalled_cycles = 0;
  uint64_t max_stall_streak = 0;
  bool stall_reported = false;
  uint64_t fetch_window_logs = 0;
  bool done = false;
  bool timed_out = false;
  uint64_t commit_events = 0;
  uint64_t commit_traps = 0;
  uint64_t commit_mem_reads = 0;
  uint64_t commit_mem_writes = 0;
  while (!done && cycles < opt.max_cycles) {
    tick(20 + cycles * 2);

    const bool enable_pc_exception_debug = false;
    if (enable_pc_exception_debug && top->io_dbg_redirectPcExceptionValid) {
      std::cerr
          << "PCEXC cyc=" << cycles
          << " target=0x" << std::hex << (uint64_t)top->io_dbg_redirectPcExceptionTarget
          << " cause=0x" << (uint64_t)top->io_dbg_liveTrapCause
          << " tval=0x" << (uint64_t)top->io_dbg_liveTrapTval
          << " x_pc=0x" << (uint64_t)top->io_dbg_x_pc
          << " wb_pc=0x" << (uint64_t)top->io_dbg_wb_pc
          << " x_rs1_addr=" << std::dec << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Decoder_RS1_ADDR
          << " x_rs2_addr=" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Decoder_RS2_ADDR
          << " x_send_agu=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOAGU
          << " x_rs1=0x" << std::hex << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1
          << " x_rs2=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2
          << " x_imm=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_SrcPlugin_IMMED
          << " order=" << std::dec << top->io_dbg_commitOrder
          << std::endl;
    }

    if (top->io_dbg_commitValid) {
      commit_events++;
      if (top->io_dbg_commitTrap) {
        commit_traps++;
      }
      if (top->io_dbg_memRmask) {
        commit_mem_reads++;
      }
      if (top->io_dbg_memWmask) {
        commit_mem_writes++;
      }
      stalled_cycles = 0;
      stall_reported = false;
    } else {
      stalled_cycles++;
      if (stalled_cycles > max_stall_streak) {
        max_stall_streak = stalled_cycles;
      }
    }

    if (!stall_reported && stalled_cycles > 50000) {
      std::cerr
          << "STALL: no commit for " << stalled_cycles << " cycles"
          << " order=" << top->io_dbg_commitOrder
          << " pc=0x" << std::hex << (uint64_t)top->io_dbg_commitPc << std::dec
          << " insn=0x" << std::hex << (uint32_t)top->io_dbg_commitInsn << std::dec
          << " trap=" << (int)top->io_dbg_commitTrap
          << " redir=" << (int)top->io_dbg_redirectAny
          << " redir_branch=" << (int)top->io_dbg_redirectBranch
          << " redir_trap=" << (int)top->io_dbg_redirectTrap
          << " redir_mret=" << (int)top->io_dbg_redirectMret
          << " redir_exec_epoch=" << (int)top->io_dbg_redirectExecEpochMatches
          << " pc_jump_valid=" << (int)top->io_dbg_redirectPcJumpValid
          << " pc_jump_target=0x" << std::hex << (uint64_t)top->io_dbg_redirectPcJumpTarget << std::dec
          << " pc_exc_valid=" << (int)top->io_dbg_redirectPcExceptionValid
          << " pc_exc_target=0x" << std::hex << (uint64_t)top->io_dbg_redirectPcExceptionTarget << std::dec
          << " live_trap_cause=0x" << std::hex << (uint64_t)top->io_dbg_liveTrapCause << std::dec
          << " live_trap_tval=0x" << std::hex << (uint64_t)top->io_dbg_liveTrapTval << std::dec
          << " rd=" << (int)top->io_dbg_commitRd
          << " we=" << (int)top->io_dbg_commitWe
          << " f_pc=0x" << std::hex << (uint64_t)top->io_dbg_f_pc
          << " d_pc=0x" << (uint64_t)top->io_dbg_d_pc
          << " x_pc=0x" << (uint64_t)top->io_dbg_x_pc
          << " wb_pc=0x" << (uint64_t)top->io_dbg_wb_pc
          << " commit_pulse=" << (int)top->io_dbg_commitPulse
          << " dup_retire=" << (int)top->io_dbg_duplicateRetire
          << " s4_seq=" << (uint32_t)top->io_dbg_s4_seq
          << " s4_fire=" << (int)top->io_dbg_s4_fire
          << " s5_seq=" << (uint32_t)top->io_dbg_s5_seq
          << " s5_fire=" << (int)top->io_dbg_s5_fire
          << " s6_seq=" << (uint32_t)top->io_dbg_s6_seq
          << " s6_fire=" << (int)top->io_dbg_s6_fire
          << " s7_seq=" << (uint32_t)top->io_dbg_s7_seq
          << " s7_fire=" << (int)top->io_dbg_s7_fire
          << " s1_pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_1_up_PC_PC
          << " s3_pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_up_PC_PC
          << " s2_pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_up_PC_PC
          << " s2_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_up_valid
          << " s2_ready=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_down_isReady
          << " s3_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_up_valid
          << " s3_ready=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_isReady
          << " s3_dec_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_Decoder_VALID
          << " s3_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_Decoder_DECODED_INSTRUCTION
          << " x_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Decoder_VALID
          << " x_lane=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Common_LANE_SEL
          << " x_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Decoder_DECODED_INSTRUCTION
          << " wb_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_7_up_Decoder_VALID
          << " wb_lane=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_7_up_Common_LANE_SEL
          << " wb_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_7_up_Decoder_DECODED_INSTRUCTION
          << " fetch_qhead=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_queueHead
          << " fetch_qcount=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_queueCount
          << " fetch_pending=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_pendingReqValid
          << " fetch_pending_addr=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_pendingReq_baseAddr
          << " fetch_stream_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_streamNextValid
          << " fetch_stream_addr=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_streamNextAddr
          << " fetch_pkt_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_packetValid
          << " fetch_cnext=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_compressedNextReqValid
          << " fetch_cnext_addr=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_compressedNextReqAddr
          << " beat0_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_beats_0_valid
          << " beat0_addr=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_beats_0_beatAddr
          << " beat0_data=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_beats_0_data
          << " beat1_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_beats_1_valid
          << " beat1_addr=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_beats_1_beatAddr
          << " beat1_data=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_beats_1_data
          << std::endl;
      stall_reported = true;
    }

    const bool enable_fetch_window_debug = false;
    const uint64_t fetch_rsp_pc = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_1_up_PC_PC;
    const bool fetch_hold = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_packetValid != 0;
    const uint64_t fetch_hold_pc = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_up_PC_PC;
    const bool in_fetch_window =
        ((fetch_rsp_pc >= 0x1f0ULL && fetch_rsp_pc <= 0x208ULL) ||
         (fetch_hold && fetch_hold_pc >= 0x1f0ULL && fetch_hold_pc <= 0x208ULL));
    if (enable_fetch_window_debug && in_fetch_window && fetch_window_logs < 240) {
      std::cerr
          << "FETCHDBG cyc=" << cycles
          << " order=" << top->io_dbg_commitOrder
          << " rspPc=0x" << std::hex << fetch_rsp_pc
          << " hold=" << std::dec << (int)fetch_hold
          << " holdPc=0x" << std::hex << fetch_hold_pc
          << " pcSeqValid=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pc_sequentialValid
          << " pktValid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_packetValid
          << " pktPop=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_packetPop
          << " pktSeq=" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_packet_seq
          << " pktInsn=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_packet_insn
          << " first16=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_cmdArea_first16
          << " curData=0x" << std::hex << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_cmdArea_curData
          << " s2pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_up_PC_PC
          << " s2v=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_up_valid
          << " s3v=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_up_valid
          << " s3insn=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_Decoder_DECODED_INSTRUCTION
          << " s4pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_4_up_PC_PC
          << " s4v=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_4_up_valid
          << " s4f=" << (int)top->io_dbg_s4_fire
          << " s4seq=" << (uint32_t)top->io_dbg_s4_seq
          << " s4insn=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_4_up_Decoder_DECODED_INSTRUCTION
          << " s5pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_5_up_PC_PC
          << " s5v=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_5_up_valid
          << " s5f=" << (int)top->io_dbg_s5_fire
          << " s5seq=" << (uint32_t)top->io_dbg_s5_seq
          << " s5lane=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_5_up_Common_LANE_SEL
          << " s5insn=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_5_up_Decoder_DECODED_INSTRUCTION
          << " s6pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_PC_PC
          << " s6v=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_valid
          << " s6f=" << (int)top->io_dbg_s6_fire
          << " s6seq=" << (uint32_t)top->io_dbg_s6_seq
          << " s6lane=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Common_LANE_SEL
          << " s6insn=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Decoder_DECODED_INSTRUCTION
          << " s6rs1a=" << std::dec << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Decoder_RS1_ADDR
          << " s6rs2a=" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Decoder_RS2_ADDR
          << " s6agu=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_Dispatch_SENDTOAGU
          << " s6rs1=0x" << std::hex << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_SrcPlugin_RS1
          << " s6rs2=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_SrcPlugin_RS2
          << " s6imm=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_6_up_SrcPlugin_IMMED
          << " s7pc=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_7_up_PC_PC
          << " s7v=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_7_up_valid
          << " s7f=" << (int)top->io_dbg_s7_fire
          << " s7seq=" << (uint32_t)top->io_dbg_s7_seq
          << " s7lane=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_7_up_Common_LANE_SEL
          << " commitPulse=" << (int)top->io_dbg_commitPulse
          << " dupRetire=" << (int)top->io_dbg_duplicateRetire
          << " s7insn=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_7_up_Decoder_DECODED_INSTRUCTION
          << std::endl;
      fetch_window_logs++;
    }

    if (opt.commit_trace && top->io_dbg_commitValid) {
      const uint64_t pc = top->io_dbg_commitPc;
      const uint32_t seq = top->io_dbg_commitSeq;
      const uint32_t insn = top->io_dbg_commitInsn;
      const uint64_t order = top->io_dbg_commitOrder;
      const uint64_t rd_val = top->io_dbg_commitWdata;
      const uint8_t rd = static_cast<uint8_t>(top->io_dbg_commitRd);
      const bool rd_we = top->io_dbg_commitWe;
      const bool trap = top->io_dbg_commitTrap;
      const uint64_t trap_cause = top->io_dbg_commitTrapCause;
      const uint64_t trap_tval = top->io_dbg_commitTrapTval;

      const uint64_t mem_addr = top->io_dbg_memAddr;
      const uint8_t mem_rmask = static_cast<uint8_t>(top->io_dbg_memRmask);
      const uint8_t mem_wmask = static_cast<uint8_t>(top->io_dbg_memWmask);
      const uint64_t mem_rdata = top->io_dbg_memRdata;
      const uint64_t mem_wdata = top->io_dbg_memWdata;
      const uint8_t rs1_addr = static_cast<uint8_t>(top->io_dbg_commitRs1);
      const uint8_t rs2_addr = static_cast<uint8_t>(top->io_dbg_commitRs2);
      const uint64_t rs1_rdata = top->io_dbg_commitRs1Data;
      const uint64_t rs2_rdata = top->io_dbg_commitRs2Data;

      commit_trace << "{\"i\":" << order
                   << ",\"seq\":" << seq
                   << ",\"pc\":\"0x" << std::hex << pc << std::dec << "\""
                   << ",\"insn\":\"0x" << std::hex << insn << std::dec << "\""
                   << ",\"commit_pulse\":" << (top->io_dbg_commitPulse ? "true" : "false")
                   << ",\"dup_retire\":" << (top->io_dbg_duplicateRetire ? "true" : "false")
                   << ",\"redirect\":{\"any\":" << (top->io_dbg_redirectAny ? "true" : "false")
                   << ",\"branch\":" << (top->io_dbg_redirectBranch ? "true" : "false")
                   << ",\"trap\":" << (top->io_dbg_redirectTrap ? "true" : "false")
                   << ",\"mret\":" << (top->io_dbg_redirectMret ? "true" : "false")
                   << ",\"exec_epoch\":" << (top->io_dbg_redirectExecEpochMatches ? "true" : "false")
                   << ",\"pc_jump_valid\":" << (top->io_dbg_redirectPcJumpValid ? "true" : "false")
                   << ",\"pc_jump_target\":\"0x" << std::hex << (uint64_t)top->io_dbg_redirectPcJumpTarget << std::dec << "\""
                   << ",\"pc_exception_valid\":" << (top->io_dbg_redirectPcExceptionValid ? "true" : "false")
                   << ",\"pc_exception_target\":\"0x" << std::hex << (uint64_t)top->io_dbg_redirectPcExceptionTarget << std::dec << "\""
                   << ",\"live_trap_cause\":\"0x" << std::hex << (uint64_t)top->io_dbg_liveTrapCause << std::dec << "\""
                   << ",\"live_trap_tval\":\"0x" << std::hex << (uint64_t)top->io_dbg_liveTrapTval << std::dec << "\"}"
                   << ",\"s4\":{\"valid\":" << (top->io_dbg_s4_valid ? "true" : "false")
                   << ",\"fire\":" << (top->io_dbg_s4_fire ? "true" : "false")
                   << ",\"seq\":" << top->io_dbg_s4_seq << "}"
                   << ",\"s5\":{\"valid\":" << (top->io_dbg_s5_valid ? "true" : "false")
                   << ",\"fire\":" << (top->io_dbg_s5_fire ? "true" : "false")
                   << ",\"lane\":" << (top->io_dbg_s5_lane ? "true" : "false")
                   << ",\"seq\":" << top->io_dbg_s5_seq << "}"
                   << ",\"s6\":{\"valid\":" << (top->io_dbg_s6_valid ? "true" : "false")
                   << ",\"fire\":" << (top->io_dbg_s6_fire ? "true" : "false")
                   << ",\"lane\":" << (top->io_dbg_s6_lane ? "true" : "false")
                   << ",\"seq\":" << top->io_dbg_s6_seq << "}"
                   << ",\"s7\":{\"valid\":" << (top->io_dbg_s7_valid ? "true" : "false")
                   << ",\"fire\":" << (top->io_dbg_s7_fire ? "true" : "false")
                   << ",\"lane\":" << (top->io_dbg_s7_lane ? "true" : "false")
                   << ",\"seq\":" << top->io_dbg_s7_seq << "}"
                   << ",\"rs1\":{\"addr\":" << static_cast<unsigned>(rs1_addr)
                   << ",\"data\":\"0x" << std::hex << rs1_rdata << std::dec << "\"}"
                   << ",\"rs2\":{\"addr\":" << static_cast<unsigned>(rs2_addr)
                   << ",\"data\":\"0x" << std::hex << rs2_rdata << std::dec << "\"}";

      if (rd_we) {
        commit_trace << ",\"rd\":" << static_cast<unsigned>(rd)
                     << ",\"rd_val\":\"0x" << std::hex << rd_val << std::dec << "\"";
      }
      if (trap) {
        commit_trace << ",\"trap\":true"
                     << ",\"trap_cause\":\"0x" << std::hex << trap_cause << std::dec << "\""
                     << ",\"trap_tval\":\"0x" << std::hex << trap_tval << std::dec << "\"";
      }

      if (mem_wmask || mem_rmask) {
        const bool we = mem_wmask != 0;
        const uint8_t mask = we ? mem_wmask : mem_rmask;
        const int size = popcount8(mask);
        const uint64_t data = we ? mem_wdata : mem_rdata;

        commit_trace << ",\"mem\":{\"we\":" << (we ? "true" : "false")
                     << ",\"addr\":\"0x" << std::hex << mem_addr << std::dec << "\""
                     << ",\"data\":\"0x" << std::hex << data << std::dec << "\""
                     << ",\"size\":" << size << "}";
      }

      commit_trace << "}\n";
    }

    if (opt.tohost != 0) {
      uint64_t t = 0;
      for (int i = 0; i < 8; i++) {
        t |= (uint64_t)read_byte(opt.tohost + i) << (8 * i);
      }
      if (t != 0) done = true;
    }

    cycles++;
  }
  if (!done && cycles >= opt.max_cycles) {
    timed_out = true;
    std::cerr << "TIMEOUT: exceeded max cycles (" << opt.max_cycles << ")"
              << " order=" << top->io_dbg_commitOrder
              << " pc=0x" << std::hex << (uint64_t)top->io_dbg_commitPc << std::dec
              << " insn=0x" << std::hex << (uint32_t)top->io_dbg_commitInsn << std::dec
              << std::endl;
  }

  std::ofstream sig(opt.signature_path);
  if (!sig) {
    std::cerr << "Failed to open signature file: " << opt.signature_path << std::endl;
    return 1;
  }

  for (uint64_t addr = opt.sig_begin; addr < opt.sig_end; addr += 4) {
    uint32_t val = 0;
    uint8_t b0 = read_byte(addr + 0);
    uint8_t b1 = read_byte(addr + 1);
    uint8_t b2 = read_byte(addr + 2);
    uint8_t b3 = read_byte(addr + 3);
    val = ((uint32_t)b3 << 24) | ((uint32_t)b2 << 16) | ((uint32_t)b1 << 8) | (uint32_t)b0;
    char buf[9];
    std::snprintf(buf, sizeof(buf), "%08x", val);
    sig << buf << "\n";
  }
  sig.close();

  #if VM_TRACE_FST
  if (fst) {
    fst->close();
    delete fst;
  }
  #endif
  if (opt.report_tohost && opt.tohost != 0) {
    uint64_t t = 0;
    for (int i = 0; i < 8; i++) {
      t |= (uint64_t)read_byte(opt.tohost + i) << (8 * i);
    }
    std::ofstream tf(opt.tohost_report_path);
    if (!tf) {
      std::cerr << "Failed to open tohost report file: " << opt.tohost_report_path << std::endl;
      return 1;
    }
    tf << "0x" << std::hex << t << "\n";
    tf.close();
  }
  if (opt.report_perf) {
    std::ofstream pf(opt.perf_report_path);
    if (!pf) {
      std::cerr << "Failed to open perf report file: " << opt.perf_report_path << std::endl;
      return 1;
    }

    const uint64_t perf_cycles = top->io_perf_cycles;
    const uint64_t perf_instret = top->io_perf_instret;
    const uint64_t perf_stalls_hazard = top->io_perf_stallsHazard;
    const uint64_t perf_stalls_fetch = top->io_perf_stallsFetch;
    const uint64_t perf_stalls_mem = top->io_perf_stallsMem;
    const uint64_t perf_stalls_backend = top->io_perf_stallsBackend;
    const uint64_t perf_stalls_writeback = top->io_perf_stallsWriteback;
    const uint64_t perf_stalls_commit = top->io_perf_stallsCommit;
    const uint64_t perf_stalls_muldiv_busy = top->io_perf_stallsMulDivBusy;
    const uint64_t perf_stalls_lsu_replay_or_wait = top->io_perf_stallsLsuReplayOrWait;
    const uint64_t perf_stalls_dispatch_to_src = top->io_perf_stallsDispatchToSrc;
    const uint64_t perf_stalls_src_to_exec = top->io_perf_stallsSrcToExec;
    const uint64_t perf_stalls_exec_to_write = top->io_perf_stallsExecToWrite;
    const uint64_t perf_cycles_dispatch_valid = top->io_perf_cyclesDispatchValid;
    const uint64_t perf_cycles_src_valid = top->io_perf_cyclesSrcValid;
    const uint64_t perf_cycles_exec_valid = top->io_perf_cyclesExecValid;
    const uint64_t perf_cycles_write_valid = top->io_perf_cyclesWriteValid;
    const uint64_t perf_cycles_dispatch_fire = top->io_perf_cyclesDispatchFire;
    const uint64_t perf_cycles_src_fire = top->io_perf_cyclesSrcFire;
    const uint64_t perf_cycles_exec_fire = top->io_perf_cyclesExecFire;
    const uint64_t perf_cycles_write_fire = top->io_perf_cyclesWriteFire;
    const uint64_t perf_frontend_pending_req_cycles = top->io_perf_frontendPendingReqCycles;
    const uint64_t perf_frontend_beat0_valid_cycles = top->io_perf_frontendBeat0ValidCycles;
    const uint64_t perf_frontend_beat1_valid_cycles = top->io_perf_frontendBeat1ValidCycles;
    const uint64_t perf_frontend_req_issued = top->io_perf_frontendReqIssued;
    const uint64_t perf_frontend_rsp_accepted = top->io_perf_frontendRspAccepted;
    const uint64_t perf_frontend_need_current_req = top->io_perf_frontendNeedCurrentReq;
    const uint64_t perf_frontend_need_next_req = top->io_perf_frontendNeedNextReq;
    const uint64_t perf_frontend_prefetch_req = top->io_perf_frontendPrefetchReq;
    const uint64_t perf_frontend_wait_cur_beat = top->io_perf_frontendWaitCurBeat;
    const uint64_t perf_frontend_wait_next_beat = top->io_perf_frontendWaitNextBeat;
    const uint64_t perf_frontend_take_insn = top->io_perf_frontendTakeInsn;
    const uint64_t perf_frontend_cur_beat_hit = top->io_perf_frontendCurBeatHit;
    const uint64_t perf_frontend_next_beat_hit = top->io_perf_frontendNextBeatHit;
    const uint64_t perf_frontend_cmd_valid_cycles = top->io_perf_frontendCmdValidCycles;
    const uint64_t perf_frontend_prefetch_window = top->io_perf_frontendPrefetchWindow;
    const uint64_t perf_frontend_prefetch_blocked_no_cmd = top->io_perf_frontendPrefetchBlockedNoCmd;
    const uint64_t perf_frontend_prefetch_blocked_pending = top->io_perf_frontendPrefetchBlockedPending;
    const uint64_t perf_frontend_prefetch_blocked_next_hit = top->io_perf_frontendPrefetchBlockedNextHit;
    const uint64_t perf_backend_occupancy0 = top->io_perf_backendOccupancy0;
    const uint64_t perf_backend_occupancy1 = top->io_perf_backendOccupancy1;
    const uint64_t perf_backend_occupancy2 = top->io_perf_backendOccupancy2;
    const uint64_t perf_backend_occupancy3 = top->io_perf_backendOccupancy3;
    const uint64_t perf_backend_occupancy4 = top->io_perf_backendOccupancy4;
    const uint64_t perf_backend_overlap_dispatch_src = top->io_perf_backendOverlapDispatchSrc;
    const uint64_t perf_backend_overlap_src_exec = top->io_perf_backendOverlapSrcExec;
    const uint64_t perf_backend_overlap_exec_write = top->io_perf_backendOverlapExecWrite;
    const uint64_t perf_branches = top->io_perf_branches;
    const uint64_t perf_branches_taken = top->io_perf_branchesTaken;
    const uint64_t perf_flushes = top->io_perf_flushes;
    const uint64_t perf_loads = top->io_perf_loads;
    const uint64_t perf_stores = top->io_perf_stores;
    const uint64_t perf_jumps = top->io_perf_jumps;
    const uint64_t perf_csr_ops = top->io_perf_csrOps;
    const uint64_t perf_muldiv_ops = top->io_perf_mulDivOps;
    const uint64_t perf_trap_commits = top->io_perf_trapCommits;

    const double cpi = perf_instret ? static_cast<double>(perf_cycles) / static_cast<double>(perf_instret) : 0.0;
    const double ipc = perf_cycles ? static_cast<double>(perf_instret) / static_cast<double>(perf_cycles) : 0.0;
    const double branch_taken_ratio = perf_branches ? static_cast<double>(perf_branches_taken) / static_cast<double>(perf_branches) : 0.0;

    pf << "{\n";
    pf << "  \"sim\": {\n";
    pf << "    \"cycles_executed\": " << cycles << ",\n";
    pf << "    \"max_cycles\": " << opt.max_cycles << ",\n";
    pf << "    \"timeout\": " << (timed_out ? "true" : "false") << ",\n";
    pf << "    \"completed\": " << (done ? "true" : "false") << ",\n";
    pf << "    \"commit_events\": " << commit_events << ",\n";
    pf << "    \"commit_traps\": " << commit_traps << ",\n";
    pf << "    \"commit_mem_reads\": " << commit_mem_reads << ",\n";
    pf << "    \"commit_mem_writes\": " << commit_mem_writes << ",\n";
    pf << "    \"max_stall_streak\": " << max_stall_streak << "\n";
    pf << "  },\n";
    pf << "  \"counters\": {\n";
    pf << "    \"cycles\": " << perf_cycles << ",\n";
    pf << "    \"instret\": " << perf_instret << ",\n";
    pf << "    \"stalls_hazard\": " << perf_stalls_hazard << ",\n";
    pf << "    \"stalls_fetch\": " << perf_stalls_fetch << ",\n";
    pf << "    \"stalls_mem\": " << perf_stalls_mem << ",\n";
    pf << "    \"stalls_backend\": " << perf_stalls_backend << ",\n";
    pf << "    \"stalls_writeback\": " << perf_stalls_writeback << ",\n";
    pf << "    \"stalls_commit\": " << perf_stalls_commit << ",\n";
    pf << "    \"stalls_muldiv_busy\": " << perf_stalls_muldiv_busy << ",\n";
    pf << "    \"stalls_lsu_replay_or_wait\": " << perf_stalls_lsu_replay_or_wait << ",\n";
    pf << "    \"stalls_dispatch_to_src\": " << perf_stalls_dispatch_to_src << ",\n";
    pf << "    \"stalls_src_to_exec\": " << perf_stalls_src_to_exec << ",\n";
    pf << "    \"stalls_exec_to_write\": " << perf_stalls_exec_to_write << ",\n";
    pf << "    \"cycles_dispatch_valid\": " << perf_cycles_dispatch_valid << ",\n";
    pf << "    \"cycles_src_valid\": " << perf_cycles_src_valid << ",\n";
    pf << "    \"cycles_exec_valid\": " << perf_cycles_exec_valid << ",\n";
    pf << "    \"cycles_write_valid\": " << perf_cycles_write_valid << ",\n";
    pf << "    \"cycles_dispatch_fire\": " << perf_cycles_dispatch_fire << ",\n";
    pf << "    \"cycles_src_fire\": " << perf_cycles_src_fire << ",\n";
    pf << "    \"cycles_exec_fire\": " << perf_cycles_exec_fire << ",\n";
    pf << "    \"cycles_write_fire\": " << perf_cycles_write_fire << ",\n";
    pf << "    \"frontend_pending_req_cycles\": " << perf_frontend_pending_req_cycles << ",\n";
    pf << "    \"frontend_beat0_valid_cycles\": " << perf_frontend_beat0_valid_cycles << ",\n";
    pf << "    \"frontend_beat1_valid_cycles\": " << perf_frontend_beat1_valid_cycles << ",\n";
    pf << "    \"frontend_req_issued\": " << perf_frontend_req_issued << ",\n";
    pf << "    \"frontend_rsp_accepted\": " << perf_frontend_rsp_accepted << ",\n";
    pf << "    \"frontend_need_current_req\": " << perf_frontend_need_current_req << ",\n";
    pf << "    \"frontend_need_next_req\": " << perf_frontend_need_next_req << ",\n";
    pf << "    \"frontend_prefetch_req\": " << perf_frontend_prefetch_req << ",\n";
    pf << "    \"frontend_wait_cur_beat\": " << perf_frontend_wait_cur_beat << ",\n";
    pf << "    \"frontend_wait_next_beat\": " << perf_frontend_wait_next_beat << ",\n";
    pf << "    \"frontend_take_insn\": " << perf_frontend_take_insn << ",\n";
    pf << "    \"frontend_cur_beat_hit\": " << perf_frontend_cur_beat_hit << ",\n";
    pf << "    \"frontend_next_beat_hit\": " << perf_frontend_next_beat_hit << ",\n";
    pf << "    \"frontend_cmd_valid_cycles\": " << perf_frontend_cmd_valid_cycles << ",\n";
    pf << "    \"frontend_prefetch_window\": " << perf_frontend_prefetch_window << ",\n";
    pf << "    \"frontend_prefetch_blocked_no_cmd\": " << perf_frontend_prefetch_blocked_no_cmd << ",\n";
    pf << "    \"frontend_prefetch_blocked_pending\": " << perf_frontend_prefetch_blocked_pending << ",\n";
    pf << "    \"frontend_prefetch_blocked_next_hit\": " << perf_frontend_prefetch_blocked_next_hit << ",\n";
    pf << "    \"backend_occupancy0\": " << perf_backend_occupancy0 << ",\n";
    pf << "    \"backend_occupancy1\": " << perf_backend_occupancy1 << ",\n";
    pf << "    \"backend_occupancy2\": " << perf_backend_occupancy2 << ",\n";
    pf << "    \"backend_occupancy3\": " << perf_backend_occupancy3 << ",\n";
    pf << "    \"backend_occupancy4\": " << perf_backend_occupancy4 << ",\n";
    pf << "    \"backend_overlap_dispatch_src\": " << perf_backend_overlap_dispatch_src << ",\n";
    pf << "    \"backend_overlap_src_exec\": " << perf_backend_overlap_src_exec << ",\n";
    pf << "    \"backend_overlap_exec_write\": " << perf_backend_overlap_exec_write << ",\n";
    pf << "    \"branches\": " << perf_branches << ",\n";
    pf << "    \"branches_taken\": " << perf_branches_taken << ",\n";
    pf << "    \"flushes\": " << perf_flushes << ",\n";
    pf << "    \"loads\": " << perf_loads << ",\n";
    pf << "    \"stores\": " << perf_stores << ",\n";
    pf << "    \"jumps\": " << perf_jumps << ",\n";
    pf << "    \"csr_ops\": " << perf_csr_ops << ",\n";
    pf << "    \"muldiv_ops\": " << perf_muldiv_ops << ",\n";
    pf << "    \"trap_commits\": " << perf_trap_commits << "\n";
    pf << "  },\n";
    pf << "  \"derived\": {\n";
    pf << std::fixed << std::setprecision(6);
    pf << "    \"cpi\": " << cpi << ",\n";
    pf << "    \"ipc\": " << ipc << ",\n";
    pf << "    \"branch_taken_ratio\": " << branch_taken_ratio << "\n";
    pf << "  }\n";
    pf << "}\n";
    pf.close();
  }
  if (commit_trace) {
    commit_trace.close();
  }
  delete top;
  return timed_out ? 3 : 0;
}
