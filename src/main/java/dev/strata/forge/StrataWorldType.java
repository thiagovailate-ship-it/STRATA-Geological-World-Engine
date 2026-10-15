package dev.strata.forge;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;

/**
 * The "STRATA" world type. Only the overworld (dimension 0) uses the
 * geological generator; other dimensions fall back to vanilla providers so
 * the Nether and the End keep working in STRATA worlds.
 */
public class StrataWorldType extends WorldType {

    public StrataWorldType() {
        super("STRATA");
    }

    @Override
    public WorldChunkManager getChunkManager(World world) {
        if (world.provider.dimensionId == 0) {
            return new StrataWorldChunkManager(world);
        }
        return new WorldChunkManager(world);
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        boolean mapFeatures = world.getWorldInfo().isMapFeaturesEnabled();
        if (world.provider.dimensionId == 0) {
            return new StrataChunkProvider(world, world.getSeed(), mapFeatures);
        }
        return new ChunkProviderGenerate(world, world.getSeed(), mapFeatures);
    }

    @Override
    public float getCloudHeight() {
        // STRATA ranges reach ~190; keep clouds above the highest peaks.
        return 208.0F;
    }
}
