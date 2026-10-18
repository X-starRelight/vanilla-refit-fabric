package com.tt23xrstudio.vanilla_refit_fabric.command;

import com.mojang.brigadier.context.CommandContext;
import com.tt23xrstudio.vanilla_refit_fabric.config.Blocklist;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * 自查命令：{@code /vanilla_refit_fabric blocklist}
 *
 * <p>打印当前生效的方块名单，以及半砖/楼梯里有多少个被名单禁用。
 * 玩家反馈"某个方块不能竖放了"时，先用它看一眼是不是名单把它关掉了
 * （配置写错的行会被忽略并打警告，所以这里显示的就是真正生效的部分）。
 *
 * <p>移植说明：原 NeoForge 版由 {@code @EventBusSubscriber} 监听 {@code RegisterCommandsEvent}
 * 自注册；Fabric 版改为 {@link CommandRegistrationCallback}，由主类显式调用 {@link #register()}。
 * 源项目未写 {@code requires}（任何人可用），此处保持一致。
 */
public final class BtsdhzCommands {

    private BtsdhzCommands() {
    }

    /** 注册 {@code /vanilla_refit_fabric blocklist}（服务端命令，服务器启动时回调一次）。 */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        Commands.literal("vanilla_refit_fabric")
                                .then(Commands.literal("blocklist")
                                        .executes(BtsdhzCommands::reportBlocklist))));
    }

    /** 打印名单逐条内容 + 原版半砖/楼梯允许/禁用统计；返回半砖+楼梯总数（Brigadier success 计数）。 */
    private static int reportBlocklist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<Blocklist.Entry> entries = Blocklist.entries();

        source.sendSuccess(() -> Component.literal("方块名单（从上往下匹配，第一条命中生效，都没命中则允许）："), false);
        for (Blocklist.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal("  " + entry.describe()), false);
        }

        int slabs = 0;
        int slabsDenied = 0;
        int stairs = 0;
        int stairsDenied = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean allowed = Blocklist.allows(block);
            if (block instanceof SlabBlock) {
                slabs++;
                if (!allowed) {
                    slabsDenied++;
                }
            } else if (block instanceof StairBlock) {
                stairs++;
                if (!allowed) {
                    stairsDenied++;
                }
            }
        }
        int totalSlabs = slabs;
        int deniedSlabs = slabsDenied;
        int totalStairs = stairs;
        int deniedStairs = stairsDenied;
        source.sendSuccess(() -> Component.literal(
                "半砖 " + totalSlabs + " 个：允许 " + (totalSlabs - deniedSlabs) + "，禁用 " + deniedSlabs), false);
        source.sendSuccess(() -> Component.literal(
                "楼梯 " + totalStairs + " 个：允许 " + (totalStairs - deniedStairs) + "，禁用 " + deniedStairs), false);
        return totalSlabs + totalStairs;
    }
}
