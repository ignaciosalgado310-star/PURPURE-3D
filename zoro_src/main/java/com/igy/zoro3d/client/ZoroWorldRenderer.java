package com.igy.zoro3d.client;

import com.igy.zoro3d.Zoro3D;
import com.igy.zoro3d.server.ZoroAttack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = Zoro3D.MOD_ID, value = Dist.CLIENT)
public final class ZoroWorldRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Vec3 camera = event.getCamera().getPosition();

        for (ZoroClientState.VisualState state : ZoroClientState.active()) {
            if (state.center().distanceToSqr(camera) > 180.0 * 180.0) continue;
            render(event, camera, state);
        }
    }

    private static void render(RenderLevelStageEvent event, Vec3 camera, ZoroClientState.VisualState state) {
        Matrix4f matrix = new Matrix4f(event.getPoseStack().last().pose());
        matrix.translate(
                (float) (state.center().x - camera.x),
                (float) (state.center().y - camera.y),
                (float) (state.center().z - camera.z)
        );

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        try {
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            if (state.stage() == ZoroAttack.STAGE_INTRO) {
                float spin = state.age() * 0.045F;
                drawRing(b, matrix, 3.0F + state.age() * 0.025F, 0.16F, 0.05F, 1.0F, 0.22F, 0.48F);
                for (int i = 0; i < 3; i++) {
                    drawSlash(b, matrix, spin + i * 2.094F, 7.0F, 0.22F, i * 0.55F - 0.2F,
                            0.10F, 1.0F, 0.24F, 0.78F);
                }
            } else if (state.stage() == ZoroAttack.STAGE_RING) {
                float p = Math.min(1.0F, (state.age() - ZoroAttack.INTRO_END) / 55.0F);
                float r = 3.5F + p * 7.5F;
                drawRing(b, matrix, r, 0.34F, 0.0F, 0.78F, 1.0F, 0.65F);
                drawRing(b, matrix, r * 0.72F, 0.20F, 0.55F, 1.0F, 1.0F, 0.72F);
                drawRing(b, matrix, r * 1.18F, 0.13F, 0.0F, 0.55F, 1.0F, 0.36F);
            } else {
                int a = state.age() - ZoroAttack.RING_END;
                drawRing(b, matrix, 5.6F + 0.55F * (float) Math.sin(a * 0.17F),
                        0.26F, 0.05F, 1.0F, 0.28F, 0.55F);
                drawRing(b, matrix, 9.2F + 0.9F * (float) Math.sin(a * 0.09F + 1.2F),
                        0.18F, 0.0F, 0.65F, 0.23F, 0.32F);

                for (int i = 0; i < 24; i++) {
                    float ang = i * 0.91F + a * (i % 2 == 0 ? 0.052F : -0.041F);
                    float len = 9.0F + (i % 7) * 1.55F + 2.5F * (float) Math.sin(a * 0.08F + i);
                    float y = -1.0F + (i % 8) * 0.63F;
                    float alpha = 0.34F + (i % 4) * 0.11F;
                    drawSlash(b, matrix, ang, len, 0.16F + (i % 3) * 0.055F, y,
                            0.08F, 1.0F, 0.26F, alpha);
                    if (i % 3 == 0) {
                        drawSlash(b, matrix, ang + 0.03F, len * 0.82F, 0.045F, y + 0.05F,
                                0.88F, 1.0F, 0.94F, 0.82F);
                    }
                }
            }

            BufferUploader.drawWithShader(b.end());
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void drawRing(BufferBuilder b, Matrix4f m, float radius, float width,
                                 float red, float green, float blue, float alpha) {
        int segments = 64;
        float inner = Math.max(0.05F, radius - width);
        float outer = radius + width;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            float x0i = (float) Math.cos(a0) * inner;
            float z0i = (float) Math.sin(a0) * inner;
            float x0o = (float) Math.cos(a0) * outer;
            float z0o = (float) Math.sin(a0) * outer;
            float x1i = (float) Math.cos(a1) * inner;
            float z1i = (float) Math.sin(a1) * inner;
            float x1o = (float) Math.cos(a1) * outer;
            float z1o = (float) Math.sin(a1) * outer;
            v(b, m, x0i, 0.02F, z0i, red, green, blue, alpha);
            v(b, m, x1i, 0.02F, z1i, red, green, blue, alpha);
            v(b, m, x1o, 0.02F, z1o, red, green, blue, alpha);
            v(b, m, x0o, 0.02F, z0o, red, green, blue, alpha);
        }
    }

    private static void drawSlash(BufferBuilder b, Matrix4f m, float angle, float length, float width, float y,
                                  float red, float green, float blue, float alpha) {
        float dx = (float) Math.cos(angle);
        float dz = (float) Math.sin(angle);
        float sx = -dz * width;
        float sz = dx * width;
        float ex = dx * length * 0.5F;
        float ez = dz * length * 0.5F;

        v(b, m, -ex - sx, y, -ez - sz, red, green, blue, alpha);
        v(b, m, ex - sx, y, ez - sz, red, green, blue, alpha);
        v(b, m, ex + sx, y, ez + sz, red, green, blue, alpha);
        v(b, m, -ex + sx, y, -ez + sz, red, green, blue, alpha);

        float halfH = width * 2.2F;
        v(b, m, -ex, y - halfH, -ez, red, green, blue, alpha);
        v(b, m, ex, y - halfH, ez, red, green, blue, alpha);
        v(b, m, ex, y + halfH, ez, red, green, blue, alpha);
        v(b, m, -ex, y + halfH, -ez, red, green, blue, alpha);
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z,
                          float r, float g, float blue, float a) {
        b.vertex(m, x, y, z).color(r, g, blue, a).endVertex();
    }

    private ZoroWorldRenderer() {}
}
