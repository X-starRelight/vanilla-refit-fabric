package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import com.tt23xrstudio.vanilla_refit_fabric.util.CrawlLogic;
import com.tt23xrstudio.vanilla_refit_fabric.util.SitLogic;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;

import java.lang.reflect.Method;

/**
 * 任务 3 的 RED 测试：四个 mixin 落地前这两条应 FAIL——
 * <ul>
 *   <li>{@code sittingLowersEyeHeight}：无 {@code PlayerEyeHeightMixin} 时眼高不下沉（差值 0 而非 0.35）；</li>
 *   <li>{@code crawlForcesSwimmingPose}：无 {@code PlayerCrawlPoseMixin} 时 {@code updatePlayerPose}
 *       不认 CrawlLogic 标记，姿态保持原版判定。</li>
 * </ul>
 *
 * <p>{@code PlayerSitMixin}（封潜行下车）与 {@code SitDismountHintMixin}（隐藏提示）依赖真实按键/客户端
 * 包处理，按计划转人工手测。
 */
public class SitCrawlMixinGameTest {

    /** 坐姿眼高下沉：getEyeHeight() 与 getEyeY() 都应比未注入重载的基准低 0.35。 */
    @GameTest
    public void sittingLowersEyeHeight(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        SitLogic.toggleSit(player);
        if (!(player.getVehicle() instanceof SitEntity)) {
            helper.fail("应已坐下，实际载具=" + player.getVehicle());
            return;
        }
        // getEyeHeight(Pose) 是未被注入的重载（计划研究 ✅），用作"原始眼高"基准
        float raw = player.getEyeHeight(player.getPose());
        if (Math.abs(player.getEyeHeight() - (raw - 0.35F)) > 1.0e-3F) {
            helper.fail("坐姿 getEyeHeight 应为 raw-0.35，实际 " + player.getEyeHeight() + " raw=" + raw);
            return;
        }
        // 不确定：eyeHeight 字段与 getEyeHeight(Pose) 是否恒等（§5-⑤-3）；若失败改为相对差值断言
        if (Math.abs(player.getEyeY() - (player.getY() + raw - 0.35D)) > 1.0e-3D) {
            helper.fail("坐姿 getEyeY 应为 y+raw-0.35，实际 " + player.getEyeY());
            return;
        }
        helper.succeed();
    }

    /** 爬行标记 → updatePlayerPose 强制 SWIMMING；清除 → 退出 SWIMMING。 */
    @GameTest
    public void crawlForcesSwimmingPose(GameTestHelper helper) throws Exception {
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        // 反射调用 protected updatePlayerPose()；若 setAccessible 被 Java 25 模块封装拦截，
        // 删本用例并转人工手测（计划风险 5）
        Method updatePose = Player.class.getDeclaredMethod("updatePlayerPose");
        updatePose.setAccessible(true);

        CrawlLogic.setCrawling(player, true);
        updatePose.invoke(player);
        if (player.getPose() != Pose.SWIMMING) {
            helper.fail("爬行中 updatePlayerPose 应强制 SWIMMING，实际 " + player.getPose());
            return;
        }

        CrawlLogic.setCrawling(player, false);
        updatePose.invoke(player);
        if (player.getPose() == Pose.SWIMMING) {
            helper.fail("停止爬行后应退出 SWIMMING");
            return;
        }
        helper.succeed();
    }
}
