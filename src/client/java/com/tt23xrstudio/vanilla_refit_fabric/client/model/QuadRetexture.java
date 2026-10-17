package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

/**
 * 26.2 的运行时换图工具（玻璃板部件与竖直台阶 / 楼梯共用）。
 *
 * <p><b>为什么不能用 {@code materialBake}：</b>原版烘焙出来的 quad，UV <b>已经是图集坐标</b>
 * （{@code fromBakedQuad} 字节码原样拷贝 UV，游戏靠它直接采样）；而 {@code materialBake}
 * 的语义是「把<b>单位 UV</b> 按给定 sprite 立即映射进图集」（旧 FRAPI 的 {@code spriteBake}，
 * 官方文档：Assigns sprite atlas u,v coordinates for the given sprite）。对图集坐标再映射一遍，
 * 采样点会飞出贴图、被钳到单独一个纹素上——整面纯色。
 *
 * <p><b>正确做法：</b>把每个顶点的 UV 从旧 sprite 的图集区域等比重映到新 sprite 的图集区域
 * （用 {@code getU0/getU1} 这种明确的区域边界 API，不碰 0..1 / 0..16 语义有版本差异的 {@code getU}），
 * 材质改用 {@code postMaterialBake} 换——它只记录材质（sprite / 半透明标记），不动 UV。
 *
 * <p>换图后同一份 UV 落在新图的相同相对位置上，模板里调好的区域裁切、留边全部保持。
 */
final class QuadRetexture {

    private QuadRetexture() {
    }

    /**
     * 把 {@code quad} 的拷贝（已在 emitter 上）换成 {@code target} 贴图并提交材质。
     *
     * <p>调用前必须先 {@code emitter.fromBakedQuad(quad)}；本方法覆盖其 UV 与材质，不碰其它属性。
     * {@code source == target} 时不重映 UV（区域完全一致），只换材质记录。
     */
    static void apply(QuadEmitter emitter, BakedQuad quad, TextureAtlasSprite source,
                      TextureAtlasSprite target, boolean translucent) {
        if (source != target) {
            float u0 = target.getU0();   // 目标区域左界（下面多次用到，先取出来）
            float v0 = target.getV0();
            float du = (target.getU1() - target.getU0()) / (source.getU1() - source.getU0());
            float dv = (target.getV1() - target.getV0()) / (source.getV1() - source.getV0());
            float su0 = source.getU0();
            float sv0 = source.getV0();
            emitter.uv(0, remapU(quad.packedUV0(), su0, u0, du), remapV(quad.packedUV0(), sv0, v0, dv));
            emitter.uv(1, remapU(quad.packedUV1(), su0, u0, du), remapV(quad.packedUV1(), sv0, v0, dv));
            emitter.uv(2, remapU(quad.packedUV2(), su0, u0, du), remapV(quad.packedUV2(), sv0, v0, dv));
            emitter.uv(3, remapU(quad.packedUV3(), su0, u0, du), remapV(quad.packedUV3(), sv0, v0, dv));
        }
        emitter.postMaterialBake(new Material.Baked(target, translucent));
    }

    /** 图集 U：旧区域内相对位置 → 新图同相对位置。 */
    private static float remapU(long packedUv, float oldU0, float newU0, float scale) {
        return newU0 + (UVPair.unpackU(packedUv) - oldU0) * scale;
    }

    /** 图集 V：同上。 */
    private static float remapV(long packedUv, float oldV0, float newV0, float scale) {
        return newV0 + (UVPair.unpackV(packedUv) - oldV0) * scale;
    }
}
