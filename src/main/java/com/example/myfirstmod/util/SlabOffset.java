package com.example.myfirstmod.util;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * "贴台阶"的三种情况（墙 / 栅栏 / 灯笼用，取代两个布尔属性）。
 *
 * <p>用两个布尔时是 {@code btsdhz_on_slab}（下方是下台阶 → 整体下移半格）与
 * {@code btsdhz_under_top_slab}（上方是上台阶 → 整体上移半格）两个布尔，
 * 但放置时是"下台阶优先"，两者不会同时为真——4 个声明值里只有 3 个可达，
 * 白占了一份状态表。合并成一个枚举后正好 3 个值，每个都可达：
 * 墙每块状态数 1,296 → 972，栅栏 128 → 96，灯笼 16 → 12。
 *
 * <p>{@link #NONE} 必须是第一个常量：缺属性时枚举按第一个常量解析
 * （和 {@code DefaultFalseBooleanProperty} 的道理一样），旧存档/结构模板里没有这个属性时
 * 才会退回"不位移"的原版外观。
 *
 * <p>火把类仍然用布尔 {@code btsdhz_on_slab}：它们只有"放在下台阶上"这一种情况，
 * 2 个值就够了，没必要跟着换成 3 值枚举。
 */
public enum SlabOffset implements StringRepresentable {
    NONE("none"),        // 不贴台阶
    LOWERED("lowered"),  // 贴在下台阶上：模型与碰撞箱整体下移半格
    RAISED("raised");    // 贴在上台阶下：模型与碰撞箱整体上移半格

    private final String name;

    SlabOffset(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
