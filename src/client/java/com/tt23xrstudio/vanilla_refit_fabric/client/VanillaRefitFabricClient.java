package com.tt23xrstudio.vanilla_refit_fabric.client;

import com.tt23xrstudio.vanilla_refit_fabric.debug.MemProbeClient;
import com.tt23xrstudio.vanilla_refit_fabric.debug.RenderBench;
import net.fabricmc.api.ClientModInitializer;

/**
 * 原版精修 Fabric 版 —— 客户端入口。
 *
 * <p>阶段 2 起在此注册客户端事件（模型装配、BlockState 变体提示、HUD 等）。
 */
public class VanillaRefitFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 内存/渲染基准工具：触发文件（run/btsdhz-memprobe.txt、run/btsdhz-bench.txt）存在时才生效
        MemProbeClient.register();
        RenderBench.register();

        // 阶段 2+：注册模型/渲染相关事件
    }
}
