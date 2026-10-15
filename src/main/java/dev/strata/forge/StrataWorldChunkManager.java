package dev.strata.forge;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;

/**
 * Biome source for STRATA worlds: every query samples the geological engine
 * directly, so the biome at a column always agrees with the terrain, the
 * chunk biome array, decoration and mob spawning.
 *
 * <p>Threading: Minecraft queries biomes from several threads (server world
 * gen, client rendering). The engine keeps per-instance caches that are not
 * safe to share, so each thread lazily owns one model plus one scratch
 * column. Models are cheap to create; caches warm within a few queries.</p>
 */
public class StrataWorldChunkManager extends WorldChunkManager {

    private final long seed;
    private final ThreadLocal<StrataWorldModel> models = new ThreadLocal<StrataWorldModel>();
    private final ThreadLocal<ColumnData> columns = new ThreadLocal<ColumnData>();

    public StrataWorldChunkManager(World world) {
        super(world);
        this.seed = world.getSeed();
    }

    private StrataWorldModel model() {
        StrataWorldModel m = models.get();
        if (m == null) {
            m = new StrataWorldModel(seed, StrataForgeConfig.snapshot());
            models.set(m);
        }
        return m;
    }

    private ColumnData scratch() {
        ColumnData c = columns.get();
        if (c == null) {
            c = new ColumnData();
            columns.set(c);
        }
        return c;
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        ColumnData c = scratch();
        model().sampleColumn(x, z, c);
        return StrataBiomeMap.toVanilla(c.biome);
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] reuse, int x, int z, int w, int d) {
        BiomeGenBase[] out = reuse;
        if (out == null || out.length < w * d) {
            out = new BiomeGenBase[w * d];
        }
        for (int dz = 0; dz < d; dz++) {
            for (int dx = 0; dx < w; dx++) {
                out[dx + dz * w] = getBiomeGenAt(x + dx, z + dz);
            }
        }
        return out;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] reuse, int x, int z, int w, int d) {
        return getBiomesForGeneration(reuse, x, z, w, d);
    }

    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] reuse, int x, int z, int w, int d, boolean cached) {
        return getBiomesForGeneration(reuse, x, z, w, d);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean areBiomesViable(int x, int z, int radius, List allowed) {
        int x0 = x - radius >> 2;
        int z0 = z - radius >> 2;
        int x1 = x + radius >> 2;
        int z1 = z + radius >> 2;
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        BiomeGenBase[] arr = getBiomesForGeneration((BiomeGenBase[]) null, x0, z0, w, d);
        for (int i = 0; i < w * d; i++) {
            if (!allowed.contains(arr[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ChunkPosition findBiomePosition(int x, int z, int range, List allowed, Random random) {
        int x0 = x - range >> 2;
        int z0 = z - range >> 2;
        int x1 = x + range >> 2;
        int z1 = z + range >> 2;
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        BiomeGenBase[] arr = getBiomesForGeneration((BiomeGenBase[]) null, x0, z0, w, d);
        ChunkPosition best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < w * d; i++) {
            int bx = (x0 + i % w) << 2;
            int bz = (z0 + i / w) << 2;
            if (allowed.contains(arr[i])) {
                int dx = bx - x;
                int dz = bz - z;
                int dist = dx * dx + dz * dz;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new ChunkPosition(bx, 0, bz);
                }
            }
        }
        return best;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List getBiomesToSpawnIn() {
        return Arrays.asList(new BiomeGenBase[] {
                BiomeGenBase.plains, BiomeGenBase.forest,
                BiomeGenBase.taiga, BiomeGenBase.savanna });
    }

    @Override
    public float[] getRainfall(float[] reuse, int x, int z, int w, int d) {
        float[] out = reuse;
        if (out == null || out.length < w * d) {
            out = new float[w * d];
        }
        for (int dz = 0; dz < d; dz++) {
            for (int dx = 0; dx < w; dx++) {
                out[dx + dz * w] = getBiomeGenAt(x + dx, z + dz).rainfall;
            }
        }
        return out;
    }

    @Override
    public float getTemperatureAtHeight(float temp, int y) {
        return temp;
    }
}
