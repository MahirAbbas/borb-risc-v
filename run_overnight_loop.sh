#!/bin/bash
set -e

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/workspace_env.sh"

exec python3 /Users/mahir/fun/borb/scripts/overnight_runner.py "$@"
