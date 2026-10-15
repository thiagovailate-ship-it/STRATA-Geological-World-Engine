package dev.strata.core.stratigraphy;

/**
 * Every placeable material the geological model can request, with no
 * Minecraft dependency. The Forge layer translates each entry to a real
 * {@code (Block, metadata)} pair (see {@code dev.strata.blocks.StrataBlocks}).
 *
 * <p>The first twelve entries are STRATA's own rocks (one block id, metadata
 * 0-11); the rest map to vanilla blocks.</p>
 */
public enum GeoMaterial {
    GRANITE("granite", 0, 0.85),
    BASALT("basalt", 1, 0.90),
    LIMESTONE("limestone", 2, 0.45),
    SANDSTONE("sandstone", 3, 0.50),
    SHALE("shale", 4, 0.35),
    MARBLE("marble", 5, 0.55),
    GNEISS("gneiss", 6, 0.80),
    SCHIST("schist", 7, 0.60),
    QUARTZITE("quartzite", 8, 0.95),
    SLATE("slate", 9, 0.55),
    ANDESITE("andesite", 10, 0.75),
    CONGLOMERATE("conglomerate", 11, 0.50),

    STONE("stone", -1, 0.70),
    SAND("sand", -1, 0.20),
    RED_SAND("red_sand", -1, 0.20),
    GRAVEL("gravel", -1, 0.25),
    DIRT("dirt", -1, 0.15),
    GRASS("grass", -1, 0.15),
    CLAY("clay", -1, 0.30),
    BEDROCK("bedrock", -1, 1.00),
    WATER("water", -1, 0.00),
    AIR("air", -1, 0.00);

    private final String name;
    private final int strataMeta;
    private final double hardness01;

    GeoMaterial(String name, int strataMeta, double hardness01) {
        this.name = name;
        this.strataMeta = strataMeta;
        this.hardness01 = hardness01;
    }

    /** Stable rock name (textures, lang keys, debug output). */
    public String getName() {
        return name;
    }

    /** Metadata on the STRATA rock block, or -1 for vanilla-mapped entries. */
    public int getStrataMeta() {
        return strataMeta;
    }

    /** Relative resistance used by the erosion model. */
    public double getHardness01() {
        return hardness01;
    }

    /** True for the twelve STRATA rock variants. */
    public boolean isStrataRock() {
        return strataMeta >= 0;
    }

    private static final GeoMaterial[] BY_META = new GeoMaterial[12];

    static {
        for (GeoMaterial m : values()) {
            if (m.strataMeta >= 0 && m.strataMeta < BY_META.length) {
                BY_META[m.strataMeta] = m;
            }
        }
    }

    /** Rock variant for a block metadata value (out of range gives stone). */
    public static GeoMaterial byMeta(int meta) {
        if (meta < 0 || meta >= BY_META.length || BY_META[meta] == null) {
            return STONE;
        }
        return BY_META[meta];
    }
}
