package com.tt23xrstudio.vanilla_refit_fabric.debug;

import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * 临时内存测量工具的客户端半边（测完即删，不随版本发布）。
 *
 * <p>到主菜单后等 {@value #SETTLE_TICKS} tick，连做几次 GC，然后把“GC 后堆占用 +
 * 五类方块的状态数统计 + 类直方图”写进 {@code run/btsdhz-memprobe-ready.txt}（直方图另存
 * {@code btsdhz-memprobe-histogram.txt}）。直方图走进程内的 DiagnosticCommandMBean
 * （{@code jcmd GC.class_histogram} 的等价物），不需要外部 attach——本机内存紧张时
 * attach 经常直接失败，进程内调用不受影响。</p>
 */
public final class MemProbeClient {

    /** 到主菜单后再等这么久（tick），确保资源重载、模型烘焙都结束了。 */
    private static final int SETTLE_TICKS = 100;

    private static int ticks;

    /** 本会话进过世界（sawWorld）后不再出报告，防止“退出世界回到标题界面”时测到脏数据。 */
    private static boolean sawWorld;

    private MemProbeClient() {
    }

    /** 注册客户端 tick 监听（由 {@code VanillaRefitFabricClient} 调用）。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MemProbeClient::onClientTick);
    }

    public static void onClientTick(Minecraft minecraft) {
        if (!MemProbe.active() || MemProbe.alreadyReported()) {
            return;
        }
        if (minecraft.level != null) {
            // 进过世界就作废这次测量：退出世界后的标题界面堆里残留世界数据，
            // 已经不是干净的标题界面基线（上一轮就是这样被污染的）。
            sawWorld = true;
            ticks = 0;
            return;
        }
        if (sawWorld || minecraft.gui.overlay() != null
                || !(minecraft.gui.screen() instanceof TitleScreen)) {
            ticks = 0;
            return;
        }
        if (++ticks < SETTLE_TICKS) {
            return;
        }
        report();
    }

    private static void report() {
        MemProbe.markReported();

        List<Long> heaps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            System.gc();
            sleep(400);
            Runtime runtime = Runtime.getRuntime();
            heaps.add(runtime.totalMemory() - runtime.freeMemory());
        }

        StringBuilder out = new StringBuilder();
        out.append("off=").append(MemProbe.offList()).append('\n');
        out.append("pid=").append(ProcessHandle.current().pid()).append('\n');
        out.append("usedHeapAfterGc=").append(String.join(",", heaps.stream().map(String::valueOf).toList()))
                .append('\n');

        Map<MemProbe.Cat, int[]> stats = new EnumMap<>(MemProbe.Cat.class);
        for (MemProbe.Cat cat : MemProbe.Cat.values()) {
            stats.put(cat, new int[]{0, 0, 0, 0});   // 方块数、状态数、带属性方块数、带属性状态数
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            MemProbe.Cat cat = categoryOf(block);
            if (cat == null) {
                continue;
            }
            int states = block.getStateDefinition().getPossibleStates().size();
            boolean injected = hasInjectedProperty(block, cat);
            int[] row = stats.get(cat);
            row[0]++;
            row[1] += states;
            if (injected) {
                row[2]++;
                row[3] += states;
            }
        }

        for (MemProbe.Cat cat : MemProbe.Cat.values()) {
            int[] row = stats.get(cat);
            out.append(cat.name()).append(" blocks=").append(row[0])
                    .append(" states=").append(row[1])
                    .append(" injectedBlocks=").append(row[2])
                    .append(" injectedStates=").append(row[3])
                    .append('\n');
        }

        // 逐方块明细：方块 id 与状态数，方便核对“哪个方块吃了多少状态”
        for (Block block : BuiltInRegistries.BLOCK) {
            MemProbe.Cat cat = categoryOf(block);
            if (cat == null) {
                continue;
            }
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            out.append("block=").append(id).append(' ').append(cat.name())
                    .append(" states=").append(block.getStateDefinition().getPossibleStates().size())
                    .append(" injected=").append(hasInjectedProperty(block, cat))
                    .append('\n');
        }

        try {
            // 类直方图：jcmd GC.class_histogram 的进程内版本。会强制一次 full GC，
            // 放在堆数字记录之后、ready 文件写出之前（ready = 全部完成的信号）。
            Path histogramPath = MemProbe.readyPath().resolveSibling("btsdhz-memprobe-histogram.txt");
            Files.writeString(histogramPath, classHistogram(), StandardCharsets.UTF_8);
            out.append("histogramFile=").append(histogramPath.getFileName()).append('\n');

            Files.writeString(MemProbe.readyPath(), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 进程内执行 {@code GC.class_histogram}（live=true）并返回文本。 */
    private static String classHistogram() {
        MBeanServer server;
        ObjectName name;
        try {
            server = ManagementFactory.getPlatformMBeanServer();
            name = new ObjectName("com.sun.management:type=DiagnosticCommand");
        } catch (Exception e) {
            return "<获取 DiagnosticCommandMBean 失败: " + e + ">";
        }
        try {
            for (MBeanOperationInfo op : server.getMBeanInfo(name).getOperations()) {
                if (!op.getName().toLowerCase(Locale.ROOT).contains("classhistogram")) {
                    continue;
                }
                // JDK 各版本对参数的暴露/接受程度不一致（25 上 boolean live 会被拒绝：
                // "Unknown argument 'true'"）。依次尝试三种组参方式，全失败则如实报告。
                MBeanParameterInfo[] sig = op.getSignature();
                if (sig.length == 1 && "boolean".equals(sig[0].getType())) {
                    try {
                        return String.valueOf(server.invoke(name, op.getName(),
                                new Object[]{Boolean.TRUE}, new String[]{"boolean"}));
                    } catch (Exception ignored) {
                        // 继续尝试下面的签名
                    }
                }
                if (sig.length == 1 && "[Ljava.lang.String;".equals(sig[0].getType())) {
                    try {
                        return String.valueOf(server.invoke(name, op.getName(),
                                new Object[]{new String[]{"true"}}, new String[]{"[Ljava.lang.String;"}));
                    } catch (Exception ignored) {
                        // 继续尝试空参
                    }
                }
                return String.valueOf(server.invoke(name, op.getName(), new Object[0], new String[0]));
            }
            return "<DiagnosticCommandMBean 没有 classHistogram 操作>";
        } catch (Exception e) {
            // 进程内这条路在个别 JDK 上走不通；外部脚本 jcmd GC.class_histogram 是后备（一直可用）。
            return "<class histogram 调用失败: " + e
                    + (e.getCause() != null ? " cause=" + e.getCause() : "") + ">";
        }
    }

    private static MemProbe.Cat categoryOf(Block block) {
        if (block instanceof SlabBlock) {
            return MemProbe.Cat.SLAB;
        }
        if (block instanceof StairBlock) {
            return MemProbe.Cat.STAIR;
        }
        if (block instanceof IronBarsBlock) {
            return MemProbe.Cat.PANE;
        }
        if (block instanceof WallBlock) {
            return MemProbe.Cat.WALL;
        }
        if (block instanceof FenceBlock) {
            return MemProbe.Cat.FENCE;
        }
        return null;
    }

    private static boolean hasInjectedProperty(Block block, MemProbe.Cat cat) {
        return switch (cat) {
            case SLAB -> block.defaultBlockState().hasProperty(ModBlockStateProperties.MODE);
            case STAIR -> block.defaultBlockState().hasProperty(ModBlockStateProperties.VERTICAL);
            case PANE -> block.defaultBlockState().hasProperty(ModBlockStateProperties.PANE_NORTH_EAST);
            case WALL, FENCE -> block.defaultBlockState().hasProperty(ModBlockStateProperties.SLAB_OFFSET);
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
