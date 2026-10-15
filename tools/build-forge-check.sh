#!/bin/bash
# Compile-checks the Forge/Minecraft integration layer against the minimal
# 1.7.10 API stubs in src/stubs-1.7.10. This proves the integration code is
# internally coherent and only uses the API surface mirrored from the real
# 1.7.10 signatures (see docs/REFERENCES.md). It is NOT a substitute for the
# real ForgeGradle build (`./gradlew build` with repository access).
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source tools/toolchain.sh

OUT="build/forge-check-classes"
mkdir -p "${OUT}" out

find src/main/java/dev/strata -name '*.java' \
    | grep -v '/dev/strata/core/' \
    | grep -v '/dev/strata/debug/' > out/forge-sources.txt
find src/stubs-1.7.10/java -name '*.java' > out/stub-sources.txt
find src/main/java/dev/strata/core -name '*.java' > out/forge-core-sources.txt
cat out/forge-core-sources.txt out/forge-sources.txt out/stub-sources.txt > out/forge-all-sources.txt
echo "[strata] forge+core+stub sources: $(wc -l < out/forge-all-sources.txt)"

rm -rf "${OUT}"
mkdir -p "${OUT}"

# ECJ 3.45 floor is 1.8 (it cannot target 1.7 anymore); real Forge builds use
# 1.7 bytecode from the same source (see build.gradle). Java-8-only syntax is
# additionally rejected by tools/check-legacy-api.sh.
if [ -n "${ECJ_JAR}" ]; then
    "${JAVAC_CMD[@]}" -source 1.8 -target 1.8 -nowarn -d "${OUT}" @"out/forge-all-sources.txt"
else
    "${JAVAC_CMD[@]}" -source 8 -target 8 -nowarn -Xlint:-options -d "${OUT}" @"out/forge-all-sources.txt"
fi

echo "[strata] forge integration layer compile-check OK -> ${OUT}"
