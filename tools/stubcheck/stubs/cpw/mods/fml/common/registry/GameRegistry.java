package cpw.mods.fml.common.registry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import cpw.mods.fml.common.IWorldGenerator;
public class GameRegistry {
    public static void registerBlock(Block block, Class<? extends ItemBlock> itemclass, String name) {}
    public static void registerWorldGenerator(IWorldGenerator gen, int weight) {}
}
