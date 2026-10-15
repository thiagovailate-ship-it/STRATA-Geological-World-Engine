package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.SeedMixer;

/**
 * Test H — Performance: measures real sampling cost (cold tiles, warm cache,
 * whole contiguous chunks) and fails on pathological slowdowns. Budgets are
 * generous; the recorded numbers in docs/TESTING.md are the real result.
 */
public final class PerfTest {

    private PerfTest() {
    }

    public static void run() {
        System.out.println("  machine: " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + ", cores="
                + Runtime.getRuntime().availableProcessors() + ", jvm="
                + System.getProperty("java.version"));
        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        StrataWorldModel model = new StrataWorldModel(0x9E4F, cfg);
        ColumnData col = new ColumnData();

        // Warmup (JIT + first tiles).
        for (int i = 0; i < 500; i++) {
            model.sampleColumn(i * 3 - 700, i * 5 + 100, col);
        }

        // Cold-ish spread: defeats the 64-tile LRU on purpose.
        long t0 = System.nanoTime();
        int spread = 4000;
        for (int i = 0; i < spread; i++) {
            int x = (int) (SeedMixer.toSigned(SeedMixer.hash(1L, i, 1L)) * 20000.0);
            int z = (int) (SeedMixer.toSigned(SeedMixer.hash(1L, i, 2L)) * 20000.0);
            model.sampleColumn(x, z, col);
        }
        double spreadMsPerCol = (System.nanoTime() - t0) / 1000000.0 / spread;

        // Warm: tight region, cache-friendly.
        t0 = System.nanoTime();
        int warm = 4000;
        for (int i = 0; i < warm; i++) {
            model.sampleColumn(1000 + (i % 64), -2000 + (i / 64), col);
        }
        double warmMsPerCol = (System.nanoTime() - t0) / 1000000.0 / warm;

        // Whole contiguous chunks (the provider's real workload).
        t0 = System.nanoTime();
        int chunks = 4;
        ColumnData c2 = new ColumnData();
        for (int c = 0; c < chunks; c++) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    model.sampleColumn(c * 16 + lx + 40, lz - 60, c2);
                }
            }
        }
        double msPerChunk = (System.nanoTime() - t0) / 1000000.0 / chunks;

        System.out.println("  spread (cold tiles):  " + String.format("%.3f", spreadMsPerCol)
                + " ms/column");
        System.out.println("  warm (cached tiles):  " + String.format("%.3f", warmMsPerCol)
                + " ms/column");
        System.out.println("  contiguous chunks:    " + String.format("%.1f", msPerChunk)
                + " ms/chunk (" + String.format("%.3f", msPerChunk / 256.0) + " ms/column)");
        System.out.println("  hydro cache: hits=" + model.getHydrologyModel().getCacheHits()
                + " misses=" + model.getHydrologyModel().getCacheMisses()
                + " size=" + model.getHydrologyModel().getCacheSize());

        // The rank-invariant nine-center field and full pair-side blend cost
        // more on cold, spatially scattered samples; the measured gameplay
        // workload (contiguous chunks) is reported separately above.
        Assert.check(spreadMsPerCol < 4.0, "spread sampling too slow: " + spreadMsPerCol);
        Assert.check(warmMsPerCol < 1.0, "warm sampling too slow: " + warmMsPerCol);
        Assert.check(msPerChunk < 120.0, "chunk sampling too slow: " + msPerChunk);
    }
}
