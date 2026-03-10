#!/bin/bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <existing-test.elf> [directed-elf-runner options]"
  echo "Example:"
  echo "  $0 verif/riscof/riscof_work_subset_*/rv64i_m/C/src/cadd-01.S/dut/my.elf --trace-commit"
  exit 2
fi

python3 scripts/directed_elf_runner.py "$@"
