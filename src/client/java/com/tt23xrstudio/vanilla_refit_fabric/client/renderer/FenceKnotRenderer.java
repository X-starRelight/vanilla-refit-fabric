package com.tt23xrstudio.vanilla_refit_fabric.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tt23xrstudio.vanilla_refit_fabric.entity.FenceKnotEntity;
import com.tt23xrstudio.vanilla_refit_fabric.util.FenceKnotOffset;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.leash.LeashKnotModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 绕绳结渲染器（26.2 render-state 管线）：渲染栅栏上的绕绳结本体，以及它连接的所有
 * 悬链线拴绳；待连接状态时再渲染一条到玩家手部的拴绳。
 *
 * <p>与源版（1.21 直接覆写 {@code render}）的差异：业务读取（伙伴/pending/端点/光照）全部
 * 前移到 {@link #extractRenderState}，{@link #submit} 阶段只读 {@link FenceKnotRenderState}；
 * 绘制从 {@code buffer.getBuffer} 换成 {@code SubmitNodeCollector}（submitModel /
 * submitCustomGeometry）。</p>
 *
 * <p>剔除框：{@code getBoundingBoxForCulling} 从 Entity 挪到了渲染器（26.2），
 * 这里按伙伴栅栏与 pending 玩家位置放宽，避免长绳在视锥边缘被整体剔除。</p>
 */
public class FenceKnotRenderer extends EntityRenderer<FenceKnotEntity, FenceKnotRenderState> {

    private static final double CATENARY_A = 3.0D;
    private static final int STEPS = 24;
    private static final float ROPE_THICKNESS = 0.05F;
    private static final float HALF_THICKNESS = ROPE_THICKNESS / 2.0F;
    private static final Identifier KNOT_LOCATION =
            Identifier.withDefaultNamespace("textures/entity/lead_knot/lead_knot.png");

    /**
     * 复用的临时缓冲区与取光用的可变坐标：渲染在主线程串行进行，
     * 复用它们可以避免“每条绳每帧”都分配 double 数组与 BlockPos。
     */
    private static final double[] ROPE_X = new double[STEPS + 1];
    private static final double[] ROPE_Y = new double[STEPS + 1];
    private static final double[] ROPE_Z = new double[STEPS + 1];
    private static final int[] ROPE_LIGHT = new int[STEPS + 1];
    private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();

    private final LeashKnotModel knotModel;

    public FenceKnotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.knotModel = new LeashKnotModel(context.bakeLayer(ModelLayers.LEASH_KNOT));
    }

    @Override
    public FenceKnotRenderState createRenderState() {
        return new FenceKnotRenderState();
    }

    @Override
    public void extractRenderState(FenceKnotEntity entity, FenceKnotRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.ropes.clear();
        state.pending = null;

        Level level = entity.level();
        Vec3 self = entity.getRopeHoldPosition(partialTick);
        double ox = state.x;
        double oy = state.y;
        double oz = state.z;
        state.startX = self.x - ox;
        state.startY = self.y - oy;
        state.startZ = self.z - oz;
        int startBlock = blockLight(level, self.x, self.y, self.z);
        int startSky = skyLight(level, self.x, self.y, self.z);

        // 为每个伙伴准备通往伙伴栅栏的悬链线（只由位置“较小”的一侧绘制，避免重复）。
        for (Map.Entry<BlockPos, Boolean> entry : entity.getPartners().entrySet()) {
            BlockPos partner = entry.getKey();
            if (entity.getPos().compareTo(partner) < 0) {
                double ex = partner.getX() + 0.5D;
                // 伙伴栅栏可能也被下移/上移了半格，端点要落在它的绕绳结上。
                double ey = FenceKnotOffset.ropeHoldY(level, partner);
                double ez = partner.getZ() + 0.5D;
                if (self.distanceToSqr(ex, ey, ez) > 1.0E-6D) {
                    state.addRope(ex - ox, ey - oy, ez - oz,
                            startBlock, startSky, blockLight(level, ex, ey, ez), skyLight(level, ex, ey, ez));
                }
            }
        }

        // 待连接状态：从本绳结渲染一条拴绳到玩家手部。
        UUID pending = entity.getPendingPlayer();
        if (pending != null) {
            for (Player player : level.players()) {
                if (player.getUUID().equals(pending)) {
                    Vec3 hand = player.getRopeHoldPosition(partialTick);
                    if (self.distanceToSqr(hand) > 1.0E-6D) {
                        FenceKnotRenderState.Rope rope = new FenceKnotRenderState.Rope();
                        rope.endX = hand.x - ox;
                        rope.endY = hand.y - oy;
                        rope.endZ = hand.z - oz;
                        rope.startBlock = startBlock;
                        rope.startSky = startSky;
                        rope.endBlock = blockLight(level, hand.x, hand.y, hand.z);
                        rope.endSky = skyLight(level, hand.x, hand.y, hand.z);
                        state.pending = rope;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void submit(FenceKnotRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state, pose, collector, camera);

        // 绕绳结本体（模型坐标系沿用源版的翻转惯例）。
        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        collector.submitModel(this.knotModel, state, pose, KNOT_LOCATION,
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0, null);
        pose.popPose();

        // 每条悬链线。
        for (FenceKnotRenderState.Rope rope : state.ropes) {
            submitRope(state, rope, pose, collector);
        }
        if (state.pending != null) {
            submitRope(state, state.pending, pose, collector);
        }
    }

    private static void submitRope(FenceKnotRenderState state, FenceKnotRenderState.Rope rope,
                                   PoseStack pose, SubmitNodeCollector collector) {
        // CustomGeometryRenderer 回调：参数1 = PoseStack.Pose（已含本节点变换），参数2 = 目标缓冲。
        collector.submitCustomGeometry(pose, RenderTypes.leash(), (knotPose, buffer) ->
                renderRopeGeometry(state, rope, knotPose, buffer));
    }

    /**
     * 悬链线几何（照搬源版算法）：24 段悬链折线、厚 0.05、两端光照沿参数插值，
     * 两条正交 ribbon 拼成方管状绳。坐标为相对渲染原点的偏移。
     */
    private static void renderRopeGeometry(FenceKnotRenderState state, FenceKnotRenderState.Rope rope,
                                           PoseStack.Pose knotPose, VertexConsumer buffer) {
        Matrix4f matrix = knotPose.pose();
        double ax = state.startX;
        double ay = state.startY;
        double az = state.startZ;
        double bx = rope.endX;
        double by = rope.endY;
        double bz = rope.endZ;

        double fx = bx - ax;
        double fy = by - ay;
        double fz = bz - az;
        double span = Math.sqrt(fx * fx + fz * fz);

        // 方向向量全部用 double 直接算，避免每帧为每条绳分配 Vec3。
        double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (length < 1.0E-6D) {
            return;
        }
        double tx = fx / length;
        double ty = fy / length;
        double tz = fz / length;

        // perpU = tangent × (0,1,0) = (-tz, 0, tx)
        double perpUx = -tz;
        double perpUy = 0.0D;
        double perpUz = tx;
        double perpULength = Math.sqrt(perpUx * perpUx + perpUz * perpUz);
        if (perpULength < 1.0E-4D) {
            perpUx = 1.0D;
            perpUy = 0.0D;
            perpUz = 0.0D;
        } else {
            perpUx /= perpULength;
            perpUz /= perpULength;
        }
        // perpV = tangent × perpU（两者已正交，长度接近 1，这里仍做一次归一化兜底）
        double perpVx = ty * perpUz - tz * perpUy;
        double perpVy = tz * perpUx - tx * perpUz;
        double perpVz = tx * perpUy - ty * perpUx;
        double perpVLength = Math.sqrt(perpVx * perpVx + perpVy * perpVy + perpVz * perpVz);
        if (perpVLength > 1.0E-4D) {
            perpVx /= perpVLength;
            perpVy /= perpVLength;
            perpVz /= perpVLength;
        }

        double aParam = Math.max(CATENARY_A * span, 1.0E-4D);
        double endYRel = aParam * Math.cosh(span / (2.0D * aParam));

        double[] cx = ROPE_X;
        double[] cy = ROPE_Y;
        double[] cz = ROPE_Z;
        int[] lights = ROPE_LIGHT;
        for (int i = 0; i <= STEPS; i++) {
            float f = (float) i / (float) STEPS;
            double xc = (f - 0.5D) * span;
            double yDrop = aParam * Math.cosh(xc / aParam) - endYRel;
            cx[i] = ax + fx * f;
            cy[i] = ay + fy * f + yDrop;
            cz[i] = az + fz * f;
            lights[i] = LightCoordsUtil.pack(
                    (int) Mth.lerp(f, (float) rope.startBlock, (float) rope.endBlock),
                    (int) Mth.lerp(f, (float) rope.startSky, (float) rope.endSky));
        }

        for (int i = 0; i <= STEPS; i++) {
            float shade = i % 2 == 0 ? 1.0F : 0.7F;
            addVertex(buffer, matrix, cx[i] + perpUx * HALF_THICKNESS, cy[i] + perpUy * HALF_THICKNESS, cz[i] + perpUz * HALF_THICKNESS, shade, lights[i]);
            addVertex(buffer, matrix, cx[i] - perpUx * HALF_THICKNESS, cy[i] - perpUy * HALF_THICKNESS, cz[i] - perpUz * HALF_THICKNESS, shade, lights[i]);
        }
        for (int i = STEPS; i >= 0; i--) {
            float shade = i % 2 == 0 ? 1.0F : 0.7F;
            addVertex(buffer, matrix, cx[i] + perpVx * HALF_THICKNESS, cy[i] + perpVy * HALF_THICKNESS, cz[i] + perpVz * HALF_THICKNESS, shade, lights[i]);
            addVertex(buffer, matrix, cx[i] - perpVx * HALF_THICKNESS, cy[i] - perpVy * HALF_THICKNESS, cz[i] - perpVz * HALF_THICKNESS, shade, lights[i]);
        }
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f pose,
                                  double x, double y, double z, float shade, int light) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(0.5F * shade, 0.4F * shade, 0.3F * shade, 1.0F)
                .setLight(light);
    }

    private static int blockLight(Level level, double x, double y, double z) {
        LIGHT_POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return level.getBrightness(LightLayer.BLOCK, LIGHT_POS);
    }

    private static int skyLight(Level level, double x, double y, double z) {
        LIGHT_POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return level.getBrightness(LightLayer.SKY, LIGHT_POS);
    }

    /** 剔除框按伙伴栅栏与 pending 玩家放宽（源版实体侧覆写在 26.2 已下沉到渲染器）。 */
    @Override
    public AABB getBoundingBoxForCulling(FenceKnotEntity entity) {
        AABB box = entity.getBoundingBox().inflate(1.0D);
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        for (BlockPos partner : entity.getPartners().keySet()) {
            box = box.expandTowards(
                    partner.getX() + 0.5D - x,
                    FenceKnotOffset.ropeHoldY(entity.level(), partner) - y,
                    partner.getZ() + 0.5D - z);
        }
        UUID pending = entity.getPendingPlayer();
        if (pending != null) {
            for (Player player : entity.level().players()) {
                if (player.getUUID().equals(pending)) {
                    box = box.expandTowards(player.getX() - x, player.getY() - y, player.getZ() - z);
                    break;
                }
            }
        }
        return box.inflate(0.5D);
    }
}
