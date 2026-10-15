package net.minecraft.world.chunk;
import net.minecraft.block.Block;
import net.minecraft.world.World;
public class Chunk {
    public Chunk(World world, Block[] blocks, byte[] meta, int x, int z) {}
    public byte[] getBiomeArray() { return null; }
    public void setBiomeArray(byte[] a) {}
    public void generateSkylightMap() {}
}
