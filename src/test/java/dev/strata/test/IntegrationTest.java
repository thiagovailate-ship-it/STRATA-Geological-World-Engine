package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;

/**
 * Test G — Integration: the full column pipeline (model -> surface plan ->
 * layer stack -> biome) over whole chunks, plus the 1.7.10 chunk block-array
 * index formula the provider relies on.
 */
public final class IntegrationTest {

    private IntegrationTest() {
    }

    public static void run() {
        runChunks(new int[][]{{0, 0}, {5, -3}, {-7, 11}, {100, 100}});
    }

    /** Small entry point used by the jar smoke check. */
    public static void runSmoke() {
        runChunks(new int[][]{{3, -5}});
    }

    private static void runChunks(int[][] chunks) {
        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        StrataWorldModel model = new StrataWorldModel(0x1E071CAL, cfg);

        int columns = 0;
        for (int c = 0; c < chunks.length; c++) {
            int cx = chunks[c][0];
            int cz = chunks[c][1];
            int minH = Integer.MAX_VALUE;
            int maxH = Integer.MIN_VALUE;
            ColumnData col = new ColumnData();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = cx * 16 + lx;
                    int z = cz * 16 + lz;
                    model.sampleColumn(x, z, col);
                    columns++;
                    Assert.inRange(col.finalHeight, 1, 255, "chunk column height");
                    if (col.finalHeight < minH) {
                        minH = col.finalHeight;
                    }
                    if (col.finalHeight > maxH) {
                        maxH = col.finalHeight;
                    }
                    Assert.check(col.layers.count >= 1, "empty stack");
                    Assert.check(col.layers.rockAt(col.finalHeight) == col.surfaceTop,
                            "stack top mismatch");
                    // Every solid block of the column must resolve a material.
                    for (int y = 1; y <= col.finalHeight; y += 5) {
                        Assert.check(col.layers.rockAt(y) != null, "null rock");
                    }
                    Assert.check(col.biome != null, "null biome");
                    // Cross-check repeat sampling through a second model.
                    if ((lx * 16 + lz) % 64 == 0) {
                        StrataWorldModel m2 = new StrataWorldModel(0x1E071CAL, cfg);
                        ColumnData col2 = new ColumnData();
                        m2.sampleColumn(x, z, col2);
                        Assert.check(col.sameAs(col2), "cross-model mismatch");
                    }
                }
            }
            System.out.println("  chunk (" + cx + ", " + cz + "): 256 columns, height ["
                    + minH + ", " + maxH + "]");
        }

        // 1.7.10 chunk block-array layout used by ChunkProviderStrata:
        // index = (localX * 16 + localZ) * 256 + y, array length 65536.
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 0; y < 256; y += 17) {
                    int idx = (lx * 16 + lz) * 256 + y;
                    Assert.inRange(idx, 0, 65535, "block index");
                }
            }
        }
        Assert.check((15 * 16 + 15) * 256 + 255 == 65535, "index formula end");
        Assert.check((0 * 16 + 0) * 256 + 0 == 0, "index formula start");
        System.out.println("  " + columns + " columns integrated; 1.7.10 index math verified");
    }
}
