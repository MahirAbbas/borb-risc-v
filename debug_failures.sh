#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_TOOL="$ROOT_DIR/scripts/riscv_failure_report.py"

RUN_FORMAL=false
RUN_RISCOF=false
RUN_TENSTORRENT=false
RUN_ALL=true

RISCOF_WORKDIR=""
TENSTORRENT_SUMMARY=""
XLEN=64
RISCOF_RERUN=true

usage() {
  cat <<'USAGE'
Usage: ./debug_failures.sh [OPTIONS]

Generate failure debugging artifacts for:
  - riscv-formal
  - RISCOF
  - Tenstorrent suite

Options:
  --formal                 Generate formal failure reports
  --riscof                 Generate RISCOF failure reports
  --tenstorrent            Generate Tenstorrent failure reports
  --all                    Generate all reports (default if none selected)
  --workdir <path>         RISCOF workdir to scan (optional)
  --summary <path>         Tenstorrent summary.json path (optional)
  --xlen <32|64>           XLEN for objdump/decode tools (default: 64)
  --no-riscof-rerun        Do not rerun failing RISCOF tests to produce debug artifacts
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
    --riscof)
      RUN_RISCOF=true
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
      RUN_RISCOF=false
      RUN_TENSTORRENT=false
      shift
      ;;
    --workdir)
      RISCOF_WORKDIR="$2"
      shift 2
      ;;
    --summary)
      TENSTORRENT_SUMMARY="$2"
      shift 2
      ;;
    --xlen)
      XLEN="$2"
      shift 2
      ;;
    --no-riscof-rerun)
      RISCOF_RERUN=false
      shift
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
  RUN_RISCOF=true
  RUN_TENSTORRENT=true
fi

if [[ "$RUN_FORMAL" = true ]]; then
  echo "[1/3] Formal reports..."
  python3 "$REPORT_TOOL" formal --checks-dir "$ROOT_DIR/formal/cores/borb/checks" --xlen "$XLEN"
fi

if [[ "$RUN_RISCOF" = true ]]; then
  echo "[2/3] RISCOF reports..."
  RISCOF_ARGS=(riscof --xlen "$XLEN")
  if [[ -n "$RISCOF_WORKDIR" ]]; then
    RISCOF_ARGS+=(--workdir "$RISCOF_WORKDIR")
  fi
  if [[ "$RISCOF_RERUN" = false ]]; then
    RISCOF_ARGS+=(--no-rerun)
  fi
  python3 "$REPORT_TOOL" "${RISCOF_ARGS[@]}"
fi

if [[ "$RUN_TENSTORRENT" = true ]]; then
  echo "[3/3] Tenstorrent reports..."
  TENSTORRENT_ARGS=(tenstorrent --xlen "$XLEN")
  if [[ -n "$TENSTORRENT_SUMMARY" ]]; then
    TENSTORRENT_ARGS+=(--summary "$TENSTORRENT_SUMMARY")
  fi
  python3 "$REPORT_TOOL" "${TENSTORRENT_ARGS[@]}"
fi

echo "Done."
