package dev.strata.core.hydrology;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.terrain.ElevationModel;
import dev.strata.core.util.BoundedCache;
import dev.strata.core.util.MathUtil;
import dev.strata.core.util.SeedMixer;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Local drainage model: D8 flow directions plus bounded-radius confluence.
 *
 * <p>How it works (see docs/ALGORITHMS.md): base heights are sampled on a
 * coarse grid (default 8 blocks/cell); micro-depressions are removed with a
 * Priority-Flood fill (Barnes) so drainage is defined everywhere; each cell
 * drains to its lowest of 8 neighbours on the filled surface (D8,
 * deterministic micro-jitter breaks flat ties outside filled areas); then
 * every patch cell is traced downstream and inner cells count how many
 * upstream cells within a <em>uniform Chebyshev radius</em> drain through
 * them. High confluence = river; fill depth with supply = lakes (rivers
 * route through them, as in nature); oceans are sinks, so rivers naturally
 * end at the sea.</p>
 *
 * <p>Honest limitations (v1, documented, not hidden):</p>
 * <ul>
 *   <li>The upstream radius is bounded (default 12 cells = 96 blocks), so this
 *       is a <em>local confluence proxy</em>, not global watershed
 *       accumulation. Long river systems still emerge as chains of
 *       high-confluence cells, but discharge does not accumulate over
 *       thousands of blocks.</li>
 *   <li>Drainage is computed from <em>base</em> (pre-carve) heights to avoid a
 *       feedback loop; carving does not re-route the rivers that caused it.</li>
 * </ul>
 *
 * <p>What it guarantees: the result is a pure function of
 * {@code (seed, x, z)} — no tile keeps state that a neighbour depends on, so
 * rivers are identical no matter which chunk generated first and continuous
 * across chunk <em>and</em> tile borders (verified by Test D).</p>
 */
public final class HydrologyModel {

    public static final class HydroSample {
        /** Upstream confluence in [0,1] (bounded-radius proxy for discharge). */
        public double confluence01;
        /** River mask in [0,1] (thresholded confluence). */
        public double river01;
        /** Lake mask in [0,1] (supplied pits only). */
        public double lake01;
        /** Wet-flat mask in [0,1] (flat + well-drained, holds no deep water). */
        public double wet01;
        /** True when the nearest cell is a land pit. */
        public boolean pit;
    }

