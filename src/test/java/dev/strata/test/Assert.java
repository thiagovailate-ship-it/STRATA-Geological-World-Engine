package dev.strata.test;

/**
 * Minimal assertion helper (no JUnit dependency on purpose: the suite must
 * run anywhere a JVM exists, including this sandbox's ECJ-built classes).
 */
public final class Assert {

    private Assert() {
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void finite(double v, String what) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new AssertionError(what + " is not finite: " + v);
        }
    }

    public static void inRange(int v, int lo, int hi, String what) {
        if (v < lo || v > hi) {
            throw new AssertionError(what + " out of range [" + lo + ", " + hi + "]: " + v);
        }
    }

    public static void inRange01(double v, String what) {
        if (Double.isNaN(v) || v < 0.0 || v > 1.0) {
            throw new AssertionError(what + " out of range [0, 1]: " + v);
        }
    }
}
