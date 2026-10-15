package dev.strata.core.erosion;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.MathUtil;

/**
 * Closed-form erosion / deposition proxies (FastScape-inspired, not a
 * landscape-evolution simulation).
 *
 * <p>Fluvial incision follows the stream-power <em>form</em>
 * {@code E = K * A^m * S^n} with {@code A} the bounded confluence proxy from
 * {@link dev.strata.core.hydrology.HydrologyModel} and {@code S} the local
 * slope; hillslope lowering adds a small quadratic slope term. Deposition is
 * allowed where slopes are gentle but drainage is strong (alluvial flats).
 * Everything is applied as a single analytic pass over sampled heights — no
 * iteration over time steps, no per-chunk simulation — which keeps generation
 * deterministic and cheap. See docs/ALGORITHMS.md for constants and
 * justification.</p>
 */
public final class ErosionModel {

    private final StrataWorldConfig cfg;

    public ErosionModel(StrataWorldConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.cfg = cfg;
    }

    /**
     * Blocks of material removed at a column. Grows with drainage area and
     * slope, shrinks with rock hardness. Always {@code >= 0} and capped.
     */
    public double lowering(double confluence01, double slope, double hardness01) {
        if (!cfg.enableErosion || cfg.erosionStrength <= 0.0) {
            return 0.0;
        }
        double area = 1.0 + 60.0 * MathUtil.clamp01(confluence01);
        double s = Math.min(Math.max(slope, 0.0), 1.2);
        double fluvial = 1.6 * Math.sqrt(area) * s;
        double hill = 2.5 * s * s;
        if (hill > 3.0) {
            hill = 3.0;
        }
        double hardness = MathUtil.clamp01(hardness01);
        double e = (fluvial + hill) * cfg.erosionStrength * (1.3 - 0.6 * hardness);
        return Math.min(e, 16.0);
    }

    /**
     * Blocks of sediment added on gentle, well-drained ground (alluvium).
     * Always {@code >= 0} and small (<= 2).
     */
    public double deposition(double confluence01, double slope) {
        if (!cfg.enableErosion || cfg.erosionStrength <= 0.0) {
            return 0.0;
        }
        if (slope > 0.10) {
            return 0.0;
        }
        double dep = 2.0 * MathUtil.smoothstep(0.25, 0.70, MathUtil.clamp01(confluence01))
                * (1.0 - slope / 0.10);
        return dep * Math.min(cfg.erosionStrength, 1.5);
    }
}
