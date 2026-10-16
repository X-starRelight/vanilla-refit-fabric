package com.tt23xrstudio.vanilla_refit_fabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * 本模组配置文件（Fabric 版：自写 Gson JSON，零额外依赖）。
 *
 * <p>位置：{@code config/vanilla_refit_fabric.json}，可直接用文本编辑器修改。
 * 键名与原 NeoForge 版的 TOML 完全一致，便于对照迁移。
 *
 * <p>与原版的行为差异：原版依赖 NeoForge 的 ModConfigSpec（TOML），这里改为手写 JSON。
 * 读取时按文件修改时间自动热重载（最多 1 秒检查一次磁盘），改完保存后无需重启即生效；
 * 文件写坏时保留上一次生效的配置并打印错误，<b>不会</b>覆盖玩家写坏的文件。
 *
 * <p>配置项说明（原 TOML 注释随 JSON 丢失，完整文档见 README）：
 *
 * <ul>
 *   <li>{@code allowTorchLanternOnSlab}：火把/灵魂火把/灯笼/灵魂灯笼放在下台阶上时，模型与碰撞箱下移半格贴合台阶面（默认 true）</li>
 *   <li>{@code allowLanternUnderTopSlab}：灯笼悬挂于上台阶下方时上移半格贴合上台阶底面（默认 true）</li>
 *   <li>{@code slabCenterSquareRatio}：默认档中央正方形边长占比（0~1，默认 0.5，台阶与楼梯共用）</li>
 *   <li>{@code allowModdedSlabs}：其它模组的台阶是否也能竖放（实验性，默认 true）</li>
 *   <li>{@code allowModdedStairs}：其它模组的楼梯是否也能竖放（实验性，默认 true）</li>
 *   <li>{@code blocklist}：方块名单，决定哪些方块允许加竖形态（见 {@link Blocklist}）</li>
 * </ul>
 */
public class BtsdhzConfig {

    /** 配置文件名（位于 config/ 目录下）。 */
    public static final String FILE_NAME = "vanilla_refit_fabric.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 文件修改时间的检查节流间隔（毫秒）。 */
    private static final long RELOAD_CHECK_INTERVAL_MS = 1000L;

    /**
     * 开关：火把/灯笼等下台阶贴合功能。
     *
     * <p>开启时，普通火把、灵魂火把、灯笼、灵魂灯笼放置在“下台阶”（下半台阶）上时，
     * 其模型与碰撞箱会整体下移半格，使它们与下台阶的上表面完全贴合，不再悬空。
     * 关闭时则使用原版行为（竖放在台阶上方，略微悬空）。
     */
    public boolean allowTorchLanternOnSlab = true;

    /**
     * 开关：灯笼悬挂“上台阶”下功能。
     *
     * <p>开启时，普通灯笼、灵魂灯笼（悬挂形态）放置于“上台阶”（上半台阶）下方时，
     * 其模型与碰撞箱会整体上移半格，灯笼悬挂贴合上台阶的底面。关闭时按原版行为处理。
     */
    public boolean allowLanternUnderTopSlab = true;

    /**
     * 默认档“中央正方形”大小（0~1，0 表示取消该正方形）。
     *
     * <p>默认档在对角线/十字分割基础上，把方块面中央抠出一个正方形：点击正方形内按原版平放，
     * 正方形之外竖放（台阶与楼梯共用此比例）。读取时会被钳制到 [0, 1]。
     */
    public double slabCenterSquareRatio = 0.5D;

    /**
     * 实验开关：是否让其它模组的台阶也能竖放（默认开启）。
     *
     * <p>开启后客户端把该台阶自己的模型旋转成竖形态来渲染，因此任何继承原版 SlabBlock
     * 的台阶都能竖放；关掉后只有原版台阶有竖形态。楼梯不在此列。
     */
    public boolean allowModdedSlabs = true;

    /**
     * 实验开关：是否让其它模组的楼梯也能竖放（默认开启）。
     *
     * <p>开启后客户端用本模组的模板几何配上对方楼梯的贴图渲染竖形态，连接形态全部支持。
     */
    public boolean allowModdedStairs = true;

    /**
     * 方块名单：哪些方块允许本模组给它加竖形态。
     *
     * <p>每行一条，从上往下匹配，第一条命中的生效；一条都没命中时默认允许。
     * 支持的写法：
     *
     * <pre>
     *   +*                        全部允许（默认，通常放第一条当兜底）
     *   -*                        全部禁止
     *   +minecraft:oak_slab       按方块 ID
     *   +somemod:*                按模组（命名空间）
     *   +#minecraft:wooden_slabs  按方块标签
     * </pre>
     *
     * <p>注意：方块状态是注册期注入的，名单不会减少状态数，它只决定放置时会不会生成竖形态。
     * 游戏里可以用 {@code /vanilla_refit_fabric blocklist} 查看当前名单和生效统计。
     */
    public List<String> blocklist = new ArrayList<>(List.of("+*"));

