package com.tt23xrstudio.vanilla_refit_fabric.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 原版 {@link StairBlock} 的注册用包装子类。
 *
 * <p>26.2 起 {@code StairBlock} 的构造器是 {@code protected}，外部无法直接
 * {@code new StairBlock(...)}，所以用一个只有构造器的子类承接。
 * 实例仍是 {@code StairBlock}，后续 StairBlock 相关 mixin 注入对它照常生效，
 * {@code StairBlock.isStairs(...)} 等判断也不受影响。
 */
public class ModStairsBlock extends StairBlock {

    public ModStairsBlock(BlockState baseState, BlockBehaviour.Properties properties) {
        super(baseState, properties);
    }
}
