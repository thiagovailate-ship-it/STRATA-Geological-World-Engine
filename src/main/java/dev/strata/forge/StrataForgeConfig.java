package dev.strata.forge;

import net.minecraftforge.common.config.Configuration;
import dev.strata.core.config.StrataWorldConfig;

/**
 * Forge configuration bridge: loads {@code config/strata.cfg} into an
 * immutable snapshot the engine copies per world. Every engine option is
 * exposed, plus vanilla-structure toggles for the chunk provider.
 *
 * <p>Changing these values requires a Minecraft restart; existing chunks are
 * never rewritten, so retuning mid-world creates (geologically honest) new
 * terrain only in unexplored areas.</p>
 */
public final class StrataForgeConfig {

    private static StrataWorldConfig active = validated(new StrataWorldConfig());

    public static boolean enableCaves = true;
    public static boolean enableRavines = true;
    public static boolean enableMineshafts = true;
    public static boolean enableVillages = true;
    public static boolean enableStrongholds = true;
    public static boolean enableScatteredFeatures = true;
    public static boolean enableDungeons = true;
    public static boolean enableSnowLayers = true;
    public static boolean enableLavaLakes = true;
    /** Vanilla water lakes; off by default because the engine grows its own. */
    public static boolean enableWaterLakes = false;

    private StrataForgeConfig() {
    }

    private static StrataWorldConfig validated(StrataWorldConfig c) {
        c.validate();
        return c;
    }

    public static void load(Configuration config) {
        config.addCustomCategoryComment("worldgen",
                "STRATA geological world engine. Distances in blocks, intensities are multipliers around 1.0.");
        config.addCustomCategoryComment("structures",
                "Vanilla structure toggles for STRATA worlds (caves, villages, strongholds, ...).");

        StrataWorldConfig c = new StrataWorldConfig();
        c.reliefIntensity = config.get("worldgen", "reliefIntensity", 1.0).getDouble();
        c.mountainIntensity = config.get("worldgen", "mountainIntensity", 1.0).getDouble();
        c.plateCellSize = config.get("worldgen", "plateCellSize", 1536).getInt();
        c.oceanFraction = config.get("worldgen", "oceanFraction", 0.55).getDouble();
        c.continentWavelength = config.get("worldgen", "continentWavelength", 2400.0).getDouble();
        c.warpAmplitude = config.get("worldgen", "warpAmplitude", 320.0).getDouble();
        c.warpWavelength = config.get("worldgen", "warpWavelength", 2800.0).getDouble();
        c.erosionStrength = config.get("worldgen", "erosionStrength", 1.0).getDouble();
        c.roughnessIntensity = config.get("worldgen", "roughnessIntensity", 1.0).getDouble();
        c.drainageIntensity = config.get("worldgen", "drainageIntensity", 1.0).getDouble();
        c.seaLevel = config.get("worldgen", "seaLevel", 63).getInt();
        c.maxSurfaceHeight = config.get("worldgen", "maxSurfaceHeight", 248).getInt();
        c.enableOrogen = config.get("worldgen", "enableOrogen", true).getBoolean();
        c.enableRivers = config.get("worldgen", "enableRivers", true).getBoolean();
        c.enableErosion = config.get("worldgen", "enableErosion", true).getBoolean();
        c.enableFaultScarps = config.get("worldgen", "enableFaultScarps", true).getBoolean();
        c.hydroCellSize = config.get("worldgen", "hydroCellSize", 8).getInt();
        c.hydroTileCells = config.get("worldgen", "hydroTileCells", 16).getInt();
        c.hydroRadiusCells = config.get("worldgen", "hydroRadiusCells", 12).getInt();
        active = validated(c);

        enableCaves = config.get("structures", "enableCaves", true).getBoolean();
        enableRavines = config.get("structures", "enableRavines", true).getBoolean();
        enableMineshafts = config.get("structures", "enableMineshafts", true).getBoolean();
        enableVillages = config.get("structures", "enableVillages", true).getBoolean();
        enableStrongholds = config.get("structures", "enableStrongholds", true).getBoolean();
        enableScatteredFeatures = config.get("structures", "enableScatteredFeatures", true).getBoolean();
        enableDungeons = config.get("structures", "enableDungeons", true).getBoolean();
        enableSnowLayers = config.get("structures", "enableSnowLayers", true).getBoolean();
        enableLavaLakes = config.get("structures", "enableLavaLakes", true).getBoolean();
        enableWaterLakes = config.get("structures", "enableWaterLakes", false).getBoolean();

        if (config.hasChanged()) {
            config.save();
        }
    }

    /** Defensive copy of the active engine configuration. */
    public static StrataWorldConfig snapshot() {
        return active.copy();
    }
}
