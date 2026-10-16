package com.tt23xrstudio.vanilla_refit_fabric.models;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 回归守卫：竖直半砖模板的<b>切面</b>（位于格子中心平面上的内侧面）不得带 cullface。
 *
 * <p>26.2 的 {@code Block.shouldRenderFace} 对带 cullface 的面只按"邻居在<b>格子边界</b>平面
 * 是否完全遮挡"来剔除（完整方块必然命中 {@code occluder == Shapes.block()} 直接剔），
 * 不检查 quad 的实际位置。切面位于格子中心平面，邻居永远够不到——若标了 cullface，
 * 相邻一侧有完整方块时该面会被错误剔除（缺面）。
 *
 * <p>原版约定：{@code block/slab.json} 切面（up）与 {@code block/slab_top.json} 切面（down）
 * 均不带 cullface，只有格子边界上的面才参与剔除。
 *
 * <p>竖楼梯模板（{@code vertical_stair_*}）的切面本来就是 {@code -}，本测试不覆盖；
 * 双竖板模板（{@code vertical_double_*}）是整格几何、无切面，同样不覆盖。
 */
class VerticalSlabTemplateCullfaceTest {

    /** 模板文件 → 切面方向（该方向上的面位于格子中心平面，不得参与剔除）。 */
    private static final Map<String, String> CUT_FACES = Map.ofEntries(
            Map.entry("vertical_slab_west.json", "east"),
            Map.entry("vertical_slab_east.json", "west"),
            Map.entry("vertical_slab_north.json", "south"),
            Map.entry("vertical_slab_south.json", "north"),
            Map.entry("vertical_slab_multi_west.json", "east"),
            Map.entry("vertical_slab_multi_east.json", "west"),
            Map.entry("vertical_slab_multi_north.json", "south"),
            Map.entry("vertical_slab_multi_south.json", "north"),
            Map.entry("vertical_slab_ew.json", "east"),
            Map.entry("vertical_slab_ns.json", "south"));

    private static final String[] ALL_DIRECTIONS = {"down", "up", "north", "south", "west", "east"};

    @Test
    void cutFacesMustNotHaveCullface() throws Exception {
        for (Map.Entry<String, String> entry : CUT_FACES.entrySet()) {
            String file = entry.getKey();
            String cut = entry.getValue();
            JsonObject faces = firstElementFaces(file);

            JsonObject cutFace = faces.getAsJsonObject(cut);
            assertNotNull(cutFace, file + " 缺少切面 " + cut);
            assertFalse(cutFace.has("cullface"),
                    file + " 的切面 " + cut + " 不得带 cullface（相邻完整方块时会被错误剔除，见类注释）");

            // 其余五个面都在格子边界上，必须保留 cullface（边界剔除是原版正确行为）
            for (String direction : ALL_DIRECTIONS) {
                if (direction.equals(cut)) {
                    continue;
                }
                assertTrue(faces.getAsJsonObject(direction).has("cullface"),
                        file + " 的边界面 " + direction + " 应保留 cullface");
            }
        }
    }

    private static JsonObject firstElementFaces(String modelFile) throws Exception {
        String path = "assets/vanilla_refit_fabric/models/block/" + modelFile;
        try (InputStreamReader reader = new InputStreamReader(
                VerticalSlabTemplateCullfaceTest.class.getClassLoader().getResourceAsStream(path),
                StandardCharsets.UTF_8)) {
            assertNotNull(reader, "缺少模型资源：" + path);
            JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
            return model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
        }
    }
}
