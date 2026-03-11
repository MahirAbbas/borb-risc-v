#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/workspace_env.sh"

python3 "$ROOT_DIR/scripts/full_validation_suite.py" "$@"
