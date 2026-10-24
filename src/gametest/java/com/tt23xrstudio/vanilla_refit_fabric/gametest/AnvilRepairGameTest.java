package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 铁砧材料修复改造（同源项目）：
 *
 * <p>1. 每份修复材料恢复 90% 最大耐久（原版 25%）——断言 damage=240 的铁剑一次修到 15；
 * 2. 材料修复不涨累计修理代价 RepairCost（物品合并/附魔书/改名保持原版）——
 * 断言 RepairCost 原样保留；
 * 3. 物品合并（反向守护）仍走原版 calculateIncreasedRepairCost——断言 RepairCost 10 → 21。
 *
 * <p>原版对照：铁剑 250 最大耐久，damage=240 时原版每份铁锭修 min(240, 250/4=62) → 剩 178
 * （RED 应精确红在 178），本模组修 min(240, 250*90/100=225) → 剩 15，若回归成 50% 会得 115；
 * RepairCost=10 时原版会走 calculateIncreasedRepairCost → 21。
 */
public class AnvilRepairGameTest {

    /** 搭一个铁砧菜单：输入槽放损坏铁剑，材料槽放铁锭，直接调 createResult()。 */
    private static AnvilMenu buildRepairMenu(GameTestHelper helper, Player player,
                                             ItemStack input, ItemStack material) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(material);
        menu.createResult();
        return menu;
    }

    /**
     * 用例 A：材料修复比例 90%（断言精度足以区分 90%/50%）。
     * 铁剑最大耐久 250，damage=240：原版每份修 min(240, 250/4=62)=62 → 剩 178；
     * 本模组修 min(240, 250*90/100=225)=225 → 剩 15；若回归成 50%（min(240,125)=125）会剩 115。
     */
    @GameTest
    public void materialRepairRestoresNinetyPercent(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(240);

        AnvilMenu menu = buildRepairMenu(helper, player, sword, new ItemStack(Items.IRON_INGOT));

        ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            helper.fail("修复结果不应为空（损坏铁剑 + 铁锭应能修复）");
            return;
        }
        int damage = result.getDamageValue();
        if (damage != 15) {
            helper.fail("材料修复应恢复 90% 最大耐久：结果 damage 应为 15，实际 " + damage
                    + "（原版 25% 行为剩 178，回归 50% 会剩 115）");
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
        ServerPlayer player = MockPlayers.mockPlayer(helper);
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

    /**
     * 用例 C（反向守护）：物品合并仍涨 RepairCost。
     * 铁剑1（damage=100、REPAIR_COST=10）在输入槽，铁剑2（damage=50）在附加槽——
     * 铁剑不是铁剑的修复材料，{@code isValidRepairItem} 返回 false，走「两把同名物品合并」分支。
     * 原版在该分支调 calculateIncreasedRepairCost(10) → 21；
     * 若 HEAD 复位被改坏导致 materialRepair 标记恒 true，合并会被错误豁免、结果保留 10 → 本用例变红。
     * 合并耐久公式（字节码实测）：剩余 150 + (200 + 250*12/100=230) = 380 > 250 → 结果 damage=0，
     * 该断言同时证明确实走到了合并分支。
     */
    @GameTest
    public void itemMergeStillIncreasesRepairCost(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        ItemStack input = new ItemStack(Items.IRON_SWORD);
        input.setDamageValue(100);
        input.set(DataComponents.REPAIR_COST, 10);
        ItemStack extra = new ItemStack(Items.IRON_SWORD);
        extra.setDamageValue(50);

        AnvilMenu menu = buildRepairMenu(helper, player, input, extra);

        ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            helper.fail("合并结果不应为空（两把铁剑应能在铁砧合并）");
            return;
        }
        int damage = result.getDamageValue();
        if (damage != 0) {
            helper.fail("两把铁剑应走合并分支（合并后耐久溢出 → damage=0），实际 damage=" + damage);
            return;
        }
        int repairCost = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        if (repairCost != 21) {
            helper.fail("物品合并应走原版 calculateIncreasedRepairCost：RepairCost 应为 21，实际 " + repairCost
                    + "（若为 10 说明 materialRepair 标记未复位、合并分支被错误豁免）");
            return;
        }
        helper.succeed();
    }
}
