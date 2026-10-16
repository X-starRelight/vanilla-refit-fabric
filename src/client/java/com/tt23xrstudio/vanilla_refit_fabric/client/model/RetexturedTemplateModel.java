package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;

/**
 * 把「其它模组方块」的竖形态渲染成「本模组的模板几何 + 对方贴图」。台阶和楼梯都走这一条路。
 * （参考端 {@code RetexturedTemplateModel} 的 26.2/FRAPI 移植：原 {@code BakedQuad} 列表换图
 * 改为逐 quad 走 {@link QuadEmitter}。）
 *
 * <p>为什么不旋转对方自己的模型：旋转会把 UV、面剔除标记（cullface）和顶点朝向一起带走——
 * 贴图会跟着转 90°、环境光遮蔽会上下颠倒、剔除还会落到错误的方向上。而这些模板
 * （{@code vertical_slab_*} / {@code vertical_stair_*}）本身就是按竖放姿势手工调好的，
 * 几何、UV、剔除标记全都是对的，只需要把贴图换掉。
 *
 * <p>换图规则和手工模型（{@code vertical_slab_multi_*} / {@code vertical_stair_multi}）一致：
 * 朝上的面用 top、朝下的面用 bottom、其余用 side。UV 只做等比映射，保持模板里定好的区域。
 */
public final class RetexturedTemplateModel extends WrapperBlockStateModel {

    private final ModelSprites sprites;
    private final Material.Baked particle;
    /** 目标平放模型 {@code render_type} 的半透明标记（模型级属性，逐 quad 保留）。 */
    private final boolean forceTranslucent;
    private final int materialFlags;
    /** 模板的静态部件（构造期提取一次，渲染期只读）。 */
    private final List<BlockStateModelPart> templateParts;

    private RetexturedTemplateModel(BlockStateModel template, ModelSprites sprites, Material.Baked particle,
                                    int materialFlags) {
        super(template);
        this.sprites = sprites;
        this.particle = particle;
        this.forceTranslucent = particle.forceTranslucent();
        this.materialFlags = materialFlags;
        List<BlockStateModelPart> parts = new ArrayList<>();
        template.collectParts(RandomSource.create(42L), parts);
        this.templateParts = List.copyOf(parts);
    }

    /**
     * @param template      本模组的竖形态模板（几何、UV、剔除标记）
     * @param sprites       目标方块的贴图（顶/底/侧）
     * @param particle      目标方块自己的粒子材质（破坏碎屑；不传就会跟着模板走成橡木）
     * @param materialFlags 保守材料标记 = 平放模型 | 模板（见 {@code materialFlags()} 说明）
     */
    public static RetexturedTemplateModel create(BlockStateModel template, ModelSprites sprites,
                                                 Material.Baked particle, int materialFlags) {
        return new RetexturedTemplateModel(template, sprites, particle, materialFlags);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        // 与 vanilla 默认实现保持一致的“强制不透明”分支（slab/楼梯状态恒为 false，代价为零）
        final boolean forceOpaque = ModelBlockRenderer.forceOpaque(
                Minecraft.getInstance().options.cutoutLeaves().get(), state);
        if (forceOpaque) {
            emitter.pushTransform(quad -> {
                quad.chunkLayer(ChunkSectionLayer.SOLID);
                return true;
            });
        }

        for (int i = 0; i < templateParts.size(); i++) {
            BlockStateModelPart part = templateParts.get(i);
            TriState ao = part.useAmbientOcclusion() ? TriState.DEFAULT : TriState.FALSE;
            for (int faceIndex = 0; faceIndex <= ModelHelper.NULL_FACE_ID; faceIndex++) {
                Direction bucket = ModelHelper.faceFromIndex(faceIndex);
                if (cullTest.test(bucket)) {
                    continue;
                }
                List<BakedQuad> quads = part.getQuads(bucket);
                for (int qi = 0; qi < quads.size(); qi++) {
                    BakedQuad quad = quads.get(qi);
                    // 桶是 cullface 桶：无剔除桶里的面只能靠顶点算朝向（模板里 south/east 就是这种）
                    Direction face = bucket != null ? bucket : QuadFacing.of(quad);
                    TextureAtlasSprite source = quad.materialInfo().sprite();
                    TextureAtlasSprite target = sprites.forFace(face);

                    emitter.cullFace(bucket);
                    emitter.fromBakedQuad(quad);
                    if (target != source) {
                        retexture(emitter, source, target);
                        emitter.postMaterialBake(new Material.Baked(target, forceTranslucent));
                    }
                    emitter.ambientOcclusion(ao);
                    emitter.shadeMode(ShadeMode.VANILLA);
                    emitter.emit();
                }
            }
        }

        if (forceOpaque) {
            emitter.popTransform();
        }
    }

    /** 按「在旧图里的相对位置」把 UV 等比映射到新图上，保持模板里定好的区域。 */
    private static void retexture(QuadEmitter emitter, TextureAtlasSprite source, TextureAtlasSprite target) {
        float sourceWidth = source.getU1() - source.getU0();
        float sourceHeight = source.getV1() - source.getV0();
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = emitter.u(vertex);
            float v = emitter.v(vertex);
            float relativeU = sourceWidth == 0.0F ? 0.0F : (u - source.getU0()) / sourceWidth;
            float relativeV = sourceHeight == 0.0F ? 0.0F : (v - source.getV0()) / sourceHeight;
            emitter.uv(vertex, target.getU(relativeU), target.getV(relativeV));
        }
    }

    @Override
    public Material.Baked particleMaterial() {
        return particle;
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return particle;
    }

    /**
     * 无上下文版本必须对所有上下文“保守正确”：取平放模型（同贴图）与模板（同几何）的
     * 材料标记按位或——前者覆盖目标贴图的半透明/动画，后者覆盖模板几何的面集合。
     */
    @Override
    public int materialFlags() {
        return materialFlags;
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return materialFlags;
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        // 换图后的几何与模板不同（UV/材质不同），不能沿用模板自己的几何键，显式返回“无键”
        return null;
    }
}
