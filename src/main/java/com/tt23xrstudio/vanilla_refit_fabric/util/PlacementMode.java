package com.tt23xrstudio.vanilla_refit_fabric.util;

import java.util.List;
import net.minecraft.util.StringRepresentable;

/**
 * 台阶/楼梯的放置逻辑。
 *
 * <p>台阶和楼梯各存一份、互不影响（可以只把台阶切回原版而楼梯保持本模组逻辑）。
 */
public enum PlacementMode implements StringRepresentable {
    /**
     * 默认档（README「默认」）：顶/底面点外圈竖放、点中央正方形内原版平放；
     * 侧面是三条竖带——左带/右带竖放、中间带原版平放。
     * 手持时显示分割辅助线（顶/底面为对角线/十字加正方形，侧面为两条竖线）。
     * 正方形边长占比见配置 {@code slabCenterSquareRatio}，见 BlockItemMixin、
     * SlabPlacementRules、StairBlockMixin。
     */
    MOD("mod"),
    /**
     * @deprecated 已删除的“混合”档：行为已并入默认档，仅保留常量以固定序号——
     * 存档与网络序列化用 {@link #ordinal()}，删掉会让 MOD_3/VANILLA 序号整体前移。
     * {@link #byOrdinal} 读到序号 1 会映射回 {@link #MOD}，轮转列表也不再包含它。
     */
    @Deprecated
    MOD_2("mod2"),
    /**
     * 紧贴档（目前只有台阶）：紧贴点击的那个面放置——
     * 点顶面放（贴在下面的）平放台阶，点侧面放贴着该侧的竖台阶。不需要辅助线。
     */
    MOD_3("mod3"),
    /** 原版逻辑：完全走原版放置。 */
    VANILLA("vanilla");

    /** 台阶可选的模式顺序（按切换键轮转；“混合”档已删除，默认档即带中央正方形判定）。 */
    public static final List<PlacementMode> SLAB_ORDER = List.of(MOD, MOD_3, VANILLA);
    /** 楼梯可选的模式顺序（默认档与原版两档）。 */
    public static final List<PlacementMode> STAIR_ORDER = List.of(MOD, VANILLA);

    private final String name;

    PlacementMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** 按切换键轮转；以后再加逻辑 3 就往对应 ORDER 列表里加一项即可。 */
    public PlacementMode next(boolean forStairs) {
        List<PlacementMode> order = forStairs ? STAIR_ORDER : SLAB_ORDER;
        int index = order.indexOf(this);
        return order.get((index + 1) % order.size());
    }

    public static PlacementMode byOrdinal(int ordinal) {
        // 序号 1 = 已删除的“混合”档：读回时并入默认档（存档与旧网络包兼容）
        if (ordinal == 1) {
            return MOD;
        }
        PlacementMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MOD;
    }
}
