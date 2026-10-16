package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.ModBlocks;
import com.tt23xrstudio.vanilla_refit_fabric.block.entity.MixedSlabBlockEntity;
import com.tt23xrstudio.vanilla_refit_fabric.util.MixedSlabBreakHandler;
import com.tt23xrstudio.vanilla_refit_fabric.util.MixedSlabPlacement;
import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementModeState;
import com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 阶段 2 测试：核心玩法——竖直半砖/竖直楼梯状态、混合半砖填充（同/异材质、原版模式、
 * 相邻面、竖直朝向）、按视线“拆一半”、放置模式持久状态、宽松工具规则。
 *
 * <p>约定：helper.* 写入方法（setBlock 等）吃相对坐标；读取一律走
 * {@code level.getBlockState(absolutePos)}——{@code helper.getBlockState} 内部也会做
 * {@code absolutePos} 换算，传绝对坐标会被双重偏移读成空气。
 */
public class Phase2GameTest {

    private static final BlockPos REL = new BlockPos(1, 1, 1);
    private static final double EPS = 1.0E-6;

    // ===== 1. 竖直半砖形状：每个朝向精确到半格 =====

    @GameTest
    public void verticalSlabShapeBounds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REL);

        // 对照组：平放半砖仍是原版下/上半
        assertShape(helper, level, pos, slabState(VerticalSlabMode.SLAB, SlabType.BOTTOM),
                0, 0, 0, 1, 0.5, 1, "平放下半（对照）");
        // 竖直四个半格 + 双竖整块
        assertShape(helper, level, pos, slabState(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM),
                0, 0, 0, 1, 1, 0.5, "竖直北半");
        assertShape(helper, level, pos, slabState(VerticalSlabMode.VERTICAL_NS, SlabType.TOP),
                0, 0, 0.5, 1, 1, 1, "竖直南半");
        assertShape(helper, level, pos, slabState(VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM),
                0, 0, 0, 0.5, 1, 1, "竖直西半");
        assertShape(helper, level, pos, slabState(VerticalSlabMode.VERTICAL_EW, SlabType.TOP),
                0.5, 0, 0, 1, 1, 1, "竖直东半");
        assertShape(helper, level, pos, slabState(VerticalSlabMode.VERTICAL_NS, SlabType.DOUBLE),
                0, 0, 0, 1, 1, 1, "竖直双块（整块）");

        helper.succeed();
    }

    // ===== 2. 竖直楼梯：属性注入 + 竖直形状与平放不同 =====

    @GameTest
    public void verticalStairShapeDiffersFromFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REL);

        BlockState flat = Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        // 属性没注入的话下面这行直接抛异常（测试同样会失败）
        BlockState vert = flat.setValue(ModBlockStateProperties.VERTICAL, true);

        helper.setBlock(1, 1, 1, vert);
        BlockState placed = level.getBlockState(pos);
        if (!placed.hasProperty(ModBlockStateProperties.VERTICAL)
                || !placed.getValue(ModBlockStateProperties.VERTICAL)) {
            helper.fail("放置后 btsdhz_vertical 标记丢失");
            return;
        }

        VoxelShape vertShape = placed.getShape(level, pos);
        VoxelShape flatShape = flat.getShape(level, pos);
        if (vertShape.isEmpty()) {
            helper.fail("竖直楼梯形状不应为空");
            return;
        }
        if (vertShape.toAabbs().equals(flatShape.toAabbs())) {
            helper.fail("竖直楼梯形状与平放完全相同 → getShape 的竖直接管未生效");
            return;
        }
        for (AABB box : vertShape.toAabbs()) {
            if (box.minX < -EPS || box.minY < -EPS || box.minZ < -EPS
                    || box.maxX > 1 + EPS || box.maxY > 1 + EPS || box.maxZ > 1 + EPS) {
                helper.fail("竖直楼梯形状超出方块范围：" + box);
                return;
            }
        }
        helper.succeed();
    }

    // ===== 3. 填充：不同材质 → 混合半砖 =====

    @GameTest
    public void tryFillDifferentMaterialMerges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REL);

        helper.setBlock(1, 1, 1, Blocks.STONE_SLAB);
        BlockState target = level.getBlockState(pos);

        if (!MixedSlabPlacement.tryFill(level, pos, target, Blocks.OAK_SLAB, Direction.UP, false)) {
            helper.fail("平放下半空台阶点顶面（朝空余半砖）填入不同材质应成功");
            return;
        }
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() != ModBlocks.MERGED_SLAB) {
            helper.fail("不同材质填充应变成 merged_slab，实际 " + after.getBlock());
            return;
        }
        if (after.getValue(ModBlockStateProperties.MODE) != VerticalSlabMode.SLAB
                || after.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            helper.fail("合并后朝向应保留原朝向（MODE=SLAB, TYPE=BOTTOM），实际 "
                    + after.getValue(ModBlockStateProperties.MODE) + "/" + after.getValue(SlabBlock.TYPE));
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MixedSlabBlockEntity mixed)) {
            helper.fail("合并后应生成混合半砖方块实体");
            return;
        }
        if (mixed.getSlabA() != Blocks.STONE_SLAB || mixed.getSlabB() != Blocks.OAK_SLAB) {
            helper.fail("A 应为先有的 stone_slab、B 应为后填的 oak_slab");
            return;
        }
        helper.succeed();
    }

    // ===== 4. 填充：同材质 → 原版 DOUBLE =====

    @GameTest
    public void tryFillSameMaterialMakesDouble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REL);

        helper.setBlock(1, 1, 1, Blocks.STONE_SLAB);
        BlockState target = level.getBlockState(pos);

        if (!MixedSlabPlacement.tryFill(level, pos, target, Blocks.STONE_SLAB, Direction.UP, false)) {
            helper.fail("同材质填满应成功（原版堆叠行为）");
            return;
        }
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() != Blocks.STONE_SLAB || after.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
            helper.fail("同材质应堆成原版 DOUBLE stone_slab，实际 "
                    + after.getBlock() + "/" + after.getValue(SlabBlock.TYPE));
            return;
        }
        helper.succeed();
    }

    // ===== 5. 填充：原版模式（mixedOnly）不接同材质 =====

    @GameTest
    public void tryFillMixedOnlyRejectsSameMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REL);

        helper.setBlock(1, 1, 1, Blocks.STONE_SLAB);
        BlockState target = level.getBlockState(pos);

        if (MixedSlabPlacement.tryFill(level, pos, target, Blocks.STONE_SLAB, Direction.UP, true)) {
            helper.fail("原版放置模式下同材质填充应交回原版处理（返回 false）");
            return;
        }
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() != Blocks.STONE_SLAB || after.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            helper.fail("拒绝后状态不应被改动");
            return;
        }
        helper.succeed();
    }

    // ===== 6. 填充：点相邻方块、面朝向半砖空余空间 =====

    @GameTest
    public void tryFillFromNeighborFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabPos = helper.absolutePos(REL);
        BlockPos dirtPos = helper.absolutePos(new BlockPos(2, 1, 1));

        helper.setBlock(1, 1, 1, Blocks.STONE_SLAB);
        helper.setBlock(2, 1, 1, Blocks.DIRT);

        // 点泥土朝西的面（朝向旁边半砖的空余半砖）
        BlockState dirtState = level.getBlockState(dirtPos);
        if (!MixedSlabPlacement.tryFill(level, dirtPos, dirtState, Blocks.OAK_SLAB, Direction.WEST, false)) {
            helper.fail("点相邻方块、面朝向空余半砖时应能填充旁边的半砖");
            return;
        }
        BlockState after = level.getBlockState(slabPos);
        if (after.getBlock() != ModBlocks.MERGED_SLAB) {
            helper.fail("被填充的应是旁边那格半砖（变成 merged_slab），实际 " + after.getBlock());
            return;
        }
        if (level.getBlockState(dirtPos).getBlock() != Blocks.DIRT) {
            helper.fail("泥土那格不应被改动");
            return;
        }
        helper.succeed();
    }

    // ===== 7. 填充：竖直朝向（北半 → 点南面空余空间），错面拒绝 =====

    @GameTest
    public void tryFillVerticalOrientation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REL);

        helper.setBlock(1, 1, 1, slabState(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM));
        BlockState target = level.getBlockState(pos);

        // 北半的空余空间在南侧；点顶面不是空余面（且上方是空气）→ 不该填
        if (MixedSlabPlacement.tryFill(level, pos, target, Blocks.OAK_SLAB, Direction.UP, false)) {
            helper.fail("竖直北半点顶面不是空余面，不应填充");
            return;
        }
        if (level.getBlockState(pos).getBlock() != Blocks.STONE_SLAB) {
            helper.fail("被拒绝的尝试不应改动方块");
            return;
        }

        // 正确空余面（南）→ 混合
        if (!MixedSlabPlacement.tryFill(level, pos, level.getBlockState(pos), Blocks.OAK_SLAB, Direction.SOUTH, false)) {
            helper.fail("竖直北半点南面（空余半砖）应能填充");
            return;
        }
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() != ModBlocks.MERGED_SLAB
                || after.getValue(ModBlockStateProperties.MODE) != VerticalSlabMode.VERTICAL_NS
                || after.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            helper.fail("竖直填充后应为 merged_slab(MODE=VERTICAL_NS, TYPE=BOTTOM)");
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MixedSlabBlockEntity mixed)
                || mixed.getSlabA() != Blocks.STONE_SLAB || mixed.getSlabB() != Blocks.OAK_SLAB) {
            helper.fail("A=stone_slab（北半） B=oak_slab（南半）");
            return;
        }
        helper.succeed();
    }

    // ===== 8. 拆一半：从北侧看竖直混合半砖 → 拆掉北半(A)，保留南半(B) =====

    @GameTest
    public void breakVerticalMergedHalfByCrosshair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REL);

        helper.setBlock(1, 1, 1, ModBlocks.MERGED_SLAB.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_NS)
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(ModBlockStateProperties.FLUID_TYPE, com.tt23xrstudio.vanilla_refit_fabric.util.FluidType.NONE));
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MixedSlabBlockEntity mixed)) {
            helper.fail("merged_slab 应生成方块实体");
            return;
        }
        mixed.setBlocks(Blocks.STONE_SLAB, Blocks.OAK_SLAB);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // 站在方块北侧、视线朝南，高度对准方块中心：无论走视线裁剪还是方位兜底，都命中北半(A)
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5 - player.getEyeHeight(), pos.getZ() - 2.0);
        player.setYRot(0.0F);   // yaw 0 = 面向 +Z（南）
        player.setXRot(0.0F);

        BlockState state = level.getBlockState(pos);
        if (!MixedSlabBreakHandler.tryBreakHalf(level, pos, state, player)) {
            helper.fail("拆一半应成功");
            return;
        }
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() != Blocks.OAK_SLAB) {
            helper.fail("拆掉北半(A=stone) 后应保留南半 B=oak_slab，实际 " + after.getBlock());
            return;
        }
        if (after.getValue(ModBlockStateProperties.MODE) != VerticalSlabMode.VERTICAL_NS
                || after.getValue(SlabBlock.TYPE) != SlabType.TOP) {
            helper.fail("保留的应是竖直南半（MODE=VERTICAL_NS, TYPE=TOP），实际 "
                    + after.getValue(ModBlockStateProperties.MODE) + "/" + after.getValue(SlabBlock.TYPE));
            return;
        }
        if (level.getBlockEntity(pos) != null) {
            helper.fail("只剩单半后混合半砖方块实体应被移除");
            return;
        }
        helper.succeed();
    }

    // ===== 9. 放置模式：服务端持久状态写入 + 读回 + 按键轮转 =====

    @GameTest
    public void placementModeAttachmentRoundTrip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        if (PlacementModeState.slab(player) != PlacementMode.MOD
                || PlacementModeState.stair(player) != PlacementMode.MOD) {
            helper.fail("未写入时应为默认 MOD/MOD");
            return;
        }

        PlacementModeState.setServer(player, PlacementMode.VANILLA, PlacementMode.VANILLA);
        if (PlacementModeState.slab(player) != PlacementMode.VANILLA
                || PlacementModeState.stair(player) != PlacementMode.VANILLA) {
            helper.fail("写入 VANILLA 后应读回 VANILLA/VANILLA");
            return;
        }

        // 旧版“混合”档（序号 1）已删除：写入旧序号应并回默认（存档兼容），
        // 且不能让它顶掉序号稳定性——MOD_3/VANILLA 的序号必须保持 2/3。
        PlacementModeState.setServer(player, PlacementMode.MOD_2, PlacementMode.MOD);
        if (PlacementModeState.slab(player) != PlacementMode.MOD
                || PlacementModeState.stair(player) != PlacementMode.MOD) {
            helper.fail("旧 MOD_2（序号1）写入后读回应并回默认 MOD");
            return;
        }
        PlacementModeState.setServer(player, PlacementMode.MOD_3, PlacementMode.MOD);
        if (PlacementModeState.slab(player) != PlacementMode.MOD_3) {
            helper.fail("写入 MOD_3 后应读回 MOD_3（序号 2 保持稳定）");
            return;
        }

        // 切换键轮转语义：台阶 3 档（混合已删除）、楼梯 2 档，各自绕回；
        // 旧存档里残留的 MOD_2 轮转一次应落回默认。
        if (PlacementMode.MOD.next(false) != PlacementMode.MOD_3
                || PlacementMode.MOD_3.next(false) != PlacementMode.VANILLA
                || PlacementMode.VANILLA.next(false) != PlacementMode.MOD
                || PlacementMode.MOD_2.next(false) != PlacementMode.MOD) {
            helper.fail("台阶放置模式轮转顺序应为 MOD→MOD_3→VANILLA→MOD（旧 MOD_2 并回 MOD）");
            return;
        }
        if (PlacementMode.MOD.next(true) != PlacementMode.VANILLA
                || PlacementMode.VANILLA.next(true) != PlacementMode.MOD) {
            helper.fail("楼梯放置模式轮转顺序应为 MOD→VANILLA→MOD");
            return;
        }
        if (PlacementMode.byOrdinal(-1) != PlacementMode.MOD || PlacementMode.byOrdinal(99) != PlacementMode.MOD
                || PlacementMode.byOrdinal(1) != PlacementMode.MOD) {
            helper.fail("越界序号与已删除的旧序号 1（混合）都应回退 MOD");
            return;
        }
        helper.succeed();
    }

    // ===== 10. 宽松工具规则：手上必须有“工具”才给需要镐的半砖掉落 =====

    @GameTest
    public void looseToolHarvestRule(GameTestHelper helper) {
        if (MixedSlabBreakHandler.canHarvestLoose(ItemStack.EMPTY, Blocks.STONE_SLAB)) {
            helper.fail("空手不应收获需要镐的石半砖");
            return;
        }
        if (!MixedSlabBreakHandler.canHarvestLoose(ItemStack.EMPTY, Blocks.OAK_SLAB)) {
            helper.fail("空手应可收获不需要工具的木半砖");
            return;
        }
        if (!MixedSlabBreakHandler.canHarvestLoose(new ItemStack(Items.IRON_PICKAXE), Blocks.STONE_SLAB)) {
            helper.fail("带镐应可收获石半砖");
            return;
        }
        if (MixedSlabBreakHandler.canHarvestLoose(new ItemStack(Items.DIRT), Blocks.STONE_SLAB)) {
            helper.fail("泥土不是工具，不应收获石半砖");
            return;
        }
        helper.succeed();
    }

    // ===== 11. 默认档放置语义（README）：中央正方形内原版平放、外圈竖放（台阶） =====

    @GameTest
    public void defaultSlabCenterClickPlacesFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(REL);
        helper.setBlock(1, 1, 1, Blocks.COBBLESTONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // 点顶面中央（0.5, 1.0, 0.5）：落在中央正方形内 → 按原版平放
        BlockHitResult hit = new BlockHitResult(
                new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SLAB));
        ((BlockItem) Items.STONE_SLAB).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        BlockState placed = level.getBlockState(base.above());
        if (placed.getBlock() != Blocks.STONE_SLAB
                || placed.getValue(ModBlockStateProperties.MODE) != VerticalSlabMode.SLAB
                || placed.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            helper.fail("默认档点顶面中央应按原版平放下半（MODE=SLAB, TYPE=BOTTOM），实际 " + placed);
            return;
        }
        helper.succeed();
    }

    @GameTest
    public void defaultSlabOuterClickPlacesVertical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(REL);
        helper.setBlock(1, 1, 1, Blocks.COBBLESTONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // 点顶面外圈 NW（0.2, 1.0, 0.2）：正方形外 → 竖放，|x|=|z| → 北象限
        BlockHitResult hit = new BlockHitResult(
                new Vec3(base.getX() + 0.2, base.getY() + 1.0, base.getZ() + 0.2), Direction.UP, base, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SLAB));
        ((BlockItem) Items.STONE_SLAB).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        BlockState placed = level.getBlockState(base.above());
        if (placed.getBlock() != Blocks.STONE_SLAB
                || placed.getValue(ModBlockStateProperties.MODE) != VerticalSlabMode.VERTICAL_NS
                || placed.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            helper.fail("默认档点顶面外圈(NW)应竖放（MODE=VERTICAL_NS, TYPE=BOTTOM），实际 " + placed);
            return;
        }
        helper.succeed();
    }

    // ===== 12. 默认档放置语义（README）：中央正方形内原版平放、外圈竖放（楼梯） =====

    @GameTest
    public void defaultStairCenterClickPlacesFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(REL);
        helper.setBlock(1, 1, 1, Blocks.COBBLESTONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlockHitResult hit = new BlockHitResult(
                new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE_STAIRS));
        ((BlockItem) Items.COBBLESTONE_STAIRS)
                .useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        BlockState placed = level.getBlockState(base.above());
        if (placed.getBlock() != Blocks.COBBLESTONE_STAIRS
                || !placed.hasProperty(ModBlockStateProperties.VERTICAL)
                || placed.getValue(ModBlockStateProperties.VERTICAL)) {
            helper.fail("默认档楼梯点顶面中央应原版平放（VERTICAL=false），实际 " + placed);
            return;
        }
        helper.succeed();
    }

    @GameTest
    public void defaultStairOuterClickPlacesVertical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(REL);
        helper.setBlock(1, 1, 1, Blocks.COBBLESTONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlockHitResult hit = new BlockHitResult(
                new Vec3(base.getX() + 0.2, base.getY() + 1.0, base.getZ() + 0.2), Direction.UP, base, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE_STAIRS));
        ((BlockItem) Items.COBBLESTONE_STAIRS)
                .useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        BlockState placed = level.getBlockState(base.above());
        if (placed.getBlock() != Blocks.COBBLESTONE_STAIRS
                || !placed.hasProperty(ModBlockStateProperties.VERTICAL)
                || !placed.getValue(ModBlockStateProperties.VERTICAL)
                || placed.getValue(StairBlock.FACING) != Direction.NORTH) {
            helper.fail("默认档楼梯点顶面外圈(NW)应竖放且 FACING=NORTH，实际 " + placed);
            return;
        }
        helper.succeed();
    }

    // ===== 工具方法 =====

    private static BlockState slabState(VerticalSlabMode mode, SlabType type) {
        return Blocks.STONE_SLAB.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, type);
    }

    /** 放置指定状态后断言其碰撞/选中形状边界（精确到半格）。 */
    private static void assertShape(GameTestHelper helper, ServerLevel level, BlockPos absPos, BlockState state,
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ, String label) {
        helper.setBlock(1, 1, 1, state);
        BlockState placed = level.getBlockState(absPos);
        AABB box = placed.getShape(level, absPos).bounds();
        if (!near(box.minX, minX) || !near(box.minY, minY) || !near(box.minZ, minZ)
                || !near(box.maxX, maxX) || !near(box.maxY, maxY) || !near(box.maxZ, maxZ)) {
            helper.fail(label + " 形状边界应为 ("
                    + minX + "," + minY + "," + minZ + " → " + maxX + "," + maxY + "," + maxZ + ")，实际 " + box);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < EPS;
    }
}
