package dev.strata.core.util;

/**
 * Deterministic 2D gradient noise (Perlin style) plus fractal helpers.
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>Lattice gradients come from {@link SeedMixer}, so no permutation table
 *       is stored and any integer lattice point hashes independently.</li>
 *   <li>Quintic fade gives C2-continuous fields (no grid-aligned creases).</li>
 *   <li>Instances are immutable and stateless, hence thread-safe.</li>
 * </ul>
 */
public final class ValueNoise2D {

    // 8 unit-ish gradient directions (axes + normalized diagonals).
    private static final double[] GRAD_X = {
        1.0, -1.0, 0.0, 0.0, 0.7071067811865476, -0.7071067811865476, 0.7071067811865476, -0.7071067811865476
    };
    private static final double[] GRAD_Y = {
        0.0, 0.0, 1.0, -1.0, 0.7071067811865476, 0.7071067811865476, -0.7071067811865476, -0.7071067811865476
    };

    /** Output rescale so typical peaks approach +/-1. */
    private static final double SCALE = 1.4142135623730951;

    private final long seed;

    public ValueNoise2D(long seed) {
        this.seed = seed;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static int floorInt(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private double lattice(int ix, int iy, double dx, double dy) {
        long h = SeedMixer.hash(SeedMixer.hashSalted(SeedMixer.SALT_NOISE, seed, 0, 0), ix, iy);
        int g = (int) ((h >>> 11) & 7L);
        return GRAD_X[g] * dx + GRAD_Y[g] * dy;
    }

    /**
     * Single-octave gradient noise, approximately in {@code [-1, 1]}.
     * Continuous everywhere (C2), deterministic in {@code (seed, x, y)}.
     */
    public double noise(double x, double y) {
        int ix = floorInt(x);
        int iy = floorInt(y);
        double fx = x - ix;
        double fy = y - iy;
        double ux = fade(fx);
        double uy = fade(fy);

        double d00 = lattice(ix, iy, fx, fy);
        double d10 = lattice(ix + 1, iy, fx - 1.0, fy);
        double d01 = lattice(ix, iy + 1, fx, fy - 1.0);
        double d11 = lattice(ix + 1, iy + 1, fx - 1.0, fy - 1.0);

        double nx0 = d00 + (d10 - d00) * ux;
        double nx1 = d01 + (d11 - d01) * ux;
        return (nx0 + (nx1 - nx0) * uy) * SCALE;
    }

    /**
     * Fractal Brownian motion in roughly {@code [-1, 1]}.
     *
     * @param octaves    number of octaves (&gt;= 1)
     * @param lacunarity frequency multiplier per octave (usually ~2)
     * @param gain       amplitude multiplier per octave (usually ~0.5)
     */
    public double fbm(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double fx = x;
        double fy = y;
        for (int i = 0; i < octaves; i++) {
            sum += noise(fx, fy) * amp;
            norm += amp;
            amp *= gain;
            fx *= lacunarity;
            fy *= lacunarity;
        }
        return norm > 0.0 ? sum / norm : 0.0;
    }

    /** fBm remapped to roughly {@code [0, 1]}. */
    public double fbm01(double x, double y, int octaves, double lacunarity, double gain) {
        return fbm(x, y, octaves, lacunarity, gain) * 0.5 + 0.5;
    }

    /**
     * Ridged multifractal in roughly {@code [0, 1]}: sharp crests where the
     * underlying noise crosses zero. Used for mountain detail.
     */
    public double ridged(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double fx = x;
        double fy = y;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(noise(fx, fy));
            n *= n;
            sum += n * amp;
            norm += amp;
            amp *= gain;
            fx *= lacunarity;
            fy *= lacunarity;
        }
        return norm > 0.0 ? sum / norm : 0.0;
    }
}
