package dev.strata.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import dev.strata.core.stratigraphy.GeoMaterial;

/**
 * STRATA's twelve rock variants on a single block id (metadata 0-11).
 * One id keeps the block palette small; hardness/resistance are uniform and
 * the geological differences live in the world generator, not the block.
 */
public class BlockStrataRock extends Block {

    private IIcon[] icons = new IIcon[12];

    public BlockStrataRock() {
        super(Material.rock);
        setBlockName("strata:strataRock");
        setCreativeTab(CreativeTabs.tabBlock);
        setHardness(1.6F);
        setResistance(10.0F);
        setStepSound(soundTypeStone);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        for (int i = 0; i < icons.length; i++) {
            icons[i] = reg.registerIcon("strata:rock_" + GeoMaterial.byMeta(i).getName());
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        if (meta < 0 || meta >= icons.length) {
            meta = 0;
        }
        return icons[meta];
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < icons.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
}
