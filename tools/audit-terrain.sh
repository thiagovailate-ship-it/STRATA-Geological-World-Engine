#!/bin/bash
# Builds the Forge-free engine and reports deterministic terrain diagnostics.
# Usage: tools/audit-terrain.sh [seed [centerX centerZ [sizeBlocks [stepBlocks [writeCsv [compareCsv]]]]]] [--stats-only]
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source tools/toolchain.sh

bash tools/build-core.sh
"${JAVA_BIN}" -cp build/core-classes dev.strata.debug.TerrainAudit "$@"
