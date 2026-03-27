#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES_FILE="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "Missing $PROPERTIES_FILE" >&2
  exit 1
fi

DISTRIBUTION_URL=$(grep '^distributionUrl=' "$PROPERTIES_FILE" | cut -d= -f2-)

if [ -z "${DISTRIBUTION_URL}" ]; then
  echo "distributionUrl is not configured in $PROPERTIES_FILE" >&2
  exit 1
fi

WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
ARCHIVE_NAME=$(basename "$DISTRIBUTION_URL")
ARCHIVE_PATH="$WRAPPER_DIR/$ARCHIVE_NAME"
DIST_DIR_NAME=$(basename "$ARCHIVE_NAME" .tar.gz)
DIST_DIR_NAME=$(printf '%s' "$DIST_DIR_NAME" | sed 's/-bin$//')
MVN_HOME="$WRAPPER_DIR/$DIST_DIR_NAME"

if [ ! -x "$MVN_HOME/bin/mvn" ]; then
  mkdir -p "$WRAPPER_DIR"
  if [ ! -f "$ARCHIVE_PATH" ]; then
    curl -fsSL "$DISTRIBUTION_URL" -o "$ARCHIVE_PATH"
  fi
  rm -rf "$MVN_HOME"
  tar -xzf "$ARCHIVE_PATH" -C "$WRAPPER_DIR"
fi

exec "$MVN_HOME/bin/mvn" "$@"
