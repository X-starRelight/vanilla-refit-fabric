package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabOffset;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 阶段 3：贴面放置（火把/灯笼/栅栏/墙在台阶上/下的半格位移）+ 跨台阶连接 + 舒适框。
 *
 * <p>语义真值 = 参考项目（NeoForge 1.21.1）的行为：
 * 火把类用 {@code btsdhz_on_slab} 布尔（只有"贴下台阶下移"一种）；
 * 墙/栅栏/灯笼用 {@code btsdhz_slab_offset} 三值枚举（none/lowered/raised）。
 */
public class Phase3GameTest {

    private static final BlockPos REL = new BlockPos(1, 1, 1);

    /** 用玩家上下文对着 absHitPos 的 face 面点击放置 item（走完整 BlockItem.place 链路）。 */
    private static void clickPlace(GameTestHelper helper, Player player,
                                   BlockPos absHitPos, Direction face, Vec3 absHitVec, Item item) {
        BlockHitResult hit = new BlockHitResult(absHitVec, face, absHitPos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        ((BlockItem) item).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static Vec3 topCenter(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
    }

    private static Vec3 bottomCenter(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 0.0, p.getZ() + 0.5);
    }

    // ===== 火把 =====

    /** 下半台阶上放火把：ON_SLAB=true，形状下移半格（minY ≈ -0.5）。 */
    @GameTest
    public void torchLowersOnBottomSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.TORCH);

