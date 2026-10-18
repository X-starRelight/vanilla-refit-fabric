package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修改铁砧的「用材料修复」逻辑（同源项目）：
 *
 * <ul>
 *   <li>每次用材料修复恢复 90% 耐久（原版为 25%）</li>
 *   <li>用材料修复不再让物品的累计修理代价（RepairCost）翻倍</li>
 * </ul>
 *
 * <p>不改变其它情形（物品合并、附魔书、重命名等）对 RepairCost 的影响；改名与经验花费一律不动。
 * 全部注入点都在 {@link AnvilMenu#createResult()} 内，26.2 无混淆，一律 {@code remap = false}。
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    /** 本次 createResult 是否正在走「材料修复」分支（每次计算前复位）。 */
    @Unique
    private boolean vanillaRefit$materialRepair;

    // ===== 1. 每次计算前复位标记 =====
    @Inject(method = "createResult", at = @At("HEAD"), remap = false)
    private void vanillaRefit$resetMaterialRepair(CallbackInfo ci) {
        this.vanillaRefit$materialRepair = false;
    }

    // ===== 2. 检测是否走材料修复分支 =====
    // 26.2 已无 Item.isValidRepairItem 双参版，现行是 ItemStack 单参版（组件 REPAIRABLE）
    /**
     * 该注入点为独占式（{@code @Redirect} 独占该调用点），换取与源项目写法一致；
     * 若未来与其它修改铁砧修复判定的模组发生冲突，可换用 {@code @WrapOperation} 改为协作式包裹。
     */
    @Redirect(method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isValidRepairItem(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    private boolean vanillaRefit$markMaterialRepair(ItemStack receiver, ItemStack material) {
        boolean valid = receiver.isValidRepairItem(material);
        if (valid) {
            this.vanillaRefit$materialRepair = true;
        }
        return valid;
    }

    // ===== 3. 修复比例 25% → 90% =====
    // createResult 里 Math.min(II)I 恰好 2 处（初算 + 循环重算，都在材料修复分支），
    // 不加 ordinal 全部 wrap 与源码意图一致。栈上取 0 号 ItemStack 局部变量即输入物品
    // （result 是它的拷贝，getMaxDamage 恒等，数值一致）。
    @WrapOperation(method = "createResult",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), remap = false)
    private int vanillaRefit$repairRatio(int damagedValue, int oldCap, Operation<Integer> original,
                                         @Local(ordinal = 0) ItemStack stack) {
        return Math.min(damagedValue, stack.getMaxDamage() * 90 / 100);
    }

    // ===== 4. 材料修复不涨 RepairCost（合并/附魔书/改名保持原版） =====
    @WrapOperation(method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AnvilMenu;calculateIncreasedRepairCost(I)I"),
            remap = false)
    private int vanillaRefit$skipRepairCost(int oldRepairCost, Operation<Integer> original) {
        if (this.vanillaRefit$materialRepair) {
            return oldRepairCost;
        }
        return original.call(oldRepairCost);
    }
}
