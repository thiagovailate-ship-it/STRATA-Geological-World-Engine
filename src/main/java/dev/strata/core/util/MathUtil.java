package dev.strata.core.util;

/**
 * Small math helpers. All methods are pure and side-effect free.
 * (Kept in one place so the geological formulas stay readable.)
 */
public final class MathUtil {

    private MathUtil() {
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    public static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Smoothstep interpolation: 0 below {@code edge0}, 1 above {@code edge1},
     * smooth Hermite curve between. {@code edge0} must be &lt; {@code edge1}.
     */
    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Reversed smoothstep: 1 below {@code edgeLow}, 0 above {@code edgeHigh}.
     */
    public static double smoothstepDown(double edgeHigh, double edgeLow, double x) {
        return 1.0 - smoothstep(edgeLow, edgeHigh, x);
    }

    /** Integer floor division for a positive divisor (Java 7 has no Math.floorDiv). */
    public static int floorDiv(int a, int b) {
        int q = a / b;
        return ((a ^ b) < 0 && q * b != a) ? q - 1 : q;
    }

    /** Integer floor modulus for a positive divisor (always in {@code [0, b)}). */
    public static int floorMod(int a, int b) {
        int m = a % b;
        return ((a ^ b) < 0 && m != 0) ? m + b : m;
    }

    /** Packs two signed tile coordinates into one long cache key. */
    public static long tileKey(int tx, int tz) {
        return ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
    }

    public static double sq(double v) {
        return v * v;
    }
}
