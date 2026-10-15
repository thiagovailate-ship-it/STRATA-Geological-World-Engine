package dev.strata.blocks;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.strata.core.stratigraphy.GeoMaterial;

/**
 * Block holder and the single place that translates geological materials to
 * real {@code (Block, metadata)} pairs. Everything else in the Forge layer
 * speaks {@link GeoMaterial} and never touches vanilla block ids directly.
 */
public final class StrataBlocks {

    /** The twelve-variant STRATA rock block (null until {@link #init}). */
    public static Block strataRock;

    private StrataBlocks() {
    }

    public static void init() {
        strataRock = new BlockStrataRock();
        GameRegistry.registerBlock(strataRock, ItemStrataRock.class, "strataRock");
    }

    /**
     * Writes one column cell into raw chunk arrays. Index layout matches the
     * 1.7.10 chunk constructor: {@code (x * 16 + z) * 256 + y}.
     */
    public static void put(Block[] blocks, byte[] meta, int index, GeoMaterial m) {
        if (m == null) {
            m = GeoMaterial.STONE;
        }
        if (m.isStrataRock()) {
            blocks[index] = strataRock;
            meta[index] = (byte) m.getStrataMeta();
            return;
        }
        if (m == GeoMaterial.SAND) {
            blocks[index] = Blocks.sand;
            meta[index] = 0;
        } else if (m == GeoMaterial.RED_SAND) {
            blocks[index] = Blocks.sand;
            meta[index] = 1;
        } else if (m == GeoMaterial.GRAVEL) {
            blocks[index] = Blocks.gravel;
            meta[index] = 0;
        } else if (m == GeoMaterial.DIRT) {
            blocks[index] = Blocks.dirt;
            meta[index] = 0;
        } else if (m == GeoMaterial.GRASS) {
            blocks[index] = Blocks.grass;
            meta[index] = 0;
        } else if (m == GeoMaterial.CLAY) {
            blocks[index] = Blocks.clay;
            meta[index] = 0;
        } else if (m == GeoMaterial.BEDROCK) {
            blocks[index] = Blocks.bedrock;
            meta[index] = 0;
        } else if (m == GeoMaterial.WATER) {
            blocks[index] = Blocks.water;
            meta[index] = 0;
        } else if (m == GeoMaterial.AIR) {
            blocks[index] = null;
            meta[index] = 0;
        } else {
            blocks[index] = Blocks.stone;
            meta[index] = 0;
        }
    }

    /** True for blocks our ore veins may replace (stone or any strata rock). */
    public static boolean isReplaceableStone(Block b) {
        return b == Blocks.stone || b == strataRock;
    }
}
