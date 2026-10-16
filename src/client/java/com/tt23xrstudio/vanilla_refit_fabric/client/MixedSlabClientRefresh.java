package com.tt23xrstudio.vanilla_refit_fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 客户端：混合半砖的材质变了以后，要求重画它所在的区块段。
 *
 * <p>混合半砖的材质是渲染时从方块实体取出后合并的。如果只是改了方块实体的数据、
 * 没有改方块状态，客户端不会重画这一区块，表现就是材质一直停在旧值
 * （镜像放置过来时是默认的石头），直到旁边有方块更新把区块标脏才刷新。
 *
 * <p>26.2 对应 API：{@code ClientLevel#setSectionDirtyWithNeighbors}（注意参数是区块段坐标，
 * 不是方块坐标）。Fabric 没有 NeoForge 的模型数据缓存，不需要额外的失效调用，
 * 但标脏本身必须做。
 */
public final class MixedSlabClientRefresh {

    private MixedSlabClientRefresh() {
    }

    public static void section(Level level, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.level == null) {
            return;
        }
        minecraft.level.setSectionDirtyWithNeighbors(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }
}
