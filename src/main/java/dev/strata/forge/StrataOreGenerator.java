package dev.strata.forge;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import cpw.mods.fml.common.IWorldGenerator;
import dev.strata.blocks.StrataBlocks;
import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.stratigraphy.Formation;
import dev.strata.core.terrain.BiomeKind;

/**
 * Strata-aware ores for STRATA worlds: vein placement reads the same engine
 * columns the terrain was built from, so coal sits in sedimentary basins,
 * gold follows orogens and rifts, and emeralds hide in extreme hills.
 *
 * <p>Runs as an {@code IWorldGenerator} after populate, so the provider's
 * thread-local engine is already hot and veins cost microseconds. Vanilla
 * decorator ores still run too, but they only replace vanilla stone, which
 * is rare inside STRATA's own rocks — these veins carry the economy.</p>
 */
public class StrataOreGenerator implements IWorldGenerator {

    private static final int ANY = 0;
    private static final int COAL = 1;
    private static final int GOLD = 2;
    private static final int LAPIS = 3;
    private static final int EMERALD = 4;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.dimensionId != 0) {
            return;
        }
        if (!(world.getWorldInfo().getTerrainType() instanceof StrataWorldType)) {
            return;
        }
        if (!(chunkGenerator instanceof StrataChunkProvider)) {
            return;
        }
        StrataWorldModel model =
                ((StrataChunkProvider) chunkGenerator).modelForThread();
        ColumnData col = new ColumnData();
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        for (int i = 0; i < 6; i++) {
            tryVein(random, world, model, col, baseX, baseZ,
                    Blocks.coal_ore, 7, 8, 64, COAL);
        }
        for (int i = 0; i < 5; i++) {
            tryVein(random, world, model, col, baseX, baseZ,
                    Blocks.iron_ore, 6, 4, 48, ANY);
        }
        for (int i = 0; i < 3; i++) {
            tryVein(random, world, model, col, baseX, baseZ,
                    Blocks.gold_ore, 5, 4, 28, GOLD);
        }
        for (int i = 0; i < 2; i++) {
            tryVein(random, world, model, col, baseX, baseZ,
                    Blocks.lapis_ore, 4, 4, 18, LAPIS);
        }
        for (int i = 0; i < 3; i++) {
            tryVein(random, world, model, col, baseX, baseZ,
                    Blocks.redstone_ore, 5, 2, 14, ANY);
        }
        for (int i = 0; i < 2; i++) {
            tryVein(random, world, model, col, baseX, baseZ,
                    Blocks.diamond_ore, 4, 2, 12, ANY);
        }
        for (int i = 0; i < 2; i++) {
            tryVein(random, world, model, col, baseX, baseZ,
                    Blocks.emerald_ore, 2, 4, 32, EMERALD);
        }
    }

    private boolean tryVein(Random rand, World world, StrataWorldModel model,
            ColumnData col, int baseX, int baseZ, Block ore, int size,
            int minY, int maxY, int predicate) {
        int x = baseX + rand.nextInt(16);
        int z = baseZ + rand.nextInt(16);
        model.sampleColumn(x, z, col);
        Formation f = col.formation;
        if (predicate == COAL) {
            if (f != Formation.CLASTIC_BASIN && f != Formation.SHIELD
                    && f != Formation.ALLUVIAL && f != Formation.RIFT) {
                return false;
            }
        } else if (predicate == GOLD) {
            if (f != Formation.OROGEN && f != Formation.RIFT
                    && f != Formation.VOLCANIC) {
                return false;
            }
        } else if (predicate == LAPIS) {
            if (f != Formation.OROGEN) {
                return false;
            }
        } else if (predicate == EMERALD) {
            if (col.biome != BiomeKind.EXTREME_HILLS) {
                return false;
            }
        }
        int y = minY + rand.nextInt(maxY - minY + 1);
        if (y >= col.finalHeight - 2) {
            return false;
        }
        placeVein(world, rand, x, y, z, size, ore);
        return true;
    }

    /** Short random-walk vein through replaceable stone (never air/water). */
    private void placeVein(World world, Random rand, int x, int y, int z,
            int size, Block ore) {
        for (int i = 0; i < size; i++) {
            setOre(world, x, y, z, ore);
            if (rand.nextBoolean()) {
                setOre(world, x + rand.nextInt(2), y, z + rand.nextInt(2), ore);
            }
            x += rand.nextInt(3) - 1;
            y += rand.nextInt(3) - 1;
            z += rand.nextInt(3) - 1;
            if (y < 2) {
                y = 2;
            }
            if (y > 250) {
                y = 250;
            }
        }
    }

    private void setOre(World world, int x, int y, int z, Block ore) {
        Block b = world.getBlock(x, y, z);
        if (b != null && StrataBlocks.isReplaceableStone(b)) {
            world.setBlock(x, y, z, ore, 0, 2);
        }
    }
}
