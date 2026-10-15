package dev.strata.debug;

import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.stratigraphy.Formation;
import dev.strata.core.stratigraphy.GeoMaterial;
import dev.strata.core.tectonics.BoundaryType;
import dev.strata.core.terrain.BiomeKind;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Geological laboratory: samples the engine without Minecraft and renders
 * diagnostic maps (binary PPM, P6) plus a statistics file.
 *
 * <p>Usage:</p>
 * <pre>
 *   StrataLab &lt;mode&gt; &lt;seed&gt; &lt;centerX&gt; &lt;centerZ&gt; &lt;size&gt; [outPrefix] [step]
 *   mode: elevation | plates | boundary | hydro | formation | biome | section
 * </pre>
 * <p>For map modes, {@code size} is pixels per side and {@code step} is blocks
 * per pixel (default 4). For {@code section}, {@code size} is the width in
 * blocks of a vertical slice at {@code centerZ} (step defaults to 2).</p>
 */
public final class StrataLab {

    private StrataLab() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 5) {
            System.out.println("Usage: StrataLab <mode> <seed> <centerX> <centerZ> <size>"
                    + " [outPrefix] [step]");
            System.out.println("Modes: elevation plates boundary hydro formation biome section");
            return;
        }
        String mode = args[0];
        long seed = Long.parseLong(args[1]);
        int centerX = Integer.parseInt(args[2]);
        int centerZ = Integer.parseInt(args[3]);
        int size = Integer.parseInt(args[4]);
        String prefix = args.length > 5 ? args[5] : "out/strata";
        int step = args.length > 6 ? Integer.parseInt(args[6]) : ("section".equals(mode) ? 2 : 4);
        if (size < 16) {
            size = 16;
        }
        if ("section".equals(mode) && size > 2048) {
            size = 2048;
        }
        if (!"section".equals(mode) && size > 1024) {
            size = 1024;
        }

        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        StrataWorldModel model = new StrataWorldModel(seed, cfg);

        long t0 = System.nanoTime();
        if ("section".equals(mode)) {
            renderSection(model, cfg, seed, centerX, centerZ, size, step, prefix);
        } else {
            renderMap(model, cfg, seed, mode, centerX, centerZ, size, step, prefix);
        }
        double secs = (System.nanoTime() - t0) / 1000000000.0;
        System.out.println("lab done in " + String.format("%.2f", secs) + " s");
    }

    // ------------------------------------------------------------------ maps

    private static void renderMap(StrataWorldModel model, StrataWorldConfig cfg, long seed,
                                  String mode, int centerX, int centerZ, int size, int step,
                                  String prefix) throws IOException {
        int w = size;
        int h = size;
        int x0 = centerX - (w * step) / 2;
        int z0 = centerZ - (h * step) / 2;

        float[] height = new float[w * h];
        float[] continent = new float[w * h];
        float[] river = new float[w * h];
        float[] lake = new float[w * h];
        float[] confluence = new float[w * h];
        float[] wet = new float[w * h];
        float[] temp = new float[w * h];
        float[] moist = new float[w * h];
        float[] edge = new float[w * h];
        float[] boundI = new float[w * h];
        byte[] boundT = new byte[w * h];
        byte[] form = new byte[w * h];
        byte[] biome = new byte[w * h];
        int[] plate = new int[w * h];

        ColumnData col = new ColumnData();
        Stats stats = new Stats();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int x = x0 + i * step;
                int z = z0 + j * step;
                model.sampleColumn(x, z, col);
                int k = j * w + i;
                height[k] = col.finalHeight;
                continent[k] = (float) col.continent;
                river[k] = (float) col.river01;
                lake[k] = (float) col.lake01;
                confluence[k] = (float) col.confluence01;
                temp[k] = (float) col.temp01;
                moist[k] = (float) col.moist01;
                edge[k] = (float) col.boundary.edgeDistance;
                double bi = Math.max(col.boundary.convergent01,
                        Math.max(col.boundary.divergent01, col.boundary.transform01));
                boundI[k] = (float) bi;
                boundT[k] = (byte) col.boundary.dominant.ordinal();
                form[k] = (byte) col.formation.ordinal();
                biome[k] = (byte) col.biome.ordinal();
                plate[k] = col.plateId;
                stats.add(col);
            }
            if ((j & 63) == 63) {
                System.err.println("  row " + j + "/" + h);
            }
        }

        byte[] rgb = new byte[w * h * 3];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                int r;
                int g;
                int b;
                if ("plates".equals(mode)) {
                    int[] c = plateColor(plate[k], edge[k]);
                    r = c[0];
                    g = c[1];
                    b = c[2];
                } else if ("boundary".equals(mode)) {
                    int[] c = boundaryColor(boundT[k], boundI[k], continent[k] < 0);
                    r = c[0];
                    g = c[1];
                    b = c[2];
                } else if ("hydro".equals(mode)) {
                    int[] c = hydroColor(height[k], river[k], lake[k], confluence[k],
                            cfg.seaLevel);
                    r = c[0];
                    g = c[1];
                    b = c[2];
                } else if ("formation".equals(mode)) {
                    int[] c = formationColor(form[k], height[k], cfg.seaLevel);
                    r = c[0];
                    g = c[1];
                    b = c[2];
                } else if ("biome".equals(mode)) {
                    int[] c = biomeColor(biome[k]);
                    r = c[0];
                    g = c[1];
                    b = c[2];
                } else {
                    double shade = hillshade(height, w, h, i, j, step);
                    int[] c = elevationColor(height[k], continent[k], river[k], lake[k],
                            cfg.seaLevel);
                    r = clampByte((int) (c[0] * shade));
                    g = clampByte((int) (c[1] * shade));
                    b = clampByte((int) (c[2] * shade));
                }
                rgb[k * 3] = (byte) r;
                rgb[k * 3 + 1] = (byte) g;
                rgb[k * 3 + 2] = (byte) b;
            }
        }

        String ppm = prefix + "_" + mode + ".ppm";
        writePpm(ppm, w, h, rgb);
        System.out.println("wrote " + ppm);
        String txt = prefix + "_" + mode + ".txt";
        PrintWriter pw = new PrintWriter(txt, "UTF-8");
        try {
            pw.println("STRATA lab map: mode=" + mode + " seed=" + seed + " center=("
                    + centerX + ", " + centerZ + ") size=" + size + " step=" + step);
            pw.println(stats);
        } finally {
            pw.close();
        }
        System.out.println("wrote " + txt);
        System.out.println(stats);
    }

    private static double hillshade(float[] height, int w, int h, int i, int j, int step) {
        int xm = i > 0 ? i - 1 : i;
        int xp = i + 1 < w ? i + 1 : i;
        int zm = j > 0 ? j - 1 : j;
        int zp = j + 1 < h ? j + 1 : j;
        double dx = (height[j * w + xp] - height[j * w + xm]) / ((xp - xm) * step + 1e-9);
        double dz = (height[zp * w + i] - height[zm * w + i]) / ((zp - zm) * step + 1e-9);
        double s = 1.0 - (dx + dz) * 0.55;
        return s < 0.55 ? 0.55 : (s > 1.25 ? 1.25 : s);
    }

    // -------------------------------------------------------------- section

    private static void renderSection(StrataWorldModel model, StrataWorldConfig cfg, long seed,
                                      int centerX, int centerZ, int widthBlocks, int step,
                                      String prefix) throws IOException {
        int w = Math.max(16, widthBlocks / step);
        int h = 256;
        int x0 = centerX - (w * step) / 2;
        byte[] rgb = new byte[w * h * 3];
        ColumnData col = new ColumnData();
        Stats stats = new Stats();
        for (int i = 0; i < w; i++) {
            int x = x0 + i * step;
            model.sampleColumn(x, centerZ, col);
            stats.add(col);
            int waterTop = Math.max(cfg.seaLevel, col.lakeWaterTop);
            for (int y = 0; y < 256; y++) {
                int[] c;
                if (y > col.finalHeight) {
                    c = y <= waterTop ? new int[]{42, 106, 191} : new int[]{12, 14, 22};
                } else if (y == 0) {
                    c = new int[]{20, 20, 20};
                } else {
                    c = materialColor(col.layers.rockAt(y));
                }
                int row = (255 - y) * w + i;
                rgb[row * 3] = (byte) c[0];
                rgb[row * 3 + 1] = (byte) c[1];
                rgb[row * 3 + 2] = (byte) c[2];
            }
            if ((i & 255) == 255) {
                System.err.println("  col " + i + "/" + w);
            }
        }
        String ppm = prefix + "_section.ppm";
        writePpm(ppm, w, h, rgb);
        System.out.println("wrote " + ppm + " (vertical slice at z=" + centerZ + ")");
        String txt = prefix + "_section.txt";
        PrintWriter pw = new PrintWriter(txt, "UTF-8");
        try {
            pw.println("STRATA lab section: seed=" + seed + " z=" + centerZ + " x0=" + x0
                    + " width=" + widthBlocks + " step=" + step);
            pw.println(stats);
        } finally {
            pw.close();
        }
        System.out.println("wrote " + txt);
        System.out.println(stats);
    }

    // ---------------------------------------------------------------- colors

    private static int[] elevationColor(double h, double c, double river, double lake, int sea) {
        int[] col;
        if (h < sea) {
            double d = (sea - h) / 30.0;
            if (d > 1.0) {
                d = 1.0;
            }
            col = new int[]{(int) (42 - 32 * d), (int) (106 - 80 * d), (int) (191 - 90 * d)};
        } else if (h <= sea + 2) {
            col = new int[]{226, 216, 168};
        } else if (h < sea + 12) {
            col = new int[]{74, 124, 58};
        } else if (h < sea + 30) {
            col = new int[]{122, 156, 74};
        } else if (h < sea + 55) {
            col = new int[]{160, 140, 78};
        } else if (h < sea + 90) {
            col = new int[]{138, 106, 74};
        } else if (h < sea + 120) {
            col = new int[]{122, 122, 122};
        } else {
            col = new int[]{240, 240, 240};
        }
        if (river > 0.45 && h >= sea) {
            col = new int[]{42, 106, 221};
        } else if (lake > 0.4 && h >= sea) {
            col = new int[]{64, 170, 200};
        }
        return col;
    }

    private static int[] plateColor(int plateId, double edge) {
        long x = plateId * 2654435761L;
        x ^= x >>> 13;
        x *= 1274126177L;
        int r = 90 + (int) ((x >>> 4) & 127);
        int g = 90 + (int) ((x >>> 11) & 127);
        int b = 90 + (int) ((x >>> 18) & 127);
        if (edge < 10.0) {
            r = 20;
            g = 20;
            b = 20;
        }
        return new int[]{r, g, b};
    }

    private static int[] boundaryColor(byte type, double intensity, boolean ocean) {
        int[] c;
        if (type == BoundaryType.CONVERGENT.ordinal()) {
            c = new int[]{(int) (120 + 135 * intensity), 40, 40};
        } else if (type == BoundaryType.DIVERGENT.ordinal()) {
            c = new int[]{40, 60, (int) (120 + 135 * intensity)};
        } else if (type == BoundaryType.TRANSFORM.ordinal()) {
            c = new int[]{(int) (130 + 125 * intensity), (int) (120 + 115 * intensity), 40};
        } else {
            c = new int[]{52, 58, 64};
        }
        if (ocean) {
            c[0] = c[0] / 2 + 10;
            c[1] = c[1] / 2 + 10;
            c[2] = c[2] / 2 + 30;
        }
        return c;
    }

    private static int[] hydroColor(double h, double river, double lake, double conf, int sea) {
        int base = h < sea ? 40 : 150;
        int[] c = {base, base, base + 20};
        if (conf > 0.05 && h >= sea) {
            int t = (int) (conf * 60);
            c[0] -= t / 2;
            c[1] -= t / 2;
        }
        if (river > 0.15 && h >= sea) {
            double k = river < 1.0 ? river : 1.0;
            c[0] = (int) (c[0] * (1 - k) + 42 * k);
            c[1] = (int) (c[1] * (1 - k) + 106 * k);
            c[2] = (int) (c[2] * (1 - k) + 221 * k);
        }
        if (lake > 0.3) {
            c[0] = 64;
            c[1] = 170;
            c[2] = 200;
        }
        return c;
    }

    private static int[] formationColor(byte f, double h, int sea) {
        int[] c;
        if (f == Formation.OROGEN.ordinal()) {
            c = new int[]{148, 110, 178};
        } else if (f == Formation.VOLCANIC.ordinal()) {
            c = new int[]{150, 60, 52};
        } else if (f == Formation.CARBONATE.ordinal()) {
            c = new int[]{232, 224, 170};
        } else if (f == Formation.CLASTIC_BASIN.ordinal()) {
            c = new int[]{196, 164, 120};
        } else if (f == Formation.RIFT.ordinal()) {
            c = new int[]{220, 140, 70};
        } else if (f == Formation.OCEANIC.ordinal()) {
            c = new int[]{70, 90, 120};
        } else if (f == Formation.SHIELD.ordinal()) {
            c = new int[]{190, 150, 160};
        } else {
            c = new int[]{150, 190, 130};
        }
        double s = h < sea ? 0.75 : 1.0;
        return new int[]{(int) (c[0] * s), (int) (c[1] * s), (int) (c[2] * s)};
    }

    private static int[] biomeColor(byte b) {
        if (b == BiomeKind.OCEAN.ordinal()) {
            return new int[]{42, 74, 191};
        }
        if (b == BiomeKind.DEEP_OCEAN.ordinal()) {
            return new int[]{26, 42, 122};
        }
        if (b == BiomeKind.FROZEN_OCEAN.ordinal()) {
            return new int[]{160, 200, 232};
        }
        if (b == BiomeKind.RIVER.ordinal()) {
            return new int[]{58, 106, 223};
        }
        if (b == BiomeKind.FROZEN_RIVER.ordinal()) {
            return new int[]{160, 208, 232};
        }
        if (b == BiomeKind.BEACH.ordinal()) {
            return new int[]{226, 216, 168};
        }
        if (b == BiomeKind.STONE_BEACH.ordinal()) {
            return new int[]{138, 138, 138};
        }
        if (b == BiomeKind.COLD_BEACH.ordinal()) {
            return new int[]{216, 226, 232};
        }
        if (b == BiomeKind.DESERT.ordinal()) {
            return new int[]{232, 216, 154};
        }
        if (b == BiomeKind.SAVANNA.ordinal()) {
            return new int[]{184, 200, 74};
        }
        if (b == BiomeKind.MESA.ordinal()) {
            return new int[]{200, 122, 58};
        }
        if (b == BiomeKind.MESA_PLATEAU.ordinal()) {
            return new int[]{168, 90, 42};
        }
        if (b == BiomeKind.FOREST.ordinal()) {
            return new int[]{58, 138, 58};
        }
        if (b == BiomeKind.BIRCH_FOREST.ordinal()) {
            return new int[]{90, 170, 74};
        }
        if (b == BiomeKind.ROOFED_FOREST.ordinal()) {
            return new int[]{42, 106, 42};
        }
        if (b == BiomeKind.TAIGA.ordinal()) {
            return new int[]{74, 138, 106};
        }
        if (b == BiomeKind.COLD_TAIGA.ordinal()) {
            return new int[]{106, 154, 138};
        }
        if (b == BiomeKind.MEGA_TAIGA.ordinal()) {
            return new int[]{58, 122, 90};
        }
        if (b == BiomeKind.JUNGLE.ordinal()) {
            return new int[]{42, 170, 42};
        }
        if (b == BiomeKind.SWAMPLAND.ordinal()) {
            return new int[]{90, 122, 58};
        }
        if (b == BiomeKind.EXTREME_HILLS.ordinal()) {
            return new int[]{138, 138, 138};
        }
        if (b == BiomeKind.ICE_PLAINS.ordinal()) {
            return new int[]{238, 242, 245};
        }
        if (b == BiomeKind.ICE_MOUNTAINS.ordinal()) {
            return new int[]{200, 216, 232};
        }
        return new int[]{122, 186, 74}; // plains
    }

    private static int[] materialColor(GeoMaterial m) {
        switch (m) {
            case GRANITE:
                return new int[]{168, 126, 118};
            case BASALT:
                return new int[]{52, 52, 58};
            case LIMESTONE:
                return new int[]{196, 188, 160};
            case SANDSTONE:
                return new int[]{203, 178, 130};
            case SHALE:
                return new int[]{116, 112, 120};
            case MARBLE:
                return new int[]{226, 226, 230};
            case GNEISS:
                return new int[]{150, 138, 150};
            case SCHIST:
                return new int[]{128, 134, 148};
            case QUARTZITE:
                return new int[]{210, 206, 200};
            case SLATE:
                return new int[]{84, 92, 104};
            case ANDESITE:
                return new int[]{136, 136, 140};
            case CONGLOMERATE:
                return new int[]{170, 158, 140};
            case STONE:
                return new int[]{125, 125, 125};
            case SAND:
                return new int[]{219, 207, 163};
            case RED_SAND:
                return new int[]{190, 120, 60};
            case GRAVEL:
                return new int[]{134, 128, 120};
            case DIRT:
                return new int[]{134, 96, 67};
            case GRASS:
                return new int[]{95, 159, 53};
            case CLAY:
                return new int[]{158, 164, 176};
            case BEDROCK:
                return new int[]{40, 40, 40};
            default:
                return new int[]{255, 0, 255};
        }
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ----------------------------------------------------------------- stats

    private static final class Stats {
        int n;
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        long sumH;
        int ocean;
        int river;
        int lake;
        int wet;
        int pit;
        Map<String, Integer> formations = new HashMap<String, Integer>();
        Map<String, Integer> biomes = new HashMap<String, Integer>();
        Map<String, Integer> bounds = new HashMap<String, Integer>();
        Map<Integer, Integer> plates = new HashMap<Integer, Integer>();

        void add(ColumnData c) {
            n++;
            if (c.finalHeight < minH) {
                minH = c.finalHeight;
            }
            if (c.finalHeight > maxH) {
                maxH = c.finalHeight;
            }
            sumH += c.finalHeight;
            if (c.ocean) {
                ocean++;
            }
            if (c.river01 > 0.45) {
                river++;
            }
            if (c.lake01 > 0.4) {
                lake++;
            }
            if (c.wet01 > 0.5) {
                wet++;
            }
            if (c.pit) {
                pit++;
            }
            bump(formations, c.formation.toString());
            bump(biomes, c.biome.toString());
            bump(bounds, c.boundary.dominant.toString());
            bump(plates, c.plateId);
        }

        private <K> void bump(Map<K, Integer> m, K k) {
            Integer v = m.get(k);
            m.put(k, v == null ? 1 : v + 1);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("samples=").append(n);
            sb.append(" height[min=").append(minH).append(" max=").append(maxH);
            sb.append(" mean=").append(String.format("%.1f", (double) sumH / Math.max(1, n))).append("]");
            sb.append(" ocean%=").append(String.format("%.1f", 100.0 * ocean / Math.max(1, n)));
            sb.append(" river%=").append(String.format("%.2f", 100.0 * river / Math.max(1, n)));
            sb.append(" lake%=").append(String.format("%.2f", 100.0 * lake / Math.max(1, n)));
            sb.append(" wet%=").append(String.format("%.2f", 100.0 * wet / Math.max(1, n)));
            sb.append(" pit%=").append(String.format("%.2f", 100.0 * pit / Math.max(1, n)));
            sb.append(" plates=").append(plates.size());
            sb.append("\nformations=").append(formations);
            sb.append("\nboundaries=").append(bounds);
            sb.append("\nbiomes=").append(biomes);
            return sb.toString();
        }
    }

    private static void writePpm(String path, int w, int h, byte[] rgb) throws IOException {
        OutputStream os = new FileOutputStream(path);
        try {
            String header = "P6\n" + w + " " + h + "\n255\n";
            os.write(header.getBytes("US-ASCII"));
            os.write(rgb);
        } finally {
            os.close();
        }
    }
}
