package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.terrain.BiomeKind;
import dev.strata.core.util.SeedMixer;

import java.util.HashSet;
import java.util.Set;

/**
 * Test B — Seed variation: different seeds give significantly different but
 * always valid worlds.
 */
public final class SeedVariationTest {

    private SeedVariationTest() {
    }

    public static void run() {
        long[] seeds = {7L, 123456789L, -555L};
        int n = 600;
        int[][] xs = new int[seeds.length][n];
        int[][] zs = new int[seeds.length][n];
        int[][] heights = new int[seeds.length][n];

        for (int s = 0; s < seeds.length; s++) {
            StrataWorldConfig cfg = new StrataWorldConfig();
            cfg.validate();
            StrataWorldModel model = new StrataWorldModel(seeds[s], cfg);
            ColumnData col = new ColumnData();
            Set<BiomeKind> biomes = new HashSet<BiomeKind>();
            for (int i = 0; i < n; i++) {
                // Same coordinate set for every seed (seed-independent spread).
                long hx = SeedMixer.hash(0xBEAC01L, i, 1L);
                long hz = SeedMixer.hash(0xBEAC01L, i, 2L);
                int x = (int) (SeedMixer.toSigned(hx) * 20000.0);
                int z = (int) (SeedMixer.toSigned(hz) * 20000.0);
                xs[s][i] = x;
                zs[s][i] = z;
                model.sampleColumn(x, z, col);
                heights[s][i] = col.finalHeight;
                Assert.inRange(col.finalHeight, 2, 255, "seed " + seeds[s] + " height");
                Assert.finite(col.continent, "continent");
                Assert.finite(col.slope, "slope");
                biomes.add(col.biome);
            }
            Assert.check(biomes.size() >= 5, "seed " + seeds[s]
                    + " has too little biome variety: " + biomes.size());
            System.out.println("  seed " + seeds[s] + ": " + biomes.size() + " biomes over "
                    + n + " samples");
        }

        for (int a = 0; a < seeds.length; a++) {
            for (int b = a + 1; b < seeds.length; b++) {
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    sum += Math.abs(heights[a][i] - heights[b][i]);
                }
                double mean = sum / n;
                System.out.println("  mean |dH| seeds " + seeds[a] + "/" + seeds[b] + " = "
                        + String.format("%.2f", mean) + " blocks");
                Assert.check(mean > 6.0, "seeds " + seeds[a] + " and " + seeds[b]
                        + " are suspiciously similar: " + mean);
            }
        }
    }
}