        BlockPos torchPos = base.above();
        BlockState torch = level.getBlockState(torchPos);
        if (torch.getBlock() != Blocks.TORCH) {
            helper.fail("下半台阶上应能放置火把，实际: " + torch);
        }
        if (!torch.hasProperty(ModBlockStateProperties.ON_SLAB)
                || !torch.getValue(ModBlockStateProperties.ON_SLAB)) {
            helper.fail("台阶上的火把应带 ON_SLAB=true，实际: " + torch);
        }
        VoxelShape shape = torch.getShape(level, torchPos);
        if (shape.min(Direction.Axis.Y) > -0.4) {
            helper.fail("ON_SLAB 火把形状应下移半格，minY=" + shape.min(Direction.Axis.Y));
        }
        helper.succeed();
    }

    /** 普通完整方块上放火把：保持原版（ON_SLAB=false，不下移）。 */
    @GameTest
    public void torchStaysVanillaOnFullBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.COBBLESTONE);
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.TORCH);

        BlockState torch = level.getBlockState(base.above());
        if (torch.getBlock() != Blocks.TORCH) {
            helper.fail("完整方块上应能放置火把");
        }
        if (!torch.hasProperty(ModBlockStateProperties.ON_SLAB)
                || torch.getValue(ModBlockStateProperties.ON_SLAB)) {
            helper.fail("完整方块上的火把应为 ON_SLAB=false，实际: " + torch);
        }
        helper.succeed();
    }

    /** 下方支撑从下台阶换成完整方块：邻居更新后火把重新同步为 ON_SLAB=false（不悬空下沉）。 */
    @GameTest
    public void torchFlagResyncsWhenSupportChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.TORCH);

        helper.setBlock(REL, Blocks.COBBLESTONE);
        BlockState torch = level.getBlockState(base.above());
        if (torch.getBlock() != Blocks.TORCH) {
            helper.fail("支撑变化后火把应保留");
        }
        if (!torch.hasProperty(ModBlockStateProperties.ON_SLAB)
                || torch.getValue(ModBlockStateProperties.ON_SLAB)) {
            helper.fail("支撑换成完整方块后火把应重新同步为 ON_SLAB=false，实际: " + torch);
        }
        helper.succeed();
    }

    /** 下台阶上的栅栏已被整体下移：放在它上面的火把也要下移（ON_SLAB=true）。 */
    @GameTest
    public void torchLowersOnLoweredFence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.OAK_FENCE);
        BlockState fence = level.getBlockState(base.above());
        if (fence.getBlock() != Blocks.OAK_FENCE
                || fence.getValue(ModBlockStateProperties.SLAB_OFFSET) != SlabOffset.LOWERED) {
            helper.fail("前置失败：台阶上的栅栏应为 LOWERED，实际: " + fence);
        }

        BlockPos fencePos = base.above();
        clickPlace(helper, player, fencePos, Direction.UP, topCenter(fencePos), Items.TORCH);
        BlockPos torchPos = base.above(2);
        BlockState torch = level.getBlockState(torchPos);
        if (torch.getBlock() != Blocks.TORCH
                || !torch.hasProperty(ModBlockStateProperties.ON_SLAB)
                || !torch.getValue(ModBlockStateProperties.ON_SLAB)) {
            helper.fail("下移栅栏上的火把应为 ON_SLAB=true，实际: " + torch);
        }
        helper.succeed();
    }

    // ===== 栅栏 =====

    /** 下半台阶上放栅栏：SLAB_OFFSET=LOWERED，形状下移半格。 */
    @GameTest
    public void fenceLowersOnBottomSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.OAK_FENCE);

        BlockPos fencePos = base.above();
        BlockState fence = level.getBlockState(fencePos);
        if (fence.getBlock() != Blocks.OAK_FENCE
                || !fence.hasProperty(ModBlockStateProperties.SLAB_OFFSET)
                || fence.getValue(ModBlockStateProperties.SLAB_OFFSET) != SlabOffset.LOWERED) {
            helper.fail("台阶上的栅栏应为 SLAB_OFFSET=LOWERED，实际: " + fence);
        }
        VoxelShape shape = fence.getShape(level, fencePos);
        if (shape.min(Direction.Axis.Y) > -0.4) {
            helper.fail("LOWERED 栅栏形状应下移半格，minY=" + shape.min(Direction.Axis.Y));
        }
        helper.succeed();
    }

    /** 上半台阶下方放栅栏（先放栅栏再放上半台阶）：邻居更新后 SLAB_OFFSET=RAISED，形状上移。 */
    @GameTest
    public void fenceRaisesUnderTopSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.COBBLESTONE);
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.OAK_FENCE);

        BlockPos fencePos = base.above();
        // helper.setBlock 要相对坐标（绝对坐标会被二次偏移）：栅栏在 REL 上方一格，台阶再上一格
        helper.setBlock(REL.above(2),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));

        BlockState fence = level.getBlockState(fencePos);
        if (fence.getBlock() != Blocks.OAK_FENCE
                || fence.getValue(ModBlockStateProperties.SLAB_OFFSET) != SlabOffset.RAISED) {
            helper.fail("上半台阶下的栅栏应为 SLAB_OFFSET=RAISED，实际: " + fence);
        }
        VoxelShape shape = fence.getShape(level, fencePos);
        if (shape.max(Direction.Axis.Y) < 1.2) {
            helper.fail("RAISED 栅栏形状应上移半格，maxY=" + shape.max(Direction.Axis.Y));
        }
        helper.succeed();
    }

    // ===== 墙 =====

    /** 下半台阶上放墙：SLAB_OFFSET=LOWERED，碰撞形状下移半格。 */
    @GameTest
    public void wallLowersOnBottomSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.COBBLESTONE_WALL);

        BlockPos wallPos = base.above();
        BlockState wall = level.getBlockState(wallPos);
        if (wall.getBlock() != Blocks.COBBLESTONE_WALL
                || !wall.hasProperty(ModBlockStateProperties.SLAB_OFFSET)
                || wall.getValue(ModBlockStateProperties.SLAB_OFFSET) != SlabOffset.LOWERED) {
            helper.fail("台阶上的墙应为 SLAB_OFFSET=LOWERED，实际: " + wall);
        }
        VoxelShape collision = wall.getCollisionShape(level, wallPos);
        if (collision.min(Direction.Axis.Y) > -0.4) {
            helper.fail("LOWERED 墙碰撞形状应下移半格，minY=" + collision.min(Direction.Axis.Y));
        }
        helper.succeed();
    }

    /**
     * 跨台阶连接：下台阶上的下移墙，与台阶四邻的普通墙互相补墙臂
     * （斜向关系，原版更新通知不到，需要本模组主动补算）。
     */
    @GameTest
    public void wallConnectsAcrossSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);

        // 台阶东侧（同一层 y=1）放普通墙（helper.setBlock 用相对坐标）
        BlockPos eastPos = base.relative(Direction.EAST);
        helper.setBlock(REL.relative(Direction.EAST), Blocks.COBBLESTONE_WALL);
        // 台阶上放墙 → LOWERED
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.COBBLESTONE_WALL);

        BlockPos lowWallPos = base.above();
        BlockState lowWall = level.getBlockState(lowWallPos);
        if (lowWall.getBlock() != Blocks.COBBLESTONE_WALL) {
            helper.fail("台阶上应能放置墙");
        }
        if (lowWall.getValue(WallBlock.EAST) == WallSide.NONE) {
            helper.fail("下移墙应跨台阶连接东侧普通墙（EAST 臂应存在），实际: " + lowWall);
        }
        BlockState eastWall = level.getBlockState(eastPos);
        if (eastWall.getValue(WallBlock.WEST) == WallSide.NONE) {
            helper.fail("普通墙应跨台阶连接台阶上的下移墙（WEST 臂应存在），实际: " + eastWall);
        }
        helper.succeed();
    }

    // ===== 灯笼 =====

    /** 下半台阶上放灯笼（非悬挂）：SLAB_OFFSET=LOWERED，形状下移半格。 */
    @GameTest
    public void lanternLowersOnBottomSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.LANTERN);

        BlockPos lanternPos = base.above();
        BlockState lantern = level.getBlockState(lanternPos);
        if (lantern.getBlock() != Blocks.LANTERN) {
            helper.fail("下半台阶上应能放置灯笼，实际: " + lantern);
        }
        if (lantern.getValue(LanternBlock.HANGING)) {
            helper.fail("台阶上的灯笼应为非悬挂（HANGING=false），实际: " + lantern);
        }
        if (!lantern.hasProperty(ModBlockStateProperties.SLAB_OFFSET)
                || lantern.getValue(ModBlockStateProperties.SLAB_OFFSET) != SlabOffset.LOWERED) {
            helper.fail("台阶上的灯笼应为 SLAB_OFFSET=LOWERED，实际: " + lantern);
        }
        VoxelShape shape = lantern.getShape(level, lanternPos);
        if (shape.min(Direction.Axis.Y) > -0.4) {
            helper.fail("LOWERED 灯笼形状应下移半格，minY=" + shape.min(Direction.Axis.Y));
        }
        helper.succeed();
    }

    /** 上半台阶下挂灯笼：原版不允许"直接点击上台阶底面悬挂"（canSupportRigidBlock 不认半砖），
     * 参考实现的 RAISED 流程是"先在完整方块下挂灯笼、再把头顶方块换成上半台阶"——
     * 邻居更新后灯笼存活（canSurvive 放行）并重新同步为 SLAB_OFFSET=RAISED，形状上移半格。 */
    @GameTest
    public void lanternRaisesUnderTopSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);

        // 头顶放完整方块（helper.setBlock 用相对坐标 = REL.above(2)），从其底面挂出原版悬挂灯笼
        BlockPos fullPos = base.above(2);
        helper.setBlock(REL.above(2), Blocks.COBBLESTONE);
        clickPlace(helper, player, fullPos, Direction.DOWN, bottomCenter(fullPos), Items.LANTERN);

        BlockPos lanternPos = base.above();
        BlockState lantern = level.getBlockState(lanternPos);
        if (lantern.getBlock() != Blocks.LANTERN || !lantern.getValue(LanternBlock.HANGING)) {
            helper.fail("前置失败：应挂出原版悬挂灯笼，实际: " + lantern);
        }

        // 把头顶的完整方块换成上半台阶：灯笼应存活并同步为 RAISED
        helper.setBlock(REL.above(2),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));

        lantern = level.getBlockState(lanternPos);
        if (lantern.getBlock() != Blocks.LANTERN) {
            helper.fail("上半台阶下悬挂灯笼应存活（canSurvive 放行），实际: " + lantern);
        }
        if (!lantern.hasProperty(ModBlockStateProperties.SLAB_OFFSET)
                || lantern.getValue(ModBlockStateProperties.SLAB_OFFSET) != SlabOffset.RAISED) {
            helper.fail("上台阶下的灯笼应为 SLAB_OFFSET=RAISED，实际: " + lantern);
        }
        VoxelShape shape = lantern.getShape(level, lanternPos);
        if (shape.max(Direction.Axis.Y) < 1.0) {
            helper.fail("RAISED 灯笼形状应上移半格，maxY=" + shape.max(Direction.Axis.Y));
        }
        helper.succeed();
    }

    // ===== 舒适框 =====

    /** 下半台阶上方是下移栅栏时：台阶的拾取形状并入栅栏伸进来的那部分（舒适框）。 */
    @GameTest
    public void slabPickShapeMergedWithLoweredFence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(REL, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos base = helper.absolutePos(REL);
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        clickPlace(helper, player, base, Direction.UP, topCenter(base), Items.OAK_FENCE);

        BlockState slab = level.getBlockState(base);
        VoxelShape shape = slab.getShape(level, base);
        if (shape.max(Direction.Axis.Y) < 1.0) {
            helper.fail("上方有下移栅栏时台阶拾取形状应并入舒适框（maxY≥1.0），maxY="
                    + shape.max(Direction.Axis.Y));
        }
        helper.succeed();
    }

    /** 辅助：跨台阶墙臂判定用到的墙上属性（26.2 中 WallBlock.NORTH 等即四向属性）。 */
    @SuppressWarnings("unused")
    private static WallSide wallSide(BlockState state, Direction dir) {
        return state.getValue(switch (dir) {
            case NORTH -> WallBlock.NORTH;
            case EAST -> WallBlock.EAST;
            case SOUTH -> WallBlock.SOUTH;
            case WEST -> WallBlock.WEST;
            default -> throw new IllegalArgumentException("不是水平方向: " + dir);
        });
    }

    /** 栅栏四向属性（后续测试扩展用，保留以免 IDE 报未用 import）。 */
    @SuppressWarnings("unused")
    private static boolean fenceConnected(BlockState state, Direction dir) {
        return state.getValue(switch (dir) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case WEST -> CrossCollisionBlock.WEST;
            default -> throw new IllegalArgumentException("不是水平方向: " + dir);
        });
    }
}
