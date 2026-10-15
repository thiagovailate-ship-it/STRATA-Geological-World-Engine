package dev.strata.core.terrain;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.MathUtil;
import dev.strata.core.util.ValueNoise2D;

/**
 * Regional temperature / moisture fields.
 *
 * <p>An infinite procedural world has no latitude, so climate provinces come
 * from low-frequency noise: temperature falls with elevation (lapse rate) and
 * moisture rises near coasts, rivers and lakes. Both fields are continuous and
 * deterministic in {@code (seed, x, z, height)}.</p>
 *
 * <p>Explicit simplification (v1): no prevailing-wind rain shadows; orographic
 * effects arrive through the river/lake moisture bonus only.</p>
 */
public final class ClimateModel {

    public static final class ClimateSample {
        public double temp01;
        public double moist01;
    }

    private final StrataWorldConfig cfg;
    private final ValueNoise2D tempNoise;
    private final ValueNoise2D moistNoise;

    public ClimateModel(long seed, StrataWorldConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.cfg = cfg;
        this.tempNoise = new ValueNoise2D(seed ^ 0x7E9A1L);
        this.moistNoise = new ValueNoise2D(seed ^ 0x90157L);
    }

    public void sample(double x, double z, double height, double continent,
                       double river01, double lake01, double wet01, ClimateSample out) {
        double t = 0.60 + 0.40 * tempNoise.fbm(x / 3000.0 + 11.3, z / 3000.0 - 5.2, 2, 2.0, 0.5);
        t -= 0.0016 * (height - cfg.seaLevel); // elevation lapse
        out.temp01 = MathUtil.clamp01(t);

        double coast = MathUtil.clamp01(1.0 - Math.abs(continent) / 0.5);
        double m = 0.45 + 0.55 * moistNoise.fbm(x / 2600.0 - 7.7, z / 2600.0 + 3.9, 3, 2.0, 0.5);
        m += 0.20 * coast + 0.30 * river01 + 0.25 * lake01 + 0.20 * wet01;
        out.moist01 = MathUtil.clamp01(m);
    }
}
