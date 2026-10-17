package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import com.mojang.math.Quadrant;
import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedExtraModel;

/**
 * 玻璃板 / 铁栏杆那 12 个部件的几何载体（参考端 {@code PaneModelEvents#onRegisterAdditional}
 * 的 26.2 Fabric 移植）。
 *
 * <p>参考端给「每块方块 × 12 个部件」各烘焙一份模型（26 × 12 = 312 份），这里改成
 * <b>只烘焙 8 个模板</b>：模板是玻璃材质的几何 / UV 真值，运行时按每块方块自己的两张贴图
 * 逐 quad 换图（见 {@link PaneBakedModel}），贴图差异不再需要一份模型。
 *
 * <p>东西两个方向的面片是北 / 南基准模型绕 y 轴转 90° 出来的（和原版 {@code glass_pane_side}
 * 用 {@code "y": 90} 摆东边横杆是同一套做法）。这里不去运行时转顶点，而是在烘焙期直接用
 * {@code y: 90} 的模型状态再烘一份——旋转与 UV 跟随（uvlock=false）全由原版几何烘焙器完成，
 * 与参考端 {@code rotateQuad} 的结果一致。
 *
 * <p>烘焙顺序没有保证（部件模型不在任何 blockstate 里），所以 {@link Holder} 用 volatile
 * 存结果，{@link PaneBakedModel} 在渲染期查表：还没烘好就退回原版那根棍。
 */
public final class PanePieceModels {

    /**
     * 部件下标 → 模板名。顺序必须与 {@code PaneCornerSupport} 里那 12 个部件属性一一对应：
     * 北 / 东 / 南 / 西下半 → 北 / 东 / 南 / 西上半 → 东北 / 东南 / 西南 / 西北角。
     */
    private static final String[] PIECE_TEMPLATE = {
            "pane_side_lower", "pane_side_lower", "pane_side_lower_alt", "pane_side_lower_alt",
            "pane_side_upper", "pane_side_upper", "pane_side_upper_alt", "pane_side_upper_alt",
            "pane_corner", "pane_corner_se", "pane_corner_sw", "pane_corner_nw"};

    /** 部件要不要绕 y 轴转 90°：东用北的模板、西用南的模板（四个角各有一份，不用转）。 */
    private static final boolean[] PIECE_ROTATED = {
            false, true, false, true, false, true, false, true, false, false, false, false};

    /** 唯一模板名，按首次出现顺序。 */
    private static final List<String> UNIQUE_TEMPLATES = List.of("pane_side_lower", "pane_side_lower_alt",
            "pane_side_upper", "pane_side_upper_alt", "pane_corner", "pane_corner_se",
            "pane_corner_sw", "pane_corner_nw");

    /** 部件下标 → {@link #UNIQUE_TEMPLATES} 下标。 */
    private static final int[] PIECE_TO_UNIQUE = buildPieceToUnique();

    /** 唯一模板下标 → 载体；{@link #register} 里填满。 */
    private static final Holder[] HOLDERS = new Holder[UNIQUE_TEMPLATES.size()];

    /** 无旋转：模型状态接口全默认方法，空实现即“原样烘焙”。 */
    private static final ModelState NO_ROTATION = new ModelState() {
    };

    /** 绕 y 轴 90°：原版 blockstate 里 {@code "y": 90} 的同一份语义（北 → 东）。 */
    private static final ModelState ROT_Y_90 = Variant.SimpleModelState.DEFAULT.withY(Quadrant.R90).asModelState();

    /** 模板自带的两张贴图（玻璃大面 / 玻璃顶断面），用来认每个 quad 该换成哪张目标图。 */
    private static volatile TemplateSprites templateSprites;

    private PanePieceModels() {
    }

    /** 登记 8 个模板为“要额外烘焙的模型”；每次资源重载都会调用。 */
    public static void register(ModelLoadingPlugin.Context context) {
        for (int i = 0; i < UNIQUE_TEMPLATES.size(); i++) {
            String template = UNIQUE_TEMPLATES.get(i);
            Holder holder = new Holder(template, needsRotation(template));
            HOLDERS[i] = holder;
            context.addModel(ExtraModelKey.<Baked>create(), holder);
        }
        templateSprites = null;
    }

