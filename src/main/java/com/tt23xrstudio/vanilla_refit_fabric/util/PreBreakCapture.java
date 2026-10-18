package com.tt23xrstudio.vanilla_refit_fabric.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * destroyBlock 进入时的方块状态快照（在 Fabric 破坏事件之前抓取）。
 *
 * <p>KleeSlabs 一类模组挂在破坏事件上（HEAD、priority 1000）：命中双层半砖时
 * 自己拆一半（DOUBLE 换成同方块单半砖）并取消原逻辑。本模组的接管注入点在事件
 * 之后，那会儿读到的已经是处理后的状态，认不出“外部模组做了半拆”。所以在更早的
 * 注入点（priority 100，见 ServerPlayerGameModeBreakCaptureMixin）先把原状态记下来，
 * 破坏被取消时用它与现状对比来识别外部半拆。
 *
 * <p>服务端单线程，快照只在同一次 destroyBlock 调用内有效。
 */
public final class PreBreakCapture {

    public static BlockPos POS;
    public static BlockState STATE;

    private PreBreakCapture() {
    }

    public static void capture(BlockPos pos, BlockState state) {
        POS = pos.immutable();
        STATE = state;
    }
}
