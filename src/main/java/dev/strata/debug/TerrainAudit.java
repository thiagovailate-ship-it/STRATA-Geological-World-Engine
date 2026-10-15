package dev.strata.debug;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.MathUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

/**
 * Deterministic quantitative audit of one sampled terrain window.
 *
 * <p>This tool reports measurements, not target values. A CSV can be written
 * before a change and compared with a later run at exactly the same seed and
 * coordinates. Connected-component counts are four-neighbour measurements on
 * the sampled grid; areas are estimates at the chosen sample spacing.</p>
 *
 * <p>Usage: TerrainAudit [seed [centerX centerZ [sizeBlocks [stepBlocks
 * [writeCsv [compareCsv]]]]]] [--stats-only]</p>
 */
public final class TerrainAudit {

    private static final String CSV_HEADER =
            "x,z,baseHeight,finalHeight,continent,slope,orogen01,river01,lake01,wet01,pit,ocean";

    private TerrainAudit() {
    }

    public static void main(String[] args) throws IOException {
        boolean statsOnly = args.length > 0 && "--stats-only".equals(args[args.length - 1]);
        int optionCount = args.length - (statsOnly ? 1 : 0);
        long seed = optionCount > 0 ? Long.parseLong(args[0]) : 12345L;
        int centerX = optionCount > 1 ? Integer.parseInt(args[1]) : 0;
        int centerZ = optionCount > 2 ? Integer.parseInt(args[2]) : 0;
        int size = optionCount > 3 ? Integer.parseInt(args[3]) : 2048;
        int step = optionCount > 4 ? Integer.parseInt(args[4]) : 4;
        String writePath = optionCount > 5 ? args[5] : null;
        String comparePath = optionCount > 6 ? args[6] : null;
        if (size <= 0 || step <= 0 || size < step) {
            throw new IllegalArgumentException("size and step must be positive, with size >= step");
        }

        final int width = size / step;
        final int height = size / step;
        final long sampleCount = (long) width * height;
        if (width > 1024 || height > 1024 || sampleCount > 1048576L) {
            throw new IllegalArgumentException("audit grid too large; increase step (max 1024 samples per side)");
        }
        final int actualSize = width * step;
        final int x0 = centerX - actualSize / 2;
        final int z0 = centerZ - actualSize / 2;
        final int x1 = x0 + actualSize;
        final int z1 = z0 + actualSize;
        final int n = (int) sampleCount;

        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        StrataWorldModel model = new StrataWorldModel(seed, cfg);
        ColumnData col = new ColumnData();

        int[] heights = new int[n];
        boolean[] land = new boolean[n];
        boolean[] positiveContinent = new boolean[n];
        boolean[] pits = new boolean[n];
        boolean[] lakes = new boolean[n];
        boolean[] strongOrogen = new boolean[n];
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        long sumHeight = 0L;
        double sumHeightSquared = 0.0;
        double sumContinent = 0.0;
        double sumContinentSquared = 0.0;
        double sumHeightContinent = 0.0;
        double sumSlope = 0.0;
        int belowSea = 0;
        int nearSea = 0;
        int aboveSea = 0;
        int flat = 0;
        int hills = 0;
        int steep = 0;
        int pitCount = 0;
        int lakeCount = 0;
        int oceanCount = 0;
        int orogenCount = 0;
        int[] heightHistogram = new int[256];

        BufferedReader baseline = comparePath == null ? null : new BufferedReader(new FileReader(comparePath));
        PrintWriter csv = writePath == null ? null : new PrintWriter(new FileWriter(writePath));
        Comparison comparison = comparePath == null ? null : new Comparison();
        try {
            if (baseline != null) {
                String header = baseline.readLine();
                if (!CSV_HEADER.equals(header)) {
                    throw new IOException("unexpected baseline CSV header in " + comparePath);
                }
            }
            if (csv != null) {
                csv.println(CSV_HEADER);
            }
            for (int j = 0; j < height; j++) {
                int z = z0 + j * step;
                for (int i = 0; i < width; i++) {
                    int x = x0 + i * step;
                    int k = j * width + i;
                    model.sampleColumn(x, z, col);
                    heights[k] = col.finalHeight;
                    land[k] = !col.ocean;
                    positiveContinent[k] = col.continent >= 0.0;
                    pits[k] = col.pit;
                    lakes[k] = col.lake01 > 0.45;
                    strongOrogen[k] = col.orogen01 > 0.5;

                    if (col.finalHeight < minHeight) {
                        minHeight = col.finalHeight;
                    }
                    if (col.finalHeight > maxHeight) {
                        maxHeight = col.finalHeight;
                    }
                    sumHeight += col.finalHeight;
                    sumHeightSquared += (double) col.finalHeight * col.finalHeight;
                    sumContinent += col.continent;
                    sumContinentSquared += col.continent * col.continent;
                    sumHeightContinent += col.finalHeight * col.continent;
                    sumSlope += col.slope;
                    heightHistogram[MathUtil.clampInt(col.finalHeight, 0, 255)]++;
                    if (col.ocean) {
                        oceanCount++;
                    }
                    if (Math.abs(col.finalHeight - cfg.seaLevel) <= 2) {
                        nearSea++;
                    } else if (col.finalHeight < cfg.seaLevel) {
                        belowSea++;
                    } else {
                        aboveSea++;
                    }
                    if (col.slope < 0.05) {
                        flat++;
                    } else if (col.slope < 0.35) {
                        hills++;
                    } else {
                        steep++;
                    }
                    if (col.pit) {
                        pitCount++;
                    }
                    if (col.lake01 > 0.45) {
                        lakeCount++;
                    }
                    if (col.orogen01 > 0.5) {
                        orogenCount++;
                    }

                    if (csv != null) {
                        csv.println(csvRow(col));
                    }
                    if (baseline != null) {
                        String row = baseline.readLine();
                        if (row == null) {
                            throw new IOException("baseline CSV ended after " + comparison.count + " rows");
                        }
                        comparison.add(row, col, x, z);
                    }
                }
            }
            if (baseline != null && baseline.readLine() != null) {
                throw new IOException("baseline CSV has more rows than the current audit grid");
            }
        } finally {
            if (baseline != null) {
                baseline.close();
            }
            if (csv != null) {
                csv.close();
            }
        }

        Arrays.sort(heights);
        double meanHeight = (double) sumHeight / n;
        double meanContinent = sumContinent / n;
        double varianceHeight = sumHeightSquared / n - meanHeight * meanHeight;
        double varianceContinent = sumContinentSquared / n - meanContinent * meanContinent;
        double covarianceContinentHeight = sumHeightContinent / n - meanHeight * meanContinent;
        double continentHeightCorrelation = varianceHeight > 0.0 && varianceContinent > 0.0
                ? covarianceContinentHeight / Math.sqrt(varianceHeight * varianceContinent) : 0.0;
        System.out.println("STRATA terrain audit; deterministic sampled window (not a whole-world census)");
        System.out.println("seed=" + seed + " center=(" + centerX + "," + centerZ + ") requestedSize="
                + size + " actualSize=" + actualSize + " step=" + step + " samples=" + n
                + " seaLevel=" + cfg.seaLevel);
        System.out.println("height min=" + minHeight + " max=" + maxHeight + " mean="
                + f((double) sumHeight / n) + " p10=" + percentile(heights, 0.10)
                + " p25=" + percentile(heights, 0.25) + " p50=" + percentile(heights, 0.50)
                + " p75=" + percentile(heights, 0.75) + " p90=" + percentile(heights, 0.90));
        System.out.println("height histogram (10-block bands, counts): " + histogram(heightHistogram));
        System.out.println("sea bands: below=" + pct(belowSea, n) + "% (height < sea-2), near="
                + pct(nearSea, n) + "% (abs(height-sea)<=2), above=" + pct(aboveSea, n)
                + "% (height > sea+2); ocean columns=" + pct(oceanCount, n) + "% (height < sea)");
        System.out.println("continent-height coupling: mean C=" + f(meanContinent)
                + " Pearson corr(C, finalHeight)=" + f(continentHeightCorrelation));
        System.out.println("slope (base-height rise/run; categories align with surface rules): flat <0.05="
                + pct(flat, n) + "%, hills 0.05.. <0.35=" + pct(hills, n)
                + "%, steep >=0.35=" + pct(steep, n) + "%, mean=" + f(sumSlope / n));
        System.out.println("continent mask C>=0=" + pct(count(positiveContinent), n)
                + "% of samples; connected components=" + components(positiveContinent, width, height, step));
        System.out.println("final land (finalHeight>=sea): " + pct(n - oceanCount, n)
                + "% land, connected components=" + components(land, width, height, step));
        System.out.println("depressions: pit=" + pct(pitCount, n) + "% components="
                + components(pits, width, height, step) + "; lake01>0.45=" + pct(lakeCount, n)
                + "% components=" + components(lakes, width, height, step));
        System.out.println("strong-orogen mask (orogen01>0.5, equivalent to >30 pre-relief-scale blocks): "
                + pct(orogenCount, n) + "% components=" + components(strongOrogen, width, height, step));
        if (statsOnly) {
            System.out.println("chunk/tile boundary continuity skipped (--stats-only); run a local window audit for exact edge pairs");
        } else {
            printChunkContinuity(model, seed, x0, z0, x1, z1, step,
                    cfg.hydroCellSize * cfg.hydroTileCells);
        }
        if (writePath != null) {
            System.out.println("wrote column samples: " + writePath);
        }
        if (comparison != null) {
            System.out.println("same-coordinate comparison vs " + comparePath + ": " + comparison);
        }
    }

