package net.minecraftforge.event.terraingen;
import java.util.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;
public class TerrainGen {
    public static MapGenBase getModdedMapGen(MapGenBase original, InitMapGenEvent.EventType type) { return null; }
    public static boolean populate(IChunkProvider p, World w, Random r, int x, int z,
            boolean village, PopulateChunkEvent.Populate.EventType type) { return false; }
}
