package gregtech.common.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.util.GTLanguageManager;

public abstract class ItemCasingsAbstract extends ItemBlock {

    protected final String mNoMobsToolTip = GTLanguageManager
        .addStringLocalization("gt.nomobspawnsonthisblock", "Mobs cannot Spawn on this Block");
    protected final String mNoTileEntityToolTip = GTLanguageManager
        .addStringLocalization("gt.notileentityinthisblock", "This is NOT a TileEntity!");
    protected final String mBlastProofTooltip = GTLanguageManager
        .addStringLocalization("gt.blastprooftooltip", "This Block is Blast Proof");

    public final BlockCasingsAbstract blockCasings;

    public ItemCasingsAbstract(Block block) {
        super(block);
        setMaxDamage(0);
        setHasSubtypes(true);
        setCreativeTab(GregTechAPI.TAB_GREGTECH_MATERIALS);

        this.blockCasings = (BlockCasingsAbstract) block;
    }

    @Override
    public int getMetadata(int aMeta) {
        return aMeta;
    }

    @Override
    public String getUnlocalizedName(ItemStack aStack) {
        return this.field_150939_a.getUnlocalizedName() + "." + getDamage(aStack);
    }

    @Override
    public void addInformation(ItemStack aStack, EntityPlayer aPlayer, List<String> aList, boolean aF3_H) {
        super.addInformation(aStack, aPlayer, aList, aF3_H);
        aList.add(this.mNoMobsToolTip);
        aList.add(this.mNoTileEntityToolTip);
        blockCasings.addInformation(aStack, aPlayer, aList, aF3_H);
    }
}
