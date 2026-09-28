#!/bin/bash
set -euo pipefail
PLUGIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKSPACE="$(dirname "$PLUGIN_ROOT")"
export GRADLE_USER_HOME="$WORKSPACE/spike/cache/gradle"
export JAVA_HOME="${FMTK_JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home}"
export JAVA_TOOL_OPTIONS="-Duser.home=$WORKSPACE/spike/build-home -Djava.io.tmpdir=$WORKSPACE/spike/tmp"
export TMPDIR="$WORKSPACE/spike/tmp"
EMMY_PLUGIN="$WORKSPACE/spike/emmylua2-module-build/verified/IntelliJ-EmmyLua2"
if ! cmp -s "$PLUGIN_ROOT/emmy-analyzer.lock" "$EMMY_PLUGIN/server/darwin-arm64/analyzer.lock"; then
  echo "Prepare the pinned EmmyLua module development build before building Factorio." >&2
  exit 1
fi
python3 - "$EMMY_PLUGIN" <<'CHECK_ROUTING'
import pathlib, sys, zipfile
jars = pathlib.Path(sys.argv[1]).joinpath("lib").glob("*.jar")
if not any("com/cppcxy/ide/lsp/EmmyLuaServerProvider.class" in zipfile.ZipFile(jar).namelist() for jar in jars):
    raise SystemExit("Build the EmmyLua2 work/patched-build file-routing hook before building Factorio.")
CHECK_ROUTING
cd "$WORKSPACE/upstream/vscode-factoriomod-debug"
EXPECTED_TOOLKIT="$(cat "$PLUGIN_ROOT/toolkit.lock")"
ACTUAL_TOOLKIT="$(git rev-parse HEAD)"
if [ "$EXPECTED_TOOLKIT" != "$ACTUAL_TOOLKIT" ]; then
  echo "Toolkit revision differs from toolkit.lock; review and update the pin before building." >&2
  exit 1
fi
npm run esbuild
python3 "$PLUGIN_ROOT/scripts/bundle-notices.py"
cd "$PLUGIN_ROOT"
bash gradlew --no-daemon \
 "-PideaPath=${FMTK_IDEA_PATH:-/Users/henning/Applications/IntelliJ IDEA.app}" \
 "-Plsp4ijPath=$WORKSPACE/spike/ide/plugins/lsp4ij" \
 "-PemmyPath=$EMMY_PLUGIN" \
 "$@"
