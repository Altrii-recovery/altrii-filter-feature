#!/usr/bin/env sh

set -e

PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")"/$link"
  fi
done
SAVED=$(pwd)
cd "$(dirname "$PRG")/"
APP_HOME=$(pwd -P)
cd "$SAVED"

PROPERTIES_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "ERROR: Unable to locate $PROPERTIES_FILE" >&2
  exit 1
fi

read_prop() {
  PROP_NAME="$1"
  grep "^$PROP_NAME=" "$PROPERTIES_FILE" | tail -n 1 | cut -d'=' -f2- | tr -d '\r'
}

DIST_URL=$(read_prop distributionUrl | tr -d '\\')
if [ -z "$DIST_URL" ]; then
  echo "ERROR: distributionUrl is not defined in $PROPERTIES_FILE" >&2
  exit 1
fi

DIST_BASE=$(read_prop distributionBase)
DIST_PATH=$(read_prop distributionPath)

[ -n "$DIST_BASE" ] || DIST_BASE="GRADLE_USER_HOME"
[ -n "$DIST_PATH" ] || DIST_PATH="wrapper/dists"

resolve_base_dir() {
  case "$1" in
    GRADLE_USER_HOME)
      if [ -n "$GRADLE_USER_HOME" ]; then
        printf '%s' "$GRADLE_USER_HOME"
      else
        printf '%s' "$HOME/.gradle"
      fi
      ;;
    PROJECT)
      printf '%s' "$APP_HOME"
      ;;
    USER_HOME)
      printf '%s' "$HOME"
      ;;
    *)
      if [ -n "$GRADLE_USER_HOME" ]; then
        printf '%s' "$GRADLE_USER_HOME"
      else
        printf '%s' "$HOME/.gradle"
      fi
      ;;
  esac
}

BASE_DIR=$(resolve_base_dir "$DIST_BASE")
INSTALL_ROOT="$BASE_DIR/$DIST_PATH"
DIST_FILENAME=$(basename "$DIST_URL")
DIST_NAME=$(printf '%s' "$DIST_FILENAME" | sed 's/\.zip$//')
GRADLE_HOME="$INSTALL_ROOT/$DIST_NAME"

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

download() {
  URL="$1"
  DEST="$2"
  if command_exists curl; then
    curl -fL "$URL" -o "$DEST"
  elif command_exists wget; then
    wget -O "$DEST" "$URL"
  else
    echo "ERROR: Neither curl nor wget is available to download Gradle." >&2
    exit 1
  fi
}

if [ ! -d "$GRADLE_HOME" ]; then
  mkdir -p "$INSTALL_ROOT"
  TMP_DIR=$(mktemp -d "$INSTALL_ROOT/.tmp-XXXXXXXX" 2>/dev/null || mktemp -d)
  cleanup() {
    if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
      rm -rf "$TMP_DIR"
    fi
  }
  trap cleanup EXIT INT TERM

  ZIP_PATH="$TMP_DIR/dist.zip"
  echo "Downloading Gradle distribution from $DIST_URL" >&2
  download "$DIST_URL" "$ZIP_PATH"

  if ! command_exists unzip; then
    echo "ERROR: unzip is required to extract the Gradle distribution." >&2
    exit 1
  fi

  unzip -q "$ZIP_PATH" -d "$TMP_DIR"
  EXTRACTED_DIR=$(find "$TMP_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)
  if [ -z "$EXTRACTED_DIR" ]; then
    echo "ERROR: Failed to extract Gradle distribution." >&2
    exit 1
  fi

  if [ -d "$GRADLE_HOME" ]; then
    rm -rf "$GRADLE_HOME"
  fi

  mv "$EXTRACTED_DIR" "$GRADLE_HOME"
  cleanup
  trap - EXIT INT TERM
fi

GRADLE_BIN="$GRADLE_HOME/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  echo "ERROR: Gradle executable not found at $GRADLE_BIN" >&2
  exit 1
fi

exec "$GRADLE_BIN" "$@"
