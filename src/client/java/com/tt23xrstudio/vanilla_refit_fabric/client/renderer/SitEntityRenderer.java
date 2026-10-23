package com.tt23xrstudio.vanilla_refit_fabric.client.renderer;

import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * 隐形座位实体的渲染器（计划 §3.3 render-state 架构）：实现 {@code createRenderState()}
 * 即可，基类 {@code submit(...)} 为空实现，什么都不画——座位的"不可见"由此保证，
 * 与 {@code shouldRender} 双保险。
 */
public class SitEntityRenderer extends EntityRenderer<SitEntity, EntityRenderState> {

    public SitEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
