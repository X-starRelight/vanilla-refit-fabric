package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.block.MixedSlabBlock;
import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.PaneCornerSupport;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabSupport;
import com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;

/**
 * 客户端模型装载事件总入口（对应参考端 {@code SlabbedModelEvents} 的阶段 2 部分：
 * 竖直台阶/楼梯的“模板几何 + 目标贴图”包装，以及混合半砖的动态合并包装）。
 *
 * <p>26.2 Fabric 没有“烘焙完拿整张 map 换模型”的事件，等价流程是：
 * <ol>
 *   <li>{@code modifyBlockModelOnLoad}（每状态、加载阶段、烘焙前）——捕获模板载体
 *       （橡木台阶/楼梯）竖直状态的未烘焙模型，作为运行时几何模板；</li>
 *   <li>{@code modifyBlockModelAfterBake}（每状态、烘焙后）——竖直状态用模板重新烘焙出
 *       几何、配目标贴图生成 {@link RetexturedTemplateModel}；混合半砖包 {@link MixedSlabModel}。</li>
 * </ol>
 *
 * <p>捕获必须放在加载阶段：烘焙是并行的，两个事件里互相等对方的状态会死锁/竞态。
 */
public final class ModModelLoading {

    /**
     * 手工建模的特例：这些方块的竖形态是手工做的多纹理模型，运行时<b>不接管</b>
     * （平滑石台阶 + 砂岩家族 4 个台阶 + 砂岩家族 4 个楼梯，与参考端一致）。
     */
    private static final Set<Identifier> HAND_AUTH = Set.of(
            Identifier.withDefaultNamespace("smooth_stone_slab"),
            Identifier.withDefaultNamespace("sandstone_slab"),
            Identifier.withDefaultNamespace("cut_sandstone_slab"),
            Identifier.withDefaultNamespace("red_sandstone_slab"),
            Identifier.withDefaultNamespace("cut_red_sandstone_slab"),
            Identifier.withDefaultNamespace("sandstone_stairs"),
            Identifier.withDefaultNamespace("red_sandstone_stairs"),
            Identifier.withDefaultNamespace("smooth_sandstone_stairs"),
            Identifier.withDefaultNamespace("smooth_red_sandstone_stairs")
    );

    /** 模板载体：它们的静态模型就是运行时用的几何模板，不接管。 */
    private static final Set<Identifier> TEMPLATE_CARRIERS = Set.of(
            Identifier.withDefaultNamespace("oak_slab"),
            Identifier.withDefaultNamespace("oak_stairs")
    );

    /**
     * 模板载体竖直状态 → 未烘焙模型。加载阶段填充（此时全部状态已解析、烘焙尚未开始），
     * 烘焙阶段查表用 {@code bake(同状态, baker)} 拿到带方块状态旋转的模板几何。
     * 每次资源重载开头清空重建（UnbakedRoot 对象随重载作废）。
     */
    private static final Map<BlockState, BlockStateModel.UnbakedRoot> TEMPLATE_ROOTS = new ConcurrentHashMap<>();

    /**
     * 玻璃板 / 铁栏杆：被包装的模型 → 共享的那个 {@link PaneBakedModel}。
     *
     * <p>blockstate 只留一个变体，所以同一块方块的 8,192 个状态共用同一份被包装模型；
     * 这里用恒等映射保证「一块材质 = 一个包装实例」，不会按状态刷出几千份。
     * 每次资源重载开头清空（模型对象随重载作废）。
     */
    private static final Map<BlockStateModel, BlockStateModel> PANE_WRAPPERS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /** 模板缺失只提示一次的方块（每次重载清空）。 */
    private static final Set<Identifier> MISSING_LOGGED = ConcurrentHashMap.newKeySet();

    private ModModelLoading() {
    }

    /** 登记模型装载插件；客户端初始化时调用一次。 */
    public static void register() {
        ModelLoadingPlugin.register(ctx -> {
            // 每次重载开头：清掉上一轮的模板缓存
            TEMPLATE_ROOTS.clear();
            MISSING_LOGGED.clear();
            PANE_WRAPPERS.clear();
            // 加载阶段：捕获橡木模板载体的竖直状态模型
            ctx.modifyBlockModelOnLoad().register(ModelModifier.OVERRIDE_PHASE, ModModelLoading::onLoadBlock);
            // 烘焙阶段：按状态换模型
            ctx.modifyBlockModelAfterBake().register(ModelModifier.OVERRIDE_PHASE, ModModelLoading::afterBake);
            // 玻璃板 / 铁栏杆：8 个部件模板登记为要额外烘焙的模型（blockstate 不再引用它们）
            PanePieceModels.register(ctx);
        });
    }

