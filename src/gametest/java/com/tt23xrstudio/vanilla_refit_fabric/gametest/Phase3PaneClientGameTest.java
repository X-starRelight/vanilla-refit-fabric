package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 阶段 3 的<b>有头</b>验收：真开客户端、真建世界、真渲染，再把画面截下来。
 *
 * <p>无头 game test 只能证明「服务端算出来的部件掩码对不对」，证明不了
 * 「屏幕上那 12 片到底画出来没有、贴图换对没有、转 90° 的东西面有没有转错」——
 * 所以每个阶段做到一定程度都要过一遍这个测试。
 *
 * <p><b>线程纪律</b>：客户端测试线程不能直接碰服务端对象（{@code getServerPlayer}
 * 之类只允许在服务端线程调），所以所有世界写入与传送都包在
 * {@link TestServerConnection#computeOnServer} / {@link TestServerConnection#runOnServer} 里。
 *
 * <p>场景（自南向北看）：
 * <ol>
 *   <li>孤立玻璃板：12 个部件全关 → 画原版那根棍（上下面断面、四面大面）；</li>
 *   <li>南北竖直面：三块玻璃板夹在两根石柱之间 → 每块都出上下两半的南北面片；</li>
 *   <li>三墙夹角：北 + 西两面 2 格高石墙 → 出西北角上面片 + 北/西上下半；</li>
 *   <li>铁栏杆同款三组，验证另一张贴图规则（大面与断面同一张）；</li>
 *   <li>悬空两块玻璃板互连：上下左右不靠任何完整方块，两块板必须像原版一样连成满高竖带
 *       （修复“必须要接到完整方块上才可以自动连接”的 bug）；</li>
 *   <li>板类贴半砖位移（自研增强）：下半砖上的玻璃板/铁栏杆/铜栏杆下移半格贴砖面，
 *       上半砖下的玻璃板上移半格贴砖底；</li>
 *   <li>镜像/旋转变换（对称之杖底层）：竖半砖贴北原态 / Z 轴镜像（应贴南）/ 顺时针转 90°
 *       （应贴东），竖楼梯 L 中心西北原态 / X 轴镜像（应到东北）——形状必须跟着换。</li>
 * </ol>
 */
public final class Phase3PaneClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            TestServerConnection connection = world.getConnection();
            TestServerContext server = world.getServer();

            // —— 场地搭建：全部在服务端线程里做，返回场地原点 ——
            int[] origin = server.computeOnServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                ServerLevel level = player.level();
                BlockPos spawn = player.blockPosition();
                int ox = spawn.getX() - 4;
                int y = spawn.getY();
                int oz = spawn.getZ() - 14;
                clearAndFloor(level, ox, y, oz, 30, 26);
                buildScene(level, ox, y, oz);
                return new int[]{ox, y, oz};
            });
            int ox = origin[0];
            int y = origin[1];
            int oz = origin[2];

            connection.waitForClientboundPackets();
            connection.waitForChunksRender();

            // 藏掉 HUD 再截图，方便肉眼与像素比对
            context.getInput().pressKey(options -> options.keyToggleGui);   // F1：截图不带 HUD
            context.waitTicks(5);

            // 全景：站在场地南边向北看
            teleport(context, connection, server, ox + 14, y + 1, oz + 18);
            context.getInput().lookAt(new BlockPos(ox + 8, y + 1, oz + 2));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_panes_wide").withSize(1280, 720));

            // 玻璃板三墙夹角近景
            teleport(context, connection, server, ox + 10, y + 1, oz + 6);
            context.getInput().lookAt(new BlockPos(ox + 10, y + 1, oz + 2));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_pane_corner").withSize(640, 640));

            // 玻璃面贴脸特写：验贴图细节（玻璃的白边框与斜纹必须可见，纯色即换图失败）
            teleport(context, connection, server, ox + 10, y + 1, oz + 4);
            context.getInput().lookAt(new BlockPos(ox + 10, y + 1, oz + 2));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_pane_texture_closeup").withSize(640, 640));

            // 铁栏杆三墙夹角近景
            teleport(context, connection, server, ox + 24, y + 1, oz + 6);
            context.getInput().lookAt(new BlockPos(ox + 24, y + 1, oz + 2));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_bars_corner").withSize(640, 640));

            // 悬空两板互连特写：不靠任何完整方块，两块板应连成一条满高竖带（原版语义）
            teleport(context, connection, server, ox + 28, y + 4, oz + 6);
            context.getInput().lookAt(new BlockPos(ox + 28, y + 3, oz + 2));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_pane_floating_pair").withSize(640, 640));

            // 板类贴半砖位移特写：后排（z=8）上半砖下的板上移、前排（z=6）下半砖上的板下移
            teleport(context, connection, server, ox + 27.5, y + 2, oz + 11);
            context.getInput().lookAt(new BlockPos(ox + 27, y + 1, oz + 7));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_pane_on_slab").withSize(960, 480));

            // 镜像/旋转变换特写：z=10 竖半砖三连（贴北/贴南/贴东）、z=12 竖楼梯两连（西北/东北）
            teleport(context, connection, server, ox + 27.5, y + 2, oz + 15);
            context.getInput().lookAt(new BlockPos(ox + 27, y + 1, oz + 11));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_mirror_transform").withSize(960, 480));

            // 竖直台阶 / 楼梯近景（石材质两款 + 橡木对照）
            teleport(context, connection, server, ox + 6, y + 1, oz + 12);
            context.getInput().lookAt(new BlockPos(ox + 6, y + 1, oz + 8));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_vertical_slabs").withSize(960, 480));

            // 侧面放置特写：石柱西面贴的竖台阶 + 北面贴的竖楼梯（从西北看，三者都可见）
            teleport(context, connection, server, ox + 9, y + 1, oz + 4);
            context.getInput().lookAt(new BlockPos(ox + 13, y + 1, oz + 8));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_side_placement").withSize(640, 640));

            // 侧面判定框特写（与顶面同构：中央正方形=平放区，外圈=竖放扇区）：
            // 手持石台阶看石柱南面 → 应画“中央正方形 + 被截断的对角线 X”
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0)
                    .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SLAB)));
            connection.waitForClientboundPackets();
            teleport(context, connection, server, ox + 13.5, y + 1, oz + 11.5);
            context.getInput().lookAt(new BlockPos(ox + 13, y + 1, oz + 8));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_guide_slab_side").withSize(640, 640));

            // 手持石楼梯看石柱东面 → 应画“中央正方形 + 被截断的十字”
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0)
                    .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE_STAIRS)));
            connection.waitForClientboundPackets();
            teleport(context, connection, server, ox + 16.5, y + 1, oz + 8.5);
            context.getInput().lookAt(new BlockPos(ox + 13, y + 1, oz + 8));
            context.waitTicks(10);
            connection.waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("phase3_guide_stair_side").withSize(640, 640));

            context.getInput().pressKey(options -> options.keyToggleGui);   // 复原 HUD
            context.restoreDefaultGameOptions();
        }
    }

    /** 摆出三组对照场景（相对坐标，原点 = 场地西南角地面）。 */
    private static void buildScene(ServerLevel level, int ox, int y, int oz) {
        // —— 玻璃板三组 ——
        // 部件属性靠 getStateForPlacement / updateShape 算，而 setBlock 直放不会重算自己：
        // 所以**先放板、后放墙/柱**，让后续放置的方块触发玻璃板的 updateShape 级联。
        set(level, ox + 2, y + 1, oz + 2, Blocks.GLASS_PANE);                       // 孤立棍（脚下是地板 → 全假 → 原版棍）
        for (int z = 2; z <= 4; z++) {
            set(level, ox + 6, y + 1, oz + z, Blocks.GLASS_PANE);                   // 南北竖直面
        }
        set(level, ox + 10, y + 1, oz + 2, Blocks.GLASS_PANE);                      // 三墙夹角：先放角板
        set(level, ox + 6, y + 1, oz + 1, Blocks.STONE);                            // 北柱
        set(level, ox + 6, y + 1, oz + 5, Blocks.STONE);                            // 南柱
        set(level, ox + 10, y + 1, oz + 1, Blocks.STONE);                           // 三墙夹角：北墙（2 高）
        set(level, ox + 10, y + 2, oz + 1, Blocks.STONE);
        set(level, ox + 9, y + 1, oz + 2, Blocks.STONE);                            // 西墙（2 高）
        set(level, ox + 9, y + 2, oz + 2, Blocks.STONE);

        // —— 铁栏杆同款三组 ——
        set(level, ox + 16, y + 1, oz + 2, Blocks.IRON_BARS);
        for (int z = 2; z <= 4; z++) {
            set(level, ox + 20, y + 1, oz + z, Blocks.IRON_BARS);
        }
        set(level, ox + 24, y + 1, oz + 2, Blocks.IRON_BARS);
        set(level, ox + 20, y + 1, oz + 1, Blocks.STONE);
        set(level, ox + 20, y + 1, oz + 5, Blocks.STONE);
        set(level, ox + 24, y + 1, oz + 1, Blocks.STONE);
        set(level, ox + 24, y + 2, oz + 1, Blocks.STONE);
        set(level, ox + 23, y + 1, oz + 2, Blocks.STONE);
        set(level, ox + 23, y + 2, oz + 2, Blocks.STONE);

        // —— 悬空两块玻璃板互连（规则 3 回归场景：不靠任何完整方块也必须连上）——
        // setBlock 直放不会重算自己：先放 A、再放 B（B 触发 A 的 updateShape），
        // B 自己再借一块临时脚手架触发重算，最后拆掉脚手架 → 两块板完全悬空
        set(level, ox + 28, y + 3, oz + 2, Blocks.GLASS_PANE);                   // 板 A（悬空）
        set(level, ox + 28, y + 3, oz + 3, Blocks.GLASS_PANE);                   // 板 B（A 正南，触发 A 重算）
        set(level, ox + 28, y + 4, oz + 3, Blocks.STONE);                        // 临时脚手架：触发 B 重算
        set(level, ox + 28, y + 4, oz + 3, Blocks.AIR);                          // 拆掉脚手架 → 完全悬空

        // —— 板类贴半砖位移（自研增强：不落属性、按邻居现算）——
        // 下半砖上（z=6 行）：玻璃板 / 铁栏杆各下移半格贴砖面，隔开放避免互相连接抢镜
        set(level, ox + 26, y, oz + 6, Blocks.STONE_SLAB);                       // 下半砖（替换地板砖）
        set(level, ox + 26, y + 1, oz + 6, Blocks.GLASS_PANE);                   // 玻璃板 → 下移半格
        set(level, ox + 28, y, oz + 6, Blocks.STONE_SLAB);
        set(level, ox + 28, y + 1, oz + 6, Blocks.IRON_BARS);                    // 铁栏杆 → 下移半格
        // 上半砖下（z=8 行）+ 铜栏杆对照
        level.setBlock(new BlockPos(ox + 28, y + 2, oz + 8),
                Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.TOP), 3);   // 上半砖
        set(level, ox + 28, y + 1, oz + 8, Blocks.GLASS_PANE);                   // 玻璃板 → 上移半格
        set(level, ox + 26, y, oz + 8, Blocks.STONE_SLAB);
        set(level, ox + 26, y + 1, oz + 8,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "copper_bars")));
        // 铜栏杆 → 下移半格（WeatheringCopperBarsBlock extends IronBarsBlock，同一套管线）

        // —— 镜像/旋转变换（BlockStateSymmetryMixin，对称之杖的底层）——
        BlockState slabNorth = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.MODE,
                        com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode.VERTICAL_NS)
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
        level.setBlock(new BlockPos(ox + 26, y + 1, oz + 10), slabNorth, 3);                      // 贴北原态
        level.setBlock(new BlockPos(ox + 27, y + 1, oz + 10),
                slabNorth.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT), 3);           // Z 轴镜像 → 贴南
        level.setBlock(new BlockPos(ox + 28, y + 1, oz + 10),
                slabNorth.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90), 3);       // 顺时针 90° → 贴东

        // 竖楼梯：L 中心西北（facing 北）原态 / X 轴镜像后到东北（facing 东）
        BlockState stairNw = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.VERTICAL, true)
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH);
        level.setBlock(new BlockPos(ox + 26, y + 1, oz + 12), stairNw, 3);
        level.setBlock(new BlockPos(ox + 27, y + 1, oz + 12),
                stairNw.mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK), 3);

        // —— 竖直台阶 / 竖直楼梯（非橡木材质：验证模板几何 + 运行时换图） ——
        // 竖直台阶（南北向）：石台阶竖起来，贴图应是石头而不是橡木、更不是纯色
        level.setBlock(new BlockPos(ox + 2, y + 1, oz + 8),
                Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.MODE,
                                com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode.VERTICAL_NS)
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                net.minecraft.world.level.block.state.properties.SlabType.BOTTOM),
                3);
        // 竖直台阶（东西向）
        level.setBlock(new BlockPos(ox + 4, y + 1, oz + 8),
                Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.MODE,
                                com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode.VERTICAL_EW)
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                net.minecraft.world.level.block.state.properties.SlabType.BOTTOM),
                3);
        // 竖直楼梯：石楼梯竖起来
        level.setBlock(new BlockPos(ox + 7, y + 1, oz + 8),
                Blocks.STONE_STAIRS.defaultBlockState()
                        .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.VERTICAL, true)
                        .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.StairBlock.HALF,
                                net.minecraft.world.level.block.state.properties.Half.BOTTOM)
                        .setValue(net.minecraft.world.level.block.StairBlock.SHAPE,
                                net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT),
                3);
        // 橡木竖直台阶作对照（模板本体材质，应与石头款几何一致）
        level.setBlock(new BlockPos(ox + 10, y + 1, oz + 8),
                Blocks.OAK_SLAB.defaultBlockState()
                        .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.MODE,
                                com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode.VERTICAL_NS)
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                net.minecraft.world.level.block.state.properties.SlabType.BOTTOM),
                3);

        // —— 侧面放置（默认档增强，参考项目没有）：左竖带贴的竖台阶 + 左竖带贴的竖楼梯 ——
        // 石柱 2 高；西面左带（u=z 负端）贴一块竖台阶（占北半 = VERTICAL_NS+BOTTOM）；
        // 北面左带（u=x 负端）贴一座竖楼梯（FACING=WEST）
        set(level, ox + 13, y + 1, oz + 8, Blocks.STONE);
        set(level, ox + 13, y + 2, oz + 8, Blocks.STONE);
        level.setBlock(new BlockPos(ox + 12, y + 1, oz + 8),
                Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.MODE,
                                com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode.VERTICAL_NS)
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                net.minecraft.world.level.block.state.properties.SlabType.BOTTOM),
                3);
        level.setBlock(new BlockPos(ox + 13, y + 1, oz + 7),
                Blocks.STONE_STAIRS.defaultBlockState()
                        .setValue(com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties.VERTICAL, true)
                        .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST)
                        .setValue(net.minecraft.world.level.block.StairBlock.HALF,
                                net.minecraft.world.level.block.state.properties.Half.BOTTOM)
                        .setValue(net.minecraft.world.level.block.StairBlock.SHAPE,
                                net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT),
                3);
    }

    /** 传送（服务端线程里做），并等客户端把周围区块渲染出来。 */
    private static void teleport(ClientGameTestContext context, TestServerConnection connection,
                                 TestServerContext server, double x, double y, double z) {
        server.runOnServer(s ->
                s.getPlayerList().getPlayers().get(0).teleportTo(x, y, z));
        connection.waitForClientboundPackets();
        context.waitTicks(5);
        connection.waitForChunksRender();
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        BlockState state = block.defaultBlockState();
        if (block == Blocks.WALL_TORCH || block == Blocks.SOUL_WALL_TORCH) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    /** 清出一片空地并铺一层石头地板（顶面 = y）。 */
    private static void clearAndFloor(ServerLevel level, int ox, int y, int oz, int width, int depth) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlock(new BlockPos(ox + dx, y + dy, oz + dz), Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(ox + dx, y, oz + dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
        }
    }
}