    private static String csvRow(ColumnData c) {
        return c.x + "," + c.z + "," + c.baseHeight + "," + c.finalHeight + ","
                + Double.toString(c.continent) + "," + Double.toString(c.slope) + ","
                + Double.toString(c.orogen01) + "," + Double.toString(c.river01) + ","
                + Double.toString(c.lake01) + "," + Double.toString(c.wet01) + ","
                + c.pit + "," + c.ocean;
    }

    private static final class Comparison {
        long count;
        long changedHeight;
        long changedBaseHeight;
        long changedRiverMask;
        double sumHeight;
        double maxHeight;
        double sumBaseHeight;
        double maxBaseHeight;
        double sumRiver;
        double maxRiver;
        double sumLake;
        double maxLake;
        double sumWet;
        double maxWet;

        void add(String row, ColumnData current, int x, int z) throws IOException {
            String[] fields = row.split(",", -1);
            if (fields.length != 12) {
                throw new IOException("invalid baseline row " + count + ": expected 12 fields");
            }
            int oldX = Integer.parseInt(fields[0]);
            int oldZ = Integer.parseInt(fields[1]);
            if (oldX != x || oldZ != z) {
                throw new IOException("baseline coordinate mismatch at row " + count + ": expected ("
                        + x + "," + z + ") got (" + oldX + "," + oldZ + ")");
            }
            int oldBase = Integer.parseInt(fields[2]);
            int oldHeight = Integer.parseInt(fields[3]);
            double oldRiver = Double.parseDouble(fields[7]);
            double oldLake = Double.parseDouble(fields[8]);
            double oldWet = Double.parseDouble(fields[9]);
            double dH = Math.abs(current.finalHeight - oldHeight);
            double dBase = Math.abs(current.baseHeight - oldBase);
            double dRiver = Math.abs(current.river01 - oldRiver);
            double dLake = Math.abs(current.lake01 - oldLake);
            double dWet = Math.abs(current.wet01 - oldWet);
            if (dH > 0.0) {
                changedHeight++;
            }
            if (dBase > 0.0) {
                changedBaseHeight++;
            }
            if ((current.river01 > 0.45) != (oldRiver > 0.45)) {
                changedRiverMask++;
            }
            sumHeight += dH;
            sumBaseHeight += dBase;
            sumRiver += dRiver;
            sumLake += dLake;
            sumWet += dWet;
            maxHeight = Math.max(maxHeight, dH);
            maxBaseHeight = Math.max(maxBaseHeight, dBase);
            maxRiver = Math.max(maxRiver, dRiver);
            maxLake = Math.max(maxLake, dLake);
            maxWet = Math.max(maxWet, dWet);
            count++;
        }

