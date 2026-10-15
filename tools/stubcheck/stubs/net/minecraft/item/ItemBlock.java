package net.minecraft.item;
import net.minecraft.block.Block;
public class ItemBlock extends Item {
    public ItemBlock(Block block) {}
    public String getUnlocalizedName() { return ""; }
    public String getUnlocalizedName(ItemStack stack) { return ""; }
    public int getMetadata(int damage) { return 0; }
}
