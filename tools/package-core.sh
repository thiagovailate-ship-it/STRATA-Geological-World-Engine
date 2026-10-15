#!/bin/bash
# Builds the core, runs the tests, and packages real jars with the stock
# `zip` format (a jar IS a zip with a manifest): binary jar + sources jar.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source tools/toolchain.sh

bash tools/run-tests.sh

VERSION="0.1.0-alpha"
python3 - "$VERSION" <<'EOF'
import os, sys, zipfile

version = sys.argv[1]
os.makedirs("out", exist_ok=True)

manifest = ("Manifest-Version: 1.0\n"
            "Specification-Title: STRATA - Geological World Engine (core)\n"
            "Specification-Version: %s\n"
            "Implementation-Title: dev.strata.core\n"
            "Created-By: STRATA tools/package-core.sh (ECJ 1.7 target)\n" % version)

def add_tree(zf, root, arc_prefix, exts):
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in sorted(filenames):
            if exts is not None and not any(fn.endswith(e) for e in exts):
                continue
            full = os.path.join(dirpath, fn)
            arc = arc_prefix + os.path.relpath(full, root).replace(os.sep, "/")
            zf.write(full, arc)

binary = "out/strata-core-%s.jar" % version
with zipfile.ZipFile(binary, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.writestr("META-INF/MANIFEST.MF", manifest)
    add_tree(zf, "build/core-classes", "", (".class",))
print("wrote", binary, os.path.getsize(binary), "bytes")

sources = "out/strata-core-%s-sources.jar" % version
with zipfile.ZipFile(sources, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.writestr("META-INF/MANIFEST.MF", manifest)
    for root in ("src/main/java/dev/strata/core",
                 "src/main/java/dev/strata/debug",
                 "src/test/java/dev/strata/test"):
        add_tree(zf, root, "", (".java",))
print("wrote", sources, os.path.getsize(sources), "bytes")
EOF

echo "[strata] jars:"
ls -la out/*.jar
"${JAVA_BIN}" -cp "out/strata-core-${VERSION}.jar" dev.strata.test.TestRunner --smoke 2>&1 | tail -n 4