        public String toString() {
            return "columns=" + count + " changedFinalHeight=" + changedHeight + " ("
                    + pct(changedHeight, count) + "%), meanAbsDeltaH=" + f(sumHeight / count)
                    + " maxAbsDeltaH=" + f(maxHeight) + "; changedBaseHeight=" + changedBaseHeight
                    + " meanAbsDeltaBase=" + f(sumBaseHeight / count) + " maxAbsDeltaBase="
                    + f(maxBaseHeight) + "; changedRiverMask=" + changedRiverMask + " ("
                    + pct(changedRiverMask, count) + "%), meanAbsDeltaRiver=" + f(sumRiver / count)
                    + " maxAbsDeltaRiver=" + f(maxRiver) + "; meanAbsDeltaLake=" + f(sumLake / count)
                    + " maxAbsDeltaLake=" + f(maxLake) + "; meanAbsDeltaWet=" + f(sumWet / count)
                    + " maxAbsDeltaWet=" + f(maxWet);
        }
    }

    private static void printChunkContinuity(StrataWorldModel model, long seed,
                                             int x0, int z0, int x1, int z1, int alongStep,
                                             int hydroTileBlocks) {
        Continuity chunk = new Continuity();
        Continuity chunkControl = new Continuity();
        Continuity hydroTile = new Continuity();
        Continuity hydroTileControl = new Continuity();
        ColumnData a = new ColumnData();
        ColumnData b = new ColumnData();
        ColumnData c = new ColumnData();
        ColumnData d = new ColumnData();

        int firstX = (MathUtil.floorDiv(x0, 16) + 1) * 16;
        for (int boundary = firstX; boundary < x1; boundary += 16) {
            boolean tileEdge = boundary % hydroTileBlocks == 0;
            for (int z = z0; z < z1; z += alongStep) {
                model.sampleColumn(boundary - 1, z, a);
                model.sampleColumn(boundary, z, b);
                chunk.add(a, b);
                if (tileEdge) {
                    hydroTile.add(a, b);
                }
                if (boundary + 4 < x1) {
                    model.sampleColumn(boundary + 3, z, c);
                    model.sampleColumn(boundary + 4, z, d);
                    chunkControl.add(c, d);
                    if (tileEdge) {
                        hydroTileControl.add(c, d);
                    }
                }
            }
        }
        int firstZ = (MathUtil.floorDiv(z0, 16) + 1) * 16;
        for (int boundary = firstZ; boundary < z1; boundary += 16) {
            boolean tileEdge = boundary % hydroTileBlocks == 0;
            for (int x = x0; x < x1; x += alongStep) {
                model.sampleColumn(x, boundary - 1, a);
                model.sampleColumn(x, boundary, b);
                chunk.add(a, b);
                if (tileEdge) {
                    hydroTile.add(a, b);
                }
                if (boundary + 4 < z1) {
                    model.sampleColumn(x, boundary + 3, c);
                    model.sampleColumn(x, boundary + 4, d);
                    chunkControl.add(c, d);
                    if (tileEdge) {
                        hydroTileControl.add(c, d);
                    }
                }
            }
        }
        System.out.println("chunk-edge adjacent |dH| (1-block pairs): " + chunk
                + "; matched interior control: " + chunkControl);
        System.out.println("hydrology-tile-edge adjacent |dH| (tile width=" + hydroTileBlocks
                + " blocks): " + hydroTile + "; matched interior control: " + hydroTileControl);
    }

