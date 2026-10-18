package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 铁砧材料修复改造（同源项目）：
 *
 * <p>1. 每份修复材料恢复 90% 最大耐久（原版 25%）——断言损坏铁剑一次修到 0；
 * 2. 材料修复不涨累计修理代价 RepairCost（物品合并/附魔书/改名保持原版）——
 * 断言 RepairCost 原样保留。
 *
 * <p>原版对照：铁剑 250 最大耐久，damage=100 时原版每份铁锭修 min(100, 250/4=62)
 * → 结果剩 38；RepairCost=10 时原版会走 calculateIncreasedRepairCost → 21。
 */
public class AnvilRepairGameTest {

    /** 搭一个铁砧菜单：输入槽放损坏铁剑，材料槽放铁锭，直接调 createResult()。 */
    private static AnvilMenu buildRepairMenu(GameTestHelper helper, ServerPlayer player,
                                             ItemStack input, ItemStack material) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(material);
        menu.createResult();
        return menu;
    }

    /**
     * 用例 A：材料修复比例 90%。
     * 铁剑最大耐久 250，damage=100：原版修 62（25%）剩 38，本模组修 min(100, 225)=100 剩 0。
     */
    @GameTest
    public void materialRepairRestoresNinetyPercent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(100);

        AnvilMenu menu = buildRepairMenu(helper, player, sword, new ItemStack(Items.IRON_INGOT));

        ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            helper.fail("修复结果不应为空（损坏铁剑 + 铁锭应能修复）");
            return;
        }
        int damage = result.getDamageValue();
        if (damage != 0) {
            helper.fail("材料修复应恢复 90% 最大耐久：结果 damage 应为 0，实际 " + damage + "（原版行为剩 38）");
            return;
        }
        helper.succeed();
    }

    /**
     * 用例 B：材料修复不涨 RepairCost。
     * 输入 RepairCost=10：原版走 calculateIncreasedRepairCost → 21，本模组应原样保留 10。
     */
    @GameTest
    public void materialRepairKeepsRepairCost(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(100);
        sword.set(DataComponents.REPAIR_COST, 10);

        AnvilMenu menu = buildRepairMenu(helper, player, sword, new ItemStack(Items.IRON_INGOT));

        ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            helper.fail("修复结果不应为空（带修理代价的铁剑 + 铁锭应能修复）");
            return;
        }
        // 26.2 已无 ItemStack.getRepairCost()，直接读 REPAIR_COST 组件（缺省为 0）
        int repairCost = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        if (repairCost != 10) {
            helper.fail("材料修复不应涨 RepairCost：应为 10，实际 " + repairCost + "（原版行为为 21）");
            return;
        }
        helper.succeed();
    }
}
