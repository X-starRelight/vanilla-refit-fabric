package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.block.MixedSlabBlock;
import com.tt23xrstudio.vanilla_refit_fabric.util.MixedSlabBreakHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端破坏方块。
 *
 * <p>潜行（按住 Shift）时：若目标是堆叠半砖（混合半砖或原版 DOUBLE），只拆被准星命中的
 * 那一半，保留另一半。非潜行时走原版“整个拆除”，会掉落整格的所有半砖物品。
 *
 * <p><b>破坏事件联动（对应原版的 BreakEvent 补发）</b>：Fabric 自己往 {@code destroyBlock} HEAD
 * 注入了 {@code PlayerBlockBreakEvents.BEFORE}（事件里带 CANCELED / AFTER 两个配套事件），
 * 它在本 mixin 之前触发（本 mixin 优先级调到 1100 &gt; 默认 1000，保证“事件先发、我们后接管”，
 * 这样保护插件/对称之杖类模组取消破坏时我们会让位）。所以这里<b>不需要</b>再补发任何事件；
 * 只要检查回调里是否已被取消即可。
 */
@Mixin(value = ServerPlayerGameMode.class, priority = 1100)
public abstract class ServerPlayerGameModeMixin {

    @Shadow(remap = false)
    @Final
    protected ServerPlayer player;

    @Shadow(remap = false)
    protected ServerLevel level;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void vanillaRefit$breakMixedHalf(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Fabric 的 PlayerBlockBreakEvents.BEFORE 已经跑过并被取消（保护插件等）：让位，不做任何接管
        if (cir.isCancelled()) {
            return;
        }
        BlockState state = this.level.getBlockState(pos);
        boolean sneaking = this.player.isShiftKeyDown();
        boolean mixedSlab = state.getBlock() instanceof MixedSlabBlock;
        boolean vanillaDouble = state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
        // 只有这两种情况本模组才接管；其余方块完全走原版
        boolean takesOver = sneaking ? (mixedSlab || vanillaDouble) : mixedSlab;
        if (!takesOver) {
            return;
        }
        // 对称之杖兼容：接管前记下镜像格（此刻源格与镜像格都还在），接管失败/整拆时收尾清理
        com.tt23xrstudio.vanilla_refit_fabric.compat.CreateSymmetryCompat.beginBreak(
                this.level, this.player, pos, state, sneaking);
        boolean handled = sneaking
                // 潜行：只拆被准星命中的那一半
                ? MixedSlabBreakHandler.tryBreakHalf(this.level, pos, state, this.player)
                // 非潜行：原版整块拆除；混合半砖无掉落表、原版产不出掉落，这里手动接管整挖掉落。
                : MixedSlabBreakHandler.dropWholeMixed(this.level, pos, state, this.player);
        if (handled) {
            // 潜行半拆成功：applyBreak 内部的 finishHalfBreak 已消费镜像上下文，这里不用收尾
            if (!sneaking) {
                // 整块拆除：destroyBlock 被我们取消，机械动力的拆除钩子不会触发，
                // 由兼容层直接把镜像格拆掉（掉落按材质记账、tick 末尾补）
                com.tt23xrstudio.vanilla_refit_fabric.compat.CreateSymmetryCompat.finishWholeBreak(
                        this.level, this.player);
            }
            cir.setReturnValue(true);
            cir.cancel();
        } else {
            // 没接管成：退回整块掉落语义，别把镜像格的材料吞了
            com.tt23xrstudio.vanilla_refit_fabric.compat.CreateSymmetryCompat.endBreak();
        }
    }
}
