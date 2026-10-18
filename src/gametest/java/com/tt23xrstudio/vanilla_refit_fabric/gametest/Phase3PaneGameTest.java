package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.PaneCornerSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 阶段 3-B：玻璃板 / 铁栏杆“部件化”（12 片）的连接规则、形状与属性注入。
 *
 * <p>语义真值 = 参考项目（NeoForge 1.21.1）的 {@code PaneConnection} 规则 1~10，外加移植时补上的
 * 规则 3“方向直连”（参考项目文档写了但代码从未实现，靠已删除的规则 11 兜底掩盖）：
 * 12 个小面 = 4 个方向的上下半（下半复用原版 north/east/south/west）+ 4 个角上的水平面片；
 * 规则只读“邻居是什么方块”，是周围环境的纯函数、与放置顺序无关。
 */
public class Phase3PaneGameTest {

    /** 结构是 8×8×8 全空气，坐标一律用相对坐标（0~7），测试主体放在中间避免贴边。 */
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);
    private static final BlockPos PANE = new BlockPos(1, 2, 1);

    /** 面中心点（用于构造 BlockHitResult 的命中向量）。 */
    private static BlockHitResult hit(BlockPos absPos, Direction face) {
        double x = absPos.getX() + 0.5;
        double y = absPos.getY() + 0.5;
        double z = absPos.getZ() + 0.5;
        if (face.getAxis() == Direction.Axis.X) {
            x = face == Direction.EAST ? absPos.getX() + 1.0 : absPos.getX();
        } else if (face.getAxis() == Direction.Axis.Y) {
            y = face == Direction.UP ? absPos.getY() + 1.0 : absPos.getY();
        } else {
            z = face == Direction.SOUTH ? absPos.getZ() + 1.0 : absPos.getZ();
        }
        return new BlockHitResult(new net.minecraft.world.phys.Vec3(x, y, z), face, absPos, false);
    }

    /** 对着 {@code absHitPos} 的 {@code face} 面放一块玻璃板（新方块落在 absHitPos.relative(face)）。 */
    private static void placePane(GameTestHelper helper, ServerPlayer player, BlockPos absHitPos, Direction face) {
        BlockHitResult hit = hit(absHitPos, face);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_PANE));
        ((BlockItem) Items.GLASS_PANE).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static BlockState stateAt(GameTestHelper helper, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        return level.getBlockState(helper.absolutePos(rel));
    }

    private static void require(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            helper.fail(message);
        }
    }

    private static void requireTrue(GameTestHelper helper, BlockState state,
                                    net.minecraft.world.level.block.state.properties.Property<Boolean> property,
                                    boolean expected, String message) {
        if (!state.hasProperty(property)) {
            helper.fail(message + "（状态缺少属性 " + property.getName() + "，实际: " + state + "）");
            return;
        }
        if (state.getValue(property) != expected) {
            helper.fail(message + "，期望 " + property.getName() + "=" + expected
                    + "，实际 " + state.getValue(property) + "（" + state + "）");
        }
    }

    // ===== 无连接：12 片全关（原规则 11 兜底已按实测反馈移除） =====

    /**
     * 悬空的孤立玻璃板：地面撤掉后上下左右都是空气，什么都没连 → 12 个部件全部关闭，
     * 外观保持原版那根中心立柱（不再整块伸出来）。
     * 放置时先靠着地面（保证有落脚点），撤掉地面这一下会触发邻居更新重算。
     */
    @GameTest
    public void isolatedFloatingPaneKeepsVanillaPost(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(GROUND, Blocks.STONE.defaultBlockState());
        placePane(helper, player, helper.absolutePos(GROUND), Direction.UP);
        require(helper, stateAt(helper, PANE).is(Blocks.GLASS_PANE), "玻璃板应落在地面上方一格");

        helper.setBlock(GROUND, Blocks.AIR.defaultBlockState());
        BlockState pane = stateAt(helper, PANE);
        require(helper, pane.is(Blocks.GLASS_PANE), "撤掉地面后玻璃板应保留在原位，实际: " + pane);

        // 4 个方向的下半
        requireTrue(helper, pane, CrossCollisionBlock.NORTH, false, "悬空孤立玻璃板不该点亮北下");
        requireTrue(helper, pane, CrossCollisionBlock.EAST, false, "悬空孤立玻璃板不该点亮东下");
        requireTrue(helper, pane, CrossCollisionBlock.SOUTH, false, "悬空孤立玻璃板不该点亮南下");
        requireTrue(helper, pane, CrossCollisionBlock.WEST, false, "悬空孤立玻璃板不该点亮西下");
        // 4 个方向的上半
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_UP, false, "悬空孤立玻璃板不该点亮北上");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_EAST_UP, false, "悬空孤立玻璃板不该点亮东上");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_UP, false, "悬空孤立玻璃板不该点亮南上");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_WEST_UP, false, "悬空孤立玻璃板不该点亮西上");
        // 4 个角上的水平面片
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_EAST, false, "悬空孤立玻璃板不该点亮东北角面片");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_EAST, false, "悬空孤立玻璃板不该点亮东南角面片");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_WEST, false, "悬空孤立玻璃板不该点亮西南角面片");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_WEST, false, "悬空孤立玻璃板不该点亮西北角面片");

        helper.succeed();
    }

    // ===== 规则 1 + 规则 6：落地只连一面墙 → 竖直南北两片整条点亮 =====

    /**
     * 落地的玻璃板 + 正北一堵墙：只有“北下”一开始为真（不连续），
     * 规则 6 把它所在的竖直南北大面 4 个小面全部点亮；东西两面与 4 个角保持关闭。
     */
    @GameTest
    public void paneOnGroundAgainstWallLightsVerticalStrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(GROUND, Blocks.STONE.defaultBlockState());
        placePane(helper, player, helper.absolutePos(GROUND), Direction.UP);
        require(helper, stateAt(helper, PANE).is(Blocks.GLASS_PANE), "玻璃板应落在地面上方一格");

        helper.setBlock(new BlockPos(1, 2, 0), Blocks.STONE.defaultBlockState());
        BlockState pane = stateAt(helper, PANE);

        requireTrue(helper, pane, CrossCollisionBlock.NORTH, true, "靠墙的玻璃板应点亮北下");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_UP, true, "规则 6 应补上北上");
        requireTrue(helper, pane, CrossCollisionBlock.SOUTH, true, "规则 6 应补上南下");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_UP, true, "规则 6 应补上南上");
        requireTrue(helper, pane, CrossCollisionBlock.EAST, false, "东西两面不应被点亮");
        requireTrue(helper, pane, CrossCollisionBlock.WEST, false, "东西两面不应被点亮");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_EAST, false, "角面片不应被点亮");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_EAST, false, "角面片不应被点亮");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_WEST, false, "角面片不应被点亮");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_WEST, false, "角面片不应被点亮");

        // 形状/碰撞箱跟着部件走：南北贯通（z 0~16 像素）、厚度 2 像素、满高
        // 碰撞箱必须走三参重载：两参版返回按状态缓存的“位置无关”形状，测不到 mixin 的按位置修正
        BlockPos absPane = helper.absolutePos(PANE);
        VoxelShape shape = pane.getShape(level, absPane);
        VoxelShape collision = pane.getCollisionShape(level, absPane, CollisionContext.empty());
        require(helper, shape.min(Direction.Axis.Z) < 0.01, "面片形状应抵达北面 z=0，minZ=" + shape.min(Direction.Axis.Z));
        require(helper, shape.max(Direction.Axis.Z) > 0.99, "面片形状应抵达南面 z=1，maxZ=" + shape.max(Direction.Axis.Z));
        require(helper, shape.min(Direction.Axis.Y) < 0.01, "面片形状应落地 minY=0，minY=" + shape.min(Direction.Axis.Y));
        require(helper, shape.max(Direction.Axis.Y) > 0.99, "面片形状应满高 maxY=1，maxY=" + shape.max(Direction.Axis.Y));
        require(helper, collision.max(Direction.Axis.Z) > 0.99, "碰撞箱应与面片形状一致（南北贯通）");

        helper.succeed();
    }

    // ===== 规则 1（方块夹角）：两面墙 + 斜角上的方块 =====

    /**
     * 落地玻璃板，北面墙 + 东面墙 + 东北斜角一块石头：三个夹角类部件各就各位，
     * 规则 4（置假）此时不命中（上下半没成对），所以只有“北下 / 东下 / 东北角”三个为真。
     */
    @GameTest
    public void paneBetweenTwoWallsLightsDiagonalCorner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // 先把周围环境摆好，再一次成型（规则是纯函数，顺序无关）
        helper.setBlock(GROUND, Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 0), Blocks.STONE.defaultBlockState());

        placePane(helper, player, helper.absolutePos(GROUND), Direction.UP);
        BlockState pane = stateAt(helper, PANE);
        require(helper, pane.is(Blocks.GLASS_PANE), "玻璃板应落在地面上方一格，实际: " + pane);

        requireTrue(helper, pane, CrossCollisionBlock.NORTH, true, "北面墙应点亮北下");
        requireTrue(helper, pane, CrossCollisionBlock.EAST, true, "东面墙应点亮东下");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_EAST, true, "东北斜角方块应点亮东北角面片");
        requireTrue(helper, pane, CrossCollisionBlock.SOUTH, false, "南面无邻居不应点亮");
        requireTrue(helper, pane, CrossCollisionBlock.WEST, false, "西面无邻居不应点亮");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_UP, false, "上方无邻居不应点亮上半");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_EAST_UP, false, "上方无邻居不应点亮上半");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_EAST, false, "东南角面片不应点亮");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_SOUTH_WEST, false, "西南角面片不应点亮");
        requireTrue(helper, pane, ModBlockStateProperties.PANE_NORTH_WEST, false, "西北角面片不应点亮");

        // 形状：东北角的水平面片（y 7~9 像素）+ 北下/东下两片竖直面片
        BlockPos absPane = helper.absolutePos(PANE);
        VoxelShape shape = pane.getShape(level, absPane);
        require(helper, shape.max(Direction.Axis.X) > 0.99,
                "东下竖直面片应伸到 x=1，maxX=" + shape.max(Direction.Axis.X));
        require(helper, shape.min(Direction.Axis.Y) < 0.01,
                "竖直面片应落地 minY=0，minY=" + shape.min(Direction.Axis.Y));
        require(helper, shape.max(Direction.Axis.Y) > 0.55 && shape.max(Direction.Axis.Y) < 0.60,
                "上半未点亮时最高只到角面片顶 9/16，maxY=" + shape.max(Direction.Axis.Y));

        helper.succeed();
    }

    // ===== 斜角邻居的间接通知（PaneIndirectNeighbourMixin）=====

    /**
     * 东北斜角上**后放**的那块玻璃板，必须把原点玻璃板的东北角面片点亮：
     * 斜角不在原版 6 个正交邻居里，靠 updateIndirectNeighbourShapes 补的那 12 格通知。
     * 顺序刻意反过来（先放原点 → 再放北 → 再放东 → 最后放斜角），中间几步原点都算不出东北角。
     */
    @GameTest
    public void diagonalPaneAddedLaterLightsOriginCorner(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(GROUND, Blocks.STONE.defaultBlockState());
        BlockPos absGround = helper.absolutePos(GROUND);

        placePane(helper, player, absGround, Direction.UP);          // 原点 (1,2,1)
        BlockPos absPane = helper.absolutePos(PANE);
        placePane(helper, player, absPane, Direction.NORTH);         // 北 (1,2,0)
        placePane(helper, player, absPane, Direction.EAST);          // 东 (2,2,1)
        placePane(helper, player, helper.absolutePos(new BlockPos(1, 2, 0)), Direction.EAST);   // 东北 (2,2,0)

        BlockState origin = stateAt(helper, PANE);
        require(helper, origin.is(Blocks.GLASS_PANE), "原点玻璃板应还在，实际: " + origin);
        require(helper, stateAt(helper, new BlockPos(2, 2, 0)).is(Blocks.GLASS_PANE),
                "东北斜角玻璃板应放置成功");
        requireTrue(helper, origin, ModBlockStateProperties.PANE_NORTH_EAST, true,
                "斜角玻璃板放下后应点亮原点的东北角面片（间接邻居通知）");
        requireTrue(helper, origin, CrossCollisionBlock.NORTH, true, "落地配对应点亮北下");
        requireTrue(helper, origin, CrossCollisionBlock.EAST, true, "落地配对应点亮东下");
        requireTrue(helper, origin, ModBlockStateProperties.PANE_SOUTH_WEST, false,
                "西南方向没有邻居，不应被点亮");

        helper.succeed();
    }

    // ===== 悬空两块板互连（规则 3：方向直连）=====

    /**
     * 两块悬空相邻的玻璃板（上下左右再无任何方块）：必须像原版一样互相连接。
     * 规则 3 让“有板的那一侧下半”直接置真，规则 6 再把整条竖直大面补满 4 个小面
     * （与落地场景“板贴墙 → 整条竖带点亮”的已验收外观一致），东西面与 4 个角保持关闭。
     * 这正是“必须要接到完整方块上才可以自动连接”那个 bug 的回归测试：
     * 上一版里配对规则一条都命中不了 → 12 片全假 → 两根互不连接的棍。
     */
    @GameTest
    public void floatingPanePairConnectsLikeVanilla(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // 临时脚手架：先借它落脚放下板 A，撤掉后 A 完全悬空（上下左右只剩另一块板）
        helper.setBlock(GROUND, Blocks.STONE.defaultBlockState());
        placePane(helper, player, helper.absolutePos(GROUND), Direction.UP);
        require(helper, stateAt(helper, PANE).is(Blocks.GLASS_PANE), "玻璃板 A 应落在脚手架上方");
        helper.setBlock(GROUND, Blocks.AIR.defaultBlockState());     // 拆脚手架 → A 悬空

        // 板 B 贴着 A 的南面真放下去（走 getStateForPlacement 路径）
        placePane(helper, player, helper.absolutePos(PANE), Direction.SOUTH);
        BlockPos relB = new BlockPos(1, 2, 2);
        require(helper, stateAt(helper, relB).is(Blocks.GLASS_PANE),
                "玻璃板 B 应落在 A 的南面，实际: " + stateAt(helper, relB));

        BlockState a = stateAt(helper, PANE);
        // A：南面连着板 → 南下/南上都亮；规则 6 把同一大面（南北竖直面）补满，
        // 于是朝空气的北面也跟着亮——与落地板贴墙时的竖带行为一致
        requireTrue(helper, a, CrossCollisionBlock.SOUTH, true, "悬空板 A 应点亮南下");
        requireTrue(helper, a, ModBlockStateProperties.PANE_SOUTH_UP, true, "悬空板 A 应点亮南上");
        requireTrue(helper, a, CrossCollisionBlock.NORTH, true, "规则 6 应把北面也补上（同一大面）");
        requireTrue(helper, a, ModBlockStateProperties.PANE_NORTH_UP, true, "规则 6 应把北上也补上（同一大面）");
        requireTrue(helper, a, CrossCollisionBlock.EAST, false, "东西两面无邻居不应点亮");
        requireTrue(helper, a, CrossCollisionBlock.WEST, false, "东西两面无邻居不应点亮");
        requireTrue(helper, a, ModBlockStateProperties.PANE_NORTH_EAST, false, "角面片不应点亮");
        requireTrue(helper, a, ModBlockStateProperties.PANE_SOUTH_EAST, false, "角面片不应点亮");
        requireTrue(helper, a, ModBlockStateProperties.PANE_SOUTH_WEST, false, "角面片不应点亮");
        requireTrue(helper, a, ModBlockStateProperties.PANE_NORTH_WEST, false, "角面片不应点亮");

        BlockState b = stateAt(helper, relB);
        // B：与 A 完全对称（北面连着板）
        requireTrue(helper, b, CrossCollisionBlock.NORTH, true, "悬空板 B 应点亮北下");
        requireTrue(helper, b, ModBlockStateProperties.PANE_NORTH_UP, true, "悬空板 B 应点亮北上");
        requireTrue(helper, b, CrossCollisionBlock.SOUTH, true, "规则 6 应把南面也补上（同一大面）");
        requireTrue(helper, b, ModBlockStateProperties.PANE_SOUTH_UP, true, "规则 6 应把南上也补上（同一大面）");
        requireTrue(helper, b, CrossCollisionBlock.EAST, false, "东西两面无邻居不应点亮");
        requireTrue(helper, b, CrossCollisionBlock.WEST, false, "东西两面无邻居不应点亮");

        // 满高连接：面片形状应从地板一直伸到顶
        ServerLevel level = helper.getLevel();
        VoxelShape shape = b.getShape(level, helper.absolutePos(relB));
        require(helper, shape.min(Direction.Axis.Y) < 0.01,
                "悬空连接面片应抵达 minY=0，minY=" + shape.min(Direction.Axis.Y));
        require(helper, shape.max(Direction.Axis.Y) > 0.99,
                "悬空连接面片应满高 maxY=1，maxY=" + shape.max(Direction.Axis.Y));

        helper.succeed();
    }

    // ===== 属性注入范围（内存相关：只给原版命名空间注入，状态数 ×256 只花在原版板类上）=====

    /**
     * 原版的玻璃板 / 染色玻璃板 / 铁栏杆必须带上 8 个附加属性（否则部件化全部失效），
     * 默认状态 12 个部件全为 false（老存档外观不变）；
     * 而“不是原版在 Blocks 静态初始化里造出来的方块”不注入 —— 这里用调用栈判定本身验证。
     */
    @GameTest
    public void vanillaPanesGetEightExtraProperties(GameTestHelper helper) {
        BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
        BlockState ironBars = Blocks.IRON_BARS.defaultBlockState();
        BlockState stained = Blocks.STAINED_GLASS_PANE.white().defaultBlockState();

        require(helper, glass.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST),
                "原版玻璃板应注入 8 个部件属性");
        require(helper, ironBars.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST),
                "原版铁栏杆应注入 8 个部件属性");
        require(helper, stained.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST),
                "原版染色玻璃板应注入 8 个部件属性");

        require(helper, PaneCornerSupport.isSupportedPaneBlock(Blocks.GLASS_PANE), "玻璃板应算部件化目标");
        require(helper, PaneCornerSupport.isSupportedPaneBlock(Blocks.IRON_BARS), "铁栏杆应算部件化目标");
        require(helper, !PaneCornerSupport.isSupportedPaneBlock(Blocks.OAK_FENCE), "栅栏不是板类，不应算部件化目标");
        require(helper, PaneCornerSupport.isSupportedPane(glass), "原版玻璃板状态应算支持的玻璃板");

        // 默认状态保持原版：12 个部件全为 false
        requireTrue(helper, glass, CrossCollisionBlock.NORTH, false, "默认玻璃板北下应为 false");
        requireTrue(helper, glass, ModBlockStateProperties.PANE_NORTH_UP, false, "默认玻璃板北上应为 false");
        requireTrue(helper, glass, ModBlockStateProperties.PANE_NORTH_EAST, false, "默认玻璃板东北角应为 false");

        // 注入判定走“调用栈里有 Blocks 类”这条：本测试的调用栈里没有 Blocks，必须判否
        require(helper, !PaneCornerSupport.shouldInjectPaneProperties(),
                "非 Blocks 构造路径不应注入部件属性");

        helper.succeed();
    }

    // ===== 放置顺序无关性（规则是周围环境的纯函数）=====

    /**
     * 同一个场景、两种摆放顺序，得到同一份状态：先放斜角再放原点 vs 先放原点再放斜角。
     */
    @GameTest
    public void paneRulesAreOrderIndependent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // 场景一：先摆周围，后放原点
        helper.setBlock(GROUND, Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 0), Blocks.STONE.defaultBlockState());
        placePane(helper, player, helper.absolutePos(GROUND), Direction.UP);
        BlockState first = stateAt(helper, PANE);

        // 场景二：拆掉原点重放一次（同一批邻居，重算路径不同：getStateForPlacement）
        helper.setBlock(PANE, Blocks.AIR.defaultBlockState());
        placePane(helper, player, helper.absolutePos(GROUND), Direction.UP);
        BlockState second = stateAt(helper, PANE);

        require(helper, first == second || first.equals(second),
                "两次放置应得到同一份状态：\n  第一次=" + first + "\n  第二次=" + second);

        helper.succeed();
    }

    // ===== 板类贴半砖位移（自研增强：参考项目没有，不落属性、按邻居现算）=====

    /**
     * 玻璃板 / 铁栏杆 / 铜栏杆放在下半砖上：形状与碰撞箱整体下移半格贴齐砖面
     * （本格坐标 -0.5~+0.5，原版是 0~1）；旁边没有台阶的板保持原样。
     * 直接 setBlock 即可：位移是查询时按邻居现算的纯函数，不依赖放置路径。
     */
    @GameTest
    public void paneOnBottomSlabShiftsDownHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block[] panes = {
                Blocks.GLASS_PANE,
                Blocks.IRON_BARS,
                BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", "copper_bars"))
        };
        for (int i = 0; i < panes.length; i++) {
            require(helper, panes[i] != null, "铜栏杆应能从注册表取到");
            BlockPos rel = new BlockPos(1 + i, 2, 1);
            helper.setBlock(new BlockPos(1 + i, 1, 1), Blocks.STONE_SLAB.defaultBlockState());
            helper.setBlock(rel, panes[i].defaultBlockState());

            BlockPos abs = helper.absolutePos(rel);
            BlockState pane = stateAt(helper, rel);
            VoxelShape shape = pane.getShape(level, abs);
            require(helper, shape.min(Direction.Axis.Y) < -0.49,
                    panes[i] + " 在下半砖上应下移半格，minY=" + shape.min(Direction.Axis.Y));
            require(helper, shape.max(Direction.Axis.Y) < 0.51,
                    panes[i] + " 下移后顶面应在半格处，maxY=" + shape.max(Direction.Axis.Y));
            VoxelShape collision = pane.getCollisionShape(level, abs, CollisionContext.empty());
            require(helper, collision.min(Direction.Axis.Y) < -0.49,
                    panes[i] + " 碰撞箱应同步下移，minY=" + collision.min(Direction.Axis.Y));
        }

        // 对照：旁边没有台阶的板保持原版 0~1
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.GLASS_PANE.defaultBlockState());
        BlockPos control = helper.absolutePos(new BlockPos(4, 2, 1));
        VoxelShape flat = stateAt(helper, new BlockPos(4, 2, 1)).getShape(level, control);
        require(helper, flat.min(Direction.Axis.Y) > -0.01,
                "无台阶时不应位移，minY=" + flat.min(Direction.Axis.Y));
        require(helper, flat.max(Direction.Axis.Y) > 0.99,
                "无台阶时形状应保持满高，maxY=" + flat.max(Direction.Axis.Y));

        helper.succeed();
    }

    /**
     * 玻璃板在上半砖下方：形状整体上移半格贴齐砖底（本格坐标 +0.5~+1.5）；
     * 上方不是上半砖时不位移。
     */
    @GameTest
    public void paneUnderTopSlabShiftsUpHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 3, 1),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(PANE, Blocks.GLASS_PANE.defaultBlockState());

        BlockPos abs = helper.absolutePos(PANE);
        VoxelShape shape = stateAt(helper, PANE).getShape(level, abs);
        require(helper, shape.min(Direction.Axis.Y) > 0.49,
                "上半砖下的玻璃板应上移半格，minY=" + shape.min(Direction.Axis.Y));
        require(helper, shape.max(Direction.Axis.Y) > 1.49,
                "上移后顶面应贴砖底 1.5，maxY=" + shape.max(Direction.Axis.Y));
        VoxelShape collision = stateAt(helper, PANE).getCollisionShape(level, abs, CollisionContext.empty());
        require(helper, collision.max(Direction.Axis.Y) > 1.49,
                "碰撞箱应同步上移，maxY=" + collision.max(Direction.Axis.Y));

        // 对照：上方没有上半砖时不位移
        helper.setBlock(new BlockPos(3, 3, 1),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.GLASS_PANE.defaultBlockState());
        BlockPos control = helper.absolutePos(new BlockPos(3, 2, 1));
        VoxelShape flat = stateAt(helper, new BlockPos(3, 2, 1)).getShape(level, control);
        require(helper, flat.min(Direction.Axis.Y) > -0.01,
                "旁边没有上半砖的板不应位移，minY=" + flat.min(Direction.Axis.Y));

        helper.succeed();
    }
}
