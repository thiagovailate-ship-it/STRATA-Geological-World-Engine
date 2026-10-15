#!/bin/bash
# STRATA toolchain bootstrap for environments WITHOUT a JDK (e.g. this sandbox).
#
# Real-world developers do NOT need this script: install JDK 8 and run
# `./gradlew build`. This script exists so the pure-Java geological core can
# still be compiled, tested and benchmarked where no JDK is installed:
#   * java runtime : jdk4py wheel from PyPI (Temurin JRE, java launcher only)
#   * javac        : ECJ batch compiler jar (Eclipse JDT, EPL-2.0) fetched from
#                    the @vscjava/java-language-server npm tarball, which embeds
#                    org.eclipse.jdt.core.compiler.batch (pure-Java compiler).
# Result: $HOME/.strata-toolchain/{ecj.jar,java-home.txt} + jdk4py in pip.
set -euo pipefail

TOOLCHAIN_DIR="${HOME}/.strata-toolchain"
mkdir -p "${TOOLCHAIN_DIR}"

have_java=false
if command -v java >/dev/null 2>&1; then have_java=true; fi

# 1) Ensure a `java` launcher exists.
if [ "${have_java}" = false ]; then
    if python3 -c "import jdk4py" >/dev/null 2>&1; then
        have_java=true
    else
        echo "[strata] installing jdk4py (JRE) from PyPI..."
        pip3 install --break-system-packages jdk4py
        have_java=true
    fi
fi

# Resolve JAVA_BIN.
if command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
else
    JAVA_BIN="$(python3 -c 'import jdk4py,os; print(os.path.join(os.path.dirname(jdk4py.__file__),"java-runtime","bin","java"))')"
fi
echo "${JAVA_BIN}" > "${TOOLCHAIN_DIR}/java-bin.txt"
"${JAVA_BIN}" -version 2>&1 | head -n 2

# 2) Ensure ECJ is available.
if [ -f "${TOOLCHAIN_DIR}/ecj.jar" ]; then
    echo "[strata] ecj.jar present: ${TOOLCHAIN_DIR}/ecj.jar"
else
    echo "[strata] fetching ECJ from the npm JDT language-server tarball..."
    TMPD="$(mktemp -d)"
    TARBALL_URL="https://registry.npmjs.org/@vscjava/java-language-server/-/java-language-server-0.1.2.tgz"
    curl -sL --max-time 180 -o "${TMPD}/jdtls.tgz" "${TARBALL_URL}"
    tar xzf "${TMPD}/jdtls.tgz" -C "${TMPD}"
    ECJ_SRC="$(ls "${TMPD}"/package/server/plugins/org.eclipse.jdt.core.compiler.batch_*.jar | head -n 1)"
    cp "${ECJ_SRC}" "${TOOLCHAIN_DIR}/ecj.jar"
    rm -rf "${TMPD}"
    echo "[strata] ecj.jar saved."
fi

"${JAVA_BIN}" -jar "${TOOLCHAIN_DIR}/ecj.jar" -version 2>&1 | head -n 2
echo "[strata] toolchain ready: ${TOOLCHAIN_DIR}"
