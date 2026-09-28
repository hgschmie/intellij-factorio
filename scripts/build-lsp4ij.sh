#!/bin/bash
set -euo pipefail
PLUGIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKSPACE="$(dirname "$PLUGIN_ROOT")"
SOURCE="$WORKSPACE/upstream/lsp4ij"
CHECKOUT="$SOURCE/build/checkouts/patched"
mkdir -p "$SOURCE/build/logs" "$WORKSPACE/dev/tmp" "$WORKSPACE/dev/build-home"
exec > >(tee "$SOURCE/build/logs/factorio-build-$(date +%Y%m%d-%H%M%S).log") 2>&1
if [ ! -d "$CHECKOUT" ]; then
  git -C "$SOURCE" worktree add "$CHECKOUT" work/factorio-build
fi
EXPECTED="$(cat "$PLUGIN_ROOT/lsp4ij.lock")"
if [ "$(git -C "$CHECKOUT" rev-parse HEAD)" != "$EXPECTED" ] || [ -n "$(git -C "$CHECKOUT" status --porcelain --untracked-files=no)" ]; then
  echo "LSP4IJ build checkout must match lsp4ij.lock without tracked edits." >&2
  exit 1
fi
IDEA="${FMTK_IDEA_PATH:-/Users/henning/Applications/IntelliJ IDEA.app}"
export GRADLE_USER_HOME="$WORKSPACE/dev/cache/gradle"
export JAVA_HOME="${FMTK_GRADLE_JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home}"
export JAVA_TOOL_OPTIONS="-Duser.home=$WORKSPACE/dev/build-home -Djava.io.tmpdir=$WORKSPACE/dev/tmp"
export TMPDIR="$WORKSPACE/dev/tmp"
cd "$CHECKOUT"
bash gradlew --no-daemon --no-configuration-cache \
  "-PideaPath=$IDEA" -PplatformVersion=2026.2 -PbuildJavaVersion=25 \
  -PpluginVersion=0.21.1-SNAPSHOT-factorio-patched -PpluginSinceBuild=262 \
  "-Dorg.gradle.java.installations.paths=$IDEA/Contents/jbr/Contents/Home" \
  test --tests '*DAPBreakpointHandlerBaseTest' buildPlugin
python3 - "$CHECKOUT" "$WORKSPACE/dev/plugins" "$EXPECTED" "$SOURCE/build/distributions" <<'PREPARE'
from pathlib import Path
import shutil,sys,zipfile
checkout,plugins,revision,dist=Path(sys.argv[1]),Path(sys.argv[2]),sys.argv[3],Path(sys.argv[4])
archive=checkout/'build/distributions/lsp4ij-0.21.1-SNAPSHOT-factorio-patched.zip'
dist.mkdir(parents=True,exist_ok=True)
shutil.copy2(archive,dist/archive.name)
# This is the shared build dependency, not an installed user IDE profile.
target=plugins/'lsp4ij'
if target.exists():shutil.rmtree(target)
with zipfile.ZipFile(archive) as z:z.extractall(plugins)
(target/'source.lock').write_text(revision+'\n')
print('Prepared LSP4IJ:',target)
print('Installable ZIP:',dist/archive.name)
PREPARE
