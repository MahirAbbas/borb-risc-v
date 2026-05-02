#!/bin/bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <asm-file.S> [directed-runner options]"
  echo "Debug-only: directed assembly is for repros, traces, waveforms, and perf probes; ACT4 subsets are the cleanup gates."
  echo "Example:"
  echo "  $0 verif/directed/asm/branch_taken_minimal.S --trace --rebuild-sim"
  exit 2
fi

python3 scripts/directed_asm_runner.py "$@"
