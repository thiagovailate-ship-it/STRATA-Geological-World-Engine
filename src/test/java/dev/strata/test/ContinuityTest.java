package dev.strata.test;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.SeedMixer;

/**
 * Test D — Regional continuity, in two parts:
 *
 * <p>1) Gross-seam guard: no adjacent-column pair anywhere may jump beyond
 * generous maxima (a hash/chunk/tile seam would spike far past these).</p>
 *
 * <p>2) Border-parity check: pairs crossing chunk borders (every 16 blocks)
 * and hydrology tile borders (every 128 blocks) must show the same mean step
 * sizes as interior pairs. Steep-but-continuous geology (gorge banks, fault
 * scarps) exists in interiors too, so comparing distributions — rather than
 * demanding tiny steps everywhere — is the rigorous seamlessness test.</p>
 */
public final class ContinuityTest {

    private ContinuityTest() {
    }

    private static final class Acc {
        double sumH;
        double sumCont;
        double sumRiver;
        double sumConf;
        double sumEdge;
        double sumWet;
        double maxH;
        double maxCont;
        double maxRiver;
        double maxConf;
        double maxEdge;
        double maxWet;
        int n;

        void add(double dH, double dCont, double dRiver, double dConf, double dEdge,
                 double dWet) {
            sumH += dH;
            sumCont += dCont;
            sumRiver += dRiver;
            sumConf += dConf;
            sumEdge += dEdge;
            sumWet += dWet;
            if (dH > maxH) {
                maxH = dH;
            }
            if (dCont > maxCont) {
                maxCont = dCont;
            }
            if (dRiver > maxRiver) {
                maxRiver = dRiver;
            }
            if (dConf > maxConf) {
                maxConf = dConf;
            }
            if (dEdge > maxEdge) {
                maxEdge = dEdge;
            }
            if (dWet > maxWet) {
                maxWet = dWet;
            }
            n++;
        }
    }

    public static void run() {
        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        long seed = 0xC0111L;
        StrataWorldModel model = new StrataWorldModel(seed, cfg);
        ColumnData a = new ColumnData();
        ColumnData b = new ColumnData();

        Acc border = new Acc();
        Acc interior = new Acc();
        int n = 1500;
        for (int i = 0; i < n; i++) {
            // Border-crossing pair: alternate chunk borders and hydro borders.
            int[] p = borderPair(seed, i);
            step(model, p[0], p[1], p[2], p[3], a, b, border);
            // Interior pair: same region scale, provably off any border.
            int[] q = interiorPair(seed, i);
            step(model, q[0], q[1], q[2], q[3], a, b, interior);
        }

        System.out.println("  border   pairs=" + border.n + " meanH=" + f(border.sumH / border.n)
                + " meanRiver=" + f(border.sumRiver / border.n)
                + " meanConf=" + f(border.sumConf / border.n)
                + " maxH=" + f(border.maxH) + " maxRiver=" + f(border.maxRiver));
        System.out.println("  interior pairs=" + interior.n + " meanH=" + f(interior.sumH / interior.n)
                + " meanRiver=" + f(interior.sumRiver / interior.n)
                + " meanConf=" + f(interior.sumConf / interior.n)
                + " maxH=" + f(interior.maxH) + " maxRiver=" + f(interior.maxRiver));

        // Part 1: gross seams (any pair, anywhere).
        Assert.check(border.maxH <= 12.0 && interior.maxH <= 12.0, "height seam");
        Assert.check(border.maxCont <= 0.05 && interior.maxCont <= 0.05, "continent seam");
        Assert.check(border.maxRiver <= 0.60 && interior.maxRiver <= 0.60, "river seam");
        Assert.check(border.maxConf <= 0.60 && interior.maxConf <= 0.60, "confluence seam");
        Assert.check(border.maxEdge <= 2.0 && interior.maxEdge <= 2.0, "edge-distance seam");
        Assert.check(border.maxWet <= 0.60 && interior.maxWet <= 0.60, "wet-flat seam");

        // Part 2: border parity (borders must look like interiors on average).
        double mH = border.sumH / border.n - interior.sumH / interior.n;
        double mRiver = border.sumRiver / border.n - interior.sumRiver / interior.n;
        double mConf = border.sumConf / border.n - interior.sumConf / interior.n;
        double mCont = border.sumCont / border.n - interior.sumCont / interior.n;
        double mWet = border.sumWet / border.n - interior.sumWet / interior.n;
        Assert.check(mH <= 1.0, "chunk/tile border height excess: " + mH);
        Assert.check(mRiver <= 0.04, "border river excess: " + mRiver);
        Assert.check(mConf <= 0.04, "border confluence excess: " + mConf);
        Assert.check(mCont <= 0.01, "border continent excess: " + mCont);
        Assert.check(mWet <= 0.04, "border wet-flat excess: " + mWet);
        System.out.println("  border excess over interior: dH=" + f(mH) + " dRiver=" + f(mRiver)
                + " dConf=" + f(mConf) + " dCont=" + f(mCont) + " dWet=" + f(mWet));
    }

