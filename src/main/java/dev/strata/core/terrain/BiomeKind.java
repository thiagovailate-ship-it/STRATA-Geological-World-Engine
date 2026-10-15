package dev.strata.core.terrain;

/**
 * STRATA biome identifiers. These are engine-level kinds; the Forge layer
 * maps each one to a vanilla {@code BiomeGenBase} (1.7.10 has no custom-biome
 * need for v1 — the geological structure is the differentiator).
 */
public enum BiomeKind {
    OCEAN,
    DEEP_OCEAN,
    FROZEN_OCEAN,
    RIVER,
    FROZEN_RIVER,
    BEACH,
    STONE_BEACH,
    COLD_BEACH,
    DESERT,
    SAVANNA,
    MESA,
    MESA_PLATEAU,
    PLAINS,
    FOREST,
    BIRCH_FOREST,
    ROOFED_FOREST,
    TAIGA,
    COLD_TAIGA,
    MEGA_TAIGA,
    JUNGLE,
    SWAMPLAND,
    EXTREME_HILLS,
    ICE_PLAINS,
    ICE_MOUNTAINS
}