    private static final class Continuity {
        long count;
        long sum;
        long sumBase;
        int max;
        int maxBase;
        int maxX1;
        int maxZ1;
        int maxX2;
        int maxZ2;
        int maxH1;
        int maxH2;
        int maxBase1;
        int maxBase2;
        final long[] histogram = new long[256];

        void add(ColumnData a, ColumnData b) {
            int d = Math.abs(a.finalHeight - b.finalHeight);
            int db = Math.abs(a.baseHeight - b.baseHeight);
            count++;
            sum += d;
            sumBase += db;
            if (d > max) {
                max = d;
                maxX1 = a.x;
                maxZ1 = a.z;
                maxX2 = b.x;
                maxZ2 = b.z;
                maxH1 = a.finalHeight;
                maxH2 = b.finalHeight;
                maxBase1 = a.baseHeight;
                maxBase2 = b.baseHeight;
            }
            if (db > maxBase) {
                maxBase = db;
            }
            histogram[Math.min(d, histogram.length - 1)]++;
        }

        public String toString() {
            return "pairs=" + count + " mean=" + (count == 0 ? "n/a" : f((double) sum / count))
                    + " p95=" + quantile(histogram, count, 0.95) + " max=" + max
                    + " at (" + maxX1 + "," + maxZ1 + ")=" + maxH1 + " to (" + maxX2 + ","
                    + maxZ2 + ")=" + maxH2 + "; base mean="
                    + (count == 0 ? "n/a" : f((double) sumBase / count)) + " max=" + maxBase
                    + " [" + maxBase1 + " to " + maxBase2 + "]";
        }
    }

