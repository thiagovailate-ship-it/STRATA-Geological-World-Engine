package dev.strata.test;

import dev.strata.core.hydrology.HydrologyModel;

/** Regression for the half-cell phase between hydrology values and block coordinates. */
public final class HydrologyGridAlignmentTest {

    private HydrologyGridAlignmentTest() {
    }

    public static void run() {
        int[] cellSizes = {4, 8, 16};
        int checked = 0;
        for (int c = 0; c < cellSizes.length; c++) {
            int cell = cellSizes[c];
            for (int index = -32; index <= 32; index++) {
                double center = (index + 0.5) * cell;
                double mapped = HydrologyModel.blockToCellCenterIndex(center, cell);
                Assert.check(Math.abs(mapped - index) < 1.0e-12,
                        "cell center " + center + " (cell=" + cell + ") mapped to " + mapped
                                + " instead of index " + index);

                double boundary = (index + 1.0) * cell;
                double midpoint = HydrologyModel.blockToCellCenterIndex(boundary, cell);
                Assert.check(Math.abs(midpoint - (index + 0.5)) < 1.0e-12,
                        "cell midpoint " + boundary + " (cell=" + cell + ") mapped to " + midpoint);
                checked += 2;
            }
        }
        boolean rejected = false;
        try {
            HydrologyModel.blockToCellCenterIndex(0.0, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        Assert.check(rejected, "zero cell size must be rejected");
        System.out.println("  " + checked + " positive/negative center and midpoint mappings; invalid size rejected");
    }
}