    /** 重载开头清空：旧模板的四边形（连同里面的贴图引用）随重载作废。 */
    public static void clear() {
        for (int i = 0; i < HOLDERS.length; i++) {
            HOLDERS[i] = null;
        }
        templateSprites = null;
    }

    /**
     * 该部件这次要画的四边形；模板还没烘好（资源正在重载）返回 {@code null}，
     * 调用方退回原版那根棍。
     */
    public static List<BakedQuad> pieceQuads(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= PIECE_TEMPLATE.length) {
            return null;
        }
        Holder holder = HOLDERS[PIECE_TO_UNIQUE[pieceIndex]];
        Baked baked = holder == null ? null : holder.value;
        if (baked == null) {
            return null;
        }
        QuadCollection collection = PIECE_ROTATED[pieceIndex] ? baked.rotated() : baked.plain();
        return collection == null ? null : collection.getAll();
    }

    /** 模板里的两张贴图：{@code pane} = 玻璃大面，{@code edge} = 断面（玻璃顶那张）。 */
    public static TemplateSprites templateSprites() {
        TemplateSprites cached = templateSprites;
        if (cached != null) {
            return cached;
        }
        Holder holder = HOLDERS[UNIQUE_TEMPLATES.indexOf("pane_corner")];
        Baked baked = holder == null ? null : holder.value;
        if (baked == null || baked.plain() == null) {
            return null;
        }
        TextureAtlasSprite pane = null;
        TextureAtlasSprite edge = null;
        for (BakedQuad quad : baked.plain().getAll()) {
            Direction facing = QuadFacing.of(quad);
            if (facing == Direction.UP || facing == Direction.DOWN) {
                pane = quad.materialInfo().sprite();
            } else if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                edge = quad.materialInfo().sprite();
            }
        }
        if (pane == null || edge == null) {
            return null;
        }
        cached = new TemplateSprites(pane, edge);
        templateSprites = cached;
        return cached;
    }

    /** 部件下标 → 唯一模板下标。 */
    private static int[] buildPieceToUnique() {
        int[] map = new int[PIECE_TEMPLATE.length];
        for (int i = 0; i < map.length; i++) {
            map[i] = UNIQUE_TEMPLATES.indexOf(PIECE_TEMPLATE[i]);
        }
        return map;
    }

    /** 该模板有没有部件要转 90°。 */
    private static boolean needsRotation(String template) {
        for (int i = 0; i < PIECE_TEMPLATE.length; i++) {
            if (PIECE_TEMPLATE[i].equals(template) && PIECE_ROTATED[i]) {
                return true;
            }
        }
        return false;
    }

    /** 模板自带的两张贴图。 */
    public record TemplateSprites(TextureAtlasSprite pane, TextureAtlasSprite edge) {
    }

    /**
     * 一个模板的烘焙结果：原样一份，需要时再烘一份绕 y 轴 90° 的。
     *
     * <p>两个 {@link QuadCollection} 都是原版烘焙器的成品：顶点位置、UV、材质标记都已定型，
     * 渲染期只做换图。
     */
    public static final class Holder implements UnbakedExtraModel<Baked> {

        private final Identifier id;
        private final boolean rotated;
        private volatile Baked value;

        Holder(String template, boolean rotated) {
            this.id = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "block/" + template);
            this.rotated = rotated;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            resolver.markDependency(id);
        }

        @Override
        public Baked bake(ModelBaker baker) {
            ResolvedModel model = baker.getModel(id);
            TextureSlots slots = model.getTopTextureSlots();
            QuadCollection plain = model.bakeTopGeometry(slots, baker, NO_ROTATION);
            QuadCollection turned = rotated ? model.bakeTopGeometry(slots, baker, ROT_Y_90) : plain;
            Baked baked = new Baked(plain, turned);
            this.value = baked;
            return baked;
        }
    }

    /** @see Holder */
    public record Baked(QuadCollection plain, QuadCollection rotated) {
    }
}
