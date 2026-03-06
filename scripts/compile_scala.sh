#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/workspace_env.sh"

BLOOP_CONFIG="${BLOOP_CONFIG:-$ROOT_DIR/.bloop/projectname.json}"

if [[ ! -f "$BLOOP_CONFIG" ]]; then
  echo "Missing Bloop config: $BLOOP_CONFIG" >&2
  exit 2
fi

read_array() {
  local __outvar="$1"
  shift
  local __items=()
  while IFS= read -r line; do
    [[ -n "$line" ]] && __items+=("$line")
  done < <("$@")
  eval "$__outvar=(\"\${__items[@]}\")"
}

read_array SOURCES python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" sources
if [[ "${#SOURCES[@]}" -eq 0 ]]; then
  echo "No Scala/Java sources found via $BLOOP_CONFIG" >&2
  exit 2
fi

JAVA_HOME_FROM_BLOOP="$(python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" java-home)"
if [[ -n "$JAVA_HOME_FROM_BLOOP" && -x "$JAVA_HOME_FROM_BLOOP/bin/java" ]]; then
  export JAVA_HOME="$JAVA_HOME_FROM_BLOOP"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

CLASSES_DIR="$(python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" classes-dir)"
mkdir -p "$CLASSES_DIR"

if [[ "$(python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" needs-compile)" != "yes" ]]; then
  echo "[compile] classes are up to date: $CLASSES_DIR"
  exit 0
fi

read_array SCALA_JARS python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" scala-jars
read_array COMPILE_CP python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" compile-classpath
read_array SCALA_OPTS python3 "$ROOT_DIR/scripts/bloop_project.py" --config "$BLOOP_CONFIG" scala-options

JAVA_SOURCES=()
SCALA_SOURCES=()
for source in "${SOURCES[@]}"; do
  case "$source" in
    *.java) JAVA_SOURCES+=("$source") ;;
    *.scala) SCALA_SOURCES+=("$source") ;;
  esac
done

if [[ "${#JAVA_SOURCES[@]}" -gt 0 ]]; then
  echo "Java sources are present; direct non-sbt compile path does not support mixed Java/Scala yet." >&2
  exit 2
fi

SCALA_CP="$(IFS=:; echo "${COMPILE_CP[*]}")"
SCALA_COMPILER_CP="$(IFS=:; echo "${SCALA_JARS[*]}")"

echo "[compile] compiling ${#SCALA_SOURCES[@]} Scala sources -> $CLASSES_DIR"
java -cp "$SCALA_COMPILER_CP" scala.tools.nsc.Main \
  -d "$CLASSES_DIR" \
  -classpath "$SCALA_CP" \
  "${SCALA_OPTS[@]}" \
  "${SCALA_SOURCES[@]}"

touch "$CLASSES_DIR/.compile-stamp"
