package com.tt23xrstudio.vanilla_refit_fabric;

import com.tt23xrstudio.vanilla_refit_fabric.config.BtsdhzConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原版精修（Vanilla Refit）Fabric 26.2 移植版 —— 主入口（通用环境）。
 *
 * <p>原 NeoForge 版主类为 {@code com.example.myfirstmod.BtsdhzOriginal}（模组 id {@code btsdhz_original}）。
 */
public class VanillaRefitFabric implements ModInitializer {

    public static final String MOD_ID = "vanilla_refit_fabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 加载 JSON 配置（config/vanilla_refit_fabric.json，不存在则写出默认值）
        BtsdhzConfig.load();
        LOGGER.info("[原版精修] Fabric 26.2 初始化完成，配置文件：{}", BtsdhzConfig.file());
    }
}
