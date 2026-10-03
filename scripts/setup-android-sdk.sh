#!/usr/bin/env bash
#
# Installs the Android SDK components this project needs into $ANDROID_HOME
# (default: /opt/android-sdk) and writes local.properties for Gradle.
#
# Safe to re-run: already-installed pieces are skipped and the downloaded
# command-line tools zip is cached under ~/.cache/llgl-android-sdk.
#
# Requirements: java (JDK 17+), curl, unzip.
#
# Usage:
#   bash scripts/setup-android-sdk.sh
#   ANDROID_HOME=/some/where bash scripts/setup-android-sdk.sh
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/llgl-android-sdk"

# Pinned versions. Keep ANDROID_PLATFORM in sync with compileSdk/compileSdkMinor in
# app/build.gradle.kts, and BUILD_TOOLS_VERSION with the version the Android Gradle Plugin
# uses by default (it downloads it itself otherwise).
CMDLINE_TOOLS_REV="16111833"
CMDLINE_TOOLS_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"
ANDROID_PLATFORM="android-37.2"
BUILD_TOOLS_VERSION="36.0.0"

ZIP_NAME="commandlinetools-linux-${CMDLINE_TOOLS_REV}_latest.zip"
ZIP_URL="https://dl.google.com/android/repository/${ZIP_NAME}"
ZIP_PATH="${CACHE_DIR}/${ZIP_NAME}"
SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log() { printf '\n==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not installed"; }

require java
require curl
require unzip

# 1. Command-line tools (provides sdkmanager).
if [ -x "$SDKMANAGER" ]; then
  log "cmdline-tools already present at ${ANDROID_HOME}/cmdline-tools/latest"
else
  mkdir -p "$CACHE_DIR" "${ANDROID_HOME}/cmdline-tools"
  if [ -f "$ZIP_PATH" ] && echo "${CMDLINE_TOOLS_SHA1}  ${ZIP_PATH}" | sha1sum -c --status; then
    log "Using cached ${ZIP_PATH}"
  else
    log "Downloading ${ZIP_URL}"
    curl -fsSL --retry 3 --retry-delay 2 -o "${ZIP_PATH}.part" "$ZIP_URL"
    mv "${ZIP_PATH}.part" "$ZIP_PATH"
    echo "${CMDLINE_TOOLS_SHA1}  ${ZIP_PATH}" | sha1sum -c --status \
      || { rm -f "$ZIP_PATH"; die "checksum mismatch for ${ZIP_NAME}"; }
  fi

  log "Extracting cmdline-tools into ${ANDROID_HOME}/cmdline-tools/latest"
  tmp="$(mktemp -d)"
  unzip -q "$ZIP_PATH" -d "$tmp"
  rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
  mv "${tmp}/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# 2. Licenses. `yes` is killed by SIGPIPE once sdkmanager exits; that is expected.
log "Accepting SDK licenses"
(yes || true) | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null

# 3. SDK packages.
log "Installing platform-tools, platforms;${ANDROID_PLATFORM}, build-tools;${BUILD_TOOLS_VERSION}"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" --install \
  "platform-tools" \
  "platforms;${ANDROID_PLATFORM}" \
  "build-tools;${BUILD_TOOLS_VERSION}"

# 4. Point Gradle at the SDK (local.properties is git-ignored).
if [ ! -f "${REPO_ROOT}/local.properties" ]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "${REPO_ROOT}/local.properties"
  log "Wrote ${REPO_ROOT}/local.properties (sdk.dir=${ANDROID_HOME})"
fi

log "Done. For this shell: export ANDROID_HOME=${ANDROID_HOME}"