    private static final class ComponentStats {
        int count;
        int touchingWindowEdge;
        int enclosed;
        int largest;
        int secondLargest;
        long occupied;
        long largestAreaBlocks2;
        long secondAreaBlocks2;

        public String toString() {
            return "count=" + count + " occupied=" + occupied + " cells; largest=" + largest
                    + " cells (~" + largestAreaBlocks2 + " block^2), second=" + secondLargest
                    + " cells (~" + secondAreaBlocks2 + " block^2), enclosed=" + enclosed
                    + ", touchingWindowEdge=" + touchingWindowEdge;
        }
    }

    private static ComponentStats components(boolean[] mask, int width, int height, int step) {
        ComponentStats stats = new ComponentStats();
        boolean[] visited = new boolean[mask.length];
        int[] queue = new int[mask.length];
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || visited[start]) {
                continue;
            }
            stats.count++;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            boolean touchesEdge = false;
            while (head < tail) {
                int index = queue[head++];
                int x = index % width;
                int z = index / width;
                if (x == 0 || z == 0 || x == width - 1 || z == height - 1) {
                    touchesEdge = true;
                }
                if (x > 0) {
                    tail = enqueue(index - 1, mask, visited, queue, tail);
                }
                if (x + 1 < width) {
                    tail = enqueue(index + 1, mask, visited, queue, tail);
                }
                if (z > 0) {
                    tail = enqueue(index - width, mask, visited, queue, tail);
                }
                if (z + 1 < height) {
                    tail = enqueue(index + width, mask, visited, queue, tail);
                }
            }
            stats.occupied += tail;
            if (touchesEdge) {
                stats.touchingWindowEdge++;
            } else {
                stats.enclosed++;
            }
            if (tail > stats.largest) {
                stats.secondLargest = stats.largest;
                stats.largest = tail;
            } else if (tail > stats.secondLargest) {
                stats.secondLargest = tail;
            }
        }
        stats.largestAreaBlocks2 = (long) stats.largest * step * step;
        stats.secondAreaBlocks2 = (long) stats.secondLargest * step * step;
        return stats;
    }

    private static int enqueue(int index, boolean[] mask, boolean[] visited, int[] queue, int tail) {
        if (mask[index] && !visited[index]) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int count(boolean[] values) {
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                count++;
            }
        }
        return count;
    }

    private static int percentile(int[] sorted, double q) {
        int index = (int) Math.floor(q * (sorted.length - 1));
        return sorted[MathUtil.clampInt(index, 0, sorted.length - 1)];
    }

    private static int quantile(long[] histogram, long total, double q) {
        if (total <= 0) {
            return 0;
        }
        long target = (long) Math.ceil(q * total);
        long seen = 0;
        for (int i = 0; i < histogram.length; i++) {
            seen += histogram[i];
            if (seen >= target) {
                return i;
            }
        }
        return histogram.length - 1;
    }

    private static String histogram(int[] histogram) {
        StringBuilder out = new StringBuilder();
        for (int lo = 0; lo < histogram.length; lo += 10) {
            int hi = Math.min(histogram.length - 1, lo + 9);
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(lo).append('-').append(hi).append(':');
            long count = 0;
            for (int h = lo; h <= hi; h++) {
                count += histogram[h];
            }
            out.append(count);
        }
        return out.toString();
    }

    private static String f(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private static String pct(long count, long total) {
        return f(100.0 * count / total);
    }
}
