#!/usr/bin/env sh
set -e
DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "Gradle is required to build this project." >&2
  exit 1
fi
