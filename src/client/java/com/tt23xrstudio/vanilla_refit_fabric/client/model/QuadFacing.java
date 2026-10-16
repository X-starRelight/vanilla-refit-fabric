package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

/**
 * 从烘焙 quad 的顶点算出它“几何上朝向哪个面”（参考端同名工具的 26.2 移植）。
 *
 * <p>为什么不能直接用 {@code BakedQuad.direction()}：烘焙是按 <b>cullface</b> 分桶的，
 * 没写 cullface 的面（例如 slab 顶在 y=8 的面）会被塞进“无剔除”那一桶，要判断
 * “这个面到底是哪一面”，只能自己看顶点。
 */
public final class QuadFacing {

    private QuadFacing() {
    }

    /** @return 该 quad 几何上朝向的面；顶点退化时返回 {@code null}。 */
    public static Direction of(BakedQuad quad) {
        var p0 = quad.position(0);
        var p1 = quad.position(1);
        var p2 = quad.position(2);

        float ux = p1.x() - p0.x();
        float uy = p1.y() - p0.y();
        float uz = p1.z() - p0.z();
        float vx = p2.x() - p0.x();
        float vy = p2.y() - p0.y();
        float vz = p2.z() - p0.z();
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;

        float ax = Math.abs(nx);
        float ay = Math.abs(ny);
        float az = Math.abs(nz);
        if (ax < 1.0E-6F && ay < 1.0E-6F && az < 1.0E-6F) {
            return null;
        }
        if (ax >= ay && ax >= az) {
            return nx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= ax && ay >= az) {
            return ny > 0 ? Direction.UP : Direction.DOWN;
        }
        return nz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
