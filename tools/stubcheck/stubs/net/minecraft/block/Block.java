package net.minecraft.block;
import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;
public class Block {
    public static class SoundType {}
    public static final SoundType soundTypeStone = new SoundType();
    protected Block(Material m) {}
    public Block setBlockName(String s) { return this; }
    public Block setCreativeTab(CreativeTabs t) { return this; }
    public Block setHardness(float f) { return this; }
    public Block setResistance(float f) { return this; }
    public Block setStepSound(SoundType s) { return this; }
    public void setHarvestLevel(String tool, int level) {}
    public void registerBlockIcons(IIconRegister reg) {}
    public IIcon getIcon(int side, int meta) { return null; }
    public int damageDropped(int meta) { return 0; }
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {}
}
