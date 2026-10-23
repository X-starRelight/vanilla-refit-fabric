package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.FenceRopeLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 待连接状态下（拴绳一头已经挂在栅栏上、另一头在玩家手上），点另一根栅栏就完成这条连接，
 * <b>不要求手里还拿着拴绳</b>。
 *
 * <p>源版挂在 {@code FenceBlock.useItemOn} 上；26.2 已删除 {@code ItemInteractionResult}
 * 且 {@code FenceBlock} 不再覆写 {@code useItemOn}（编译/注入双失败），改为方案 B：
 * 注入 {@code FenceBlock.useWithoutItem}（5 参）。服务端右键链实测（研究 3.4）：
 * 手持非拴绳物品、非潜行时 {@code BlockBehaviour.useItemOn} 默认返回
 * {@code TRY_WITH_EMPTY_HAND}（不消费）→ 紧接着 {@code useWithoutItem} 必触发，
 * 覆盖面与源版一致；仅主手会走到这里。</p>
 *
 * <p>客户端只回一个"这次右键不预测放方块"的结果，真正的连接在服务端做（和拴绳那条路一致）。</p>
 */
@Mixin(FenceBlock.class)
public abstract class FenceLeadUseMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void vanillaRefit$completePendingWithAnyItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit,
                                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null) {
            return;
        }
        // 手里拿着拴绳：走 LeadItem.useOn 那条路（开始连接 / 再点一次取消 / 第二次点击完成）。
        // useWithoutItem 没有 stack/hand 参数，26.2 调用链仅主手会走到这里，用主手判断。
        if (player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.LEAD)) {
            return;
        }
        BlockPos pending = FenceRopeLogic.getPending(player);
        // 没有待连接，或点的就是待连接的那根：不接管，保持原版行为（该放方块放方块）
        if (pending == null || pending.equals(pos)) {
            return;
        }
        if (level.isClientSide()) {
            // 服务端会把这条待连接用掉并清空状态，客户端镜像也要一起清（否则之后点栅栏一直被它吃掉）
            FenceRopeLogic.clearPendingWithKnot(player, level);
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        FenceRopeLogic.connect(level, pending, pos, !creative);
        FenceRopeLogic.clearPendingWithKnot(player, level);
        level.playSound(null, pos, SoundEvents.LEAD_TIED, SoundSource.BLOCKS, 1.0F, 1.0F);
        cir.setReturnValue(InteractionResult.SUCCESS);
        cir.cancel();
    }
}
