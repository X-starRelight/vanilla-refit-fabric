package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

/**
 * 玻璃类方块加入镐/斧可加速挖掘标签（纯数据包标签，同源项目）。
 *
 * <p>断言 {@code data/minecraft/tags/block/mineable/pickaxe.json} 与 {@code axe.json}
 * 两个标签文件生效：玻璃类方块进镐/斧名单（手持对应工具时速度倍率生效，不改掉落），
 * 同时守住名单边界——普通木板不被整体覆盖进斧名单、铁栏杆不进镐名单。
 */
public class GlassMiningTagGameTest {

    private static void require(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            helper.fail(message);
        }
    }

    /**
     * 玻璃 / 玻璃板应在镐名单，染色玻璃应在斧名单；
     * 橡木板（防标签被整体覆盖）与铁栏杆（名单边界）必须不在对应名单。
     */
    @GameTest
    public void glassBlocksJoinMineableTags(GameTestHelper helper) {
        require(helper, Blocks.GLASS.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "玻璃应加入 #minecraft:mineable/pickaxe");
        require(helper, Blocks.GLASS_PANE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "玻璃板应加入 #minecraft:mineable/pickaxe");
        // 26.2 无 BLACK_STAINED_GLASS 常量，染色玻璃走注册表式访问（同 Phase3PaneGameTest 的 STAINED_GLASS_PANE.white()）
        require(helper, Blocks.STAINED_GLASS.black().defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),
                "黑色染色玻璃应加入 #minecraft:mineable/axe");
        require(helper, !Blocks.OAK_PLANKS.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),
                "橡木板不应加入 #minecraft:mineable/axe（防止标签被整体覆盖）");
        require(helper, !Blocks.IRON_BARS.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "铁栏杆不应加入 #minecraft:mineable/pickaxe（名单边界）");

        helper.succeed();
    }
}
