package com.tt23xrstudio.vanilla_refit_fabric.util;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * 竖楼梯的连接形态。
 *
 * <p>不用独立的方块属性（{@code btsdhz_connection}）时它会带 6 个取值，让每块楼梯的状态数乘 6。
 * 现在**借用原版 {@code shape} 的 5 个值当载体**：竖放时 shape 本来就是死的——放置时写
 * {@code straight}，{@code updateShape} 也只重算连接与上下、不碰 shape——5 个值正好和这里的
 * 5 种形态一一对应，于是整条属性可以删掉，每块楼梯的状态数从 2,880 降到 480。
 *
 * <p>{@code bottom} / {@code top} 两个拐角形态合并成 {@link #CORNER}：它们只差"补块落在
 * 上半还是下半"，而这个信息本来就在 {@code half} 里（见 {@code StairBlockMixin#getConnectionHalf}），
 * 合并后一个信息都不丢。
 *
 * <p>载体对应关系（改这里必须同时改 datagen 的 blockstate 生成与手写 blockstate）：
 * {@code straight} → 无连接、{@code inner_left} → 连接左、{@code inner_right} → 连接右、
 * {@code outer_left} → 拐角、{@code outer_right} → 双面连接。左右这一对和原版镜像映射
 * （{@code inner_left ↔ inner_right}）正好对上，镜像时天然正确。
 */
public enum StairConnection implements StringRepresentable {
    NONE("none"),                // 普通竖 L
    CORNER("corner"),            // 拐角连接：补块在上半还是下半由 half 决定
    CONN_RIGHT("conn_right"),    // 单大面连接右（模型A）
    CONN_LEFT("conn_left"),      // 单大面连接左（模型B）
    CONN_DOUBLE("conn_double");  // 双大面连接（模型C）

    private final String name;

    StairConnection(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** 该形态占用的 {@code shape} 载体值。 */
    public StairsShape toShape() {
        return switch (this) {
            case NONE -> StairsShape.STRAIGHT;
            case CONN_LEFT -> StairsShape.INNER_LEFT;
            case CONN_RIGHT -> StairsShape.INNER_RIGHT;
            case CORNER -> StairsShape.OUTER_LEFT;
            case CONN_DOUBLE -> StairsShape.OUTER_RIGHT;
        };
    }

    /** 载体值 → 形态。 */
    public static StairConnection fromShape(StairsShape shape) {
        return switch (shape) {
            case STRAIGHT -> NONE;
            case INNER_LEFT -> CONN_LEFT;
            case INNER_RIGHT -> CONN_RIGHT;
            case OUTER_LEFT -> CORNER;
            case OUTER_RIGHT -> CONN_DOUBLE;
        };
    }

    /**
     * 读一个楼梯状态的连接形态。
     *
     * <p>平放楼梯的 shape 是原版语义（直/内角/外角），不能当载体解，所以先看
     * {@code btsdhz_vertical}：不是竖放就一律 {@link #NONE}。
     */
    public static StairConnection of(BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.VERTICAL)
                || !state.hasProperty(BlockStateProperties.STAIRS_SHAPE)
                || !state.getValue(ModBlockStateProperties.VERTICAL)) {
            return NONE;
        }
        return fromShape(state.getValue(BlockStateProperties.STAIRS_SHAPE));
    }
}
