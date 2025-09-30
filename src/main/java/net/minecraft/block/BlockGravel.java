package net.minecraft.block;

import java.util.Random;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.world.World;

public class BlockGravel extends BlockFalling
{
	private static final String __OBFID = "CL_00000252";

	public Item getItemDropped(int p_149650_1_, Random p_149650_2_, int p_149650_3_)
	{
		if (p_149650_3_ > 3)
		{
			p_149650_3_ = 3;
		}

		return p_149650_2_.nextInt(10 - p_149650_3_ * 3) == 0 ? Items.flint : Item.getItemFromBlock(this);
	}

	@Override
	public void updateTick(World p_149674_1_, int p_149674_2_, int p_149674_3_, int p_149674_4_, Random p_149674_5_)
	{
		if (!p_149674_1_.isRemote && p_149674_1_.getGameRules().getGameRuleBooleanValue("doGravelFalling"))
		{
			this.func_149830_m(p_149674_1_, p_149674_2_, p_149674_3_, p_149674_4_);
		}
	}
}