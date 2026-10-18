package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import com.tt23xrstudio.vanilla_refit_fabric.util.PaneCornerSupport;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabOffset;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabSupport;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;

/**
 * 玻璃板 / 铁栏杆的“运行时拼装”模型：一块材质只留一个模型实例，所有 8,192 个状态共用
 * （参考端 {@code PaneBakedModel} 的 26.2 FRAPI 移植）。
 *
 * <p>一块玻璃板的外观 = 12 个部件（四个方向的上下半 + 四个角上面片），全关时是原版那根棍。
 * 原版做法是给每个状态各烘一份模型，按状态展开的模型数据是内存大头；这里的 blockstate
 * 只写一个变体（指向本模组的“棍”模型），要画哪些部件全在渲染期按状态里的 12 个属性决定——
 * 所以这个包装类对整块方块只有<b>一个实例</b>。
 *
 * <p>部件几何来自 {@link PanePieceModels} 的 8 个模板；贴图按方块自己的两张图逐 quad 换
 * （大面 = 模板的玻璃面，断面 = 模板的玻璃顶面），换图规则与竖直台阶的
 * {@link RetexturedTemplateModel} 一致。
 *
 * <p>碰撞箱那边（{@code PaneCornerSupport#adjustShape}）读的是同一份掩码，模型与形状永远一致。
 */
public final class PaneBakedModel extends WrapperBlockStateModel {

    /** 目标方块的两张贴图：侧边面即大面，顶/底面即断面（模板的 up/down 用的就是断面图）。 */
    private final TextureAtlasSprite targetPane;
    private final TextureAtlasSprite targetEdge;
    /** 破坏碎屑粒子；跟着方块自己的“棍”模型走，不跟模板走成玻璃。 */
    private final Material.Baked particle;
    /** 模型级半透明标记（玻璃板 true，栏杆 false）。 */
    private final boolean forceTranslucent;
    /** 材料标记：棍（被包装的模型）自己那份，逐 quad 铺到部件上。 */
    private final int materialFlags;

    private PaneBakedModel(BlockStateModel delegate, ModelSprites sprites) {
        super(delegate);
        this.targetPane = sprites.side();
        this.targetEdge = sprites.top() != null ? sprites.top() : sprites.bottom();
        this.particle = delegate.particleMaterial();
        this.forceTranslucent = particle.forceTranslucent();
        this.materialFlags = delegate.materialFlags();
    }

    /**
     * 造一个包装；被包装的模型里认不出大面 / 断面贴图时返回 {@code null}，
     * 调用方保持原样（降级成原版“棍”外观）。
     *
     * <p>不在这里查部件模板：模板是“额外模型”，烘焙晚于方块模型，
     * 包装发生时它们还没烘好（{@link PanePieceModels#templateSprites()} 还是 null）。
     * 模板在渲染期懒取——那时所有烘焙必然已完成。
     */
    public static PaneBakedModel create(BlockStateModel delegate) {
        ModelSprites sprites = ModelSprites.of(delegate);
        if (sprites == null || sprites.side() == null || sprites.top() == null) {
            return null;
        }
        return new PaneBakedModel(delegate, sprites);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        // 板类贴台阶位移（自研增强，与服务端 PaneCornerShapeMixin 的形状位移同源同判）：
        // 下方是下台阶 → 整体下移半格贴齐砖面；否则上方是上台阶 → 上移半格贴齐砖底。
        // 不落属性、渲染期按邻居现算，所以同一个模型实例对"贴砖的"与"不贴砖的"板都能用。
        SlabOffset offset = SlabSupport.offsetAt(level, pos);
        if (offset == SlabOffset.NONE) {
            emitPieces(emitter, level, pos, state, random, cullTest);
            return;
        }
        boolean lower = offset == SlabOffset.LOWERED;
        float shiftY = lower ? -0.5F : 0.5F;
        // 剔除救援（与 SlabbedLoweringModel/SlabbedRaisingModel 同理）：位移后原本贴着相邻方块、
        // 带 cullface 的那组面（下移 → 原 up 面；上移 → 原 down 面）不再贴合，强制放出，避免缺面。
        Direction rescue = lower ? Direction.UP : Direction.DOWN;
        emitter.pushTransform(quad -> {
            for (int i = 0; i < 4; i++) {
                quad.pos(i, quad.x(i), quad.y(i) + shiftY, quad.z(i));
            }
            if (quad.cullFace() == rescue) {
                quad.cullFace(null);
            }
            return true;
        });
        emitPieces(emitter, level, pos, state, random, dir -> dir != rescue && cullTest.test(dir));
        emitter.popTransform();
    }

    /** 按 12 个部件状态拼装 quad（原 {@link #emitQuads} 主体）。 */
    private void emitPieces(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                            RandomSource random, Predicate<Direction> cullTest) {
        int mask = PaneCornerSupport.pieceMask(state);
        if (mask == 0) {
            // 一个部件都没启用 = 什么都没连：原版那根棍（post + 四个未连接方向的柱面）
            super.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        PanePieceModels.TemplateSprites template = PanePieceModels.templateSprites();
        if (template == null) {
            // 极端兜底：模板还没烘好（正常只可能出现在资源重载的第一帧之前）
            super.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        for (int piece = 0; piece < 12; piece++) {
            if ((mask & (1 << piece)) == 0) {
                continue;
            }
            List<BakedQuad> quads = PanePieceModels.pieceQuads(piece);
            if (quads == null) {
                continue;   // 模板还没烘好：这个部件先不画，比退回整块棍更接近正确外观
            }
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                TextureAtlasSprite source = quad.materialInfo().sprite();
                TextureAtlasSprite target = source == template.edge() ? targetEdge : targetPane;
                // 部件模型自己不带 cullface（和参考端一致）：12 个部件都在“不剔除”桶里
                emitter.cullFace(null);
                emitter.fromBakedQuad(quad);
                // 26.2：模板 quad 的 UV 已是图集坐标——换图必须把 UV 从旧图区域重映到新图区域
                // （materialBake 是“单位 UV → 图集”的映射，对图集坐标再乘一遍会飞出贴图变成纯色）
                QuadRetexture.apply(emitter, quad, source, target, forceTranslucent);
                emitter.ambientOcclusion(TriState.FALSE);
                emitter.shadeMode(ShadeMode.VANILLA);
                emitter.emit();
            }
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
        // 一个实例服务 8,192 个状态，几何键必须带上状态才不会被块内去重复用到别的玻璃板上；
        // 显式返回“无键”最省事（部件本身也都是不剔除面，去重收益很小）
        return null;
    }
}
