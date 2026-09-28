#!/bin/bash
set -euo pipefail
PLUGIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKSPACE="$(dirname "$PLUGIN_ROOT")"
export IDEA_PROPERTIES="$WORKSPACE/dev/ide/idea.properties"
export IDEA_VM_OPTIONS="$WORKSPACE/dev/ide/idea.vmoptions"
export TMPDIR="$WORKSPACE/dev/ide/tmp"
exec "${FMTK_IDEA_PATH:-/Users/henning/Applications/IntelliJ IDEA.app}/Contents/MacOS/idea" "$WORKSPACE/dev/fixtures/plugin"
