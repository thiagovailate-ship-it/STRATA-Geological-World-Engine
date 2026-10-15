package net.minecraft.world.gen;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
public class ChunkProviderGenerate implements IChunkProvider {
    public ChunkProviderGenerate(World world, long seed, boolean features) {}
    public net.minecraft.world.chunk.Chunk provideChunk(int x, int z) { return null; }
    public net.minecraft.world.chunk.Chunk loadChunk(int x, int z) { return null; }
    public boolean chunkExists(int x, int z) { return false; }
    public void populate(IChunkProvider p, int x, int z) {}
    public boolean saveChunks(boolean f, net.minecraft.util.IProgressUpdate u) { return false; }
    public boolean unloadQueuedChunks() { return false; }
    public boolean canSave() { return false; }
    public String makeString() { return null; }
    public java.util.List getPossibleCreatures(net.minecraft.entity.EnumCreatureType t, int x, int y, int z) { return null; }
    public net.minecraft.world.ChunkPosition func_147416_a(World w, String s, int x, int y, int z) { return null; }
    public int getLoadedChunkCount() { return 0; }
    public void recreateStructures(int x, int z) {}
    public void saveExtraData() {}
}
