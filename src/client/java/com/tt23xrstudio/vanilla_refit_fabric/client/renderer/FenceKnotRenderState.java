package com.tt23xrstudio.vanilla_refit_fabric.client.renderer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * 绕绳结渲染状态（26.2 render-state 管线）：{@code extractRenderState} 阶段把伙伴端点、
 * 待连接手部端点与两端光照全部算好拷进来，{@code submit} 阶段只读本状态、不碰实体。
 *
 * <p>所有坐标都是<b>相对渲染原点</b>（{@link net.minecraft.client.renderer.entity.state.EntityRenderState#x}
 * 等字段）的偏移——{@code submit} 收到的 PoseStack 原点即渲染原点。</p>
 */
public class FenceKnotRenderState extends EntityRenderState {

    /** 通向伙伴栅栏的悬链线（只含由本侧负责绘制的那些，见渲染器去重逻辑）。 */
    public final List<Rope> ropes = new ArrayList<>();

    /** 待连接时通向玩家手部的那条绳；{@code null} 表示没有。 */
    public Rope pending;

    /** 本结握绳点（相对渲染原点）。 */
    public double startX;
    public double startY;
    public double startZ;

    /** 追加一条到 {@code (endX,endY,endZ)} 的绳；光照存两端各自的 block/sky 四值（沿线插值用）。 */
    public void addRope(double endX, double endY, double endZ,
                        int startBlock, int startSky, int endBlock, int endSky) {
        Rope rope = new Rope();
        rope.endX = endX;
        rope.endY = endY;
        rope.endZ = endZ;
        rope.startBlock = startBlock;
        rope.startSky = startSky;
        rope.endBlock = endBlock;
        rope.endSky = endSky;
        this.ropes.add(rope);
    }

    /** 一条绳的终点与两端光照（起点光照 = 本结侧，见 {@link #startBlock/startSky} 之外的兜底）。 */
    public static final class Rope {
        public double endX;
        public double endY;
        public double endZ;
        public int startBlock;
        public int startSky;
        public int endBlock;
        public int endSky;
    }
}
