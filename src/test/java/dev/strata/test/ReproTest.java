package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.SeedMixer;

/**
 * Test A — Reproducibility: same seed + same coordinates = same result,
 * regardless of query order or interleaved queries elsewhere.
 */
public final class ReproTest {

    private ReproTest() {
    }

    public static void run() {
        long[] seeds = {12345L, -987654321L, 0L, 0x5EED1234L};
        int n = 400;
        for (int s = 0; s < seeds.length; s++) {
            StrataWorldConfig cfg = new StrataWorldConfig();
            cfg.validate();
            StrataWorldModel forward = new StrataWorldModel(seeds[s], cfg);
            StrataWorldModel backward = new StrataWorldModel(seeds[s], cfg);

            int[] xs = new int[n];
            int[] zs = new int[n];
            for (int i = 0; i < n; i++) {
                // Spread over +/-20000 with exact chunk-border crossings.
                long hx = SeedMixer.hash(seeds[s], i, 0xA);
                long hz = SeedMixer.hash(seeds[s], i, 0xB);
                xs[i] = (int) (SeedMixer.toSigned(hx) * 20000.0);
                zs[i] = (int) (SeedMixer.toSigned(hz) * 20000.0);
                if (i % 7 == 0) {
                    xs[i] = (xs[i] / 16) * 16 + 15; // last column of a chunk
                }
                if (i % 11 == 0) {
                    zs[i] = (zs[i] / 16) * 16; // first column of a chunk
                }
            }

            ColumnData a = new ColumnData();
            ColumnData b = new ColumnData();
            ColumnData junk = new ColumnData();
            for (int i = 0; i < n; i++) {
                forward.sampleColumn(xs[i], zs[i], a);
                // Interleave unrelated queries to disturb any shared state.
                backward.sampleColumn(zs[i] * 3 + 5, xs[i] * 3 - 7, junk);
                backward.sampleColumn(xs[n - 1 - i], zs[n - 1 - i], b);
                // Re-sample the mirrored coordinate on the forward model.
                ColumnData c = new ColumnData();
                forward.sampleColumn(xs[n - 1 - i], zs[n - 1 - i], c);
                Assert.check(c.sameAs(b), "seed " + seeds[s] + " coord (" + xs[n - 1 - i]
                        + ", " + zs[n - 1 - i] + ") differs by query order");
            }
            // Same-instance repeat query.
            ColumnData r1 = new ColumnData();
            ColumnData r2 = new ColumnData();
            forward.sampleColumn(xs[0], zs[0], r1);
            forward.sampleColumn(xs[0], zs[0], r2);
            Assert.check(r1.sameAs(r2), "repeat query differs on seed " + seeds[s]);
        }
        System.out.println("  4 seeds x 400 coords: order-independent, bit-identical");
    }
}
