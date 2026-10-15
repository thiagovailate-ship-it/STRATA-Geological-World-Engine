#!/bin/bash
# Builds the core and runs the geological laboratory (map/section renderer).
# Usage: tools/run-lab.sh <mode> <seed> <centerX> <centerZ> <size> [outPrefix] [step]
# Modes: elevation plates boundary hydro formation biome section
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source tools/toolchain.sh

bash tools/build-core.sh
"${JAVA_BIN}" -cp build/core-classes dev.strata.debug.StrataLab "$@"
