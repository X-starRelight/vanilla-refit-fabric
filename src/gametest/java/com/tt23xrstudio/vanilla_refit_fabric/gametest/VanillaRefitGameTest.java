package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * 阶段 0 冒烟测试：确认模组加载、game test 框架在无头服务端跑通。
 *
 * <p>后续阶段在 {@code vanilla_refit_fabric.gametest} 包下按功能补测试
 * （竖台阶放置、贴合碰撞箱、玻璃板 12 段连接、名单命中等）。
 */
public class VanillaRefitGameTest {

    /** 本模组与测试模组都应已加载。 */
    @GameTest
    public void modIsLoaded(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded(VanillaRefitFabric.MOD_ID)) {
            throw new IllegalStateException("模组 vanilla_refit_fabric 未加载");
        }
        if (!FabricLoader.getInstance().isModLoaded("vanilla_refit_fabric_test")) {
            throw new IllegalStateException("测试模组 vanilla_refit_fabric_test 未加载");
        }
        helper.succeed();
    }

    /** 基础世界交互：放置并断言一个原版方块。 */
    @GameTest
    public void placeSmoothStoneSlab(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.SMOOTH_STONE_SLAB);
        helper.assertBlockPresent(Blocks.SMOOTH_STONE_SLAB, 1, 1, 1);
        helper.succeed();
    }

    /** 配置系统在真实游戏环境中可读。 */
    @GameTest
    public void configReadable(GameTestHelper helper) {
        var config = com.tt23xrstudio.vanilla_refit_fabric.config.BtsdhzConfig.get();
        if (config.blocklist == null || config.blocklist.isEmpty()) {
            throw new IllegalStateException("配置 blocklist 应当至少有一条默认值");
        }
        helper.succeed();
    }
}
