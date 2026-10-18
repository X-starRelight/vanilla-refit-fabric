package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tt23xrstudio.vanilla_refit_fabric.util.SymmetrySupport;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让竖半砖 / 竖楼梯 / 玻璃板角面片跟着原版的镜像与旋转一起变换
 * （机械动力飞跃版的对称之杖、原版结构方块的镜像/旋转、WorldEdit 等都会走这两个方法）。
 *
 * <p>注入点是 {@code BlockState} 的公开入口（实现在 {@code BlockBehaviour.BlockStateBase}），
 * 也就是所有方块（含原版、含别的模组）都必经的一层：原版自己的 mirror/rotate 先算完，
 * 这里再把本模组注入的属性补上，不需要给每个方块类型单独打补丁。
 *
 * <p>没装本模组属性的方块在这里只做两次 hasProperty 判断，没有额外开销。
 * 换算本体见 {@link SymmetrySupport}（与参考项目同一套语义）。
 */
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSymmetryMixin {

    @ModifyReturnValue(method = "mirror", at = @At("RETURN"), remap = false)
    private BlockState vanillaRefit$mirror(BlockState mirrored, Mirror mirror) {
        // this 就是"镜像前"那份状态
        BlockState self = (BlockState) (Object) this;
        return SymmetrySupport.mirror(self, mirrored, mirror);
    }

    @ModifyReturnValue(method = "rotate", at = @At("RETURN"), remap = false)
    private BlockState vanillaRefit$rotate(BlockState rotated, Rotation rotation) {
        BlockState self = (BlockState) (Object) this;
        return SymmetrySupport.rotate(self, rotated, rotation);
    }
}
