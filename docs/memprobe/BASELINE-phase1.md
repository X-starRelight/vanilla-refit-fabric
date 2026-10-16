# 阶段 1 内存基线（标题界面，注入前）

采集时间：2026-10-16
采集方式：MemProbe（`run/btsdhz-memprobe.txt` 触发）→ 客户端标题界面稳定 100 tick
→ `System.gc()` ×3 → 写 `run/btsdhz-memprobe-ready.txt` → 外部 `jcmd <pid> GC.class_histogram`。

## 环境

- Minecraft 26.2 / Fabric Loader 0.19.5 / Fabric API 0.161.0+26.2
- 本模组 1.0.5-fabric.1（阶段 1：仅注册 merged_slab + smooth_stone_stairs，**无任何 mixin 注入**）
- 标题界面、无世界、资源包仅默认 + fabric API 内置
- JVM：Zulu 25.0.1，`-Xmx3G`（runClient 配置）

## 结果

**GC 后堆占用：≈ 210 MB**（三次采样 209,940,464 / 209,975,904 / 209,975,928 字节；
另一次独立运行采得 210,445,232 / 210,512,280 / 210,445,336，相互印证）。

类直方图：`phase1-baseline-histogram.txt`（17,329 行，jcmd 在同一进程、同一时刻抓取）。
Top 占用（节选）：

| # | 实例数 | 字节 | 类 |
|---|--------|------|----|
| 1 | 349,744 | 46,970,904 | `[B` |
| 2 | 134,797 | 9,705,384 | `BakedQuad` |
| 3 | 108,607 | 9,356,520 | `[Ljava.lang.Object;` |
| 4 | 223,776 | 7,160,832 | `ModelPart$Vertex` |
| 5 | 286,233 | 6,869,592 | `String` |
| 13 | 32,477 | 3,377,608 | `BlockState` |

五类方块统计（阶段 4/6 注入后的对照分母；injected 全 0 = 注入前）：

```
SLAB   blocks=68   states=408     injectedBlocks=0 injectedStates=0
STAIR  blocks=65   states=5200    injectedBlocks=0 injectedStates=0
PANE   blocks=26   states=832     injectedBlocks=0 injectedStates=0
WALL   blocks=32   states=10368   injectedBlocks=0 injectedStates=0
FENCE  blocks=13   states=416     injectedBlocks=0 injectedStates=0
```

## 验收口径（阶段 6 用）

- NeoForge 1.21.1 参考：全功能开启后净增内存 ≈ **+155 MiB**（同机对照）。
- Fabric 侧在每个阶段用同一套 MemProbe 重测：对比本文件的 210 MB 基线与直方图，
  验证五项内存优化（玻璃板 12 位掩码缓存 / 栅栏墙选件 / CompactNeighbours /
  ModelKeyPruner / SlabbedModelEvents 包装）生效后净增量级与 NeoForge 参考一致。

## 已知坑（采集时踩过的）

- 触发/报告路径必须用 `FabricLoader.getGameDir()`：Gradle `runClient` 的进程 CWD
  是项目根目录而不是 `run/`，相对路径找不到触发文件。
- 本机 16GB 内存 + 录屏时提交内存接近耗尽：进世界会 malloc 失败、jcmd attach 被拒。
  基线只需标题界面；进程内 MBean 在 JDK 25 上拒绝参数（已留降级链+诊断信息），
  外部 `jcmd` 在标题界面时机抓取一直可用。
- 进过世界之后的标题界面不算干净基线（堆里有世界残留），MemProbe 已加 `sawWorld`
  闩锁：进过世界就不出报告。
