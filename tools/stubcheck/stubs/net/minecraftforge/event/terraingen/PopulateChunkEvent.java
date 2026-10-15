package net.minecraftforge.event.terraingen;
import java.util.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
public class PopulateChunkEvent {
    public static class Pre {
        public Pre(IChunkProvider p, World w, Random r, int x, int z, boolean village) {}
    }
    public static class Post {
        public Post(IChunkProvider p, World w, Random r, int x, int z, boolean village) {}
    }
    public static class Populate {
        public enum EventType {
            LAKE, LAVA, DUNGEON, ANIMALS, ICE
        }
    }
}
