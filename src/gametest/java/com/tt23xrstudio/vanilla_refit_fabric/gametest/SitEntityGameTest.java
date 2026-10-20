package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.ModEntities;
import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * 座位实体（{@code vanilla_refit_fabric:sit}）注册与自清理行为测试。
 *
 * <p>座位不写盘、无人乘坐即销毁，避免玩家死亡/掉线后留下“幽灵座位”。
 */
public class SitEntityGameTest {

    /** 注册表双向可查：id → 实例。 */
    @GameTest
    public void sitEntityRegistered(GameTestHelper helper) {
        Identifier id = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "sit");
        if (BuiltInRegistries.ENTITY_TYPE.getValue(id) != ModEntities.SIT) {
            helper.fail("实体注册表查不到 vanilla_refit_fabric:sit（或指向了别的实例）");
        }
        helper.succeed();
    }

    /** 生成后无人乘坐 → 下一次 tick 自动移除。 */
    @GameTest
    public void seatDiscardsWithoutPassenger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        SitEntity seat = new SitEntity(ModEntities.SIT, level);
        seat.setPos(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        level.addFreshEntity(seat);

        helper.runAfterDelay(2L, () -> {
            if (!seat.isRemoved()) {
                helper.fail("无人乘坐的座位实体应在生成后自动移除");
            }
            helper.succeed();
        });
    }
}
