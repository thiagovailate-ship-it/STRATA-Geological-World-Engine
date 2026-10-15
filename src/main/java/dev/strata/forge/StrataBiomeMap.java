package dev.strata.forge;

import net.minecraft.world.biome.BiomeGenBase;
import dev.strata.core.terrain.BiomeKind;

/**
 * Maps engine biome kinds to vanilla 1.7.10 biomes, one to one.
 *
 * <p>STRATA v1 deliberately adds no custom biomes: the geological structure
 * (terrain, strata, rivers, lakes) is the differentiator, while decoration,
 * mob spawning, village placement and potion effects keep exact vanilla
 * behavior through the biome the engine chose.</p>
 */
public final class StrataBiomeMap {

    private StrataBiomeMap() {
    }

    public static BiomeGenBase toVanilla(BiomeKind kind) {
        if (kind == null) {
            return BiomeGenBase.plains;
        }
        switch (kind) {
        case OCEAN:
            return BiomeGenBase.ocean;
        case DEEP_OCEAN:
            return BiomeGenBase.deepOcean;
        case FROZEN_OCEAN:
            return BiomeGenBase.frozenOcean;
        case RIVER:
            return BiomeGenBase.river;
        case FROZEN_RIVER:
            return BiomeGenBase.frozenRiver;
        case BEACH:
            return BiomeGenBase.beach;
        case STONE_BEACH:
            return BiomeGenBase.stoneBeach;
        case COLD_BEACH:
            return BiomeGenBase.coldBeach;
        case DESERT:
            return BiomeGenBase.desert;
        case SAVANNA:
            return BiomeGenBase.savanna;
        case MESA:
            return BiomeGenBase.mesa;
        case MESA_PLATEAU:
            return BiomeGenBase.mesaPlateau_F;
        case PLAINS:
            return BiomeGenBase.plains;
        case FOREST:
            return BiomeGenBase.forest;
        case BIRCH_FOREST:
            return BiomeGenBase.birchForest;
        case ROOFED_FOREST:
            return BiomeGenBase.roofedForest;
        case TAIGA:
            return BiomeGenBase.taiga;
        case COLD_TAIGA:
            return BiomeGenBase.coldTaiga;
        case MEGA_TAIGA:
            return BiomeGenBase.megaTaiga;
        case JUNGLE:
            return BiomeGenBase.jungle;
        case SWAMPLAND:
            return BiomeGenBase.swampland;
        case EXTREME_HILLS:
            return BiomeGenBase.extremeHills;
        case ICE_PLAINS:
            return BiomeGenBase.icePlains;
        case ICE_MOUNTAINS:
            return BiomeGenBase.iceMountains;
        default:
            return BiomeGenBase.plains;
        }
    }
}
