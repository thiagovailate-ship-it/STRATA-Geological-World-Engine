package dev.strata.core.terrain;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.tectonics.BoundarySample;
import dev.strata.core.tectonics.PlateField;
import dev.strata.core.util.MathUtil;
import dev.strata.core.util.ValueNoise2D;

/**
 * Base terrain height before hydrology and erosion are applied.
 *
 * <p>Conceptual decomposition (see docs/ALGORITHMS.md for the full rationale):</p>
 * <pre>
 *   H = sea + (continentBase + orogen + rift/ridge + scarp + regional + local - sea) * relief
 * </pre>
 * <p>Key continuity properties (all verified by Test D):</p>
 * <ul>
 *   <li>Ocean and continent branches are <em>blended</em> across the coastline
 *       instead of switched, so coasts never cliff.</li>
 *   <li>Per-plate quantities (bias, base offset) blend with the smooth
 *       inverse-distance side weight, so plate contacts never step or crease.
 *       The V-shaped signed distance is never used for relief.</li>
 *   <li>Boundary profiles are flat-top functions of edge distance (or an
 *       intended smooth scarp step), so F1/F2 swaps at bisectors are seamless.</li>
 * </ul>
 *
 * <p>Thread-safe: immutable after construction.</p>
 */
public final class ElevationModel {

    /** Pre-erosion height plus the fields other stages need. */
    public static final class BaseSample {
        public double height;
        public double continent;
        public double orogen01;
        public final BoundarySample boundary = new BoundarySample();
    }

    // Profile widths in blocks (regional scale >> chunk scale).
    private static final double RANGE_WIDTH = 150.0;
    private static final double FOOT_WIDTH = 330.0;
    private static final double TRENCH_WIDTH = 45.0;
    private static final double RIFT_WIDTH = 70.0;
    // Mid-ocean ridges are broad swells, not narrow spikes (real width/height
    // ratio is high); 220 blocks keeps crests submarine but regionally visible.
    private static final double RIDGE_WIDTH = 220.0;
    /** Full ramp width of the transform fault step, in blocks. */
    private static final double SCARP_WIDTH = 60.0;

    private final StrataWorldConfig cfg;
    private final PlateField plates;
    private final ValueNoise2D continentNoise;
    private final ValueNoise2D regionalNoise;
    private final ValueNoise2D localNoise1;
    private final ValueNoise2D localNoise2;
    private final ValueNoise2D abyssNoise;
    private final ValueNoise2D crestNoise;

    public ElevationModel(long seed, StrataWorldConfig cfg, PlateField plates) {
        if (cfg == null || plates == null) {
            throw new IllegalArgumentException("config and plates must not be null");
        }
        this.cfg = cfg;
        this.plates = plates;
        this.continentNoise = new ValueNoise2D(seed ^ 0xC0111EL);
        this.regionalNoise = new ValueNoise2D(seed ^ 0x9E9101L);
        this.localNoise1 = new ValueNoise2D(seed ^ 0x10CA1L);
        this.localNoise2 = new ValueNoise2D(seed ^ 0x10CA2L);
        this.abyssNoise = new ValueNoise2D(seed ^ 0xA8E55L);
        this.crestNoise = new ValueNoise2D(seed ^ 0xC4E57L);
    }

    public PlateField getPlates() {
        return plates;
    }

    /**
     * Shared fault-scarp offset (blocks), tapered from the boundary over
     * {@value #SCARP_WIDTH} blocks and signed by the weighted oriented-pair
     * side field. The field can approach zero at a boundary, so multiply it
     * directly rather than taking its sign (which would introduce a step).
     * Used by both relief and stratigraphy so layer lag matches the surface.
     */
    public static double scarpOffset(double edgeDistance, double sideW, double transform01) {
        double ramp = MathUtil.smoothstep(0.0, SCARP_WIDTH, edgeDistance);
        return 8.0 * ramp * MathUtil.clamp(sideW, -1.0, 1.0) * transform01;
    }

