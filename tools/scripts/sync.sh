#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

APP_NAME="${APP_NAME:-je-util}"
INSTALL_DIR="${INSTALL_DIR:-${HOME}/.local/}"
GRADLE_TASKS="${GRADLE_TASKS:-clean assemble installDist}"
TARGET_BIN="${INSTALL_DIR}/${APP_NAME}"
SOURCE_BIN="${REPO_ROOT}/build/install/${APP_NAME}/bin/${APP_NAME}"

echo "Building project..."
"${REPO_ROOT}/gradlew" -p "${REPO_ROOT}" ${GRADLE_TASKS}

if [[ ! -f "${SOURCE_BIN}" ]]; then
  echo "Build completed, but binary was not found: ${SOURCE_BIN}" >&2
  exit 1
fi

mkdir -p "${INSTALL_DIR}"
ln -sf "${SOURCE_BIN}" "${TARGET_BIN}"
chmod +x "${TARGET_BIN}"

echo "Installed '${APP_NAME}' to '${TARGET_BIN}'"

if [[ ":${PATH}:" != *":${INSTALL_DIR}:"* ]]; then
  echo "Add '${INSTALL_DIR}' to PATH to run '${APP_NAME}' from anywhere."
  echo "Example for zsh: echo 'export PATH=\"${INSTALL_DIR}:\$PATH\"' >> ~/.zshrc && source ~/.zshrc"
fi
