package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * 自查命令 {@code /vanilla_refit_fabric blocklist}（同源项目 /btsdhz_original blocklist）：
 *
 * <p>1. 命令已注册：命令树根下有 vanilla_refit_fabric，其下有 blocklist 子命令；
 * 2. 执行返回值 = 原版注册表中半砖 + 楼梯总数（命令体内独立统计的语义，见
 *    {@code BtsdhzCommands#reportBlocklist} 的 {@code return totalSlabs + totalStairs}），
 *    测试内用 instanceof 独立统计同一总数做交叉断言（不调用被测命令的统计代码路径）。
 */
public class CommandBlocklistGameTest {

    /** 用例 A：命令已注册（根节点 + blocklist 子节点都存在）。 */
    @GameTest
    public void commandRegistered(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        if (dispatcher.getRoot().getChild("vanilla_refit_fabric") == null) {
            helper.fail("命令树根下应有 vanilla_refit_fabric 节点（BtsdhzCommands.register() 未生效或未在主类调用）");
            return;
        }
        if (dispatcher.getRoot().getChild("vanilla_refit_fabric").getChild("blocklist") == null) {
            helper.fail("vanilla_refit_fabric 节点下应有 blocklist 子命令");
            return;
        }
        helper.succeed();
    }

    /**
     * 用例 B：执行 {@code vanilla_refit_fabric blocklist}，返回值应 > 0
     * 且等于测试内独立统计的原版半砖（SlabBlock）+ 楼梯（StairBlock）总数。
     */
    @GameTest
    public void blocklistReturnsTotalCount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        // 独立统计：遍历注册表数 instanceof，不经过被测命令的统计代码
        int expected = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof SlabBlock || block instanceof StairBlock) {
                expected++;
            }
        }

        int result;
        try {
            result = dispatcher.execute("vanilla_refit_fabric blocklist", server.createCommandSourceStack());
        } catch (CommandSyntaxException e) {
            helper.fail("blocklist 命令执行不应抛出语法异常：" + e.getMessage());
            return;
        }
        if (result <= 0) {
            helper.fail("blocklist 命令返回值应为半砖+楼梯总数（> 0），实际 " + result
                    + "（若为 0 说明返回值语义不是 totalSlabs + totalStairs）");
            return;
        }
        if (result != expected) {
            helper.fail("blocklist 返回值应等于独立统计的半砖+楼梯总数：期望 " + expected + "，实际 " + result);
            return;
        }
        helper.succeed();
    }
}
