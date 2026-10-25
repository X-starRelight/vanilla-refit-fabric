package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 坐姿第一人称眼高（源项目 §2.7 降 0.35）+ 26.2 骑乘 origin 下沉补偿。
 *
 * <p>26.2 骑乘把 origin 下沉乘客 VEHICLE 挂点（玩家 0.6，见 {@code SitEntity} 注释）换取
 * 模型髋部贴座位；绝对眼高要补回这 0.6，才与源项目一致（原站位上方 1.62-0.35=1.27）。
 * 只对「玩家骑在本模组座位上」生效。{@code defaultRequire 1}：注入点失效即启动期硬错误。
 */
@Mixin(Entity.class)
public abstract class PlayerEyeHeightMixin {

    // Entity 有 getEyeHeight()/getEyeHeight(Pose) 重载：写完整描述符消歧（返回 float）。
    @ModifyReturnValue(method = "getEyeHeight()F", at = @At("RETURN"), remap = false)
    private float vanillaRefit$lowerEyeWhileSitting(float original) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player player && player.getVehicle() instanceof SitEntity vehicle) {
            return original - 0.35F + (float) player.getVehicleAttachmentPoint(vehicle).y;
        }
        return original;
    }

    // 26.2 第一人称相机与弹道起点走 getEyeY()→字段，不再经过 getEyeHeight()，必须一并下沉（计划 §5-①）
    @ModifyReturnValue(method = "getEyeY()D", at = @At("RETURN"), remap = false)
    private double vanillaRefit$lowerEyeYWhileSitting(double original) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player player && player.getVehicle() instanceof SitEntity vehicle) {
            return original - 0.35D + player.getVehicleAttachmentPoint(vehicle).y;
        }
        return original;
    }
}
