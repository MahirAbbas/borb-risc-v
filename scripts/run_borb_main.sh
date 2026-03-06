#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/workspace_env.sh"

MAIN_CLASS="${1:-}"
if [[ -z "$MAIN_CLASS" ]]; then
  echo "Usage: $0 <main-class> [args...]" >&2
  exit 2
fi
shift

BLOOP_CONFIG="${BLOOP_CONFIG:-$ROOT_DIR/.bloop/projectname.json}"
if [[ ! -f "$BLOOP_CONFIG" ]]; then
  echo "Missing Bloop config: $BLOOP_CONFIG" >&2
  exit 2
fi

"$ROOT_DIR/scripts/compile_scala.sh"

JAVA_HOME_FROM_BLOOP="$(python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" java-home)"
if [[ -n "$JAVA_HOME_FROM_BLOOP" && -x "$JAVA_HOME_FROM_BLOOP/bin/java" ]]; then
  export JAVA_HOME="$JAVA_HOME_FROM_BLOOP"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

RUNTIME_CP=()
while IFS= read -r line; do
  [[ -n "$line" ]] && RUNTIME_CP+=("$line")
done < <(python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" runtime-classpath)
JAVA_CP="$(IFS=:; echo "${RUNTIME_CP[*]}")"

exec java -cp "$JAVA_CP" "$MAIN_CLASS" "$@"
