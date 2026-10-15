package com.tt23xrstudio.vanilla_refit_fabric.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 配置与方块名单的单元测试（纯逻辑，不启动世界）。
 *
 * <p>需要 {@link Bootstrap#bootStrap()} 初始化注册表，才能访问 {@link Blocks}。
 */
class ConfigBlocklistTest {

    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BtsdhzConfig cfg(List<String> blocklist) {
        BtsdhzConfig config = new BtsdhzConfig();
        config.blocklist = blocklist;
        BtsdhzConfig.setForTest(config);
        return config;
    }

    @Test
    void defaultsAllowEverything() {
        BtsdhzConfig config = new BtsdhzConfig();
        BtsdhzConfig.setForTest(config);

        assertTrue(config.allowTorchLanternOnSlab);
        assertTrue(config.allowLanternUnderTopSlab);
        assertEquals(0.5D, config.slabCenterSquareRatio);
        assertTrue(config.allowModdedSlabs);
        assertTrue(config.allowModdedStairs);
        assertEquals(List.of("+*"), config.blocklist);

        assertTrue(Blocklist.allows(Blocks.OAK_SLAB));
        assertEquals(1, Blocklist.entries().size());
    }

    @Test
    void firstMatchWins() {
        cfg(List.of("-minecraft:oak_slab", "+*"));

        assertFalse(Blocklist.allows(Blocks.OAK_SLAB), "第一条命中的条目生效");
        assertTrue(Blocklist.allows(Blocks.COBBLESTONE_SLAB), "没命中的方块走兜底 +*");
    }

    @Test
    void modNamespaceRule() {
        cfg(List.of("-minecraft:*", "+*"));

        assertFalse(Blocklist.allows(Blocks.OAK_SLAB));
        assertFalse(Blocklist.allows(Blocks.STONE_STAIRS));
    }

    @Test
    void denyAllThenFallbackNeverReached() {
        cfg(List.of("-*", "+minecraft:oak_slab"));

        assertFalse(Blocklist.allows(Blocks.OAK_SLAB), "-* 是第一条，直接禁止");
        assertFalse(Blocklist.allows(Blocks.STONE_SLAB));
    }

    @Test
    void invalidEntriesAreSkippedNotFatal() {
        cfg(List.of("这行没写符号", "=", "-minecraft:stone_slab"));

        // 前两条写错被忽略，第三条生效：stone_slab 被禁止
        assertFalse(Blocklist.allows(Blocks.STONE_SLAB));
        // 没命中的方块走"默认允许"
        assertTrue(Blocklist.allows(Blocks.OAK_SLAB));
    }

    @Test
    void allInvalidFallsBackToDefault() {
        cfg(List.of("???", "=bad"));

        // 一条都没解析出来 → 默认全开
        assertEquals(1, Blocklist.entries().size());
        assertTrue(Blocklist.allows(Blocks.OAK_SLAB));
    }

    @Test
    void tagEntryParses() {
        cfg(List.of("-#minecraft:wooden_slabs", "+*"));

        List<Blocklist.Entry> entries = Blocklist.entries();
        assertEquals(2, entries.size());
        assertEquals(Blocklist.Kind.BLOCK_TAG, entries.get(0).kind());
        assertFalse(entries.get(0).allow());
        assertEquals("-#minecraft:wooden_slabs  标签", entries.get(0).describe());
        // 注：单元测试环境没有数据包标签数据，标签的语义匹配（is(tag)）留到 game test 验证
    }

    @Test
    void jsonRoundTrip() throws IOException {
        Path tmp = Files.createTempFile("vanilla_refit_fabric_test", ".json");
        try {
            Files.writeString(tmp, """
                {
                  "allowTorchLanternOnSlab": false,
                  "slabCenterSquareRatio": 0.75,
                  "allowModdedStairs": false,
                  "blocklist": ["-minecraft:oak_slab", "+*"]
                }
                """, StandardCharsets.UTF_8);

            BtsdhzConfig config = BtsdhzConfig.loadFrom(tmp);

            assertFalse(config.allowTorchLanternOnSlab);
            assertEquals(0.75D, config.slabCenterSquareRatio);
            assertFalse(config.allowModdedStairs);
            assertEquals(List.of("-minecraft:oak_slab", "+*"), config.blocklist);
            // 未出现的键保持默认值
            assertTrue(config.allowLanternUnderTopSlab);
            assertTrue(config.allowModdedSlabs);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void outOfRangeRatioIsClamped() throws IOException {
        Path tmp = Files.createTempFile("vanilla_refit_fabric_test", ".json");
        try {
            Files.writeString(tmp, """
                { "slabCenterSquareRatio": 1.5 }
                """, StandardCharsets.UTF_8);
            assertEquals(1.0D, BtsdhzConfig.loadFrom(tmp).slabCenterSquareRatio);

            Files.writeString(tmp, """
                { "slabCenterSquareRatio": -3 }
                """, StandardCharsets.UTF_8);
            assertEquals(0.0D, BtsdhzConfig.loadFrom(tmp).slabCenterSquareRatio);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void brokenJsonKeepsLastGoodConfig() throws IOException {
        cfg(List.of("+*"));
        Path tmp = Files.createTempFile("vanilla_refit_fabric_test", ".json");
        try {
            Files.writeString(tmp, "{ 这是坏掉的 JSON !!!", StandardCharsets.UTF_8);
            BtsdhzConfig config = BtsdhzConfig.loadFrom(tmp);
            // 解析失败 → 沿用注入的当前配置（全开），而不是空配置
            assertEquals(List.of("+*"), config.blocklist);
            assertTrue(Blocklist.allows(Blocks.OAK_SLAB));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
