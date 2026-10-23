package com.tt23xrstudio.vanilla_refit_fabric.client;

import com.tt23xrstudio.vanilla_refit_fabric.ModEntities;
import com.tt23xrstudio.vanilla_refit_fabric.client.model.ModModelLoading;
import com.tt23xrstudio.vanilla_refit_fabric.client.renderer.SitEntityRenderer;
import com.tt23xrstudio.vanilla_refit_fabric.debug.MemProbeClient;
import com.tt23xrstudio.vanilla_refit_fabric.debug.RenderBench;
import com.tt23xrstudio.vanilla_refit_fabric.util.ClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原版精修 Fabric 版 —— 客户端入口。
 *
 * <p>阶段 2 起在此注册客户端事件（模型装配、BlockState 变体提示、HUD 等）。
 */
public class VanillaRefitFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("vanilla_refit_fabric");

    @Override
    public void onInitializeClient() {
        // 内存/渲染基准工具：触发文件（run/btsdhz-memprobe.txt、run/btsdhz-bench.txt）存在时才生效
        MemProbeClient.register();
        RenderBench.register();

        // 阶段 2：放置逻辑切换按键 + 服务端同步
        ModKeyBindings.register();
        PlacementModeClient.init();

        // 坐/爬：座位渲染器（隐形）+ 按键消费与爬行姿态同步
        EntityRendererRegistry.register(ModEntities.SIT, SitEntityRenderer::new);
        SitCrawlClient.init();

        // 阶段 2：模型层——竖直台阶/楼梯的模板换图 + 混合半砖动态合并 + 区块标脏钩子注入
        ModModelLoading.register();
        ClientHooks.refreshRenderSection = MixedSlabClientRefresh::section;

        // 阶段 2：放置判定显示框（准星对方块面时画切分线，移植自源项目 PlacementGuideRenderer）
        PlacementGuideRenderer.register();

        // [VR-dbg] 客户端入口完整跑完的证据（V 键问题排查用，确认无异常后可移除）
        LOGGER.info("[VR-dbg] 客户端入口初始化完成：按键、放置逻辑同步、模型层、判定显示框均已注册");
    }
}
