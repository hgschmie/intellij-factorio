#!/bin/bash
set -euo pipefail
export IDEA_PROPERTIES='/Users/henning/ai/fmtk/spike/plugin-ide/idea.properties'
export IDEA_VM_OPTIONS='/Users/henning/ai/fmtk/spike/plugin-ide/idea.vmoptions'
export TMPDIR='/Users/henning/ai/fmtk/spike/plugin-ide/tmp'
exec '/Users/henning/Applications/IntelliJ IDEA.app/Contents/MacOS/idea' '/Users/henning/ai/fmtk/spike/plugin-fixture'
