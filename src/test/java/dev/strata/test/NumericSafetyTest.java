package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.SeedMixer;

/**
 * Test E — Numeric safety: extreme coordinates and extreme (but validated)
 * configurations must never produce NaN, infinities, crashes or invalid
 * heights.
 */
public final class NumericSafetyTest {

    private NumericSafetyTest() {
    }

    public static void run() {
        // Far-field coordinates, including near the 32-bit int range edges
        // used by scaled lattice math (multiplications stay in double/long).
        int[] extremes = {0, 1, -1, 1000000, -1000000, 10000000, -10000000,
                30000000, -30000000, Integer.MAX_VALUE / 64, Integer.MIN_VALUE / 64};
        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        StrataWorldModel model = new StrataWorldModel(0xE77E45L, cfg);
        ColumnData col = new ColumnData();
        int checked = 0;
        for (int i = 0; i < extremes.length; i++) {
            for (int j = 0; j < extremes.length; j++) {
                checkColumn(model, extremes[i] + j, extremes[j] - i, col);
                checked++;
            }
        }
        // Deterministic pseudo-random far field.
        for (int i = 0; i < 3000; i++) {
            int x = (int) (SeedMixer.toSigned(SeedMixer.hash(9L, i, 1L)) * 20000000.0);
            int z = (int) (SeedMixer.toSigned(SeedMixer.hash(9L, i, 2L)) * 20000000.0);
            checkColumn(model, x, z, col);
            checked++;
        }

        // Hostile-but-validated configurations.
        StrataWorldConfig[] hostile = new StrataWorldConfig[8];
        for (int i = 0; i < hostile.length; i++) {
            hostile[i] = new StrataWorldConfig();
        }
        hostile[0].reliefIntensity = 0.0;
        hostile[1].reliefIntensity = 3.0;
        hostile[1].mountainIntensity = 3.0;
        hostile[2].erosionStrength = 3.0;
        hostile[2].drainageIntensity = 3.0;
        hostile[3].drainageIntensity = 0.0;
        hostile[3].warpAmplitude = 1200.0;
        hostile[4].plateCellSize = 384;
        hostile[4].oceanFraction = 1.0;
        hostile[5].plateCellSize = 8192;
        hostile[5].oceanFraction = 0.0;
        hostile[5].seaLevel = 120;
        hostile[6].seaLevel = 20;
        hostile[6].enableOrogen = false;
        hostile[6].enableErosion = false;
        hostile[7].warpAmplitude = 0.0;
        hostile[7].roughnessIntensity = 3.0;
        hostile[7].hydroRadiusCells = 24;
        for (int c = 0; c < hostile.length; c++) {
            hostile[c].validate();
            StrataWorldModel m = new StrataWorldModel(0xC0F19L + c, hostile[c]);
            for (int i = 0; i < 200; i++) {
                int x = (int) (SeedMixer.toSigned(SeedMixer.hash(c, i, 5L)) * 15000.0);
                int z = (int) (SeedMixer.toSigned(SeedMixer.hash(c, i, 6L)) * 15000.0);
                checkColumn(m, x, z, col);
                checked++;
            }
        }
        System.out.println("  " + checked + " columns (far-field + hostile configs): "
                + "all finite, all heights valid");
    }

    private static void checkColumn(StrataWorldModel model, int x, int z, ColumnData col) {
        model.sampleColumn(x, z, col);
        Assert.inRange(col.finalHeight, 1, 255, "finalHeight at (" + x + ", " + z + ")");
        Assert.finite(col.continent, "continent");
        Assert.finite(col.slope, "slope");
        Assert.finite(col.orogen01, "orogen");
        Assert.finite(col.confluence01, "confluence");
        Assert.finite(col.river01, "river");
        Assert.finite(col.lake01, "lake");
        Assert.finite(col.erosionLower, "erosion");
        Assert.finite(col.temp01, "temp");
        Assert.finite(col.moist01, "moist");
        Assert.finite(col.boundary.edgeDistance, "edge");
        Assert.finite(col.boundary.signedDistance, "signed");
        Assert.finite(col.faultShift, "faultShift");
        Assert.inRange01(col.confluence01, "confluence01");
        Assert.inRange01(col.river01, "river01");
        Assert.inRange01(col.lake01, "lake01");
        Assert.inRange01(col.wet01, "wet01");
        Assert.inRange01(col.temp01, "temp01");
        Assert.inRange01(col.moist01, "moist01");
    }
}
