#!/bin/bash
# Static guards for the 1.7.10 legacy contract:
#  1) the pure core must not import any Minecraft/Forge class;
#  2) no Java 8+ language/library constructs may sneak in (ECJ -source 1.7
#     already rejects syntax; this scan catches newer *library* calls).
set -euo pipefail
cd "$(dirname "$0")/.."

FAIL=0

echo "[strata] check 1/2: core purity (no net.minecraft/cpw/forge imports in core+debug+tests)..."
if grep -rn -E "import (net\.minecraft|net\.minecraftforge|cpw\.mods)" \
        src/main/java/dev/strata/core src/main/java/dev/strata/debug src/test/java 2>/dev/null; then
    echo "[strata] FAIL: core layer references game classes." >&2
    FAIL=1
else
    echo "[strata] core purity OK."
fi

echo "[strata] check 2/2: no post-Java-7 API tokens..."
# Tokens that only exist on Java 8+ runtimes/APIs. Streams/lambdas would also
# fail the ECJ 1.7 build; this list targets newer library methods.
PATTERN='List\.of\(|Set\.of\(|Map\.of\(|Map\.entry\(|\.isBlank\(|\.strip\(\)|\.stripLeading|\.stripTrailing|\.repeat\(|Optional\.isEmpty|String\.isEmpty\(\)|InputStream\.readAllBytes|readString\(|writeString\(|Files\.mismatch|Striped64|LongAdder|CompletableFuture|BiFunction|Function<|Consumer<|Supplier<|Predicate<|StringJoiner|IntStream|LongStream|DoubleStream|Arrays\.stream\(|Collection\.stream\(|\.stream\(\)|->|::'
# Full-line comments (* and //) are excluded: "a -> b" in prose is not a lambda.
if grep -rn -E "${PATTERN}" src/main/java src/test/java src/stubs-1.7.10 2>/dev/null \
    | grep -v -E ':[[:space:]]*(\*|//)'; then
    echo "[strata] FAIL: post-Java-7 API usage detected." >&2
    FAIL=1
else
    echo "[strata] legacy API surface OK."
fi

exit "${FAIL}"
