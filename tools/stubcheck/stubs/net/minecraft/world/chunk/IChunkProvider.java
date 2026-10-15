package net.minecraft.world.chunk;
import java.util.List;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
public interface IChunkProvider {
    Chunk provideChunk(int x, int z);
    Chunk loadChunk(int x, int z);
    boolean chunkExists(int x, int z);
    void populate(IChunkProvider p, int x, int z);
    boolean saveChunks(boolean flag, IProgressUpdate progress);
    boolean unloadQueuedChunks();
    boolean canSave();
    String makeString();
    List getPossibleCreatures(EnumCreatureType type, int x, int y, int z);
    ChunkPosition func_147416_a(World world, String name, int x, int y, int z);
    int getLoadedChunkCount();
    void recreateStructures(int x, int z);
    void saveExtraData();
}
