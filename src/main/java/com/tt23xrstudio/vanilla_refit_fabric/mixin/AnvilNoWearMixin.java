package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 铁砧使用不掉耐久：把「取出结果时 12% 概率让铁砧降级」的概率清零，
 * 只保留「铁砧从高处落下摔坏」的降级。
 *
 * <p>实现方式：wrap {@code AnvilMenu.onTake} 尾部合成方法 {@code lambda$onTake$0} 里
 * 判定用的 {@code RandomSource.nextFloat()}，钳到 {@code >= 0.12F} → 恒不命中降级分支，
 * 仍走原版「未损坏」分支（保留 ANVIL_USE 音效）。跌落降级在 {@code FallingBlockEntity}
 * 里另行调用 {@code AnvilBlock.damage}，不经过本注入点，因此完全不受影响。
 *
 * <p>注入范围限定在 {@link AnvilMenu} 内（绝不全局 redirect AnvilBlock.damage）。
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilNoWearMixin {

    // lambda$onTake$0 是 javac 生成的 private static 合成方法 → 处理器也必须是 static
    @WrapOperation(method = "lambda$onTake$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F"),
            remap = false)
    private static float vanillaRefit$neverWear(RandomSource instance, Operation<Float> original) {
        // 恒不 < 0.12F → 原版判假走「未损坏」分支；RNG 抽取保留，与原版消耗一致
        return Math.max(original.call(instance), 0.12F);
    }
}