    private static volatile BtsdhzConfig instance;
    private static volatile long loadedMtime = Long.MIN_VALUE;
    private static volatile long lastMtimeCheck;

    /** 仅限本包（测试）直接构造默认配置。 */
    BtsdhzConfig() {
    }

    /** 配置文件路径。 */
    public static Path file() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir == null ? null : configDir.resolve(FILE_NAME);
    }

    /**
     * 当前生效的配置。
     *
     * <p>首次访问时自动加载（文件不存在则写出默认值）；此后按文件修改时间自动重载
     * （节流：最多 1 秒检查一次磁盘），改完配置保存后无需重启即生效。
     */
    public static synchronized BtsdhzConfig get() {
        BtsdhzConfig cfg = instance;
        long now = System.currentTimeMillis();
        if (cfg == null) {
            return loadLocked(file(), true);
        }
        if (now - lastMtimeCheck >= RELOAD_CHECK_INTERVAL_MS) {
            lastMtimeCheck = now;
            Path file = file();
            if (file != null && Files.isRegularFile(file)) {
                try {
                    long mtime = Files.getLastModifiedTime(file).toMillis();
                    if (mtime != loadedMtime) {
                        return loadLocked(file, false);
                    }
                } catch (IOException cannotStat) {
                    // 读不到修改时间就算了，保持当前配置
                }
            }
        }
        return cfg;
    }

    /**
     * 显式加载（模组初始化、命令重载时调用）。文件不存在则写出默认配置。
     *
     * @return 生效的配置
     */
    public static synchronized BtsdhzConfig load() {
        lastMtimeCheck = System.currentTimeMillis();
        return loadLocked(file(), true);
    }

    /**
     * 从指定路径加载并设为当前配置（测试用；文件不存在时用默认值，且不写出文件）。
     *
     * @param file 配置文件路径，可为 {@code null}（等价于直接用默认值）
     * @return 生效的配置
     */
    static synchronized BtsdhzConfig loadFrom(Path file) {
        lastMtimeCheck = System.currentTimeMillis();
        return loadLocked(file, false);
    }

    /** 直接注入当前配置（测试用，绕过文件系统）。 */
    static synchronized void setForTest(BtsdhzConfig cfg) {
        instance = cfg != null ? cfg : new BtsdhzConfig();
        loadedMtime = Long.MIN_VALUE;
        lastMtimeCheck = System.currentTimeMillis();
    }

    /**
     * 读取磁盘上的配置文件并设为当前实例。
     *
     * @param file            文件路径；{@code null} 或不存在时：{@code writeIfMissing} 为 true 则写出默认值，否则沿用已有配置/默认值
     * @param writeIfMissing  文件不存在时是否写出默认配置
     */
    private static BtsdhzConfig loadLocked(Path file, boolean writeIfMissing) {
        if (file != null && Files.isRegularFile(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                BtsdhzConfig parsed = GSON.fromJson(json, BtsdhzConfig.class);
                if (parsed == null) {
                    throw new IllegalStateException("配置文件内容为空");
                }
                parsed.sanitize();
                return set(parsed, statMtime(file));
            } catch (Exception parseFailed) {
                // 解析失败：沿用当前配置（没有就用默认值），并把文件的当前 mtime 记下，
                // 避免接下来每秒重复报同一条错；绝不覆盖玩家写坏的文件。
                LOGGER.error("[原版精修/配置] {} 解析失败，本次沿用当前配置（该文件不会被覆盖）", file, parseFailed);
                BtsdhzConfig fallback = instance != null ? instance : new BtsdhzConfig();
                return set(fallback, statMtime(file));
            }
        }
        BtsdhzConfig fallback = instance != null ? instance : new BtsdhzConfig();
        if (writeIfMissing && file != null) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(fallback) + System.lineSeparator(), StandardCharsets.UTF_8);
                return set(fallback, statMtime(file));
            } catch (IOException writeFailed) {
                LOGGER.error("[原版精修/配置] 默认配置写入失败：{}", file, writeFailed);
            }
        }
        return set(fallback, Long.MIN_VALUE);
    }

    private static BtsdhzConfig set(BtsdhzConfig cfg, long mtime) {
        instance = cfg;
        loadedMtime = mtime;
        return cfg;
    }

    private static long statMtime(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException cannotStat) {
            return Long.MIN_VALUE;
        }
    }

    /** 纠正越界/缺失字段，保证配置始终处于合法状态。 */
    private void sanitize() {
        if (blocklist == null || blocklist.isEmpty()) {
            blocklist = new ArrayList<>(List.of("+*"));
        } else {
            blocklist.removeIf(entry -> entry == null);
            if (blocklist.isEmpty()) {
                blocklist.add("+*");
            }
        }
        if (Double.isNaN(slabCenterSquareRatio)) {
            slabCenterSquareRatio = 0.5D;
        }
        slabCenterSquareRatio = Math.min(1.0D, Math.max(0.0D, slabCenterSquareRatio));
    }
}
