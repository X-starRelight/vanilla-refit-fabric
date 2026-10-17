package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.PaneConnection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 区块生成 / 加载后处理结束时，把区块里的玻璃板/铁栏杆再刷到稳定状态。
 *
 * 原版的后处理只对每个标记过的位置按任意顺序跑一次 updateFromNeighbourShapes；本模组的规则
 * 还要读 12 个斜角，顺序不对时先算的方块可能停在“邻居还没修正”的结果上。这里在原版后处理结束后
 * 再做一次局部收敛（见 {@link PaneConnection#refreshChunk}），保证世界生成出来的玻璃板/铁栏杆全部正确。
 *
 * <p>普通加载（没有碰到任何本模组的玻璃板）时 {@code endPostProcess()} 返回 false，什么都不做。
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkPostProcessMixin {

    @Inject(method = "postProcessGeneration", at = @At("HEAD"), remap = false)
    private void vanillaRefit$beginPostProcess(ServerLevel level, CallbackInfo ci) {
        PaneConnection.beginPostProcess();
    }

    @Inject(method = "postProcessGeneration", at = @At("RETURN"), remap = false)
    private void vanillaRefit$refreshPanesAtEnd(ServerLevel level, CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (PaneConnection.endPostProcess()) {
            PaneConnection.refreshChunk(self, self.getLevel());
        }
    }
}
