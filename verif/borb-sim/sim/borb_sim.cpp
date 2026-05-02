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
  uint64_t dump_begin = 0;
  uint64_t dump_end = 0;
  std::string dump_mem_path;
  bool dump_mem = false;
};

static void usage(const char* prog) {
  std::cerr << "Usage: " << prog << " (--bin <path> | --elf <path>) --sig-begin <hex> --sig-end <hex> --tohost <hex> --signature <path> [--max-cycles <n>] [--fst <path>] [--trace-commit <path>] [--report-tohost <path>] [--report-perf <path>] [--dump-begin <hex> --dump-end <hex> --dump-mem <path>]" << std::endl;
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
    } else if (a == "--dump-begin") {
      const char* v = need("--dump-begin");
      if (!v) return false;
      opt.dump_begin = std::stoull(v, nullptr, 0);
    } else if (a == "--dump-end") {
      const char* v = need("--dump-end");
      if (!v) return false;
      opt.dump_end = std::stoull(v, nullptr, 0);
    } else if (a == "--dump-mem") {
      const char* v = need("--dump-mem");
      if (!v) return false;
      opt.dump_mem_path = v;
      opt.dump_mem = true;
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

  auto is_cbo_zero = [](uint32_t insn) -> bool {
    return (insn & 0xfff07fffU) == 0x0040200fU;
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
  bool stale_wb_reported = false;
  const bool verbose_diag = std::getenv("BORB_SIM_VERBOSE_DIAG") != nullptr;
  uint64_t fetch_window_logs = 0;
  bool done = false;
  bool timed_out = false;
  bool tohost_seen = false;
  uint64_t tohost_seen_cycle = 0;
  const uint64_t tohost_drain_cycles = 64;
  uint64_t commit_events = 0;
  uint64_t commit_traps = 0;
  uint64_t commit_mem_reads = 0;
  uint64_t commit_mem_writes = 0;
  struct RecentCycle {
    uint64_t cycle = 0;
    bool commit_valid = false;
    bool commit_pulse = false;
    bool duplicate_retire = false;
    bool redirect_any = false;
    bool redirect_branch = false;
    bool redirect_exec_epoch = false;
    bool redirect_pc_jump_valid = false;
    uint64_t redirect_pc_jump_target = 0;
    bool redirect_pending = false;
    uint32_t redirect_seq = 0;
    uint32_t current_epoch = 0;
    uint32_t s5_epoch = 0;
    uint32_t s6_epoch = 0;
    uint32_t s7_epoch = 0;
    uint32_t s4_seq = 0;
    uint32_t s5_seq = 0;
    uint32_t s6_seq = 0;
    uint32_t s7_seq = 0;
    uint64_t f_pc = 0;
    uint64_t d_pc = 0;
    uint64_t x_pc = 0;
    uint64_t wb_pc = 0;
    uint32_t s3_insn = 0;
    uint32_t x_insn = 0;
    uint32_t wb_insn = 0;
    bool s2_valid = false;
    bool s2_ready = false;
    bool s3_valid = false;
    bool s3_ready = false;
    bool x_valid = false;
    bool x_lane = false;
    bool wb_valid = false;
    bool wb_lane = false;
    bool fetch_ctl_active = false;
    uint64_t fetch_ctl_pc = 0;
    uint32_t fetch_ctl_epoch = 0;
    bool adapter_active = false;
    bool scalar_boundary_valid = false;
    uint64_t scalar_boundary_pc = 0;
    uint32_t scalar_boundary_seq = 0;
    uint32_t scalar_boundary_bundle_seq = 0;
    uint32_t scalar_boundary_epoch = 0;
    uint32_t ftq_alloc_ptr = 0;
    bool builder_valid = false;
    bool queue_push_ready = false;
    bool bus_rsp_valid = false;
    bool bus_inflight0 = false;
    bool bus_inflight1 = false;
    bool stale_rsp_dropped = false;
    bool l1i_hit1 = false;
  };
  std::vector<RecentCycle> recent_cycles(64);
  size_t recent_head = 0;
  size_t recent_count = 0;
  auto push_recent_cycle = [&](const RecentCycle& sample) {
    recent_cycles[recent_head] = sample;
    recent_head = (recent_head + 1) % recent_cycles.size();
    if (recent_count < recent_cycles.size()) recent_count++;
  };
  auto dump_recent_cycles = [&](const char* reason) {
    std::cerr << "RECENT[" << reason << "] count=" << recent_count << std::endl;
    for (size_t i = 0; i < recent_count; ++i) {
      const size_t idx = (recent_head + recent_cycles.size() - recent_count + i) % recent_cycles.size();
      const auto& rc = recent_cycles[idx];
      std::cerr
          << "  cyc=" << rc.cycle
          << " commit_valid=" << (int)rc.commit_valid
          << " commit_pulse=" << (int)rc.commit_pulse
          << " dup_retire=" << (int)rc.duplicate_retire
          << " redir_any=" << (int)rc.redirect_any
          << " redir_branch=" << (int)rc.redirect_branch
          << " redir_exec_epoch=" << (int)rc.redirect_exec_epoch
          << " redir_jump_valid=" << (int)rc.redirect_pc_jump_valid
          << " redir_jump_target=0x" << std::hex << rc.redirect_pc_jump_target
          << " redir_pending=" << (int)rc.redirect_pending
          << " redir_seq=0x" << rc.redirect_seq
          << " cur_epoch=" << std::dec << rc.current_epoch
          << " s5_epoch=" << rc.s5_epoch
          << " s6_epoch=" << rc.s6_epoch
          << " s7_epoch=" << rc.s7_epoch
          << " s4_seq=0x" << std::hex << rc.s4_seq
          << " s5_seq=0x" << rc.s5_seq
          << " s6_seq=0x" << rc.s6_seq
          << " s7_seq=0x" << rc.s7_seq
          << " f_pc=0x" << rc.f_pc
          << " d_pc=0x" << rc.d_pc
          << " x_pc=0x" << rc.x_pc
          << " wb_pc=0x" << rc.wb_pc
          << " s3_insn=0x" << rc.s3_insn
          << " x_insn=0x" << rc.x_insn
          << " wb_insn=0x" << rc.wb_insn
          << " s2_valid=" << std::dec << (int)rc.s2_valid
          << " s2_ready=" << (int)rc.s2_ready
          << " s3_valid=" << (int)rc.s3_valid
          << " s3_ready=" << (int)rc.s3_ready
          << " x_valid=" << (int)rc.x_valid
          << " x_lane=" << (int)rc.x_lane
          << " wb_valid=" << (int)rc.wb_valid
          << " wb_lane=" << (int)rc.wb_lane
          << " fetch_ctl_active=" << (int)rc.fetch_ctl_active
          << " fetch_ctl_pc=0x" << std::hex << rc.fetch_ctl_pc
          << " fetch_ctl_epoch=" << std::dec << rc.fetch_ctl_epoch
          << " adapter_active=" << (int)rc.adapter_active
          << " scalar_boundary_valid=" << (int)rc.scalar_boundary_valid
          << " scalar_boundary_pc=0x" << std::hex << rc.scalar_boundary_pc
          << " scalar_boundary_seq=0x" << rc.scalar_boundary_seq
          << " scalar_boundary_bundle_seq=0x" << rc.scalar_boundary_bundle_seq
          << " scalar_boundary_epoch=" << std::dec << rc.scalar_boundary_epoch
          << " ftq_alloc_ptr=0x" << std::hex << rc.ftq_alloc_ptr
          << " builder_valid=" << std::dec << (int)rc.builder_valid
          << " queue_push_ready=" << (int)rc.queue_push_ready
          << " bus_rsp_valid=" << (int)rc.bus_rsp_valid
          << " bus_inflight0=" << (int)rc.bus_inflight0
          << " bus_inflight1=" << (int)rc.bus_inflight1
          << " stale_rsp_drop=" << (int)rc.stale_rsp_dropped
          << " l1i_hit1=" << (int)rc.l1i_hit1
          << std::endl;
    }
  };
  while (!done && cycles < opt.max_cycles) {
    tick(20 + cycles * 2);

    const uint32_t current_epoch = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_currentEpoch);
    const uint32_t s5_epoch = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_5_up_Common_SPEC_EPOCH);
    const uint32_t s6_epoch = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Common_SPEC_EPOCH);
    const uint32_t s7_epoch = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Common_SPEC_EPOCH);
    const bool wb_valid = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Decoder_VALID;
    const bool wb_lane = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Common_LANE_SEL;
    const bool stale_wb = wb_valid && wb_lane && (s7_epoch != current_epoch) && !top->io_dbg_commitPulse;

    RecentCycle sample;
    sample.cycle = cycles;
    sample.commit_valid = top->io_dbg_commitValid;
    sample.commit_pulse = top->io_dbg_commitPulse;
    sample.duplicate_retire = top->io_dbg_duplicateRetire;
    sample.redirect_any = top->io_dbg_redirectAny;
    sample.redirect_branch = top->io_dbg_redirectBranch;
    sample.redirect_exec_epoch = top->io_dbg_redirectExecEpochMatches;
    sample.redirect_pc_jump_valid = top->io_dbg_redirectPcJumpValid;
    sample.redirect_pc_jump_target = top->io_dbg_redirectPcJumpTarget;
    sample.redirect_pending = rootp->SoC__DOT__area_cpu__DOT__coreArea_redirectCommitPending;
    sample.redirect_seq = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_redirectCommitSeq);
    sample.current_epoch = current_epoch;
    sample.s5_epoch = s5_epoch;
    sample.s6_epoch = s6_epoch;
    sample.s7_epoch = s7_epoch;
    sample.s4_seq = static_cast<uint32_t>(top->io_dbg_s4_seq);
    sample.s5_seq = static_cast<uint32_t>(top->io_dbg_s5_seq);
    sample.s6_seq = static_cast<uint32_t>(top->io_dbg_s6_seq);
    sample.s7_seq = static_cast<uint32_t>(top->io_dbg_s7_seq);
    sample.f_pc = static_cast<uint64_t>(top->io_dbg_f_pc);
    sample.d_pc = static_cast<uint64_t>(top->io_dbg_d_pc);
    sample.x_pc = static_cast<uint64_t>(top->io_dbg_x_pc);
    sample.wb_pc = static_cast<uint64_t>(top->io_dbg_wb_pc);
    sample.s3_insn = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_Decoder_DECODED_INSTRUCTION);
    sample.x_insn = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Decoder_DECODED_INSTRUCTION);
    sample.wb_insn = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Decoder_DECODED_INSTRUCTION);
    sample.s2_valid = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_up_valid;
    sample.s2_ready = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_down_isReady;
    sample.s3_valid = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_up_valid;
    sample.s3_ready = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_isReady;
    sample.x_valid = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Decoder_VALID;
    sample.x_lane = rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Common_LANE_SEL;
    sample.wb_valid = wb_valid;
    sample.wb_lane = wb_lane;
    sample.fetch_ctl_active = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_control_io_activeValid;
    sample.fetch_ctl_pc = static_cast<uint64_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_control__DOT__activePc);
    sample.fetch_ctl_epoch = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_control__DOT__activeEpoch);
    sample.adapter_active = 0;
    sample.scalar_boundary_valid = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryValid;
    sample.scalar_boundary_pc = static_cast<uint64_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_pc);
    sample.scalar_boundary_seq = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_scalarSeq);
    sample.scalar_boundary_bundle_seq = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_bundleSeq);
    sample.scalar_boundary_epoch = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_epoch);
    sample.ftq_alloc_ptr = static_cast<uint32_t>(rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_predictor__DOT__ftqAllocPtr);
    sample.builder_valid = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_builder_io_bundle_valid;
    sample.queue_push_ready = 0;
    sample.bus_rsp_valid = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_busBridge_rspValid;
    sample.bus_inflight0 = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_busBridge_inflightValid_0;
    sample.bus_inflight1 = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_busBridge_inflightValid_1;
    sample.stale_rsp_dropped = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_l1i_io_staleRspDropped;
    sample.l1i_hit1 = rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_l1i_io_lookupRsp_1_hit;
    push_recent_cycle(sample);

    const bool enable_pc_exception_debug = false;
    if (enable_pc_exception_debug && top->io_dbg_redirectPcExceptionValid) {
      std::cerr
          << "PCEXC cyc=" << cycles
          << " target=0x" << std::hex << (uint64_t)top->io_dbg_redirectPcExceptionTarget
          << " cause=0x" << (uint64_t)top->io_dbg_liveTrapCause
          << " tval=0x" << (uint64_t)top->io_dbg_liveTrapTval
          << " x_pc=0x" << (uint64_t)top->io_dbg_x_pc
          << " wb_pc=0x" << (uint64_t)top->io_dbg_wb_pc
          << " x_rs1_addr=" << std::dec << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Decoder_RS1_ADDR
          << " x_rs2_addr=" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Decoder_RS2_ADDR
          << " x_send_agu=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Dispatch_SENDTOAGU
          << " x_rs1=0x" << std::hex << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_SrcPlugin_RS1
          << " x_rs2=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_SrcPlugin_RS2
          << " x_imm=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_SrcPlugin_IMMED
          << " order=" << std::dec << top->io_dbg_commitOrder
          << std::endl;
    }
    if (verbose_diag && cycles < 64 && top->io_dbg_redirectAny) {
      std::cerr
          << "REDIRDBG: cyc=" << cycles
          << " any=" << (int)top->io_dbg_redirectAny
          << " branch=" << (int)top->io_dbg_redirectBranch
          << " trap=" << (int)top->io_dbg_redirectTrap
          << " mret=" << (int)top->io_dbg_redirectMret
          << " pc_jump=" << (int)top->io_dbg_redirectPcJumpValid
          << " pc_exc=" << (int)top->io_dbg_redirectPcExceptionValid
          << " x_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_valid
          << " mret_fire=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_trapLogic_redirect_mretFire
          << " cause=0x" << std::hex << (uint64_t)top->io_dbg_liveTrapCause
          << " tval=0x" << (uint64_t)top->io_dbg_liveTrapTval
          << " x_pc=0x" << (uint64_t)top->io_dbg_x_pc
          << std::dec << std::endl;
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

    if (verbose_diag && stale_wb && !stale_wb_reported) {
      std::cerr
          << "STALEWB: cyc=" << cycles
          << " wb_pc=0x" << std::hex << (uint64_t)top->io_dbg_wb_pc
          << " wb_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Decoder_DECODED_INSTRUCTION
          << " wb_seq=0x" << (uint32_t)top->io_dbg_s7_seq
          << " wb_epoch=" << std::dec << s7_epoch
          << " cur_epoch=" << current_epoch
          << " dup_retire=" << (int)top->io_dbg_duplicateRetire
          << " redir_pending=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_redirectCommitPending
          << " redir_seq=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_redirectCommitSeq
          << " x_pc=0x" << (uint64_t)top->io_dbg_x_pc
          << " x_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Decoder_DECODED_INSTRUCTION
          << " s6_seq=0x" << (uint32_t)top->io_dbg_s6_seq
          << " mem_wmask=0x" << (uint32_t)top->io_dbg_memWmask
          << " mem_addr=0x" << (uint64_t)top->io_dbg_memAddr
          << " mem_wdata=0x" << (uint64_t)top->io_dbg_memWdata
          << " redir_any=" << std::dec << (int)top->io_dbg_redirectAny
          << std::endl;
      dump_recent_cycles("stale_wb");
      stale_wb_reported = true;
    } else if (!stale_wb) {
      stale_wb_reported = false;
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
          << " s2_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_up_valid
          << " s2_ready=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_2_down_isReady
          << " s3_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_up_valid
          << " s3_ready=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_isReady
          << " s3_down_valid=0"
          << " s3_seq=" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_up_Fetch_FETCH_SEQ
          << " s3_epoch=" << rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_up_Common_SPEC_EPOCH
          << " s3_dec_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_valid
          << " s3_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_3_down_Decoder_DECODED_INSTRUCTION
          << " x_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Decoder_VALID
          << " x_lane=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Common_LANE_SEL
          << " x_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_8_up_Decoder_DECODED_INSTRUCTION
          << " wb_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Decoder_VALID
          << " wb_lane=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Common_LANE_SEL
          << " wb_insn=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Decoder_DECODED_INSTRUCTION
          << " wb_commit=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_pipeline_ctrl_9_up_Common_COMMIT
          << " cur_epoch=" << std::dec << current_epoch
          << " s5_epoch=" << s5_epoch
          << " s6_epoch=" << s6_epoch
          << " wb_epoch=" << s7_epoch
          << " dcacheState=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_state
          << " dArwFired=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_arwSent
          << " dWFired=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_wSent
          << " dAxiArwValid=" << (int)rootp->SoC__DOT__area_cpu_io_dAxi_arw_valid
          << " dAxiWValid=" << (int)rootp->SoC__DOT__area_cpu_io_dAxi_w_valid
          << " dAxiArwFire=" << (int)rootp->SoC__DOT__area_cpu__DOT__io_dAxi_arw_fire
          << " dAxiWFire=" << (int)rootp->SoC__DOT__area_cpu__DOT__io_dAxi_w_fire
          << " dCmdReady=" << (int)(rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_state == 0)
          << " dCmdWrite=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_activeCmd_write
          << " dCmdMask=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_activeCmd_mask
          << " dCmdAddr=0x0"
          << " dCmdData=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_activeCmd_data
          << " vecExec=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorExecPacket
          << " vecMemActive=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorMemoryActive
          << " vecCmdValid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine_io_command_valid
          << " vecMemState=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine__DOT__memState
          << " vecStart=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine__DOT__startMemory
          << " vecReqValid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine_io_memReq_valid
          << " vecReqAddr=0x" << std::hex << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine_io_memReq_payload_address
          << " vecBase=0x" << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine__DOT__memBase
          << " vecVl=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine__DOT__memVl
          << " vecElem=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_vectorEngine__DOT__memElem
          << " lsuCmdReady=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_lsu_io_dBus_cmd_ready
          << " lsuBusFire=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_lsuBus_cmd_fire
          << " dRspValid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_dcache_rspValid
          << " lsu_wait=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_lsu_logic_waitingResponse
          << " lsu_amoStore=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_lsu_logic_amoStorePending
          << " rsp_bubble=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_redirectRspBubbleCounter
          << " redir_pending=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_redirectCommitPending
          << " redir_seq=" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_redirectCommitSeq
          << " fetch_ctl_active=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_control_io_activeValid
          << " fetch_ctl_pc=0x" << std::hex << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_control__DOT__activePc
          << " fetch_ctl_epoch=" << std::dec << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_control__DOT__activeEpoch
          << " adapter_active=0"
          << " adapter_epoch=0"
          << " adapter_slot=0"
          << " adapter_slotCount=0"
          << " adapter_bundleSeq=0"
          << " adapter_scalarSeqBase=0"
          << " scalar_boundary_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryValid
          << " scalar_boundary_pc=0x" << std::hex << (uint64_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_pc
          << " scalar_boundary_seq=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_scalarSeq
          << " scalar_boundary_bundleSeq=0x" << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_bundleSeq
          << " scalar_boundary_epoch=" << std::dec << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_scalarBoundaryPayload_epoch
          << " ftq_alloc_ptr=0x" << std::hex << (uint32_t)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_predictor__DOT__ftqAllocPtr
          << " builder_valid=" << std::dec << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_builder_io_bundle_valid
          << " queue_push_ready=0"
          << " bus_rsp_valid=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_busBridge_rspValid
          << " bus_inflight0=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_busBridge_inflightValid_0
          << " bus_inflight1=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_busBridge_inflightValid_1
          << " stale_rsp_drop=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_l1i_io_staleRspDropped
          << " l1i_hit1=" << (int)rootp->SoC__DOT__area_cpu__DOT__coreArea_fetch_l1i_io_lookupRsp_1_hit
          << " adapter_slot0_valid=0"
          << " adapter_slot0_pc=0x0"
          << " adapter_slot0_insn=0x0"
          << " adapter_slot1_valid=0"
          << " adapter_slot1_pc=0x0"
          << " adapter_slot1_insn=0x0"
          << " frontendPending=" << (uint64_t)top->io_perf_frontendPendingReqCycles
          << " frontendTakeInsn=" << (uint64_t)top->io_perf_frontendTakeInsn
          << " frontendFtqAlloc=" << (uint64_t)top->io_perf_frontendFtqAlloc
          << " frontendPredRedirect=" << (uint64_t)top->io_perf_frontendPredictedRedirect
          << std::endl;
      dump_recent_cycles("stall");
      stall_reported = true;
    }

    if (top->io_dbg_commitValid) {
      const uint64_t mem_addr = top->io_dbg_memAddr;
      const uint8_t mem_wmask = static_cast<uint8_t>(top->io_dbg_memWmask);
      const uint64_t mem_wdata = top->io_dbg_memWdata;
      if (is_cbo_zero(static_cast<uint32_t>(top->io_dbg_commitInsn))) {
        const uint64_t block_base = mem_addr & ~0x3fULL;
        for (int i = 0; i < 64; ++i) {
          write_byte(block_base + static_cast<uint64_t>(i), 0);
        }
      } else if (mem_wmask != 0) {
        for (int i = 0; i < 8; ++i) {
          if ((mem_wmask >> i) & 0x1) {
            write_byte(mem_addr + i, static_cast<uint8_t>((mem_wdata >> (8 * i)) & 0xFF));
          }
        }
      }
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
	                   << ",\"epoch\":{\"current\":" << current_epoch
	                   << ",\"s5\":" << s5_epoch
	                   << ",\"s6\":" << s6_epoch
	                   << ",\"s7\":" << s7_epoch << "}"
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
      if (t != 0) {
        if (!tohost_seen) {
          tohost_seen = true;
          tohost_seen_cycle = cycles;
        }
        if ((cycles - tohost_seen_cycle) >= tohost_drain_cycles) {
          done = true;
        }
      }
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
  if (opt.dump_mem) {
    if (opt.dump_end < opt.dump_begin) {
      std::cerr << "Invalid dump range: end < begin" << std::endl;
      return 1;
    }
    std::ofstream df(opt.dump_mem_path);
    if (!df) {
      std::cerr << "Failed to open memory dump file: " << opt.dump_mem_path << std::endl;
      return 1;
    }
    for (uint64_t addr = opt.dump_begin; addr < opt.dump_end; addr += 8) {
      uint64_t val = 0;
      for (int i = 0; i < 8; i++) {
        val |= (uint64_t)read_byte(addr + i) << (8 * i);
      }
      df << "0x" << std::hex << addr << ": 0x" << val << "\n";
    }
    df.close();
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
    const uint64_t perf_frontend_loop_predict_used = top->io_perf_frontendLoopPredictUsed;
    const uint64_t perf_frontend_loop_predict_hit = top->io_perf_frontendLoopPredictHit;
    const uint64_t perf_frontend_fast_predict_hit = top->io_perf_frontendFastPredictHit;
    const uint64_t perf_frontend_main_predict_hit = top->io_perf_frontendMainPredictHit;
    const uint64_t perf_frontend_indirect_predict_hit = top->io_perf_frontendIndirectPredictHit;
    const uint64_t perf_frontend_ras_use = top->io_perf_frontendRasUse;
    const uint64_t perf_frontend_ras_repair = top->io_perf_frontendRasRepair;
    const uint64_t perf_frontend_ftq_alloc = top->io_perf_frontendFtqAlloc;
    const uint64_t perf_frontend_ftq_restore = top->io_perf_frontendFtqRestore;
    const uint64_t perf_frontend_predicted_redirect = top->io_perf_frontendPredictedRedirect;
    const uint64_t perf_frontend_miss_current_block = top->io_perf_frontendMissCurrentBlock;
    const uint64_t perf_frontend_miss_next_block = top->io_perf_frontendMissNextBlock;
    const uint64_t perf_frontend_miss_prefetch = top->io_perf_frontendMissPrefetch;
    const uint64_t perf_frontend_req_blocked_outstanding = top->io_perf_frontendReqBlockedOutstanding;
    const uint64_t perf_frontend_packet_queue_full_cycles = top->io_perf_frontendPacketQueueFullCycles;
    const uint64_t perf_frontend_straddle_packets = top->io_perf_frontendStraddlePackets;
    const uint64_t perf_frontend_second_block_used = top->io_perf_frontendSecondBlockUsed;
    const uint64_t perf_frontend_second_block_late = top->io_perf_frontendSecondBlockLate;
    const uint64_t perf_frontend_wrong_path_beats = top->io_perf_frontendWrongPathBeats;
    const uint64_t perf_frontend_wrong_path_insns = top->io_perf_frontendWrongPathInsns;
    const uint64_t perf_l1i_bank_conflict_cycles = top->io_perf_l1iBankConflictCycles;
    const uint64_t perf_l1i_bank_busy_cycles = top->io_perf_l1iBankBusyCycles;
    const uint64_t perf_l1i_cross_bank_dual_fetch_success = top->io_perf_l1iCrossBankDualFetchSuccess;
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
    pf << "    \"frontend_loop_predict_used\": " << perf_frontend_loop_predict_used << ",\n";
    pf << "    \"frontend_loop_predict_hit\": " << perf_frontend_loop_predict_hit << ",\n";
    pf << "    \"frontend_fast_predict_hit\": " << perf_frontend_fast_predict_hit << ",\n";
    pf << "    \"frontend_main_predict_hit\": " << perf_frontend_main_predict_hit << ",\n";
    pf << "    \"frontend_indirect_predict_hit\": " << perf_frontend_indirect_predict_hit << ",\n";
    pf << "    \"frontend_ras_use\": " << perf_frontend_ras_use << ",\n";
    pf << "    \"frontend_ras_repair\": " << perf_frontend_ras_repair << ",\n";
    pf << "    \"frontend_ftq_alloc\": " << perf_frontend_ftq_alloc << ",\n";
    pf << "    \"frontend_ftq_restore\": " << perf_frontend_ftq_restore << ",\n";
    pf << "    \"frontend_predicted_redirect\": " << perf_frontend_predicted_redirect << ",\n";
    pf << "    \"frontend_miss_current_block\": " << perf_frontend_miss_current_block << ",\n";
    pf << "    \"frontend_miss_next_block\": " << perf_frontend_miss_next_block << ",\n";
    pf << "    \"frontend_miss_prefetch\": " << perf_frontend_miss_prefetch << ",\n";
    pf << "    \"frontend_req_blocked_outstanding\": " << perf_frontend_req_blocked_outstanding << ",\n";
    pf << "    \"frontend_packet_queue_full_cycles\": " << perf_frontend_packet_queue_full_cycles << ",\n";
    pf << "    \"frontend_straddle_packets\": " << perf_frontend_straddle_packets << ",\n";
    pf << "    \"frontend_second_block_used\": " << perf_frontend_second_block_used << ",\n";
    pf << "    \"frontend_second_block_late\": " << perf_frontend_second_block_late << ",\n";
    pf << "    \"frontend_wrong_path_beats\": " << perf_frontend_wrong_path_beats << ",\n";
    pf << "    \"frontend_wrong_path_insns\": " << perf_frontend_wrong_path_insns << ",\n";
    pf << "    \"l1i_bank_conflict_cycles\": " << perf_l1i_bank_conflict_cycles << ",\n";
    pf << "    \"l1i_bank_busy_cycles\": " << perf_l1i_bank_busy_cycles << ",\n";
    pf << "    \"l1i_cross_bank_dual_fetch_success\": " << perf_l1i_cross_bank_dual_fetch_success << ",\n";
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
