package com.tt23xrstudio.vanilla_refit_fabric.util;

import com.tt23xrstudio.vanilla_refit_fabric.ModEntities;
import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 服务端坐/起逻辑：坐在一个静止的隐形座位上（原 NeoForge 版 {@code util.SitLogic}）。
 *
 * <p>入口是 C2S 载荷 {@code vanilla_refit_fabric:sit_toggle}，只在服务端线程执行。
 */
public final class SitLogic {

    private SitLogic() {
    }

    /** 切换“坐/起”：已在座位上则下来并销毁座位，否则在脚下生成新座位骑上去。 */
    public static void toggleSit(Player player) {
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof SitEntity seat) {
            // 已在座位上：下来并移除座位（座位无人乘坐时本也会被 tick 清理，这里立即生效）。
            player.stopRiding();
            seat.discard();
            return;
        }

        // 已在其它载具（矿车、船等）上，不做处理。
        if (player.isPassenger()) {
            return;
        }

        if (player.isDeadOrDying() || player.isSpectator()) {
            return;
        }

        // 座位必须进服务端世界（26.2 起 addFreshEntity 收在 ServerLevel 上）。
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        SitEntity seat = new SitEntity(ModEntities.SIT, level);
        seat.setPos(player.getX(), player.getY(), player.getZ());
        // 创造飞行悬停时坐下（用户要求）：保持原地悬停，不施加重力下落；
        // 生存/非飞行半空坐下仍受重力落到地面。座位一次性使用，坐姿会话内标志不变。
        if (player.getAbilities().flying) {
            seat.setNoGravity(true);
        }
        serverLevel.addFreshEntity(seat);
        // (entityToRide, force, sendEventAndTriggers) —— 26.2 的三参签名。
        player.startRiding(seat, true, true);
    }
}
