package dev.strata.blocks;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import dev.strata.core.stratigraphy.GeoMaterial;

/**
 * Item form of the STRATA rock block: one item id, twelve named subtypes.
 */
public class ItemStrataRock extends ItemBlock {

    public ItemStrataRock(Block block) {
        super(block);
        setMaxDamage(0);
        setHasSubtypes(true);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int meta = stack == null ? 0 : stack.getItemDamage();
        return super.getUnlocalizedName() + "." + GeoMaterial.byMeta(meta).getName();
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }
}
