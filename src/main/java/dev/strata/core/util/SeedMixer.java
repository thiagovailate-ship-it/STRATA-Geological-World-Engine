package dev.strata.core.util;

/**
 * Deterministic integer hashing for the STRATA core.
 *
 * <p>Every procedural decision in the engine derives from
 * {@code (worldSeed, salt, x, z)} through these functions. There is no shared
 * mutable {@code Random} anywhere in the core, so a coordinate always yields
 * the same value no matter which chunk was generated first or in which order
 * samples were taken (see Test A).</p>
 *
 * <p>The mixer is the SplitMix64 finalizer (public-domain style integer
 * avalanche, after Steele, Lea &amp; Flood). Only explicit 64-bit overflow
 * arithmetic is used, so results are identical on every JVM.</p>
 */
public final class SeedMixer {

    // Distinct per-subsystem salts. Adding a new subsystem? Add a new salt so
    // its hash stream can never collide with an existing one.
    public static final int SALT_PLATE_CENTER = 0x1A7E;
    public static final int SALT_PLATE_TYPE = 0x0BE5;
    public static final int SALT_PLATE_MOTION = 0xF00D;
    public static final int SALT_PLATE_BASE = 0x8A5E;
    public static final int SALT_NOISE = 0x0015E;
    public static final int SALT_HYDRO_TIE = 0x0D40;
    public static final int SALT_FORM = 0xF047;
    public static final int SALT_LAYER = 0x1A4E;
    public static final int SALT_VOLC = 0xB016;
    public static final int SALT_SURFACE = 0x5EED;
    public static final int SALT_ORE = 0x00E5;
    public static final int SALT_BEDROCK = 0xBED4;

    private SeedMixer() {
    }

    /** SplitMix64 finalizer avalanche. */
    public static long mix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Hashes {@code (seed, a, b)} into a well-mixed 64-bit value. */
    public static long hash(long seed, long a, long b) {
        long h = seed + 0x9E3779B97F4A7C15L;
        h = mix64(h + a * 0xBF58476D1CE4E5B9L);
        h = mix64(h + b * 0x94D049BB133111EBL);
        return h;
    }

    /** Salted variant that keeps subsystem streams independent. */
    public static long hashSalted(int salt, long seed, long a, long b) {
        return hash(seed ^ (salt * 0x9E3779B97F4A7C15L), a, b);
    }

    /** Maps hash bits to a double in {@code [0, 1)}. */
    public static double toUnit(long h) {
        return (double) (h >>> 11) * (1.0 / 9007199254740992.0);
    }

    /** Maps hash bits to a double in {@code [-1, 1)}. */
    public static double toSigned(long h) {
        return toUnit(h) * 2.0 - 1.0;
    }

    /** Maps hash bits to an int in {@code [0, bound)}. {@code bound} must be positive. */
    public static int toIntBounded(long h, int bound) {
        int v = (int) (toUnit(h) * bound);
        return v >= bound ? bound - 1 : v;
    }
}
