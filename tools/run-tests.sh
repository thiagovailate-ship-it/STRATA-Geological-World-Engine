#!/bin/bash
# Builds the core and runs the full STRATA test suite (Tests A-H) on the JVM.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source tools/toolchain.sh

bash tools/build-core.sh
bash tools/check-legacy-api.sh

echo "[strata] running test suite..."
"${JAVA_BIN}" -cp build/core-classes dev.strata.test.TestRunner 2>&1 | tee out/test-output.txt
STATUS="${PIPESTATUS[0]}"
echo "[strata] test exit code: ${STATUS} (full log: out/test-output.txt)"
exit "${STATUS}"