    /** D8 neighbour offsets, fixed order (jitter decides ties). */
    private static final int[] NDX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] NDZ = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int WALK_CAP = 64;

    private static final class Tile {
        final float[] conf;
        final float[] lake;
        final float[] wet;
        final boolean[] pit;

        Tile(int n) {
            conf = new float[n * n];
            lake = new float[n * n];
            wet = new float[n * n];
            pit = new boolean[n * n];
        }
    }

    private final long seed;
    private final StrataWorldConfig cfg;
    private final ElevationModel base;
    private final BoundedCache<Long, Tile> tiles;
    private int cacheHits;
    private int cacheMisses;

    public HydrologyModel(long seed, StrataWorldConfig cfg, ElevationModel base) {
        if (cfg == null || base == null) {
            throw new IllegalArgumentException("config and base model must not be null");
        }
        this.seed = seed;
        this.cfg = cfg;
        this.base = base;
        // 64 tiles x (16x16 cells); each tile covers 8x8 chunks. ~150 KB total.
        this.tiles = new BoundedCache<Long, Tile>(64);
    }

    public synchronized int getCacheHits() {
        return cacheHits;
    }

    public synchronized int getCacheMisses() {
        return cacheMisses;
    }

    public int getCacheSize() {
        return tiles.size();
    }

    /**
     * Converts a block coordinate to a fractional index for a field sampled at
     * cell centers ({@code i * cellSize + cellSize / 2}).
     */
    public static double blockToCellCenterIndex(double coordinate, int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive");
        }
        return coordinate / cellSize - 0.5;
    }

    /** Samples drainage at a block coordinate (bilinear over cell centers). */
    public void sample(double x, double z, HydroSample out) {
        if (!cfg.enableRivers) {
            out.confluence01 = 0.0;
            out.river01 = 0.0;
            out.lake01 = 0.0;
            out.wet01 = 0.0;
            out.pit = false;
            return;
        }
        int cell = cfg.hydroCellSize;
        // buildTile stores grid values at cell centers, not cell corners.
        // Subtract the half-cell phase before choosing bilinear neighbours;
        // otherwise rivers/lakes are displaced by half a hydro cell.
        double fcx = blockToCellCenterIndex(x, cell);
        double fcz = blockToCellCenterIndex(z, cell);
        int ccx = (int) Math.floor(fcx);
        int ccz = (int) Math.floor(fcz);
        double fx = fcx - ccx;
        double fz = fcz - ccz;

        double c00 = confAt(ccx, ccz);
        double c10 = confAt(ccx + 1, ccz);
        double c01 = confAt(ccx, ccz + 1);
        double c11 = confAt(ccx + 1, ccz + 1);
        double conf = MathUtil.lerp(MathUtil.lerp(c00, c10, fx), MathUtil.lerp(c01, c11, fx), fz);

        double l00 = lakeAt(ccx, ccz);
        double l10 = lakeAt(ccx + 1, ccz);
        double l01 = lakeAt(ccx, ccz + 1);
        double l11 = lakeAt(ccx + 1, ccz + 1);
        double lake = MathUtil.lerp(MathUtil.lerp(l00, l10, fx), MathUtil.lerp(l01, l11, fx), fz);

        double w00 = wetAt(ccx, ccz);
        double w10 = wetAt(ccx + 1, ccz);
        double w01 = wetAt(ccx, ccz + 1);
        double w11 = wetAt(ccx + 1, ccz + 1);
        double wet = MathUtil.lerp(MathUtil.lerp(w00, w10, fx), MathUtil.lerp(w01, w11, fx), fz);

        out.confluence01 = conf;
        double d = cfg.drainageIntensity;
        out.river01 = d <= 0.01 ? 0.0 : MathUtil.smoothstep(0.20 / d, 0.55 / d, conf);
        out.lake01 = lake;
        out.wet01 = wet;
        out.pit = pitAt(ccx + (fx < 0.5 ? 0 : 1), ccz + (fz < 0.5 ? 0 : 1));
    }

    private double confAt(int cellX, int cellZ) {
        int n = cfg.hydroTileCells;
        Tile t = tileFor(cellX, cellZ);
        return t.conf[MathUtil.floorMod(cellZ, n) * n + MathUtil.floorMod(cellX, n)];
    }

    private double lakeAt(int cellX, int cellZ) {
        int n = cfg.hydroTileCells;
        Tile t = tileFor(cellX, cellZ);
        return t.lake[MathUtil.floorMod(cellZ, n) * n + MathUtil.floorMod(cellX, n)];
    }

    private boolean pitAt(int cellX, int cellZ) {
        int n = cfg.hydroTileCells;
        Tile t = tileFor(cellX, cellZ);
        return t.pit[MathUtil.floorMod(cellZ, n) * n + MathUtil.floorMod(cellX, n)];
    }

    private double wetAt(int cellX, int cellZ) {
        int n = cfg.hydroTileCells;
        Tile t = tileFor(cellX, cellZ);
        return t.wet[MathUtil.floorMod(cellZ, n) * n + MathUtil.floorMod(cellX, n)];
    }

    private Tile tileFor(int cellX, int cellZ) {
        int n = cfg.hydroTileCells;
        int tx = MathUtil.floorDiv(cellX, n);
        int tz = MathUtil.floorDiv(cellZ, n);
        long key = MathUtil.tileKey(tx, tz);
        Tile t = tiles.get(key);
        if (t != null) {
            synchronized (this) {
                cacheHits++;
            }
            return t;
        }
        synchronized (this) {
            cacheMisses++;
        }
        t = buildTile(tx, tz);
        tiles.put(key, t);
        return t;
    }

    /**
     * Builds one tile: sample patch heights (pure base model), D8 directions,
     * downstream walks with uniform-radius credit, pit/lake detection.
     */
    private Tile buildTile(int tx, int tz) {
        int n = cfg.hydroTileCells;
        int r = cfg.hydroRadiusCells;
        int cell = cfg.hydroCellSize;
        int sea = cfg.seaLevel;
        int x0 = tx * n - r;
        int z0 = tz * n - r;
        int p = n + 2 * r;

        double[] h = new double[p * p];
        double[] hj = new double[p * p];
        ElevationModel.BaseSample bs = new ElevationModel.BaseSample();
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < p; i++) {
                double wx = (x0 + i) * cell + cell * 0.5;
                double wz = (z0 + j) * cell + cell * 0.5;
                base.sampleBase(wx, wz, bs);
                int idx = j * p + i;
                h[idx] = bs.height;
                // +/-1 cm deterministic jitter: breaks flat ties, never routes
                // flow uphill in any meaningful sense.
                long hh = SeedMixer.hashSalted(SeedMixer.SALT_HYDRO_TIE, seed, x0 + i, z0 + j);
                hj[idx] = bs.height + (SeedMixer.toUnit(hh) - 0.5) * 0.02;
            }
        }

        // Priority-Flood (Barnes): fill depressions so every land cell drains.
        // Patch-edge and ocean cells are sinks. Filled cells get an epsilon
        // gradient toward the spill point; unfilled cells keep their jitter
        // for flat tie-breaking. Pure function of patch heights (the queue
        // order is deterministic: filled level, then index).
        final double[] filled = new double[p * p];
        boolean[] closed = new boolean[p * p];
        for (int k = 0; k < p * p; k++) {
            filled[k] = h[k];
        }
        PriorityQueue<Integer> open = new PriorityQueue<Integer>(p * p,
                new Comparator<Integer>() {
                    public int compare(Integer a, Integer b) {
                        int ai = a.intValue();
                        int bi = b.intValue();
                        if (filled[ai] < filled[bi]) {
                            return -1;
                        }
                        if (filled[ai] > filled[bi]) {
                            return 1;
                        }
                        return ai - bi;
                    }
                });
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < p; i++) {
                int idx = j * p + i;
                if (i == 0 || j == 0 || i == p - 1 || j == p - 1 || h[idx] < sea) {
                    closed[idx] = true;
                    open.add(Integer.valueOf(idx));
                }
            }
        }
        while (!open.isEmpty()) {
            int c = open.poll().intValue();
            int ci = c % p;
            int cj = c / p;
            for (int d = 0; d < 8; d++) {
                int ni = ci + NDX[d];
                int nj = cj + NDZ[d];
                if (ni < 0 || nj < 0 || ni >= p || nj >= p) {
                    continue;
                }
                int nidx = nj * p + ni;
                if (closed[nidx]) {
                    continue;
                }
                closed[nidx] = true;
                double spill = filled[c] + 1e-3;
                filled[nidx] = h[nidx] > spill ? h[nidx] : spill;
                open.add(Integer.valueOf(nidx));
            }
        }
        // Drained surface: filled level inside depressions, jittered height
        // outside (jitter inside filled areas would drown the epsilon
        // gradient and re-create micro-pits).
        double[] dr = new double[p * p];
        for (int k = 0; k < p * p; k++) {
            dr[k] = filled[k] - h[k] > 1e-6 ? filled[k] : hj[k];
        }

        byte[] flow = new byte[p * p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < p; i++) {
                int idx = j * p + i;
                if (h[idx] < sea) {
                    flow[idx] = -1; // oceans are sinks; rivers end at the sea
                    continue;
                }
                int best = -1;
                double bestH = dr[idx];
                for (int d = 0; d < 8; d++) {
                    int ni = i + NDX[d];
                    int nj = j + NDZ[d];
                    if (ni < 0 || nj < 0 || ni >= p || nj >= p) {
                        continue;
                    }
                    double nh = dr[nj * p + ni];
                    if (nh < bestH) {
                        bestH = nh;
                        best = d;
                    }
                }
                flow[idx] = (byte) best;
            }
        }

        // Downstream walks: every patch cell traces its path; inner cells
        // within Chebyshev radius r of the walk START get credited. The radius
        // is uniform for every cell, so tile borders are seamless.
        int[] visits = new int[p * p];
        for (int sj = 0; sj < p; sj++) {
            for (int si = 0; si < p; si++) {
                int ci = si;
                int cj = sj;
                for (int step = 0; step < WALK_CAP; step++) {
                    if (ci >= r && cj >= r && ci < r + n && cj < r + n) {
                        int dx = ci - si;
                        int dz = cj - sj;
                        if (dx < 0) {
                            dx = -dx;
                        }
                        if (dz < 0) {
                            dz = -dz;
                        }
                        if (dx <= r && dz <= r) {
                            visits[cj * p + ci]++;
                        }
                    }
                    int f = flow[cj * p + ci];
                    if (f < 0) {
                        break;
                    }
                    ci += NDX[f];
                    cj += NDZ[f];
                    if (ci < 0 || cj < 0 || ci >= p || cj >= p) {
                        break;
                    }
                }
            }
        }

        Tile t = new Tile(n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int pi = i + r;
                int pj = j + r;
                int pidx = pj * p + pi;
                int v = visits[pidx];
                double conf = MathUtil.clamp01((v - 1) / 48.0);
                t.conf[j * n + i] = (float) conf;
                boolean oceanCell = h[pidx] < sea;
                double fillDepth = filled[pidx] - h[pidx];
                // Real lake basins: filled a block or more below spill level.
                // Sub-block fill is micro-relief, not a lake.
                boolean pit = !oceanCell && fillDepth > 1.0;
                t.pit[j * n + i] = pit;
                double lake = 0.0;
                if (pit) {
                    lake = MathUtil.clamp01((fillDepth - 1.0) / 3.0)
                            * MathUtil.clamp01(v / 14.0);
                }
                t.lake[j * n + i] = (float) lake;
                // Wet flats: gentle + well-drained land (clay pans, wetlands).
                // Cell slope from central differences over the patch.
                double gx = (h[pj * p + pi + 1] - h[pj * p + pi - 1]) / (2.0 * cell);
                double gz = (h[(pj + 1) * p + pi] - h[(pj - 1) * p + pi]) / (2.0 * cell);
                double cellSlope = Math.sqrt(gx * gx + gz * gz);
                double wet = 0.0;
                if (!oceanCell) {
                    wet = MathUtil.smoothstep(0.55, 0.85, conf)
                            * MathUtil.smoothstepDown(0.07, 0.025, cellSlope);
                }
                t.wet[j * n + i] = (float) wet;
            }
        }
        return t;
    }
}
