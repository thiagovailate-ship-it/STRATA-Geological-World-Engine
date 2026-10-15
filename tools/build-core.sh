#!/bin/bash
# Compiles the Forge-independent part of STRATA (core engine + lab + tests)
# with ECJ or javac at language level 1.7.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source tools/toolchain.sh

OUT="build/core-classes"
mkdir -p "${OUT}" out

echo "[strata] compiler: ${JAVAC_CMD[*]}"
echo "[strata] sources: core + debug + tests (no Minecraft/Forge classes needed)"

find src/main/java/dev/strata/core src/main/java/dev/strata/debug \
     src/test/java/dev/strata/test -name '*.java' > out/core-sources.txt
wc -l out/core-sources.txt

rm -rf "${OUT}"
mkdir -p "${OUT}"

# ECJ 3.45 floor is 1.8 (it cannot target 1.7 anymore); real Forge builds use
# 1.7 bytecode from the same source (see build.gradle). Java-8-only syntax is
# additionally rejected by tools/check-legacy-api.sh.
if [ -n "${ECJ_JAR}" ]; then
    "${JAVAC_CMD[@]}" -source 1.8 -target 1.8 -nowarn -d "${OUT}" @"out/core-sources.txt"
else
    "${JAVAC_CMD[@]}" -source 8 -target 8 -nowarn -Xlint:-options -d "${OUT}" @"out/core-sources.txt"
fi

echo "[strata] core compiled OK -> ${OUT} ($(find "${OUT}" -name '*.class' | wc -l) classes)"
