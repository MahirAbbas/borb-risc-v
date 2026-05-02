#!/bin/bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <existing-test.elf> [directed-elf-runner options]"
  echo "Example:"
  echo "  $0 verif/act4/work/borb-RVA23S64/elfs/rv64i/I/I-add-00.elf --trace-commit"
  exit 2
fi

python3 scripts/directed_elf_runner.py "$@"
