package net.minecraft.world;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.storage.WorldInfo;
public class World {
    public WorldProvider provider;
    public Random rand;
    public long getSeed() { return 0; }
    public WorldInfo getWorldInfo() { return null; }
    public BiomeGenBase getBiomeGenForCoords(int x, int z) { return null; }
    public int getPrecipitationHeight(int x, int z) { return 0; }
    public boolean isBlockFreezable(int x, int y, int z) { return false; }
    public boolean func_147478_e(int x, int y, int z, boolean b) { return false; }
    public boolean setBlock(int x, int y, int z, Block b, int meta, int flags) { return false; }
    public Block getBlock(int x, int y, int z) { return null; }
}
