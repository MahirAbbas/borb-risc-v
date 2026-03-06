#!/usr/bin/env bash

if [[ -n "${ROOT_DIR:-}" ]]; then
  ROOT_DIR="$(cd "${ROOT_DIR}" && pwd)"
elif [[ -n "${BASH_SOURCE[0]:-}" ]]; then
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
elif [[ -n "${ZSH_VERSION:-}" ]]; then
  ROOT_DIR="$(cd "$(dirname "${(%):-%N}")/.." && pwd)"
else
  ROOT_DIR="$(pwd)"
fi
CACHE_DIR="${ROOT_DIR}/.cache"

mkdir -p \
  "${CACHE_DIR}/sbt/boot" \
  "${CACHE_DIR}/sbt/global" \
  "${CACHE_DIR}/ivy2" \
  "${CACHE_DIR}/coursier" \
  "${CACHE_DIR}/tmp" \
  "${CACHE_DIR}/runtime"

export TMPDIR="${CACHE_DIR}/tmp"
export XDG_RUNTIME_DIR="${CACHE_DIR}/runtime"
export JAVA_OPTS="${JAVA_OPTS:-} -Djava.io.tmpdir=${TMPDIR}"
export SBT_OPTS="${SBT_OPTS:-} -Dsbt.boot.directory=${CACHE_DIR}/sbt/boot -Dsbt.global.base=${CACHE_DIR}/sbt/global -Dsbt.ivy.home=${CACHE_DIR}/ivy2 -Dsbt.server.autostart=false -Djava.io.tmpdir=${TMPDIR}"
export COURSIER_CACHE="${COURSIER_CACHE:-${CACHE_DIR}/coursier}"
export IVY_HOME="${IVY_HOME:-${CACHE_DIR}/ivy2}"
export SBT_BOOT_DIR="${CACHE_DIR}/sbt/boot"
export SBT_GLOBAL_DIR="${CACHE_DIR}/sbt/config"

if [[ -n "${PYENV_VERSION:-}" ]]; then
  PYENV_BIN="${HOME}/.pyenv/versions/${PYENV_VERSION}/bin"
  if [[ -d "${PYENV_BIN}" ]]; then
    export PATH="${PYENV_BIN}:$PATH"
  fi
fi
