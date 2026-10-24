package com.tt23xrstudio.vanilla_refit_fabric.util;

import com.mojang.serialization.Codec;
import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/**
 * 玩家“待连接”状态的存储（牵引绳悬链功能）。
 *
 * <p>1.21 源版把 pending 写在 {@code player.getPersistentData()} 里；26.2 该方法已删除，
 * 语义等价物是 Fabric 数据附件（Data Attachment）：
 *
 * <ul>
 *   <li>{@link #PENDING_POS}：服务端权威、随存档保存（等价原 persistentData）；</li>
 *   <li>{@link #PENDING_POS_LOCAL}：客户端本地镜像，不写盘、不同步，只用于右键预测
 *       （等价源版 {@code mirrorPending} 的本地推演——pending 本来就不下发客户端）。</li>
 * </ul>
 *
 * <p>统一用 {@code -1L} 表示“没有待连接”。附件在类加载时完成注册，主初始化无需显式调用。
 */
public final class FenceRopeAttachments {

    /** 待连接的栅栏坐标（BlockPos.asLong），服务端权威、持久化。 */
    public static final AttachmentType<Long> PENDING_POS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "fence_knot_pending_pos"),
            builder -> builder.persistent(Codec.LONG).initializer(() -> -1L));

    /** 待连接的客户端镜像，仅本地预测用（不写盘、不同步）。 */
    public static final AttachmentType<Long> PENDING_POS_LOCAL = AttachmentRegistry.createDefaulted(
            Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "fence_knot_pending_pos_local"),
            () -> -1L);

    private FenceRopeAttachments() {
    }
}