    private static BlockStateModel.UnbakedRoot onLoadBlock(BlockStateModel.UnbakedRoot root,
                                                           ModelModifier.OnLoadBlock.Context context) {
        BlockState state = context.state();
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block == Blocks.OAK_SLAB || block == Blocks.OAK_STAIRS) {
            TEMPLATE_ROOTS.put(state, root);
        }
        return root;
    }

    private static BlockStateModel afterBake(BlockStateModel model, ModelModifier.AfterBakeBlock.Context context) {
        BlockState state = context.state();
        net.minecraft.world.level.block.Block block = state.getBlock();

        // 混合半砖：包装成读方块实体动态合并的模型（唯一不被“非本模组命名空间”规则挡住的本模组方块）
        if (block instanceof MixedSlabBlock) {
            return state.hasProperty(ModBlockStateProperties.MODE) ? new MixedSlabModel(model) : model;
        }
        // 玻璃板 / 铁栏杆：12 个部件按状态拼装（一个实例服务全部状态）
        if (PaneCornerSupport.isSupportedPaneBlock(block)) {
            return PANE_WRAPPERS.computeIfAbsent(model, ModModelLoading::wrapPane);
        }
        // 竖直台阶 / 竖直楼梯：模板几何 + 目标贴图
        if (state.hasProperty(ModBlockStateProperties.MODE)) {
            return wrapSlabState(state, block, model, context);
        }
        if (state.hasProperty(ModBlockStateProperties.VERTICAL)) {
            return wrapStairState(state, block, model, context);
        }
        // 阶段 3：贴台阶位移的视觉包装。
        // 火把类（ON_SLAB=true）与墙/栅栏/灯笼（SLAB_OFFSET=lowered/raised）整体位移半格。
        // 栅栏先包 FenceStepModel（内层）：FRAPI transform 按 LIFO 应用，后压入的先执行，
        // 横杆分类看到的是未位移的原版坐标；位移包装在外层最后平移。普通栅栏也要包
        // FenceStepModel——它可能是跨台阶关节里"未位移的高/低侧"。
        BlockStateModel result = model;
        if (block instanceof net.minecraft.world.level.block.FenceBlock) {
            result = new FenceStepModel(result);
        }
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB)) {
            return new SlabbedLoweringModel(result);
        }
        if (state.hasProperty(ModBlockStateProperties.SLAB_OFFSET)) {
            com.tt23xrstudio.vanilla_refit_fabric.util.SlabOffset offset =
                    state.getValue(ModBlockStateProperties.SLAB_OFFSET);
            if (offset == com.tt23xrstudio.vanilla_refit_fabric.util.SlabOffset.LOWERED) {
                return new SlabbedLoweringModel(result);
            }
            if (offset == com.tt23xrstudio.vanilla_refit_fabric.util.SlabOffset.RAISED) {
                return new SlabbedRaisingModel(result);
            }
        }
        return result;
    }

    /** 竖直台阶状态 → {@link RetexturedTemplateModel}。 */
    private static BlockStateModel wrapSlabState(BlockState state, net.minecraft.world.level.block.Block block,
                                                 BlockStateModel model, ModelModifier.AfterBakeBlock.Context context) {
        if (!(block instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) {
            return model;
        }
        if (state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB) {
            return model;   // 平放状态用对方自己的模型
        }
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (!isRuntimeWrapped(blockId) || !SlabSupport.isSupportedSlab(block)) {
            return model;
        }
        ModelSprites sprites = ModelSprites.of(model);
        if (sprites == null) {
            VanillaRefitFabric.LOGGER.debug("[原版精修/模型] 跳过台阶 {}：无法解析顶/底/侧贴图", blockId);
            return model;
        }
        // 同状态的橡木竖直台阶 = 模板（几何、UV、剔除标记都在这儿）
        BlockState donor = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, state.getValue(ModBlockStateProperties.MODE))
                .setValue(SlabBlock.TYPE, state.getValue(SlabBlock.TYPE));
        BlockStateModel geometry = bakeTemplate(donor, blockId, context);
        if (geometry == null) {
            return model;
        }
        return RetexturedTemplateModel.create(geometry, sprites, model.particleMaterial(),
                model.materialFlags() | geometry.materialFlags());
    }

    /** 竖直楼梯状态 → {@link RetexturedTemplateModel}（目标与模板状态用同一组 connection/facing/half）。 */
    private static BlockStateModel wrapStairState(BlockState state, net.minecraft.world.level.block.Block block,
                                                  BlockStateModel model, ModelModifier.AfterBakeBlock.Context context) {
        if (!(block instanceof StairBlock)
                || !state.hasProperty(StairBlock.FACING)
                || !state.hasProperty(StairBlock.HALF)
                || !state.hasProperty(StairBlock.SHAPE)) {
            return model;
        }
        if (!state.getValue(ModBlockStateProperties.VERTICAL)) {
            return model;   // 平放状态用对方自己的模型
        }
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (!isRuntimeWrapped(blockId) || !SlabSupport.isSupportedStair(block)) {
            return model;
        }
        ModelSprites sprites = ModelSprites.of(model);
        if (sprites == null) {
            VanillaRefitFabric.LOGGER.debug("[原版精修/模型] 跳过楼梯 {}：无法解析顶/底/侧贴图", blockId);
            return model;
        }
        // 同 (facing, half, shape) 的橡木竖直楼梯 = 模板；连接形态的载体就是 shape，沿用即可
        BlockState donor = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(ModBlockStateProperties.VERTICAL, true)
                .setValue(StairBlock.FACING, state.getValue(StairBlock.FACING))
                .setValue(StairBlock.HALF, state.getValue(StairBlock.HALF))
                .setValue(StairBlock.SHAPE, state.getValue(StairBlock.SHAPE));
        BlockStateModel geometry = bakeTemplate(donor, blockId, context);
        if (geometry == null) {
            return model;
        }
        return RetexturedTemplateModel.create(geometry, sprites, model.particleMaterial(),
                model.materialFlags() | geometry.materialFlags());
    }

    private static BlockStateModel bakeTemplate(BlockState donor, Identifier targetBlockId,
                                                ModelModifier.AfterBakeBlock.Context context) {
        BlockStateModel.UnbakedRoot root = TEMPLATE_ROOTS.get(donor);
        if (root == null) {
            if (MISSING_LOGGED.add(targetBlockId)) {
                VanillaRefitFabric.LOGGER.warn("[原版精修/模型] 找不到橡木模板状态 {}，方块 {} 的竖直状态退回平放渲染",
                        donor, targetBlockId);
            }
            return null;
        }
        try {
            return root.bake(donor, context.baker());
        } catch (Exception exception) {
            if (MISSING_LOGGED.add(targetBlockId)) {
                VanillaRefitFabric.LOGGER.warn("[原版精修/模型] 模板烘焙失败（方块 {}）", targetBlockId, exception);
            }
            return null;
        }
    }

    /**
     * 玻璃板 / 铁栏杆 → 按状态拼装 12 个部件的模型。
     *
     * <p>认不出大面 / 断面贴图（资源包改了棍模型）时保持原样，只警告一次的语义交给
     * 重复调用自然形成（同一批模型只会走到这里一次）。
     */
    private static BlockStateModel wrapPane(BlockStateModel model) {
        PaneBakedModel wrapped = PaneBakedModel.create(model);
        if (wrapped == null) {
            VanillaRefitFabric.LOGGER.warn("[原版精修/模型] 玻璃板/铁栏杆的棍模型认不出大面或断面贴图，退回原版外观");
            return model;
        }
        return wrapped;
    }

    /** 该方块是否由运行时接管竖形态模型（手工特例、模板载体、本模组自己的方块都不接管）。 */
    private static boolean isRuntimeWrapped(Identifier blockId) {
        return !HAND_AUTH.contains(blockId)
                && !TEMPLATE_CARRIERS.contains(blockId)
                && !blockId.getNamespace().equals(VanillaRefitFabric.MOD_ID);
    }
}
