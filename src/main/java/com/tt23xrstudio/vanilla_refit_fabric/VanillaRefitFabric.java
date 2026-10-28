package com.tt23xrstudio.vanilla_refit_fabric;

import com.tt23xrstudio.vanilla_refit_fabric.command.BtsdhzCommands;
import com.tt23xrstudio.vanilla_refit_fabric.config.BtsdhzConfig;
import com.tt23xrstudio.vanilla_refit_fabric.event.FenceRopeEvents;
import com.tt23xrstudio.vanilla_refit_fabric.network.ModNetworking;
import com.tt23xrstudio.vanilla_refit_fabric.util.CrawlLogic;
import com.tt23xrstudio.vanilla_refit_fabric.util.PaneCornerSupport;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementModeState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

        // 自查命令：/vanilla_refit_fabric blocklist（打印生效方块名单与半砖/楼梯统计）
        BtsdhzCommands.register();

        // 注册方块/物品 → 方块实体类型（顺序：方块先行）
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        // 自定义实体数据序列化器必须先于任何使用它们的实体类加载（ModEntities 类初始化会触发
        // FenceKnotEntity 的 SynchedEntityData.defineId，届时序列化器必须已在表中）
        ModEntityData.initialize();
        ModEntities.initialize();
        // 牵引绳悬链：待连接状态下持拴绳点动物 → 拴到 pending 栅栏的绳结（UseEntityCallback）
        FenceRopeEvents.register();

        // 放置模式：网络载荷类型 + 服务端接收器 + 登录下发（附件在类加载时注册）
        ModNetworking.register();
        // 爬行状态：断线时清掉 UUID 标记
        CrawlLogic.init();
        PlacementModeState.init();

        // 玻璃板部件属性注入自检（服务器启动时方块注册表已就绪）
        ServerLifecycleEvents.SERVER_STARTED.register(server -> PaneCornerSupport.logSummary());

        // 机械动力「对称之杖」兼容（反射软依赖：机械动力不在时类内逻辑自行短路）
        com.tt23xrstudio.vanilla_refit_fabric.compat.CreateSymmetryCompat.init();

        LOGGER.info("[原版精修] Fabric 26.2 初始化完成，配置文件：{}", BtsdhzConfig.file());
    }
}
