package com.tt23xrstudio.vanilla_refit_fabric.event;

import com.tt23xrstudio.vanilla_refit_fabric.entity.FenceKnotEntity;
import com.tt23xrstudio.vanilla_refit_fabric.util.FenceRopeLogic;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 拴绳“先点栅栏、再点动物”这条路（源版 {@code FenceRopeEvents}，NeoForge
 * {@code PlayerInteractEvent.EntityInteract} → Fabric {@code UseEntityCallback}）。
 *
 * <p>原本只有一种先后顺序：先拿拴绳点动物（动物跟到玩家手上），再点栅栏把动物拴上去。
 * 反过来——先点栅栏开始一条连接（绳结已经挂在栅栏上、另一头在玩家手上），再点动物——不会有任何反应，
 * 动物反而被原版拴到玩家身上。这里补上另一半：处于待连接状态时，拿拴绳点动物就把动物拴到那根栅栏的绳结上，
 * 和点第二根栅栏一样会消耗这次连接。</p>
 *
 * <p>只有手里拿着拴绳时才接管：其它物品点动物可能是喂食、繁殖、骑乘等原版行为，不能抢。</p>
 *
 * <p>26.2：{@code canHaveALeashAttachedToIt} → {@code canHaveALeashAttachedTo}（1.21.6 起改名，
 * 计划所注“不确定 4”）；回调返回 {@code SUCCESS} 而非 {@code CONSUME}（否则客户端挥手可能不播）。</p>
 */
public final class FenceRopeEvents {

    private FenceRopeEvents() {
    }

    /** 注册进通用入口（回调内自判 {@code level.isClientSide()}，两端都跑，与源工程语义一致）。 */
    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.LEAD) || !(entity instanceof Leashable leashable)) {
                return InteractionResult.PASS;
            }
            BlockPos pending = FenceRopeLogic.getPending(player);
            if (pending == null) {
                return InteractionResult.PASS;
            }
            // 26.2：canHaveALeashAttachedTo(holder) 是“本实体能否被拴到 holder 上”（yarn 名
            // canBeLeashedTo(Entity)；1.21.5 的无参 canHaveALeashAttachedToIt 已参数化）——
            // holder 是即将拴上的绳结，不是被点的动物本身。客户端用已同步的结，服务端就地创建。
            FenceKnotEntity knot = level.isClientSide()
                    ? FenceRopeLogic.findKnot(level, pending)
                    : FenceRopeLogic.getOrCreateKnot(level, pending);
            if (knot == null || !leashable.canHaveALeashAttachedTo(knot)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                leashable.setLeashedTo(knot, true);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pending, SoundEvents.LEAD_TIED, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            // 两端都清：服务端清真正的状态并清掉绳结上“拴到手上”的标记（否则手上会残留一条拴绳），
            // 客户端清镜像（见 FenceRopeLogic.mirrorPending）
            FenceRopeLogic.clearPendingWithKnot(player, level);
            return InteractionResult.SUCCESS;
        });
    }
}
