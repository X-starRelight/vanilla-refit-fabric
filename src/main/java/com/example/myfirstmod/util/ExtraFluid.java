package com.example.myfirstmod.util;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * 台阶 / 楼梯里"<b>除水以外</b>的液体"（目前只有熔岩）。
 *
 * <p>为什么水不在这里：原版方块本来就带 {@code waterlogged} 布尔，水的存在由它表示；
 * 不用 3 值的 {@code btsdhz_fluid}（none/water/lava）把水也编码一遍，也不把原版
 * {@code waterlogged} 恒置为 false——结果是"含水"这件事被存了两份，状态数是
 * {@code 3(液体值) × 2(waterlogged)} = 6 种组合，而其中只有 3 种有意义。
 *
 * <p>这里的做法：<b>水 → 原版 {@code waterlogged}；其它液体 → 本模组这个 2 值属性</b>，
 * 两者在逻辑上互斥。好处有两条：
 * <ul>
 *   <li>状态数从 6 种组合降到 4 种（台阶每块 54 → 36、楼梯每块 480 → 320）；</li>
 *   <li><b>卸掉模组后水还在</b>：水存在原版属性里，不依赖本模组；若水存在
 *       {@code btsdhz_fluid} 里，模组一卸载就变回"不含水"。</li>
 * </ul>
 *
 * <p>读写请一律走 {@link FluidHolder}，它负责在原版属性与本属性之间做互斥映射。
 */
public enum ExtraFluid implements StringRepresentable {
    NONE("none"),
    LAVA("lava");

    private final String name;

    ExtraFluid(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
