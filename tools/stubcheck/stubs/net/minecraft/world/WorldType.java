package net.minecraft.world;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
public class WorldType {
    public WorldType(String name) {}
    public WorldChunkManager getChunkManager(World world) { return null; }
    public IChunkProvider getChunkGenerator(World world, String options) { return null; }
    public float getCloudHeight() { return 0; }
}
