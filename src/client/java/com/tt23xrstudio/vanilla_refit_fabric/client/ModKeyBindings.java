package com.tt23xrstudio.vanilla_refit_fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * 拥有本模组的自定义按键绑定（原 NeoForge 版在 RegisterKeyMappingsEvent 里注册，
 * Fabric 侧用 KeyMappingHelper 在客户端初始化时注册）。
 *
 * <p>26.2 的按键分类（category）是 {@link KeyMapping.Category} 对象而不是字符串：
 * {@code Category.register(Identifier)} 注册自定义分类，界面文案取翻译键
 * {@code key.category.<namespace>.<path>}。
 */
public final class ModKeyBindings {

    /** 按键分类 id：界面文案翻译键为 {@code key.category.vanilla_refit_fabric.main}。 */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vanilla_refit_fabric", "main"));

    private static final KeyMapping CRAWL_KEY = new KeyMapping(
            "key.vanilla_refit_fabric.crawl",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY
    );

    private static final KeyMapping SIT_KEY = new KeyMapping(
            "key.vanilla_refit_fabric.sit",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    /** 切换当前手持台阶/楼梯的放置逻辑(本模组竖放 <-> 原版)。 */
    private static final KeyMapping PLACEMENT_MODE_KEY = new KeyMapping(
            "key.vanilla_refit_fabric.placement_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    private ModKeyBindings() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(CRAWL_KEY);
        KeyMappingHelper.registerKeyMapping(SIT_KEY);
        KeyMappingHelper.registerKeyMapping(PLACEMENT_MODE_KEY);
        // 与源项目对齐的防御性刷新：按键分发表（KeyMapping.MAP）由 Options.load() 建表，
        // 若模组按键注册晚于建表则补一次重建，保证模组键能收到点击事件。
        KeyMapping.resetMapping();
    }

    public static KeyMapping crawlKey() {
        return CRAWL_KEY;
    }

    public static KeyMapping sitKey() {
        return SIT_KEY;
    }

    public static KeyMapping placementModeKey() {
        return PLACEMENT_MODE_KEY;
    }
}
