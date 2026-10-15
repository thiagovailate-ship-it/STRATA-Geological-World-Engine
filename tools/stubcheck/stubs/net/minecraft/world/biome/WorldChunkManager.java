package net.minecraft.world.biome;
import java.util.List;
import java.util.Random;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
public class WorldChunkManager {
    public WorldChunkManager(World world) {}
    public BiomeGenBase getBiomeGenAt(int x, int z) { return null; }
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] a, int x, int z, int w, int d) { return null; }
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] a, int x, int z, int w, int d) { return null; }
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] a, int x, int z, int w, int d, boolean c) { return null; }
    public boolean areBiomesViable(int x, int z, int r, List allowed) { return false; }
    public ChunkPosition findBiomePosition(int x, int z, int r, List allowed, Random rand) { return null; }
    public List getBiomesToSpawnIn() { return null; }
    public float[] getRainfall(float[] a, int x, int z, int w, int d) { return null; }
    public float getTemperatureAtHeight(float t, int y) { return 0; }
}
