package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * 「一个方块的顶 / 底 / 侧三张图」，以及按手工模型约定换图的小工具（参考端的 26.2/FRAPI 移植）。
 *
 * <p>约定（与 {@code vertical_slab_multi_*} / {@code vertical_stair_multi} 一致）：
 *
 * <pre>
 *   朝上的面 → top     朝下的面 → bottom     其余（东南西北）→ side
 * </pre>
 *
 * <p>单纹理方块上这三张是同一张图，换与不换看不出区别；多纹理方块（砂岩类）才会体现出来。
 */
public record ModelSprites(TextureAtlasSprite top, TextureAtlasSprite bottom, TextureAtlasSprite side) {

    /** 六个方向 + 无剔除桶（null）。 */
    private static final Direction[] BUCKETS = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, null
    };

    /**
     * 从某个平放模型里问出三张图；问不出来（模型结构太特殊）返回 {@code null}。
     *
     * <p>不能按方向直接问 —— 烘焙按 cullface 分桶，没写 cullface 的面在“无剔除”桶里。
     * 所以把 7 个桶全部拿出来，按顶点算出的几何朝向归类。
     */
    public static ModelSprites of(BlockStateModel flatModel) {
        RandomSource random = RandomSource.create(42L);
        List<BlockStateModelPart> parts = new ArrayList<>();
        flatModel.collectParts(random, parts);

        TextureAtlasSprite top = null;
        TextureAtlasSprite bottom = null;
        TextureAtlasSprite side = null;
        for (BlockStateModelPart part : parts) {
            for (Direction bucket : BUCKETS) {
                for (BakedQuad quad : part.getQuads(bucket)) {
                    Direction facing = QuadFacing.of(quad);
                    if (facing == null) {
                        continue;
                    }
                    TextureAtlasSprite sprite = quad.materialInfo().sprite();
                    switch (facing) {
                        case UP -> top = top == null ? sprite : top;
                        case DOWN -> bottom = bottom == null ? sprite : bottom;
                        default -> side = side == null ? sprite : side;
                    }
                }
            }
        }
        // 顶/底取不到就退回侧面图（很多方块顶底与侧面同图，这么做不会出错）
        if (side == null) {
            return null;
        }
        return new ModelSprites(top != null ? top : side, bottom != null ? bottom : side, side);
    }

    /** 该朝向的面该用哪张图；朝向未知（null）退回侧面图。 */
    public TextureAtlasSprite forFace(Direction face) {
        if (face == null) {
            return side;
        }
        return switch (face) {
            case UP -> top;
            case DOWN -> bottom;
            default -> side;
        };
    }
}
