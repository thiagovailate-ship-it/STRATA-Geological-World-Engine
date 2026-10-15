package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.SeedMixer;

/**
 * Test C — Terrain bounds: heights always land inside the valid 1.7.10
 * vertical range, layer stacks always answer, water tops are sane.
 */
public final class BoundsTest {

    private BoundsTest() {
    }

    public static void run() {
        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        long seed = 0xB001D5L;
        StrataWorldModel model = new StrataWorldModel(seed, cfg);
        ColumnData col = new ColumnData();

        int n = 20000;
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            long hx = SeedMixer.hash(seed, i, 0xC);
            long hz = SeedMixer.hash(seed, i, 0xD);
            int x = (int) (SeedMixer.toSigned(hx) * 50000.0);
            int z = (int) (SeedMixer.toSigned(hz) * 50000.0);
            model.sampleColumn(x, z, col);

            Assert.inRange(col.finalHeight, 1, 255, "finalHeight at (" + x + ", " + z + ")");
            Assert.inRange(col.baseHeight, 1, 255, "baseHeight");
            if (col.finalHeight < minH) {
                minH = col.finalHeight;
            }
            if (col.finalHeight > maxH) {
                maxH = col.finalHeight;
            }
            Assert.check(col.layers.count >= 1, "empty layer stack");
            Assert.check(col.layers.count <= 12, "layer stack overflow");
            for (int y = 1; y <= col.finalHeight; y += 7) {
                Assert.check(col.layers.rockAt(y) != null, "null rock at y=" + y);
            }
            Assert.check(col.layers.rockAt(col.finalHeight) == col.surfaceTop,
                    "stack top is not the planned surface");
            Assert.check(col.biome != null && col.formation != null, "null biome/formation");
            Assert.check(col.surfaceTop != null && col.surfaceFiller != null, "null surface");
            Assert.check(col.soilDepth >= 0 && col.soilDepth <= 8, "bad soilDepth");
            if (col.lakeWaterTop >= 0) {
                Assert.check(col.lakeWaterTop > col.finalHeight, "lake water below surface");
                Assert.inRange(col.lakeWaterTop, 1, 255, "lakeWaterTop");
            }
            Assert.check(col.slope >= 0.0, "negative slope");
            Assert.finite(col.slope, "slope");
            Assert.finite(col.erosionLower, "erosion");
            Assert.check(col.erosionLower >= 0.0, "negative erosion");
        }
        System.out.println("  " + n + " columns: height range [" + minH + ", " + maxH
                + "], all stacks/biomes/surfaces valid");
        Assert.check(minH < 63 && maxH > 63, "world has no land AND ocean");
    }
}
