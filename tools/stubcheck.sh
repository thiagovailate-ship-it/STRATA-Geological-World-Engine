#!/bin/bash
# Compiles core + Forge-layer sources against minimal Minecraft/Forge API
# stubs. This checks OUR code for syntax/type/self-consistency errors; it
# cannot verify the stub signatures against the real 1.7.10 APIs (those were
# cross-checked against RTG 1.7.10 sources instead — see docs/TESTING.md).
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source tools/toolchain.sh

OUT="build/stubcheck-classes"
mkdir -p "${OUT}" out

echo "[strata] stubcheck: core + forge + blocks against MC/Forge stubs"

find src/main/java/dev/strata/core src/main/java/dev/strata/forge \
     src/main/java/dev/strata/blocks tools/stubcheck/stubs -name '*.java' > out/stubcheck-sources.txt
wc -l out/stubcheck-sources.txt

rm -rf "${OUT}"
mkdir -p "${OUT}"

if [ -n "${ECJ_JAR}" ]; then
    "${JAVAC_CMD[@]}" -source 1.8 -target 1.8 -nowarn -d "${OUT}" @"out/stubcheck-sources.txt"
else
    "${JAVAC_CMD[@]}" -source 8 -target 8 -nowarn -Xlint:-options -d "${OUT}" @"out/stubcheck-sources.txt"
fi

echo "[strata] stubcheck OK -> ${OUT} ($(find "${OUT}" -name '*.class' | wc -l) classes)"
