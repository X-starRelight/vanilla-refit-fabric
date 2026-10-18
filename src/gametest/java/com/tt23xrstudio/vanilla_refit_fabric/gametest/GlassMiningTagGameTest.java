package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

/**
 * 玻璃类方块加入镐/斧可加速挖掘标签（纯数据包标签，同源项目）。
 *
 * <p>断言 {@code data/minecraft/tags/block/mineable/pickaxe.json} 与 {@code axe.json}
 * 两个标签文件生效：玻璃类方块进镐/斧名单（手持对应工具时速度倍率生效，不改掉落）；
 * 同时验证我们的标签是追加合并而非整体覆盖——原版条目未被覆盖（橡木板仍在斧名单、
 * 石头仍在镐名单），且非目标方块不入名单（白色羊毛不被波及）。
 */
public class GlassMiningTagGameTest {

    private static void require(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            helper.fail(message);
        }
    }

    /**
     * 玻璃 / 玻璃板应在镐名单，黑色染色玻璃应在斧名单；
     * 防覆盖用正向断言：原版成员（橡木板→斧名单经 #planks、石头→镐名单）合并后必须仍在册；
     * 名单边界换成原版与本名单都不含的白色羊毛（铁栏杆原版本就在镐名单，无法用成员断言表达"我们没加它"）。
     */
    @GameTest
    public void glassBlocksJoinMineableTags(GameTestHelper helper) {
        // 本名单正向：玻璃类方块进镐/斧
        require(helper, Blocks.GLASS.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "玻璃应加入 #minecraft:mineable/pickaxe");
        require(helper, Blocks.GLASS_PANE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "玻璃板应加入 #minecraft:mineable/pickaxe");
        // 26.2 无 BLACK_STAINED_GLASS 常量，染色玻璃走注册表式访问（同 Phase3PaneGameTest 的 STAINED_GLASS_PANE.white()）
        require(helper, Blocks.STAINED_GLASS.black().defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),
                "黑色染色玻璃应加入 #minecraft:mineable/axe");

        // 防覆盖（正向）：我们的标签文件与原版合并（无 replace），原版条目不得丢失
        require(helper, Blocks.OAK_PLANKS.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),
                "橡木板应在 #minecraft:mineable/axe（原版条目经 #planks，合并后不得丢失）");
        require(helper, Blocks.STONE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "石头应在 #minecraft:mineable/pickaxe（原版条目，合并后不得丢失）");

        // 名单边界：原版和本名单都不含的方块不得被波及（26.2 无 WHITE_WOOL 常量，羊毛同走 WOOL.white() 访问）
        require(helper, !Blocks.WOOL.white().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "白色羊毛不应进入 #minecraft:mineable/pickaxe（名单边界）");
        require(helper, !Blocks.WOOL.white().defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),
                "白色羊毛不应进入 #minecraft:mineable/axe（名单边界）");

        helper.succeed();
    }
}
