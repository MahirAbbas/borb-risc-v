#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_TOOL="$ROOT_DIR/scripts/riscv_failure_report.py"

RUN_FORMAL=false
RUN_TENSTORRENT=false
RUN_ALL=true

TENSTORRENT_SUMMARY=""
XLEN=64

usage() {
  cat <<'USAGE'
Usage: ./debug_failures.sh [OPTIONS]

Generate failure debugging artifacts for:
  - riscv-formal
  - Tenstorrent suite

Options:
  --formal                 Generate formal failure reports
  --tenstorrent            Generate Tenstorrent failure reports
  --all                    Generate all reports (default if none selected)
  --summary <path>         Tenstorrent summary.json path (optional)
  --xlen <32|64>           XLEN for objdump/decode tools (default: 64)
  -h, --help               Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --formal)
      RUN_FORMAL=true
      RUN_ALL=false
      shift
      ;;
    --tenstorrent)
      RUN_TENSTORRENT=true
      RUN_ALL=false
      shift
      ;;
    --all)
      RUN_ALL=true
      RUN_FORMAL=false
      RUN_TENSTORRENT=false
      shift
      ;;
    --summary)
      TENSTORRENT_SUMMARY="$2"
      shift 2
      ;;
    --xlen)
      XLEN="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ "$XLEN" != "32" && "$XLEN" != "64" ]]; then
  echo "Invalid --xlen value: $XLEN (expected 32 or 64)" >&2
  exit 2
fi

if [[ ! -f "$REPORT_TOOL" ]]; then
  echo "Report tool not found: $REPORT_TOOL" >&2
  exit 2
fi

if [[ "$RUN_ALL" = true ]]; then
  RUN_FORMAL=true
  RUN_TENSTORRENT=true
fi

if [[ "$RUN_FORMAL" = true ]]; then
  echo "[1/2] Formal reports..."
  python3 "$REPORT_TOOL" formal --checks-dir "$ROOT_DIR/formal/cores/borb/checks" --xlen "$XLEN"
fi

if [[ "$RUN_TENSTORRENT" = true ]]; then
  echo "[2/2] Tenstorrent reports..."
  TENSTORRENT_ARGS=(tenstorrent --xlen "$XLEN")
  if [[ -n "$TENSTORRENT_SUMMARY" ]]; then
    TENSTORRENT_ARGS+=(--summary "$TENSTORRENT_SUMMARY")
  fi
  python3 "$REPORT_TOOL" "${TENSTORRENT_ARGS[@]}"
fi

echo "Done."
