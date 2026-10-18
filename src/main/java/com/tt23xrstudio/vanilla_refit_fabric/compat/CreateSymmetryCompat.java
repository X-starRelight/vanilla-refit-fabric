package com.tt23xrstudio.vanilla_refit_fabric.compat;

import com.tt23xrstudio.vanilla_refit_fabric.ModBlocks;
import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.block.entity.MixedSlabBlockEntity;
import com.tt23xrstudio.vanilla_refit_fabric.util.FluidHolder;
import com.tt23xrstudio.vanilla_refit_fabric.util.FluidType;
import com.tt23xrstudio.vanilla_refit_fabric.util.MixedSlabBreakHandler;
import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 机械动力「对称之杖」的兼容：让组合半砖被镜像过去时也带着两块材质。
 *
 * <p>组合半砖的两块材质存在方块实体里，而对称之杖走的是 {@code BlockState#mirror} +
 * {@code setBlockAndUpdate}：方块状态（MODE/TYPE）能镜像过去，但新格子的方块实体是新建的
 * 默认值，所以镜像出来的那一块看起来就是默认（石头+石头）材质的组合半砖。
 *
 * <p><b>放置侧</b>：机械动力的镜像钩子（飞跃版 ItemStackMixin）用的是
 * {@code BlockPlaceContext.getClickedPos()}——“直接点半砖朝空余面”时它指向的是旁边那格
 * <b>空气</b>（原版合并点击不算 canBeReplaced），机械动力根本看不到这次合并；“点相邻方块”
 * 时它能看到，创造模式会直接 {@code setBlockAndUpdate}（方块实体是新的，默认石头），
 * 生存模式却会因为背包里没有“组合半砖”物品而放弃。所以这里在 tick 末尾统一补位：
 * 机械动力已经落位的只补材质；没落位的（本模组自己把镜像组合半砖放下去，生存模式
 * 按机械动力的语义消耗一份新填的半砖，不白给材料）。
 *
 * <p><b>拆除侧</b>：本模组在 {@code destroyBlock} 头部接管了组合半砖的破坏并取消原逻辑，
 * 机械动力挂在 {@code destroyBlock} 内部的拆除钩子因此<b>永远不会触发</b>。于是接管成功后
 * 由本兼容直接把镜像格拆掉（掉落按材质在方块还在时记账，tick 末尾补）；半拆时把
 * “保留的那一半”在 tick 末尾重建回镜像格。
 *
 * <p>写入统一等本 tick 结束（{@code ServerTickEvents.END_LEVEL_TICK}）再落盘——
 * 机械动力自己也在处理镜像，等 tick 结束就跟双方的处理顺序无关了。
 *
 * <p>机械动力（飞跃版，Fabric 版的机械动力就是它）的包根是 {@code com.zurrtum}，
 * 镜像目标格的算法在 {@code SymmetryMirror#process(BlockPos, Pair)}——
 * 返回 {@code Pair<放置方向, 镜像状态>} 的表（原版 Create 那边是直接传 BlockState）。
 *
 * <p>这里全程反射调用，为的是不给本模组增加机械动力的编译依赖；机械动力不在、
 * 或哪天方法改名，这里会打一条警告日志后关闭，只影响这一条兼容，不影响其它功能。
 */
public final class CreateSymmetryCompat {

    private static final String CREATE_MOD_ID = "create";
    private static final String WAND_ITEM_ID = "create:wand_of_symmetry";
    private static final String WAND_CLASS =
            "com.zurrtum.create.content.equipment.symmetryWand.SymmetryWandItem";
    private static final String MIRROR_CLASS =
            "com.zurrtum.create.infrastructure.component.SymmetryMirror";
    /** 飞跃版 {@code process} 的第二参数类型：{@code Pair<放置方向, 镜像状态>}。 */
    private static final String PAIR_CLASS = "com.zurrtum.create.catnip.data.Pair";

    /** 反射一次就缓存；失败（机械动力改接口）后不再重试。 */
    private static boolean reflectionFailed;
    private static Method getMirrorMethod;
    private static Method isEnabledMethod;
    private static Method processMethod;
    private static Method pairOfMethod;
    private static Method pairSecondMethod;

    /** 待写入的材质搬运（本 tick 结束、镜像目标格确实落位后再写）。 */
    private static final ArrayDeque<Copy> PENDING = new ArrayDeque<>();

    /**
     * 一次镜像搬运：目标格 + 镜像后的组合半砖状态 + 两块材质（已按镜像换算决定是否对调）
     * + 本次合并新填的那块半砖（生存模式补位时按它消耗物品）+ 玩家。
     */
    private record Copy(Level level, BlockPos pos, BlockState mirroredState, Block first, Block second,
                        Block added, @Nullable Player player) {
    }

    /** 待补的掉落：机械动力的镜像拆除是先 setBlock(AIR) 再问掉落，那会儿方块实体已经没了。 */
    private static final ArrayDeque<Drop> PENDING_DROPS = new ArrayDeque<>();

    private record Drop(Level level, BlockPos pos, List<ItemStack> items) {
    }

    /** 半拆后要在镜像格重建的半砖（等 tick 结束、确认那边确实被拆掉再放回去）。 */
    private static final ArrayDeque<Rebuild> PENDING_REBUILDS = new ArrayDeque<>();

    private record Rebuild(Level level, BlockPos pos, BlockState state, ItemStack item, Block hitBlock) {
    }

    // 本次破坏的镜像上下文（服务端单线程，一次破坏内有效）
    private static Level breakLevel;
    private static BlockPos breakSourcePos;
    private static Object breakMirror;
    private static boolean breakIsHalf;
    private static List<BlockPos> breakTargets;
    private static List<List<ItemStack>> breakTargetItems;

    private CreateSymmetryCompat() {
    }

    /** 注册 tick 末尾的延迟写入钩子（主类 onInitialize 里调一次）。 */
    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (!PENDING.isEmpty()) {
                applyPending(level);
            }
            if (!PENDING_DROPS.isEmpty()) {
                applyPendingDrops(level);
            }
            if (!PENDING_REBUILDS.isEmpty()) {
                applyPendingRebuilds(level);
            }
        });
    }

    /**
     * 本模组合并出组合半砖后调用（BlockItemMixin 混合填充成功处）：
     * 若玩家拿着对称之杖，就把两块材质登记到所有镜像目标格，tick 末尾写入。
     *
     * @param sourcePos 实际填出组合半砖的那一格（情形 B 下是相邻格，不是点击格）
     * @param addedSlab 本次合并新填进去的那块半砖（生存模式补位镜像时按它扣物品）
     */
    public static void onMergedSlabPlaced(Level level, BlockPos sourcePos, Block addedSlab, Player player) {
        if (reflectionFailed || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(CREATE_MOD_ID)) {
            return;
        }
        if (level.isClientSide() || player == null) {
            return;
        }
        BlockState sourceState = level.getBlockState(sourcePos);
        if (sourceState.getBlock() != ModBlocks.MERGED_SLAB) {
            return;   // 只有“刚合并出来的组合半砖”需要搬材质
        }
        if (!(level.getBlockEntity(sourcePos) instanceof MixedSlabBlockEntity source) || isUnset(source)) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand == null || !prepareReflection()) {
            return;
        }
        if (!invokeIsEnabled(wand)) {
            return;   // 杖被关掉了（机械动力自己这时也不镜像）
        }
        Object mirror = invokeGetMirror(wand);
        if (mirror == null) {
            return;
        }
        Map<BlockPos, BlockState> targets = invokeProcess(mirror, sourcePos, sourceState);
        if (targets == null) {
            return;
        }

        Direction sourceSide = firstHalfSide(sourceState);
        for (Map.Entry<BlockPos, BlockState> entry : targets.entrySet()) {
            BlockPos targetPos = entry.getKey();
            if (targetPos.equals(sourcePos)) {
                continue;   // 对称面正好穿过自己，机械动力也会跳过
            }
            // A 永远代表“下/北/西”那半块（见渲染模型）。镜像后这半块的几何位置可能被换到
            // 另一侧，那就得把两块材质对调，否则上下（南北/东西）会反。
            boolean swap = mirroredFirstHalfSide(mirror, sourcePos, targetPos, sourceSide)
                    != firstHalfSide(entry.getValue());
            PENDING.add(new Copy(level, targetPos.immutable(), entry.getValue(),
                    swap ? source.getSecondSlab() : source.getFirstSlab(),
                    swap ? source.getFirstSlab() : source.getSecondSlab(),
                    addedSlab, player));
        }
    }

    /**
     * 等这一 tick 结束时再写：这时机械动力的镜像方块无论先后都已经落位。
     *
     * <p>机械动力已经把镜像格变成组合半砖的（点相邻方块 + 创造），只补材质；
     * 没落位的（直接点半砖朝空余面——它的钩子拿到的是旁边空气格；以及生存模式
     * 因为背包里没有“组合半砖”物品而放弃的），由本模组把镜像组合半砖放下去。
     */
    private static void applyPending(Level tickedLevel) {
        for (Iterator<Copy> it = PENDING.iterator(); it.hasNext(); ) {
            Copy copy = it.next();
            if (copy.level() != tickedLevel) {
                continue;
            }
            it.remove();
            Level level = copy.level();
            BlockPos pos = copy.pos();
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() == ModBlocks.MERGED_SLAB) {
                // 机械动力已落位：只补材质，已经有材质的不覆盖
                if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity target && isUnset(target)) {
                    target.setBlocks(copy.first(), copy.second());
                }
                continue;
            }
            if (!canMirrorInto(current, copy)) {
                continue;   // 被别的方块挡住了：不动它（与机械动力的 isUnobstructed 一致）
            }
            // 生存模式：机械动力的生存镜像要从背包找“组合半砖”物品（正常玩家不会有），
            // 永远失败。这里替它补位，但按它的语义先扣一份新填的那块半砖——找不到就不镜像，
            // 不给白拿材料的口子。
            if (copy.player() != null && !copy.player().getAbilities().instabuild
                    && !consumeOne(copy.player(), copy.added())) {
                continue;
            }
            if (!level.setBlock(pos, copy.mirroredState(), 3)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity target) {
                target.setBlocks(copy.first(), copy.second());
            }
            level.playSound(null, pos, copy.mirroredState().getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * 镜像格能不能落位：空位/可替换方块直接放；
     * 标准流程里镜像格还留着先前镜像过去的“第一块半砖”（半砖不可被原版替换），
     * 这时要用组合半砖把它覆盖掉——但只覆盖材质匹配的半砖，
     * 避免吃掉玩家在镜像格里搭的无关方块。
     */
    private static boolean canMirrorInto(BlockState current, Copy copy) {
        if (current.isAir() || current.canBeReplaced()) {
            return true;
        }
        return current.getBlock() instanceof SlabBlock
                && current.hasProperty(ModBlockStateProperties.MODE)
                && current.hasProperty(SlabBlock.TYPE)
                && current.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                && (current.getBlock() == copy.first() || current.getBlock() == copy.second());
    }

    /** 从玩家背包（含快捷栏）扣掉一份指定半砖物品；没有则不动。 */
    private static boolean consumeOne(Player player, Block block) {
        var inventory = player.getInventory();
        for (int i = 0, size = inventory.getContainerSize(); i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(block.asItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** 半拆的镜像格：确认那边确实被拆掉了，就把“保留的那一半”放回去，并只掉落被拆的那半块。 */
    private static void applyPendingRebuilds(Level tickedLevel) {
        for (Iterator<Rebuild> it = PENDING_REBUILDS.iterator(); it.hasNext(); ) {
            Rebuild rebuild = it.next();
            if (rebuild.level() != tickedLevel) {
                continue;
            }
            it.remove();
            if (!rebuild.level().getBlockState(rebuild.pos()).isAir()) {
                continue;   // 那边没被拆掉（没联动上），不能动它
            }
            rebuild.level().setBlock(rebuild.pos(), rebuild.state(), 3);
            rebuild.level().playSound(null, rebuild.pos(),
                    rebuild.hitBlock().defaultBlockState().getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!rebuild.item().isEmpty()) {
                Block.popResource(rebuild.level(), rebuild.pos(), rebuild.item());
            }
        }
    }

    private static void applyPendingDrops(Level tickedLevel) {
        for (Iterator<Drop> it = PENDING_DROPS.iterator(); it.hasNext(); ) {
            Drop drop = it.next();
            if (drop.level() != tickedLevel) {
                continue;
            }
            it.remove();
            if (!drop.level().getBlockState(drop.pos()).isAir()) {
                continue;   // 镜像那边没被拆掉（没联动上），不能凭空给掉落
            }
            for (ItemStack item : drop.items()) {
                Block.popResource(drop.level(), drop.pos(), item);
            }
        }
    }

    // ===== 破坏侧的镜像处理 =====

    /**
     * 破坏接管前调用（此刻源格与镜像格的方块都还在）：记下这次破坏会波及的镜像格。
     *
     * <p>本模组接管组合半砖破坏时取消了原版 {@code destroyBlock}，机械动力挂在里面的
     * 拆除钩子不会触发——镜像格由本兼容自己拆（整拆在 {@link #finishWholeBreak}、
     * 半拆在 {@link #finishHalfBreak}）。拆除时方块实体已经没了，问不到组合半砖的两块
     * 材质，所以在方块还在的时候先把镜像格和它们的材质记下来，tick 末尾再补掉落 /
     * 把“少掉的那一半”补回原样。
     */
    public static void beginBreak(Level level, Player player, BlockPos pos, BlockState state, boolean halfBreak) {
        clearBreakContext();
        if (reflectionFailed || level.isClientSide()
                || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(CREATE_MOD_ID)) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand == null || !prepareReflection()) {
            return;
        }
        if (!invokeIsEnabled(wand)) {
            return;   // 杖被关掉了（机械动力自己这时也不镜像）
        }
        Object mirror = invokeGetMirror(wand);
        if (mirror == null) {
            return;
        }
        Map<BlockPos, BlockState> mirrored = invokeProcess(mirror, pos, state);
        if (mirrored == null) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        boolean creative = player.getAbilities().instabuild;
        List<BlockPos> targets = new ArrayList<>(mirrored.size());
        List<List<ItemStack>> items = new ArrayList<>(mirrored.size());
        for (BlockPos targetPos : mirrored.keySet()) {
            if (targetPos.equals(pos)) {
                continue;
            }
            BlockState targetState = level.getBlockState(targetPos);
            if (!isMirrorCounterpart(targetState, state)) {
                continue;   // 那边不是对应的堆叠半砖，交给机械动力按原样处理
            }
            targets.add(targetPos.immutable());
            items.add(creative ? List.of() : collectSlabItems(level, targetPos, tool));
        }
        if (targets.isEmpty()) {
            return;
        }
        breakLevel = level;
        breakSourcePos = pos.immutable();
        breakMirror = mirror;
        breakIsHalf = halfBreak;
        breakTargets = targets;
        breakTargetItems = items;
        if (!halfBreak) {
            // 整块拆除：镜像那边机械动力会把方块去掉但掉不出东西，这里替它补掉落
            for (int i = 0; i < targets.size(); i++) {
                if (!items.get(i).isEmpty()) {
                    PENDING_DROPS.add(new Drop(level, targets.get(i), items.get(i)));
                }
            }
        }
    }

    /**
     * 半拆完成后调用（MixedSlabBreakHandler#applyBreak 里）：
     * 让镜像格也只少掉对应的那一半（保留另一半）。
     *
     * <p>做法是先把镜像格整个拆掉（机械动力的拆除钩子已被我们取消，得自己拆），
     * tick 末尾再把“保留的那一半”放回去，并只掉落被拆掉的那半块。
     */
    public static void finishHalfBreak(Level level, Player player, BlockPos pos,
                                       BlockState keepState, Block hitBlock) {
        if (breakTargets == null || breakLevel != level || !breakIsHalf
                || breakSourcePos == null || !breakSourcePos.equals(pos)) {
            clearBreakContext();
            return;
        }
        List<BlockPos> targets = breakTargets;
        Object mirror = breakMirror;
        clearBreakContext();
        if (mirror == null) {
            return;
        }
        // 用原版发射器的六向 facing 当探针，问机械动力“保留的这半块被镜像到了哪一侧”
        Direction keepSide = occupiedSide(keepState);
        BlockState probe = Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, keepSide);
        Map<BlockPos, BlockState> probeMirror = invokeProcess(mirror, pos, probe);
        if (probeMirror == null) {
            return;
        }
        Block keepBlock = keepState.getBlock();
        ItemStack removed = player.getAbilities().instabuild
                || !MixedSlabBreakHandler.canHarvestLoose(player, hitBlock)
                ? ItemStack.EMPTY
                : new ItemStack(hitBlock);
        for (BlockPos targetPos : targets) {
            BlockState mapped = probeMirror.get(targetPos);
            Direction side = mapped != null && mapped.hasProperty(BlockStateProperties.FACING)
                    ? mapped.getValue(BlockStateProperties.FACING)
                    : keepSide;
            PENDING_REBUILDS.add(new Rebuild(level, targetPos, halfStateOf(keepBlock, side), removed, hitBlock));
        }
        // 机械动力的拆除钩子在被我们取消的 destroyBlock 里，永远不会触发；由我们直接把
        // 镜像格拆掉——tick 末尾的重建（上面，门卫是 isAir）才会把“保留的那一半”放回去。
        removeMirrorTargets(level, player, targets);
    }

    /**
     * 整块拆除接管成功后调用（接管了组合半砖的破坏、原版 destroyBlock 被取消）：
     * 机械动力挂在 destroyBlock 里的拆除钩子不会触发，这里直接把镜像格拆掉；
     * 掉落已在 {@link #beginBreak} 里按材质记账，tick 末尾镜像格变空气后补出。
     */
    public static void finishWholeBreak(Level level, Player player) {
        if (breakTargets != null && breakLevel == level && !breakIsHalf) {
            removeMirrorTargets(level, player, breakTargets);
        }
        clearBreakContext();
    }

    /** 把记下的镜像格拆掉（破坏粒子 + 置空气），与机械动力 SymmetryWandItem#remove 的效果一致。 */
    private static void removeMirrorTargets(Level level, @Nullable Player player, List<BlockPos> targets) {
        for (BlockPos targetPos : targets) {
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }
            level.levelEvent(player, net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK,
                    targetPos, Block.getId(targetState));
            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** 本次破坏结束（没接管成、事件被取消等）：清理上下文；半拆没走完就退回整块掉落，别把材料吞了。 */
    public static void endBreak() {
        if (breakTargets != null && breakIsHalf && breakTargetItems != null) {
            for (int i = 0; i < breakTargets.size(); i++) {
                if (!breakTargetItems.get(i).isEmpty()) {
                    PENDING_DROPS.add(new Drop(breakLevel, breakTargets.get(i), breakTargetItems.get(i)));
                }
            }
        }
        clearBreakContext();
    }

    private static void clearBreakContext() {
        breakLevel = null;
        breakSourcePos = null;
        breakMirror = null;
        breakIsHalf = false;
        breakTargets = null;
        breakTargetItems = null;
    }

    /** 镜像格是否就是“对应的堆叠半砖”：组合半砖对组合半砖，原版堆叠半砖对同方块的 DOUBLE。 */
    private static boolean isMirrorCounterpart(BlockState targetState, BlockState sourceState) {
        if (sourceState.getBlock() == ModBlocks.MERGED_SLAB) {
            return targetState.getBlock() == ModBlocks.MERGED_SLAB;
        }
        return targetState.getBlock() == sourceState.getBlock()
                && targetState.hasProperty(SlabBlock.TYPE)
                && targetState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    /** 取某个组合半砖格两块材质的掉落物（按宽松工具规则）。 */
    private static List<ItemStack> collectSlabItems(Level level, BlockPos pos, ItemStack tool) {
        if (!(level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed)) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>(2);
        for (Block slab : List.of(mixed.getFirstSlab(), mixed.getSecondSlab())) {
            if (MixedSlabBreakHandler.canHarvestLoose(tool, slab)) {
                items.add(new ItemStack(slab));
            }
        }
        return items;
    }

    // ===== 组合半砖的“第一块（A）”在哪一侧 =====

    /** A 所在的半边方向：平放=下半、竖放南北=北半、竖放东西=西半（与渲染模型一致）。 */
    private static Direction firstHalfSide(BlockState state) {
        VerticalSlabMode mode = state.hasProperty(ModBlockStateProperties.MODE)
                ? state.getValue(ModBlockStateProperties.MODE)
                : VerticalSlabMode.SLAB;
        return switch (mode) {
            case VERTICAL_NS -> Direction.NORTH;
            case VERTICAL_EW -> Direction.WEST;
            default -> Direction.DOWN;
        };
    }

    /**
     * 用探针方块测出“源格的 A 侧”被这次镜像搬到了哪个方向。
     *
     * <p>机械动力的镜像作用在方块状态上，具体用哪种翻转（东西/南北/上下/对角）它自己知道，
     * 与其去猜，不如拿一个带六向 facing 的原版方块再问它一次：同一个镜面、同一个源格，
     * 目标格的 key 是一致的，把 facing 设成 A 侧方向，读回来的就是映射后的方向。
     * 探针取原版发射器（facing 六向，且原版实现了 rotate/mirror）。
     */
    private static Direction mirroredFirstHalfSide(Object mirror, BlockPos sourcePos,
                                                   BlockPos targetPos, Direction sourceSide) {
        BlockState probe = Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, sourceSide);
        Map<BlockPos, BlockState> probeTargets = invokeProcess(mirror, sourcePos, probe);
        if (probeTargets == null) {
            return sourceSide;
        }
        BlockState mapped = probeTargets.get(targetPos);
        return mapped != null && mapped.hasProperty(BlockStateProperties.FACING)
                ? mapped.getValue(BlockStateProperties.FACING)
                : sourceSide;
    }

    private static boolean isUnset(MixedSlabBlockEntity entity) {
        return entity.getFirstSlab() == Blocks.STONE_SLAB && entity.getSecondSlab() == Blocks.STONE_SLAB;
    }

    // ===== 单半砖状态 ↔ “占住哪一侧” =====

    /** 单半砖（普通半砖状态）占住的那一侧：平放=下/上，竖放南北=北/南，竖放东西=西/东。 */
    private static Direction occupiedSide(BlockState state) {
        VerticalSlabMode mode = state.hasProperty(ModBlockStateProperties.MODE)
                ? state.getValue(ModBlockStateProperties.MODE)
                : VerticalSlabMode.SLAB;
        boolean first = !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.TOP;
        return switch (mode) {
            case VERTICAL_NS -> first ? Direction.NORTH : Direction.SOUTH;
            case VERTICAL_EW -> first ? Direction.WEST : Direction.EAST;
            default -> first ? Direction.DOWN : Direction.UP;
        };
    }

    /** 反向：让某个半砖方块占住指定的一侧（含液体状态清空，与合并/半拆的规则一致）。 */
    private static BlockState halfStateOf(Block block, Direction side) {
        VerticalSlabMode mode = switch (side.getAxis()) {
            case Y -> VerticalSlabMode.SLAB;
            case Z -> VerticalSlabMode.VERTICAL_NS;
            default -> VerticalSlabMode.VERTICAL_EW;
        };
        boolean first = side == Direction.DOWN || side == Direction.NORTH || side == Direction.WEST;
        BlockState base = block.defaultBlockState();
        if (!base.hasProperty(ModBlockStateProperties.MODE) || !base.hasProperty(SlabBlock.TYPE)) {
            return base;   // 兜底：没有本模组半砖属性的方块就不硬套（正常不会走到）
        }
        BlockState state = base
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, first ? SlabType.BOTTOM : SlabType.TOP);
        return FluidHolder.with(state, FluidType.NONE);
    }

    // ===== 机械动力那边的取用（全程反射，机械动力不在时整条逻辑不生效） =====

    @Nullable
    private static ItemStack findWand(Player player) {
        // 与机械动力一致：只认快捷栏（它自己的 onBlockPlaced/onBlockDestroyed 也只扫选择栏）。
        // 杖放在背包深处时它不镜像，我们也不能单独镜像，否则双方行为分叉。
        var inventory = player.getInventory();
        for (int i = 0, size = net.minecraft.world.entity.player.Inventory.getSelectionSize(); i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()
                    && WAND_ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return stack;
            }
        }
        return null;
    }

    private static boolean prepareReflection() {
        if (getMirrorMethod != null && processMethod != null) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            Class<?> wandClass = Class.forName(WAND_CLASS);
            Class<?> mirrorClass = Class.forName(MIRROR_CLASS);
            Class<?> pairClass = Class.forName(PAIR_CLASS);
            getMirrorMethod = wandClass.getMethod("getMirror", ItemStack.class);
            isEnabledMethod = wandClass.getMethod("isEnabled", ItemStack.class);
            processMethod = mirrorClass.getMethod("process", BlockPos.class, pairClass);
            pairOfMethod = pairClass.getMethod("of", Object.class, Object.class);
            pairSecondMethod = pairClass.getMethod("getSecond");
            VanillaRefitFabric.LOGGER.info("[原版精修] 对称之杖兼容已接入机械动力（飞跃版）");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disableReflection("接入飞跃版接口", e);
            return false;
        }
    }

    /** 反射失败：只记一次日志并永久关闭本兼容。 */
    private static void disableReflection(String where, Exception e) {
        if (!reflectionFailed) {
            reflectionFailed = true;
            VanillaRefitFabric.LOGGER.warn(
                    "[原版精修] 对称之杖兼容反射失败（{}），本条兼容已关闭：{}", where, e.toString());
        }
    }

    @Nullable
    private static Object invokeGetMirror(ItemStack wand) {
        try {
            return getMirrorMethod.invoke(null, wand);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disableReflection("调用 getMirror", e);
            return null;
        }
    }

    /** 杖是否处于启用状态（机械动力允许“存着镜面但关掉”，关掉时它不镜像，我们也不能）。 */
    private static boolean invokeIsEnabled(ItemStack wand) {
        try {
            return Boolean.TRUE.equals(isEnabledMethod.invoke(null, wand));
        } catch (ReflectiveOperationException | RuntimeException e) {
            disableReflection("调用 isEnabled", e);
            return false;
        }
    }

    /**
     * 飞跃版入口：{@code process(BlockPos, Pair)} → {@code Map<BlockPos, Pair>}。
     * Pair 的方向位对我们无意义（探针要回读的是镜像后的<b>状态</b>），随便塞个方向；
     * 出参把每格的 {@code pair.getSecond()}（镜像后的状态）解出来，调用方按老接口用。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static Map<BlockPos, BlockState> invokeProcess(Object mirror, BlockPos pos, BlockState state) {
        try {
            Object pair = pairOfMethod.invoke(null, Direction.DOWN, state);
            Map<?, ?> raw = (Map<?, ?>) processMethod.invoke(mirror, pos, pair);
            if (raw == null) {
                return null;
            }
            Map<BlockPos, BlockState> out = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                out.put((BlockPos) entry.getKey(),
                        (BlockState) pairSecondMethod.invoke(entry.getValue()));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disableReflection("调用 process", e);
            return null;
        }
    }
}