    /**
     * Samples base height at a block coordinate.
     * Pure function of {@code (seed, x, z)}.
     */
    public void sampleBase(double x, double z, BaseSample out) {
        BoundarySample b = out.boundary;
        plates.sample(x, z, b);

        double edge = b.edgeDistance;
        int sea = cfg.seaLevel;

        // --- Continent / ocean mask: symmetric full-stencil plate-bias blend
        // (rank-swap continuous while the 3x3 stencil is fixed), plus an independent low-frequency
        // field so coasts are organic and do NOT coincide with plate edges.
        double bias = b.plateBias;
        double wave = cfg.continentWavelength;
        double contNoise = continentNoise.fbm(x / wave, z / wave, 3, 2.03, 0.5);
        double c = bias + contNoise * 1.25;
        out.continent = c;

        // --- Ocean branch (computed everywhere so coasts blend smoothly).
        double depth01 = MathUtil.clamp01(-c / 1.2);
        double hOcean = sea - 6.0 - 26.0 * depth01;
        hOcean += abyssNoise.fbm(x / 420.0, z / 420.0, 2, 2.0, 0.5) * 4.0;
        if (cfg.enableOrogen && b.divergent01 > 0.001) {
            // Mid-ocean ridge where divergent AND oceanic (smooth C gate —
            // plate booleans would jump at rank swaps, the field cannot).
            double ridgeGate = MathUtil.smoothstepDown(0.2, -0.4, c);
            double r = Math.exp(-MathUtil.sq(edge / RIDGE_WIDTH));
            hOcean += 16.0 * r * b.divergent01 * ridgeGate; // stays submarine
        }

        // --- Continent branch.
        double land01 = MathUtil.clamp01(c / 1.2);
        double hLand = sea + 3.0 + 24.0 * land01;
        hLand += b.plateBase;
        hLand += regionalNoise.fbm(x / 600.0, z / 600.0, 3, 2.02, 0.5) * 13.0;
        if (cfg.enableOrogen && b.divergent01 > 0.001) {
            // Continental rift where divergent AND continental (smooth C gate):
            // narrow graben plus uplifted shoulders.
            double riftGate = MathUtil.smoothstep(-0.2, 0.4, c);
            hLand -= 22.0 * Math.exp(-MathUtil.sq(edge / RIFT_WIDTH))
                    * b.divergent01 * riftGate;
            hLand += 18.0 * Math.exp(-MathUtil.sq((edge - 150.0) / 100.0))
                    * b.divergent01 * riftGate;
        }

        // --- Blend branches across the coastline (never a cliff).
        double h = MathUtil.lerp(hOcean, hLand, MathUtil.smoothstep(-0.08, 0.08, c));

        // --- Convergent orogeny (both branches). Range width and height class
        // follow the smooth continent field: plate booleans would jump at
        // rank swaps, the field cannot. Ranges widen into continents.
        double orogen = 0.0;
        if (cfg.enableOrogen && b.convergent01 > 0.001) {
            double contW = MathUtil.smoothstep(-0.3, 0.5, c);
            double wEff = RANGE_WIDTH * (1.0 + 0.25 * contW);
            double dd = edge / wEff;
            double prof = Math.exp(-dd * dd);
            double wide = Math.exp(-MathUtil.sq(edge / FOOT_WIDTH));
            // Height class: deep-ocean island arcs < subduction < collision.
            double maxH = 55.0 + 55.0 * MathUtil.smoothstep(-0.9, 0.5, c);
            maxH *= cfg.mountainIntensity;
            // Isotropic ridged detail masked by the range profile; the profile
            // itself (a function of distance to the boundary curve) provides
            // the along-strike elongation. Deliberately isotropic so F1/F2
            // swaps at the crest cannot seam (see docs/ALGORITHMS.md).
            double n = crestNoise.ridged(x / 160.0, z / 160.0, 4, 2.05, 0.52);
            double peak = 0.45 + 0.55 * n;
            orogen = maxH * prof * peak * b.convergent01
                    + maxH * 0.18 * wide * b.convergent01;
            // Trench / foredeep: a moat ringing the range, deep where oceanic
            // (trenches) and shallow where continental (foreland basins).
            double td = (edge - 50.0) / TRENCH_WIDTH;
            double ring = Math.exp(-td * td);
            double trenchDepth = 12.0 + 18.0 * MathUtil.smoothstepDown(0.3, -0.3, c);
            orogen -= trenchDepth * ring * b.convergent01;
            h += orogen;
        }
        out.orogen01 = MathUtil.clamp01(orogen / 60.0);

        // --- Transform fault scarp: a true smooth step (intended offset whose
        // gradient is bounded and continuous — verified by Test D).
        if (cfg.enableFaultScarps && b.transform01 > 0.001) {
            h += scarpOffset(edge, b.sideW, b.transform01);
        }

        // --- Local relief (detail octaves; amplified on ranges, muted offshore).
        double rough = cfg.roughnessIntensity * (c < 0.0 ? 0.5 : (1.0 + 0.6 * out.orogen01));
        h += localNoise1.fbm(x / 95.0, z / 95.0, 3, 2.0, 0.5) * 5.0 * rough;
        h += localNoise2.fbm(x / 23.0, z / 23.0, 2, 2.0, 0.5) * 1.6 * rough;

        // --- Global relief scaling around sea level.
        h = sea + (h - sea) * cfg.reliefIntensity;
        out.height = h;
    }
}
