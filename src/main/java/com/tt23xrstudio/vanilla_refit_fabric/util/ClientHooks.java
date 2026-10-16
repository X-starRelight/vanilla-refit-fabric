package com.tt23xrstudio.vanilla_refit_fabric.util;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 客户端渲染刷新钩子。
 *
 * <p>{@code src/main} 源码集同时运行在专用服务器上，不能直接引用客户端类；因此把
 * “数据变了、要求客户端重画所在区块段”做成可注入的回调：客户端初始化时由
 * {@code VanillaRefitFabricClient} 注入实现（调用 {@code ClientLevel
 * #setSectionDirtyWithNeighbors}），服务端上保持无操作。
 *
 * <p>对应参考端 NeoForge 版的 {@code requestModelDataUpdate()}（模型数据缓存失效，Fabric 无此层）
 * + {@code MixedSlabClientRefresh}（区块标脏）两步中的“标脏”那一步——这一步在 Fabric 上
 * 同样不可省略：vanilla 对“方块状态没变”的方块实体更新不会标脏（{@code ModelManager
 * .requiresRender} 在新旧状态相同时直接返回 false）。
 */
public final class ClientHooks {

    /** 方块实体数据变化后标脏所在区块段；客户端初始化时注入实现。 */
    public static BiConsumer<Level, BlockPos> refreshRenderSection = (level, pos) -> { };

    private ClientHooks() {
    }
}
