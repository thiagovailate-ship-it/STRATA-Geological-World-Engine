package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.SeedMixer;

/**
 * Test F — Geological coupling: tectonic regimes must measurably shape the
 * relief (convergent land higher than stable land higher than ocean floor;
 * mid-ocean ridges higher than abyssal plains).
 */
public final class CouplingTest {

    private CouplingTest() {
    }

    public static void run() {
        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        StrataWorldModel model = new StrataWorldModel(424242L, cfg);
        ColumnData col = new ColumnData();

        double convSum = 0.0;
        int convN = 0;
        double stabSum = 0.0;
        int stabN = 0;
        double oceanSum = 0.0;
        int oceanN = 0;
        double ridgeSum = 0.0;
        int ridgeN = 0;
        double abyssSum = 0.0;
        int abyssN = 0;

        int n = 4000;
        for (int i = 0; i < n; i++) {
            int x = (int) (SeedMixer.toSigned(SeedMixer.hash(424242L, i, 1L)) * 12000.0);
            int z = (int) (SeedMixer.toSigned(SeedMixer.hash(424242L, i, 2L)) * 12000.0);
            model.sampleColumn(x, z, col);
            if (!col.ocean && col.continent > 0.2) {
                if (col.boundary.convergent01 > 0.5) {
                    convSum += col.finalHeight;
                    convN++;
                } else if (col.boundary.boundary01 < 0.2) {
                    stabSum += col.finalHeight;
                    stabN++;
                }
            }
            if (col.ocean) {
                oceanSum += col.finalHeight;
                oceanN++;
                if (col.boundary.divergent01 > 0.4 && col.continent < -0.3) {
                    ridgeSum += col.finalHeight;
                    ridgeN++;
                } else if (col.boundary.boundary01 < 0.2) {
                    abyssSum += col.finalHeight;
                    abyssN++;
                }
            }
        }

        System.out.println("  samples: convLand=" + convN + " stableLand=" + stabN
                + " ocean=" + oceanN + " ridge=" + ridgeN + " abyss=" + abyssN);
        Assert.check(convN >= 100 && stabN >= 100 && oceanN >= 100,
                "regime sampling failed (world too uniform?)");

        double convMean = convSum / convN;
        double stabMean = stabSum / stabN;
        double oceanMean = oceanSum / oceanN;
        System.out.println("  mean heights: convergent=" + String.format("%.1f", convMean)
                + " stable=" + String.format("%.1f", stabMean)
                + " ocean=" + String.format("%.1f", oceanMean));
        Assert.check(convMean > stabMean + 15.0,
                "convergent land not higher than stable land");
        Assert.check(stabMean > oceanMean + 20.0, "stable land not above ocean floor");

        if (ridgeN >= 40 && abyssN >= 40) {
            double ridgeMean = ridgeSum / ridgeN;
            double abyssMean = abyssSum / abyssN;
            System.out.println("  mid-ocean ridge=" + String.format("%.1f", ridgeMean)
                    + " abyss=" + String.format("%.1f", abyssMean));
            Assert.check(ridgeMean > abyssMean + 1.5, "ridge not above abyssal plain");
        } else {
            System.out.println("  (ridge/abyss sample too small for this seed; skipped)");
        }
    }
}
