package dev.strata.core.terrain;

import dev.strata.core.stratigraphy.GeoMaterial;

/**
 * Deterministic climate/elevation -> biome table plus each biome's default
 * surface materials (used by the surface planner when geology does not
 * override them: deserts stay sandy, mesas stay red, forests stay grassy).
 */
public final class BiomeMapper {

    private BiomeMapper() {
    }

    /**
     * Maps local conditions to a biome kind. Pure function; boundaries between
     * kinds are step functions of smooth fields (standard for biome maps —
     * abruptness is hidden by the smooth underlying fields and by surface
     * blending, never by chunk seams).
     */
    public static BiomeKind map(double temp01, double moist01, double height, int seaLevel,
                                boolean ocean, boolean deepOcean, boolean beach,
                                double river01, double slope) {
        if (ocean) {
            if (temp01 < 0.24) {
                return BiomeKind.FROZEN_OCEAN;
            }
            return deepOcean ? BiomeKind.DEEP_OCEAN : BiomeKind.OCEAN;
        }
        if (river01 > 0.45) {
            return temp01 < 0.25 ? BiomeKind.FROZEN_RIVER : BiomeKind.RIVER;
        }
        if (beach) {
            if (temp01 < 0.25) {
                return BiomeKind.COLD_BEACH;
            }
            return slope > 0.45 ? BiomeKind.STONE_BEACH : BiomeKind.BEACH;
        }

        double rel = height - seaLevel;
        // High country is rocky everywhere; frozen if cold enough.
        if (rel > 80.0 && temp01 < 0.30) {
            return BiomeKind.ICE_MOUNTAINS;
        }
        if (rel > 55.0) {
            return BiomeKind.EXTREME_HILLS;
        }

        if (temp01 < 0.25) {
            return BiomeKind.ICE_PLAINS;
        }
        if (temp01 < 0.40) {
            if (moist01 > 0.45) {
                return BiomeKind.COLD_TAIGA;
            }
            if (moist01 > 0.30) {
                return BiomeKind.TAIGA;
            }
            return BiomeKind.PLAINS;
        }
        if (temp01 < 0.60) {
            if (moist01 > 0.70 && temp01 < 0.45) {
                return BiomeKind.MEGA_TAIGA;
            }
            if (moist01 > 0.65) {
                return BiomeKind.BIRCH_FOREST;
            }
            if (moist01 > 0.50) {
                return BiomeKind.FOREST;
            }
            if (moist01 < 0.28) {
                return BiomeKind.SAVANNA;
            }
            return BiomeKind.PLAINS;
        }
        // Hot climates.
        if (moist01 < 0.25) {
            return BiomeKind.DESERT;
        }
        if (moist01 < 0.32 && temp01 > 0.65 && rel > 25.0) {
            return rel > 45.0 ? BiomeKind.MESA_PLATEAU : BiomeKind.MESA;
        }
        if (moist01 < 0.40) {
            return BiomeKind.SAVANNA;
        }
        if (moist01 > 0.70 && rel < 6.0) {
            return BiomeKind.SWAMPLAND;
        }
        if (moist01 > 0.62 && temp01 > 0.70) {
            return BiomeKind.JUNGLE;
        }
        if (moist01 > 0.60) {
            return BiomeKind.ROOFED_FOREST;
        }
        return BiomeKind.PLAINS;
    }

    /** Default top surface material for a biome (geology may override). */
    public static GeoMaterial defaultTop(BiomeKind biome) {
        switch (biome) {
            case DESERT:
            case BEACH:
                return GeoMaterial.SAND;
            case MESA:
            case MESA_PLATEAU:
                return GeoMaterial.RED_SAND;
            case COLD_BEACH:
            case STONE_BEACH:
                return GeoMaterial.GRAVEL;
            case RIVER:
            case FROZEN_RIVER:
                return GeoMaterial.GRAVEL;
            case OCEAN:
            case DEEP_OCEAN:
            case FROZEN_OCEAN:
                return GeoMaterial.SAND;
            case EXTREME_HILLS:
            case ICE_MOUNTAINS:
                return GeoMaterial.STONE;
            case ICE_PLAINS:
                return GeoMaterial.DIRT;
            case SWAMPLAND:
                return GeoMaterial.GRASS;
            default:
                return GeoMaterial.GRASS;
        }
    }

    /** Default subsurface material for a biome (geology may override). */
    public static GeoMaterial defaultFiller(BiomeKind biome) {
        switch (biome) {
            case DESERT:
            case BEACH:
                return GeoMaterial.SAND;
            case MESA:
            case MESA_PLATEAU:
                return GeoMaterial.RED_SAND;
            case EXTREME_HILLS:
            case ICE_MOUNTAINS:
            case STONE_BEACH:
                return GeoMaterial.STONE;
            default:
                return GeoMaterial.DIRT;
        }
    }
}
