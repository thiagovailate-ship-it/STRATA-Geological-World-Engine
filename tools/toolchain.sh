#!/bin/bash
# Shared helper: locates java + a Java compiler (javac preferred, ECJ fallback).
# Source this file from the other tools/*.sh scripts.
# Sets: JAVA_BIN, JAVAC_CMD (array), ECJ_JAR (maybe empty).

TOOLCHAIN_DIR="${HOME}/.strata-toolchain"

resolve_java_bin() {
    if [ -f "${TOOLCHAIN_DIR}/java-bin.txt" ]; then
        cat "${TOOLCHAIN_DIR}/java-bin.txt"
    elif command -v java >/dev/null 2>&1; then
        command -v java
    else
        python3 -c 'import jdk4py,os; print(os.path.join(os.path.dirname(jdk4py.__file__),"java-runtime","bin","java"))'
    fi
}

JAVA_BIN="$(resolve_java_bin)"
if [ ! -x "${JAVA_BIN}" ]; then
    echo "[strata] ERROR: no java launcher found. Run tools/setup-toolchain.sh first." >&2
    exit 1
fi

JAVAC_CMD=()
ECJ_JAR=""
if command -v javac >/dev/null 2>&1; then
    JAVAC_CMD=(javac)
elif [ -f "${TOOLCHAIN_DIR}/ecj.jar" ]; then
    ECJ_JAR="${TOOLCHAIN_DIR}/ecj.jar"
    JAVAC_CMD=("${JAVA_BIN}" -jar "${ECJ_JAR}")
else
    echo "[strata] ERROR: no javac and no ecj.jar. Run tools/setup-toolchain.sh first." >&2
    exit 1
fi
