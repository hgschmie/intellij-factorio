#!/bin/bash
set -euo pipefail
PLUGIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKSPACE="$(dirname "$PLUGIN_ROOT")"
mkdir -p "$WORKSPACE/dev/tmp" "$WORKSPACE/dev/build-home"
mkdir -p "$PLUGIN_ROOT/build/logs"
exec > >(tee "$PLUGIN_ROOT/build/logs/build-$(date +%Y%m%d-%H%M%S)-$$.log") 2>&1
export GRADLE_USER_HOME="$WORKSPACE/dev/cache/gradle"
export npm_config_cache="$WORKSPACE/dev/cache/npm"
export JAVA_HOME="${FMTK_JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home}"
export JAVA_TOOL_OPTIONS="-Duser.home=$WORKSPACE/dev/build-home -Djava.io.tmpdir=$WORKSPACE/dev/tmp"
export TMPDIR="$WORKSPACE/dev/tmp"
cd "$PLUGIN_ROOT"
exec bash gradlew --no-daemon "$@"
