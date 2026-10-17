package com.tt23xrstudio.vanilla_refit_fabric.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 玻璃板的自动连接逻辑（直接覆盖原版那套“只设置东西南北”的逻辑）。
 *
 * ===== 连接规则（编号 1~11，其中编号 3 为空位、实际 10 条；**编号大的覆盖编号小的＝后面覆盖前面**）=====
 *
 * | 编号 | 状态 | 规则 |
 * | --- | --- | --- |
 * | 1 | 启用 | 任意两个相邻方向都是“原版会连接的方块”，且其中**至少一个是可连接但不是玻璃板 / 铁栏杆**的方块（实心方块、墙）→ 它们的夹角为真（上/下 × 水平 = 那个方向的上下半；水平 × 水平 = 那个斜角的面片） |
 * | 2 | 启用 | 任意两个相邻方向都有**玻璃板 / 铁栏杆**、且它们夹角上的斜角位置是**方块**（原版会连接的方块，板类也算）→ 夹角为真（同样分上/下 × 水平与水平 × 水平两种） |
 * | 4 | 启用 | **两个中面刚好形成夹角 → 夹在它们中间的那个小面置假**：角小面 北竖×东竖→东北 / 南竖×东竖→东南 / 南竖×西竖→西南 / 北竖×西竖→西北；上下小面 上南北×东平→东上 / 上南北×西平→西上 / 下南北×东平→东下 / 下南北×西平→西下 / 上东西×北平→北上 / 上东西×南平→南上 / 下东西×北平→北下 / 下东西×南平→南下 |
 * | 5 | 启用 | **规则 2 的原始版本**：任意两个相邻方向都是**玻璃板 / 铁栏杆**、且它们夹角上的**斜角位置也是玻璃板 / 铁栏杆** → 夹角置真（排在规则 4 之后） |
 * | 6 | 启用 | **全模型只有一个小面为真** → 它所在的那个**大面的 4 个小面全为真** |
 * | 7 | 启用 | **某个大面已经有 3 个小面为真** → 剩下那个小面也置真 |
 * | 8 | 启用 | 为真的小面**全部集中在同一个方向**（东/南/西/北/上/下 6 个方向之一）时，把这些小面按该方向镜像到相反方向，那些小面也置真 |
 * | 9 | 启用 | 走完前面几条**仍然不连续**、但**只补一个小面就能变连续且候选唯一**时，把那个小面补上（候选 ≥2 个的不处理） |
 * | 10 | 启用 | **连"补 1 个小面"的方案都没有**、但**"补 2 个小面"的方案恰好唯一**时，把这两个小面一起补上（2 面的非唯一解、需要 3 个面的情况不处理） |
 *
 * （原规则 11 兜底“上下都是空气且全假 → 12 面全真”已按实测反馈移除：无连接时就是原版那根立柱。）
 *
 * <b>连续性</b>：看 6 个方向各自为真的小面数——**所有方向都不是 1** 就是“连续”，
 * **只要有一个方向恰好是 1** 就是“不连续”。**规则 6~10 只在不连续的玻璃板上执行**；连续的玻璃板只走规则 1~2 + 兜底。
 *
 * <b>规则 4 的置假</b>：两个中面为真时，夹在它们中间的那个小面置假。它排在规则 5（规则 2 的原始版本）之后，
 * 所以**置假赢过规则 5**，这是本套里唯一条置假规则。兜底的判据因此按“12 个面全假”来判，
 * 而不是按“前面几条有没有命中”来判。
 *
 * <b>“方块”的判定</b>：全部指“原版玻璃板会连接的方块”，与原版 {@code IronBarsBlock#attachsTo} 一致——
 * 玻璃板 / 铁栏杆、墙（{@code BlockTags.WALLS}）、或者朝本块那一面是完整实心面的方块。
 * 斜角位置（同一水平面里两个相邻方向之间那一格、以及上/下 × 水平那 8 格）没有单一朝向，那里取
 * “它朝本块的那两个侧面里任意一个满足上面这条”即算有方块——规则 2、规则 5 会看这 12 个斜角。
 * 本文件里的“板类”专指**玻璃板与铁栅栏**（{@link IronBarsBlock} 一族，含染色玻璃板）。
 *
 * <b>优先级怎么落地</b>：代码按“优先级从低到高”写，**靠后写的覆盖靠前的**（编号大的赢）：
 * 先算规则 1、2（都只置真、互不否决）→ 规则 4（置假）→ 规则 5（规则 2 原始版，置真）
 * → 连续性修补 6~10 → 兜底 11。
 *
 * <b>起点</b>：12 个属性先全部置假；规则 1~5 都没点亮面片时就是全假，也就是原版那根中心十字，
 * 只有兜底（上下都是空气、且 12 个面全假）才会变成 12 面全真。
 *
 * <b>与按属性级联的规则的区别</b>：这套规则**不读邻居算出来的部件属性**，只读邻居是什么方块，
 * 所以结果是周围环境的纯函数、与放置顺序无关（“先摆一片、再在下面放玻璃板就不会重新连接”那类问题不存在）。
 *
 * <b>更新范围</b>：本块要读 6 个正交方向 + 12 个“夹角”斜角（同一水平面 4 个 + 上下 × 水平 8 个），
 * 共 18 格，见 {@link #NEIGHBOUR_OFFSETS} / {@link #DIAGONAL_OFFSETS}。原版的邻居级联只通知 6 个
 * 正交邻居，斜角那一路由 PaneIndirectNeighbourMixin 补（见 {@link #refreshDiagonals}，照原版红石线
 * 走 updateIndirectNeighbourShapes）。世界生成那一路仍然走 refreshChunk 的队列，但那只是保险——
 * 现在的规则一次 update 就到位（幂等），队列不会算出不同的结果。
 */
public final class PaneConnection {

    /**
     * 是否正处在区块后处理这一次调用里（LevelChunkPostProcessMixin 会在方法前后开关）。
     * 后处理的时机是“区块被提升为 tick 中”，从磁盘加载的区块也会走，所以不能用它来当
     * “这块区块是新生成的”判据；这里记录“本次后处理有没有真的碰到本模组的方块”，
     * 只有碰到了才需要做后面的收敛，普通加载就完全不额外做事。
     */
    private static boolean inPostProcess;
    private static boolean touchedPane;

    private PaneConnection() {
    }

    /**
     * 会与本块互相影响的偏移：6 个正交方向 + 12 个“夹角”斜角（同一水平面 4 个 + 上下 × 水平 8 个），共 18 个。
     * 规则 1、2 读的全部位置都在这里面，改动规则牵到新位置时必须同步这张表。
     */
    private static final int[][] NEIGHBOUR_OFFSETS = {
            {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
            {1, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, -1}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0},
            {0, -1, -1}, {1, -1, 0}, {0, -1, 1}, {-1, -1, 0}
    };

    /** 12 个“夹角”斜角偏移（与 {@link #NEIGHBOUR_OFFSETS} 的后 12 项一致，这里单独一张便于遍历）。 */
    private static final int[][] DIAGONAL_OFFSETS = {
            {1, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, -1}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0},
            {0, -1, -1}, {1, -1, 0}, {0, -1, 1}, {-1, -1, 0}
    };

    // ===== 规则 5 用的位序与“方向掩码”（12 个小面；位序与 PaneCornerSupport 的部件表一致）=====
    private static final int FACE_NORTH_LOWER = 0, FACE_NORTH_UPPER = 1, FACE_SOUTH_LOWER = 2, FACE_SOUTH_UPPER = 3,
            FACE_EAST_LOWER = 4, FACE_EAST_UPPER = 5, FACE_WEST_LOWER = 6, FACE_WEST_UPPER = 7,
            FACE_NORTH_EAST = 8, FACE_SOUTH_EAST = 9, FACE_SOUTH_WEST = 10, FACE_NORTH_WEST = 11;

    /** 6 个方向各自包含的 4 个小面（“北”= 北下 北上 东北 西北，其余同理）。 */
    private static final int DIRECTION_NORTH = (1 << FACE_NORTH_LOWER) | (1 << FACE_NORTH_UPPER)
            | (1 << FACE_NORTH_EAST) | (1 << FACE_NORTH_WEST);
    private static final int DIRECTION_SOUTH = (1 << FACE_SOUTH_LOWER) | (1 << FACE_SOUTH_UPPER)
            | (1 << FACE_SOUTH_EAST) | (1 << FACE_SOUTH_WEST);
    private static final int DIRECTION_EAST = (1 << FACE_EAST_LOWER) | (1 << FACE_EAST_UPPER)
            | (1 << FACE_NORTH_EAST) | (1 << FACE_SOUTH_EAST);
    private static final int DIRECTION_WEST = (1 << FACE_WEST_LOWER) | (1 << FACE_WEST_UPPER)
            | (1 << FACE_SOUTH_WEST) | (1 << FACE_NORTH_WEST);
    private static final int DIRECTION_UP = (1 << FACE_NORTH_UPPER) | (1 << FACE_SOUTH_UPPER)
            | (1 << FACE_EAST_UPPER) | (1 << FACE_WEST_UPPER);
    private static final int DIRECTION_DOWN = (1 << FACE_NORTH_LOWER) | (1 << FACE_SOUTH_LOWER)
            | (1 << FACE_EAST_LOWER) | (1 << FACE_WEST_LOWER);

    /** 3 个大面各自包含的 4 个小面（规则 6：只有一个小面为真时，把它所在的大面 4 个小面全点亮）。 */
    private static final int GREAT_FACE_VERTICAL_NS = (1 << FACE_NORTH_LOWER) | (1 << FACE_NORTH_UPPER)
            | (1 << FACE_SOUTH_LOWER) | (1 << FACE_SOUTH_UPPER);
    private static final int GREAT_FACE_VERTICAL_EW = (1 << FACE_EAST_LOWER) | (1 << FACE_EAST_UPPER)
            | (1 << FACE_WEST_LOWER) | (1 << FACE_WEST_UPPER);
    private static final int GREAT_FACE_HORIZONTAL = (1 << FACE_NORTH_EAST) | (1 << FACE_SOUTH_EAST)
            | (1 << FACE_SOUTH_WEST) | (1 << FACE_NORTH_WEST);

    /** 6 个方向与 3 个大面的掩码表（“连续性”判定、规则 6、规则 7 遍历用）。 */
    private static final int[] DIRECTIONS = {
            DIRECTION_NORTH, DIRECTION_SOUTH, DIRECTION_EAST, DIRECTION_WEST, DIRECTION_UP, DIRECTION_DOWN};
    private static final int[] GREAT_FACES = {
            GREAT_FACE_VERTICAL_NS, GREAT_FACE_VERTICAL_EW, GREAT_FACE_HORIZONTAL};

    /** 区块后处理开始（由 LevelChunkPostProcessMixin 调用）。 */
    public static void beginPostProcess() {
        inPostProcess = true;
        touchedPane = false;
    }

    /** 区块后处理结束；返回本次是否处理过玻璃板/铁栏杆（由 LevelChunkPostProcessMixin 调用）。 */
    public static boolean endPostProcess() {
        boolean touched = touchedPane;
        inPostProcess = false;
        touchedPane = false;
        return touched;
    }

    /** 按当前周围环境重算玻璃板的 12 个部件属性；不是本模组支持的玻璃板时原样返回。 */
    public static BlockState update(BlockState state, BlockGetter level, BlockPos pos) {
        if (!PaneCornerSupport.isSupportedPane(state)
                || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)
                || !(state.getBlock() instanceof IronBarsBlock pane)) {
            return state;   // 第三方玻璃板/铁栏杆等：连“碰到过玻璃板”的标记都不打，免得白跑一次收敛
        }
        if (inPostProcess) {
            touchedPane = true;
        }

        // ===== 六个方向：是不是“原版玻璃板会连接的方块”，以及是不是玻璃板/铁栏杆 =====
        boolean north = connectsTo(pane, level, pos, Direction.NORTH);
        boolean east = connectsTo(pane, level, pos, Direction.EAST);
        boolean south = connectsTo(pane, level, pos, Direction.SOUTH);
        boolean west = connectsTo(pane, level, pos, Direction.WEST);
        boolean up = connectsTo(pane, level, pos, Direction.UP);
        boolean down = connectsTo(pane, level, pos, Direction.DOWN);
        boolean paneNorth = isPaneBlock(level, pos.north());
        boolean paneEast = isPaneBlock(level, pos.east());
        boolean paneSouth = isPaneBlock(level, pos.south());
        boolean paneWest = isPaneBlock(level, pos.west());
        BlockState aboveState = level.getBlockState(pos.above());
        BlockState belowState = level.getBlockState(pos.below());
        boolean paneUp = aboveState.getBlock() instanceof IronBarsBlock;
        boolean paneDown = belowState.getBlock() instanceof IronBarsBlock;
        // “其余可连接方块”= 可连接但不是玻璃板/铁栏杆（实心方块、墙）
        boolean solidNorth = north && !paneNorth;
        boolean solidEast = east && !paneEast;
        boolean solidSouth = south && !paneSouth;
        boolean solidWest = west && !paneWest;
        boolean solidUp = up && !paneUp;
        boolean solidDown = down && !paneDown;

        // ===== 规则 1 的 12 组条件：两个相邻方向都是“原版会连接的方块”，且其中至少一个是
        // “可连接但不是玻璃板/铁栏杆”的方块（实心方块、墙）=====
        // 条件是“一边是非板类方块、另一边只要是方块即可”，所以 实心方块 × 玻璃板 这种组合也会点亮夹角；
        // 板类 × 板类 不归规则 1 管（归规则 2）。
        boolean solidPairNorthEast = north && east && (solidNorth || solidEast);
        boolean solidPairSouthEast = south && east && (solidSouth || solidEast);
        boolean solidPairSouthWest = south && west && (solidSouth || solidWest);
        boolean solidPairNorthWest = north && west && (solidNorth || solidWest);
        boolean solidPairDownNorth = down && north && (solidDown || solidNorth);
        boolean solidPairDownEast = down && east && (solidDown || solidEast);
        boolean solidPairDownSouth = down && south && (solidDown || solidSouth);
        boolean solidPairDownWest = down && west && (solidDown || solidWest);
        boolean solidPairUpNorth = up && north && (solidUp || solidNorth);
        boolean solidPairUpEast = up && east && (solidUp || solidEast);
        boolean solidPairUpSouth = up && south && (solidUp || solidSouth);
        boolean solidPairUpWest = up && west && (solidUp || solidWest);
        // ===== 规则 2 的 12 组条件：两个相邻方向都是玻璃板/铁栏杆、且它们夹角上的斜角位置是“方块” =====
        // 斜角只要“原版会连接的方块”即可（板类也算），不要求必须是玻璃板/铁栏杆。
        // 水平 × 水平 → 同一水平面里的那个斜角面片；上/下 × 水平 → 那个水平方向的上半/下半片
        boolean panePairNorthEast = paneNorth && paneEast && connectsToDiagonal(pane, level, pos, 1, 0, -1);
        boolean panePairSouthEast = paneSouth && paneEast && connectsToDiagonal(pane, level, pos, 1, 0, 1);
        boolean panePairSouthWest = paneSouth && paneWest && connectsToDiagonal(pane, level, pos, -1, 0, 1);
        boolean panePairNorthWest = paneNorth && paneWest && connectsToDiagonal(pane, level, pos, -1, 0, -1);
        boolean panePairDownNorth = paneDown && paneNorth && connectsToDiagonal(pane, level, pos, 0, -1, -1);
        boolean panePairDownEast = paneDown && paneEast && connectsToDiagonal(pane, level, pos, 1, -1, 0);
        boolean panePairDownSouth = paneDown && paneSouth && connectsToDiagonal(pane, level, pos, 0, -1, 1);
        boolean panePairDownWest = paneDown && paneWest && connectsToDiagonal(pane, level, pos, -1, -1, 0);
        boolean panePairUpNorth = paneUp && paneNorth && connectsToDiagonal(pane, level, pos, 0, 1, -1);
        boolean panePairUpEast = paneUp && paneEast && connectsToDiagonal(pane, level, pos, 1, 1, 0);
        boolean panePairUpSouth = paneUp && paneSouth && connectsToDiagonal(pane, level, pos, 0, 1, 1);
        boolean panePairUpWest = paneUp && paneWest && connectsToDiagonal(pane, level, pos, -1, 1, 0);
        // ----- 规则 1、2：命中就把对应夹角置真（两条都只置真、互不否决，谁命中结果一样） -----
        boolean northEast = solidPairNorthEast || panePairNorthEast;
        boolean southEast = solidPairSouthEast || panePairSouthEast;
        boolean southWest = solidPairSouthWest || panePairSouthWest;
        boolean northWest = solidPairNorthWest || panePairNorthWest;
        boolean northLower = solidPairDownNorth || panePairDownNorth;
        boolean eastLower = solidPairDownEast || panePairDownEast;
        boolean southLower = solidPairDownSouth || panePairDownSouth;
        boolean westLower = solidPairDownWest || panePairDownWest;
        boolean northUpper = solidPairUpNorth || panePairUpNorth;
        boolean eastUpper = solidPairUpEast || panePairUpEast;
        boolean southUpper = solidPairUpSouth || panePairUpSouth;
        boolean westUpper = solidPairUpWest || panePairUpWest;

        // ----- 规则 4：两个中面为真 → 夹在它们中间的那个小面置假 -----
        // 术语：小面 12 个 / 中面 12 个 / 大面 3 个。
        // 12 个中面里"刚好形成夹角"的组合正好把 12 个小面各夹住一个：每个小面都由两个中面夹着
        // （一个是它所在竖直大面上的中面、一个是它所在水平大面上的中面），两个都为真时它就置假。
        //   角小面   ：北竖×东竖→东北、南竖×东竖→东南、南竖×西竖→西南、北竖×西竖→西北
        //   上下小面 ：上南北×东平→东上、上南北×西平→西上、下南北×东平→东下、下南北×西平→西下
        //             上东西×北平→北上、上东西×南平→南上、下东西×北平→北下、下东西×南平→南下
        // 判据取"规则 1~2 算完之后"的值，先全部算出来再一次性写回，避免互相影响。
        boolean northVert = northLower && northUpper;   // 北竖
        boolean eastVert = eastLower && eastUpper;      // 东竖
        boolean southVert = southLower && southUpper;   // 南竖
        boolean westVert = westLower && westUpper;      // 西竖
        boolean northFlat = northEast && northWest;     // 北平
        boolean southFlat = southEast && southWest;     // 南平
        boolean eastFlat = northEast && southEast;      // 东平
        boolean westFlat = southWest && northWest;      // 西平
        boolean upperNS = northUpper && southUpper;     // 上南北
        boolean lowerNS = northLower && southLower;     // 下南北
        boolean upperEW = eastUpper && westUpper;       // 上东西
        boolean lowerEW = eastLower && westLower;       // 下东西
        if (northVert && eastVert) {
            northEast = false;
        }
        if (southVert && eastVert) {
            southEast = false;
        }
        if (southVert && westVert) {
            southWest = false;
        }
        if (northVert && westVert) {
            northWest = false;
        }
        if (upperNS && eastFlat) {
            eastUpper = false;
        }
        if (upperNS && westFlat) {
            westUpper = false;
        }
        if (lowerNS && eastFlat) {
            eastLower = false;
        }
        if (lowerNS && westFlat) {
            westLower = false;
        }
        if (upperEW && northFlat) {
            northUpper = false;
        }
        if (upperEW && southFlat) {
            southUpper = false;
        }
        if (lowerEW && northFlat) {
            northLower = false;
        }
        if (lowerEW && southFlat) {
            southLower = false;
        }

        // ----- 规则 5：规则 2 的原始版本——斜角位置必须是**板类** -----
        // 两个相邻方向都是玻璃板/铁栏杆、且它们夹角上的斜角位置也是玻璃板/铁栏杆 → 夹角置真。
        // 它排在规则 4（置假）之后，按“后面覆盖前面”的规则，会把规则 4 刚熄灭的那个小面重新点亮。
        if (paneNorth && paneEast && isPaneAt(level, pos, 1, 0, -1)) {
            northEast = true;
        }
        if (paneSouth && paneEast && isPaneAt(level, pos, 1, 0, 1)) {
            southEast = true;
        }
        if (paneSouth && paneWest && isPaneAt(level, pos, -1, 0, 1)) {
            southWest = true;
        }
        if (paneNorth && paneWest && isPaneAt(level, pos, -1, 0, -1)) {
            northWest = true;
        }
        if (paneDown && paneNorth && isPaneAt(level, pos, 0, -1, -1)) {
            northLower = true;
        }
        if (paneDown && paneEast && isPaneAt(level, pos, 1, -1, 0)) {
            eastLower = true;
        }
        if (paneDown && paneSouth && isPaneAt(level, pos, 0, -1, 1)) {
            southLower = true;
        }
        if (paneDown && paneWest && isPaneAt(level, pos, -1, -1, 0)) {
            westLower = true;
        }
        if (paneUp && paneNorth && isPaneAt(level, pos, 0, 1, -1)) {
            northUpper = true;
        }
        if (paneUp && paneEast && isPaneAt(level, pos, 1, 1, 0)) {
            eastUpper = true;
        }
        if (paneUp && paneSouth && isPaneAt(level, pos, 0, 1, 1)) {
            southUpper = true;
        }
        if (paneUp && paneWest && isPaneAt(level, pos, -1, 1, 0)) {
            westUpper = true;
        }

        // ----- 连续性判定：6 个方向各自为真的小面数， -----
        // ----- 只要有一个方向**恰好 1 个**为真就是“不连续”，规则 6~10 只在不连续时执行 -----
        // 方向（各含 4 个小面）：
        //   北 = 北下 北上 东北 西北；南 = 南下 南上 东南 西南；东 = 东下 东上 东北 东南；
        //   西 = 西下 西上 西南 西北；上 = 北上 东上 南上 西上；下 = 北下 东下 南下 西下。
        // 先建 12 位掩码，规则 6~10 都在掩码上做，最后一次性写回。
        int litParts = 0;
        if (northLower) {
            litParts |= 1 << FACE_NORTH_LOWER;
        }
        if (northUpper) {
            litParts |= 1 << FACE_NORTH_UPPER;
        }
        if (southLower) {
            litParts |= 1 << FACE_SOUTH_LOWER;
        }
        if (southUpper) {
            litParts |= 1 << FACE_SOUTH_UPPER;
        }
        if (eastLower) {
            litParts |= 1 << FACE_EAST_LOWER;
        }
        if (eastUpper) {
            litParts |= 1 << FACE_EAST_UPPER;
        }
        if (westLower) {
            litParts |= 1 << FACE_WEST_LOWER;
        }
        if (westUpper) {
            litParts |= 1 << FACE_WEST_UPPER;
        }
        if (northEast) {
            litParts |= 1 << FACE_NORTH_EAST;
        }
        if (southEast) {
            litParts |= 1 << FACE_SOUTH_EAST;
        }
        if (southWest) {
            litParts |= 1 << FACE_SOUTH_WEST;
        }
        if (northWest) {
            litParts |= 1 << FACE_NORTH_WEST;
        }
        // 连续性：6 个方向里只要有一个方向的为真小面数恰好是 1，就是“不连续”
        if (isDiscontinuous(litParts)) {
            // ----- 规则 6：全模型只有一个小面为真时，它所在的那个大面的 4 个小面全为真 -----
            // 大面：竖直南北 = 北下 北上 南下 南上；竖直东西 = 东下 东上 西下 西上；水平 = 东北 东南 西南 西北。
            if (Integer.bitCount(litParts) == 1) {
                if ((litParts & GREAT_FACE_VERTICAL_NS) != 0) {
                    litParts |= GREAT_FACE_VERTICAL_NS;
                } else if ((litParts & GREAT_FACE_VERTICAL_EW) != 0) {
                    litParts |= GREAT_FACE_VERTICAL_EW;
                } else {
                    litParts |= GREAT_FACE_HORIZONTAL;
                }
            }

            // ----- 规则 7：某个大面已经有 3 个小面为真时，剩下那个小面也置真 -----
            // 大面只有 3 个面为真时，它的两个方向必然是“2 + 1”，所以必然是“不连续”，与上面的门槛不冲突。
            for (int greatFace : GREAT_FACES) {
                if (Integer.bitCount(litParts & greatFace) == 3) {
                    litParts |= greatFace;
                }
            }

            // ----- 规则 8：为真的小面全部集中在同一个方向时， -----
            // ----- 把这些小面按该方向“镜像”到相反方向，那些小面也置真 -----
            if (litParts != 0) {
                int mirrored = 0;
                if ((litParts & ~DIRECTION_NORTH) == 0 || (litParts & ~DIRECTION_SOUTH) == 0) {
                    mirrored |= mirrorNorthSouth(litParts);
                }
                if ((litParts & ~DIRECTION_EAST) == 0 || (litParts & ~DIRECTION_WEST) == 0) {
                    mirrored |= mirrorEastWest(litParts);
                }
                if ((litParts & ~DIRECTION_UP) == 0 || (litParts & ~DIRECTION_DOWN) == 0) {
                    mirrored |= mirrorUpDown(litParts);
                }
                litParts |= mirrored;
            }

            // ----- 规则 9：走完前面几条仍然不连续、但**只补一个小面就能变连续且候选唯一**时， -----
            // ----- 就把那个小面补上（候选 ≥2 个的非唯一解不处理） -----
            int fixCount = 0;
            int fixFace = -1;
            for (int face = 0; face < 12; face++) {
                if ((litParts & (1 << face)) != 0) {
                    continue;   // 已经为真的面不能重复补
                }
                if (!isDiscontinuous(litParts | (1 << face))) {
                    fixCount++;
                    fixFace = face;
                }
            }
            // 这里必须**重新判一次当前是否仍然不连续**：规则 5~7 也可能把原本不连续的形状补成连续的，
            // 如果只用在门口算过一次的那个标记，就会给**已经连续的玻璃板**再补一个多余的面。
            if (isDiscontinuous(litParts) && fixCount == 1) {
                litParts |= 1 << fixFace;
            }

            // ----- 规则 10：连“补 1 个小面”的方案都没有，但**“补 2 个小面”的方案恰好唯一**时， -----
            // ----- 就把这两个小面一起补上（2 面的非唯一解、以及需要 3 个面的情况不处理） -----
            if (isDiscontinuous(litParts) && fixCount == 0) {
                int pairCount = 0;
                int firstFace = -1;
                int secondFace = -1;
                for (int a = 0; a < 12; a++) {
                    if ((litParts & (1 << a)) != 0) {
                        continue;
                    }
                    for (int b = a + 1; b < 12; b++) {
                        if ((litParts & (1 << b)) != 0) {
                            continue;
                        }
                        if (!isDiscontinuous(litParts | (1 << a) | (1 << b))) {
                            pairCount++;
                            firstFace = a;
                            secondFace = b;
                        }
                    }
                }
                if (pairCount == 1) {
                    litParts |= (1 << firstFace) | (1 << secondFace);
                }
            }
        }
        // 把掩码一次性写回（规则 6~10 都只在“不连续”时才会改动它）。
        northLower = (litParts & (1 << FACE_NORTH_LOWER)) != 0;
        northUpper = (litParts & (1 << FACE_NORTH_UPPER)) != 0;
        southLower = (litParts & (1 << FACE_SOUTH_LOWER)) != 0;
        southUpper = (litParts & (1 << FACE_SOUTH_UPPER)) != 0;
        eastLower = (litParts & (1 << FACE_EAST_LOWER)) != 0;
        eastUpper = (litParts & (1 << FACE_EAST_UPPER)) != 0;
        westLower = (litParts & (1 << FACE_WEST_LOWER)) != 0;
        westUpper = (litParts & (1 << FACE_WEST_UPPER)) != 0;
        northEast = (litParts & (1 << FACE_NORTH_EAST)) != 0;
        southEast = (litParts & (1 << FACE_SOUTH_EAST)) != 0;
        southWest = (litParts & (1 << FACE_SOUTH_WEST)) != 0;
        northWest = (litParts & (1 << FACE_NORTH_WEST)) != 0;

        // （原规则 11“悬空孤立玻璃板 → 12 面全真”已按实测反馈移除：
        // 无连接时保持全假 = 原版那根中心立柱，不再整块伸出来。）

        return state
                .setValue(CrossCollisionBlock.NORTH, northLower)
                .setValue(CrossCollisionBlock.EAST, eastLower)
                .setValue(CrossCollisionBlock.SOUTH, southLower)
                .setValue(CrossCollisionBlock.WEST, westLower)
                .setValue(ModBlockStateProperties.PANE_NORTH_UP, northUpper)
                .setValue(ModBlockStateProperties.PANE_EAST_UP, eastUpper)
                .setValue(ModBlockStateProperties.PANE_SOUTH_UP, southUpper)
                .setValue(ModBlockStateProperties.PANE_WEST_UP, westUpper)
                .setValue(ModBlockStateProperties.PANE_NORTH_EAST, northEast)
                .setValue(ModBlockStateProperties.PANE_SOUTH_EAST, southEast)
                .setValue(ModBlockStateProperties.PANE_SOUTH_WEST, southWest)
                .setValue(ModBlockStateProperties.PANE_NORTH_WEST, northWest);
    }

    /** 与原版 IronBarsBlock 相同的连接判定：邻居是玻璃板/铁栏杆、墙，或朝本块那一面是完整实心面。 */
    private static boolean connectsTo(IronBarsBlock pane, BlockGetter level, BlockPos pos, Direction direction) {
        return attachesTo(pane, level, pos.relative(direction), direction.getOpposite());
    }

    /**
     * 站在 {@code neighborPos} 看“它会不会和本块连上”，判定与原版 {@code IronBarsBlock#attachsTo} 完全一致：
     * 玻璃板 / 铁栏杆、墙（{@code BlockTags.WALLS}）无条件算连接，其余方块看朝本块那一面是不是完整实心面。
     *
     * @param faceTowardUs 邻居朝本块的那一面
     */
    private static boolean attachesTo(IronBarsBlock pane, BlockGetter level, BlockPos neighborPos,
                                      Direction faceTowardUs) {
        BlockState neighbor = level.getBlockState(neighborPos);
        return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, faceTowardUs));
    }

    /** 该位置是不是玻璃板 / 铁栏杆（也就是本模组做部件化的那一族方块）。 */
    private static boolean isPaneBlock(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof IronBarsBlock;
    }

    /** 按偏移 (dx, dy, dz) 看那一格是不是玻璃板 / 铁栏杆（规则 5“斜角必须是板类”用）。 */
    private static boolean isPaneAt(BlockGetter level, BlockPos pos, int dx, int dy, int dz) {
        return isPaneBlock(level, pos.offset(dx, dy, dz));
    }

    /** 交换两个部位（两个位都为真或都为假时不变）；规则 8 的镜像用。 */
    private static int swapBits(int parts, int a, int b) {
        int diff = ((parts >> a) ^ (parts >> b)) & 1;
        return parts ^ (diff << a) ^ (diff << b);
    }

    /**
     * 连续性判定：6 个方向各自包含 4 个小面，
     * **只要有一个方向的为真小面数恰好是 1** 就是“不连续”，全都不是 1 就是“连续”。
     */
    private static boolean isDiscontinuous(int parts) {
        for (int direction : DIRECTIONS) {
            if (Integer.bitCount(parts & direction) == 1) {
                return true;
            }
        }
        return false;
    }

    /** 南北镜像：北下↔南下、北上↔南上、东北↔东南、西北↔西南。 */
    private static int mirrorNorthSouth(int parts) {
        return swapBits(swapBits(swapBits(swapBits(parts, FACE_NORTH_LOWER, FACE_SOUTH_LOWER),
                FACE_NORTH_UPPER, FACE_SOUTH_UPPER), FACE_NORTH_EAST, FACE_SOUTH_EAST),
                FACE_NORTH_WEST, FACE_SOUTH_WEST);
    }

    /** 东西镜像：东下↔西下、东上↔西上、东北↔西北、东南↔西南。 */
    private static int mirrorEastWest(int parts) {
        return swapBits(swapBits(swapBits(swapBits(parts, FACE_EAST_LOWER, FACE_WEST_LOWER),
                FACE_EAST_UPPER, FACE_WEST_UPPER), FACE_NORTH_EAST, FACE_NORTH_WEST),
                FACE_SOUTH_EAST, FACE_SOUTH_WEST);
    }

    /** 上下镜像：北下↔北上、南下↔南上、东下↔东上、西下↔西上（角小面无上下对应，不参与）。 */
    private static int mirrorUpDown(int parts) {
        return swapBits(swapBits(swapBits(swapBits(parts, FACE_NORTH_LOWER, FACE_NORTH_UPPER),
                FACE_SOUTH_LOWER, FACE_SOUTH_UPPER), FACE_EAST_LOWER, FACE_EAST_UPPER),
                FACE_WEST_LOWER, FACE_WEST_UPPER);
    }

    /**
     * 规则 2 的“夹角的斜角”判定：按偏移 (dx, dy, dz) 看那一格算不算“原版会连接的方块”。
     *
     * 斜角位置没有单一朝向（总是带两个非零分量），所以取“它朝本块的那两个侧面里任意一个满足
     * {@link #attachesTo}”即算有方块——和原版在水平斜角上的写法一致，只是这里同时覆盖上/下 × 水平。
     */
    private static boolean connectsToDiagonal(IronBarsBlock pane, BlockGetter level, BlockPos pos,
                                              int dx, int dy, int dz) {
        BlockPos diagonal = pos.offset(dx, dy, dz);
        if (dx != 0 && attachesTo(pane, level, diagonal, dx > 0 ? Direction.WEST : Direction.EAST)) {
            return true;
        }
        if (dy != 0 && attachesTo(pane, level, diagonal, dy > 0 ? Direction.DOWN : Direction.UP)) {
            return true;
        }
        if (dz != 0 && attachesTo(pane, level, diagonal, dz > 0 ? Direction.NORTH : Direction.SOUTH)) {
            return true;
        }
        return false;
    }

    /** 从斜角位置指回本块的方向，只用来给原版那一步一个合法的朝向（本模组重算时直接读世界）。 */
    private static Direction directionBackToOrigin(int dx, int dy, int dz) {
        if (dx != 0) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        }
        if (dy != 0) {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        }
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * 世界生成结束后，把一个区块里参与部件化的玻璃板/铁栏杆刷到稳定状态。
     *
     * 原版的后处理（LevelChunk.postProcessGeneration）只对每个标记过的位置按任意顺序调用一次
     * updateFromNeighbourShapes。只读邻居属性的规则在顺序不对时会让先算的方块停在旧结果上
     * （表现就是部分铁栏杆没被更新，要手动在旁边放个方块才刷新），所以需要这里的队列收敛。
     *
     * 现在的规则只读“邻居是什么方块”，一次 update 就到位，所以这个队列是**保险**性质：
     * update 幂等，收敛不会算出不同的结果。留着它比留一堆“以后可能不需要”的判断更安全。
     */
    public static void refreshChunk(LevelChunk chunk, Level level) {
        List<BlockPos> seeds = collectPanesInChunk(chunk);
        if (seeds.isEmpty()) {
            return;
        }
        // inQueue 只用来避免同一个位置在队列里重复排队；出队后会移除，
        // 这样某个方块在它之后又被邻居改动影响时还能再算一次，做到真正的收敛。
        Set<BlockPos> inQueue = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : seeds) {
            if (inQueue.add(pos)) {
                queue.add(pos);
            }
        }
        // 安全上限，避免极端形状下反复互相触发
        int budget = seeds.size() * 8 + 64;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos pos = queue.poll();
            inQueue.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (!PaneCornerSupport.isSupportedPane(state)) {
                continue;
            }
            BlockState next = update(state, level, pos);
            if (next == state || next.equals(state)) {
                continue;   // setValue 在值没变时通常直接返回原实例，但不依赖这一点，等价也跳过
            }
            // UPDATE_KNOWN_SHAPE：跳过原版的邻居形状级联，改由本方法的队列收敛
            level.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            // 这里必须连 12 个斜角一起入队：原版级联覆盖不到斜角，而规则 2 会读斜角有没有方块
            for (int[] offset : NEIGHBOUR_OFFSETS) {
                BlockPos neighbor = mutable.setWithOffset(pos, offset[0], offset[1], offset[2]).immutable();
                if (level.getBlockState(neighbor).getBlock() instanceof IronBarsBlock && inQueue.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * 补上“夹角斜角”这条原版邻居级联覆盖不到的依赖：规则 2 会读斜角位置有没有方块，
     * 而 BlockStateBase#updateNeighbourShapes 只通知 6 个正交邻居，所以斜角上的方块放置或拆除后
     * 本块不会被重算（表现就是要手动在边上放个方块才更新）。
     *
     * 一共 12 个斜角位置（同一水平面 4 个 + 上下 × 水平 8 个，见 {@link #DIAGONAL_OFFSETS}）。
     *
     * 这里照原版红石线（RedStoneWireBlock#updateIndirectNeighbourShapes）的做法，在“间接邻居”这一步
     * 把变化位置周围的 12 个斜角各走一遍 neighborShapeChanged，让它们重算 12 个部件属性；
     * 斜角那块要是真的变了，它自己又会照常级联下去，所以整条链能收敛。
     *
     * <p>26.2 的签名是 {@code neighborShapeChanged(Direction, BlockPos pos, BlockPos neighborPos,
     * BlockState neighborState, int, int)}：第 2 参是“要被更新的那格”、第 3 参是“它的邻居”、
     * 第 4 参是“邻居的状态”。
     *
     * @param state 发生变化的方块状态（放置时是新状态、拆除时是旧状态，两种情况都能补到）
     * @param pos   发生变化的位置
     */
    public static void refreshDiagonals(LevelAccessor level, BlockState state, BlockPos pos,
                                        int flags, int recursionLeft) {
        for (int[] offset : DIAGONAL_OFFSETS) {
            BlockPos diagonal = pos.offset(offset[0], offset[1], offset[2]);
            if (!PaneCornerSupport.isSupportedPane(level.getBlockState(diagonal))) {
                continue;
            }
            // 斜角与变化位置之间没有单一轴向，这里给的朝向只是让原版那一步的计算拿到一个合法邻居方向；
            // 本模组重算时直接读世界，不受这个朝向影响。
            level.neighborShapeChanged(directionBackToOrigin(offset[0], offset[1], offset[2]), diagonal, pos,
                    state, flags, recursionLeft);
        }
    }

    /** 收集区块里所有参与部件化的玻璃板/铁栏杆；先用 section 的调色板过滤，避免整块扫描。 */
    private static List<BlockPos> collectPanesInChunk(LevelChunk chunk) {
        List<BlockPos> result = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();
        int minSectionY = chunk.getMinSectionY();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()
                    || !section.getStates().maybeHas(state -> state.getBlock() instanceof IronBarsBlock)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(minSectionY + index);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (PaneCornerSupport.isSupportedPane(state)) {
                            result.add(new BlockPos(chunkPos.getMinBlockX() + x, baseY + y,
                                    chunkPos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
        return result;
    }

}
