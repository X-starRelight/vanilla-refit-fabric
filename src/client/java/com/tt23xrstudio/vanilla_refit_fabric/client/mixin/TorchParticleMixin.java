package com.tt23xrstudio.vanilla_refit_fabric.client.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 火把/灵魂火把（都是 TorchBlock）放在下台阶上时，粒子特效（烟雾/火焰）整体下移半格，
 * 与下移后的模型对齐。
 *
 * <p>animateTick 里粒子 Y 固定为 pos.getY()+0.7；当下移（ON_SLAB=true）时改为 -0.5。
 */
@Mixin(TorchBlock.class)
public abstract class TorchParticleMixin {

    @Unique
    private boolean vanillaRefit$onSlab = false;

    @Inject(method = "animateTick", at = @At("HEAD"), remap = false)
    private void vanillaRefit$capture(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        this.vanillaRefit$onSlab = state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB);
    }

    // addParticle(ParticleOptions, x, y, z, xSpeed, ySpeed, zSpeed)：y 参数是 index 2
    @ModifyArg(method = "animateTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"),
            index = 2, remap = false, require = 0)
    private double vanillaRefit$shiftParticleY(double y) {
        return this.vanillaRefit$onSlab ? y - 0.5 : y;
    }
}