    private static String f(double v) {
        return String.format("%.4f", v);
    }

    private static void step(StrataWorldModel model, int x, int z, int x2, int z2,
                             ColumnData a, ColumnData b, Acc acc) {
        model.sampleColumn(x, z, a);
        model.sampleColumn(x2, z2, b);
        acc.add(Math.abs(a.finalHeight - b.finalHeight),
                Math.abs(a.continent - b.continent),
                Math.abs(a.river01 - b.river01),
                Math.abs(a.confluence01 - b.confluence01),
                Math.abs(a.boundary.edgeDistance - b.boundary.edgeDistance),
                Math.abs(a.wet01 - b.wet01));
    }

    /** Pair straddling a chunk (16) or hydro-tile (128) border. */
    private static int[] borderPair(long seed, int i) {
        boolean tile = (i % 2 == 0);
        int size = tile ? 128 : 16;
        boolean stepX = (i % 4 < 2);
        long h1 = SeedMixer.hash(seed, i, 0xD1);
        long h2 = SeedMixer.hash(seed, i, 0xD2);
        int along = (int) (SeedMixer.toSigned(h1) * 30000.0);
        int cross = (int) (SeedMixer.toSigned(h2) * 30000.0);
        int base = floorBase(cross, size) + size - 1; // last cell before the border
        if (stepX) {
            return new int[]{base, along, base + 1, along};
        }
        return new int[]{along, base, along, base + 1};
    }

    /** Pair fully inside one chunk and one hydro tile (off every border). */
    private static int[] interiorPair(long seed, int i) {
        long h1 = SeedMixer.hash(seed, i, 0xD3);
        long h2 = SeedMixer.hash(seed, i, 0xD4);
        boolean stepX = (i % 2 == 0);
        // Map into [2, 13] inside a chunk and away from tile borders too:
        // offset = tileBase + 8 + (hash % 112) keeps 8+ cells of margin... in
        // blocks, margin 8 from tile edges and (8 + k) % 16 not in {0, 15}.
        int ax = 8 + (int) (SeedMixer.toUnit(h1) * 112.0);
        int az = 8 + (int) (SeedMixer.toUnit(h2) * 112.0);
        int tx = ((int) (SeedMixer.toSigned(h1) * 200.0)) * 128;
        int tz = ((int) (SeedMixer.toSigned(h2) * 200.0)) * 128;
        int x = tx + ax;
        int z = tz + az;
        // Nudge off chunk edges: local = (8+k)%16 must avoid {0,15} for the step.
        while (chunkLocal(x) == 0 || chunkLocal(x) == 15) {
            x++;
        }
        while (chunkLocal(z) == 0 || chunkLocal(z) == 15) {
            z++;
        }
        if (stepX) {
            if (chunkLocal(x) == 15) {
                x--;
            }
            return new int[]{x, z, x + 1, z};
        }
        if (chunkLocal(z) == 15) {
            z--;
        }
        return new int[]{x, z, x, z + 1};
    }

    private static int floorBase(int v, int size) {
        int q = v / size;
        if (((v ^ size) < 0) && (q * size != v)) {
            q--;
        }
        return q * size;
    }

    private static int chunkLocal(int v) {
        int m = v % 16;
        return m < 0 ? m + 16 : m;
    }
}
