package net.minecraft.world.biome;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.World;
public class BiomeGenBase {
    public int biomeID;
    public float rainfall;
    public static BiomeGenBase ocean, deepOcean, frozenOcean, river, frozenRiver;
    public static BiomeGenBase beach, stoneBeach, coldBeach, desert, desertHills;
    public static BiomeGenBase savanna, mesa, mesaPlateau_F, plains, forest;
    public static BiomeGenBase birchForest, roofedForest, taiga, coldTaiga;
    public static BiomeGenBase megaTaiga, jungle, swampland, extremeHills;
    public static BiomeGenBase icePlains, iceMountains;
    public List getSpawnableList(EnumCreatureType type) { return null; }
    public void decorate(World world, Random rand, int x, int z) {}
}
