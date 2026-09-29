package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GokuFxMod;
import com.igygaming.gokufx.KameTiming;
import com.igygaming.gokufx.network.GokuEffectPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Capas 3D adicionales para dar protagonismo a la esfera de carga sin reemplazar
 * el renderer original: la esfera existente queda como núcleo y estas mallas son
 * su envolvente/energía comprimida.
 */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientKameChargeEnhancer {
    private static final float SOURCE_X = -14.0F;
    private static final float SOURCE_Y = 1.71F;

    private ClientKameChargeEnhancer() {}

    // V12: desactivado como subscriber para no duplicar la esfera nueva.
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();

        for (AbstractClientPlayer target : mc.level.players()) {
            int raw = ClientGokuEffects.rawEffectTick(target.getUUID(), GokuEffectPacket.KAMEHAMEHA);
            if (raw < 0 || raw > KameTiming.CHARGE_TICKS + 18) continue;

            float t = raw + partial;
            float radius;
            float alpha;

            if (t <= KameTiming.CHARGE_TICKS) {
                float p = smooth(0.0F, KameTiming.CHARGE_TICKS, t);
                float pulse = 1.0F + Mth.sin(t * 0.20F) * 0.035F;
                radius = (0.85F + 5.55F * p) * pulse;
                alpha = 0.12F + 0.12F * p;
            } else {
                float release = Mth.clamp((t - KameTiming.CHARGE_TICKS) / 18.0F, 0.0F, 1.0F);
                radius = Mth.lerp(release, 6.40F, 3.20F);
                alpha = (1.0F - release) * 0.22F;
            }

            pose.pushPose();
            pose.translate(
                    target.getX() - camera.getPosition().x + SOURCE_X,
                    target.getY() - camera.getPosition().y + SOURCE_Y,
                    target.getZ() - camera.getPosition().z
            );

            drawGlowSphere(pose, radius, 0.01F, 0.20F, 1.00F, alpha, 64, 32);
            drawGlowSphere(pose, radius * 0.86F, 0.02F, 0.58F, 1.00F, alpha * 0.72F, 56, 28);
            drawGlowSphere(pose, radius * 0.68F, 0.55F, 0.90F, 1.00F, alpha * 0.36F, 48, 24);

            float spin = t * 2.3F;
            drawBand(pose, radius * 0.96F, Math.max(0.055F, radius * 0.018F), spin, 18.0F, 0.03F, 0.52F, 1.00F, alpha * 1.25F);
            drawBand(pose, radius * 0.90F, Math.max(0.050F, radius * 0.016F), -spin * 0.82F, 68.0F, 0.12F, 0.80F, 1.00F, alpha);
            drawBand(pose, radius * 1.02F, Math.max(0.045F, radius * 0.014F), spin * 0.61F, 112.0F, 0.02F, 0.34F, 1.00F, alpha * 0.80F);

            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void drawBand(PoseStack pose, float majorRadius, float tubeRadius,
                                 float spinY, float tiltX,
                                 float red, float green, float blue, float alpha) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(spinY));
        pose.mulPose(Axis.XP.rotationDegrees(tiltX));
        drawTorus(pose, majorRadius, tubeRadius, red, green, blue, alpha, 56, 8);
        pose.popPose();
    }

    private static void drawTorus(PoseStack pose, float major, float minor,
                                  float red, float green, float blue, float alpha,
                                  int majorSegments, int minorSegments) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = pose.last().pose();

        for (int i = 0; i < majorSegments; i++) {
            double a0 = Math.PI * 2.0D * i / majorSegments;
            double a1 = Math.PI * 2.0D * (i + 1) / majorSegments;
            for (int j = 0; j < minorSegments; j++) {
                double b0 = Math.PI * 2.0D * j / minorSegments;
                double b1 = Math.PI * 2.0D * (j + 1) / minorSegments;

                vertexTorus(buffer, matrix, major, minor, a0, b0, red, green, blue, alpha);
                vertexTorus(buffer, matrix, major, minor, a1, b0, red, green, blue, alpha);
                vertexTorus(buffer, matrix, major, minor, a1, b1, red, green, blue, alpha);

                vertexTorus(buffer, matrix, major, minor, a0, b0, red, green, blue, alpha);
                vertexTorus(buffer, matrix, major, minor, a1, b1, red, green, blue, alpha);
                vertexTorus(buffer, matrix, major, minor, a0, b1, red, green, blue, alpha);
            }
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    private static void vertexTorus(BufferBuilder buffer, Matrix4f matrix,
                                    float major, float minor, double a, double b,
                                    float red, float green, float blue, float alpha) {
        float ring = major + minor * (float) Math.cos(b);
        float x = ring * (float) Math.cos(a);
        float y = minor * (float) Math.sin(b);
        float z = ring * (float) Math.sin(a);
        buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private static void drawGlowSphere(PoseStack pose, float radius,
                                       float red, float green, float blue, float alpha,
                                       int slices, int stacks) {
        if (radius <= 0.01F || alpha <= 0.001F) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = pose.last().pose();

        for (int stack = 0; stack < stacks; stack++) {
            double v0 = stack / (double) stacks;
            double v1 = (stack + 1) / (double) stacks;
            double phi0 = -Math.PI / 2.0D + Math.PI * v0;
            double phi1 = -Math.PI / 2.0D + Math.PI * v1;

            float y0 = radius * (float) Math.sin(phi0);
            float y1 = radius * (float) Math.sin(phi1);
            float r0 = radius * (float) Math.cos(phi0);
            float r1 = radius * (float) Math.cos(phi1);

            for (int slice = 0; slice < slices; slice++) {
                double u0 = slice / (double) slices;
                double u1 = (slice + 1) / (double) slices;
                double theta0 = Math.PI * 2.0D * u0;
                double theta1 = Math.PI * 2.0D * u1;

                float x00 = r0 * (float) Math.cos(theta0);
                float z00 = r0 * (float) Math.sin(theta0);
                float x01 = r0 * (float) Math.cos(theta1);
                float z01 = r0 * (float) Math.sin(theta1);
                float x10 = r1 * (float) Math.cos(theta0);
                float z10 = r1 * (float) Math.sin(theta0);
                float x11 = r1 * (float) Math.cos(theta1);
                float z11 = r1 * (float) Math.sin(theta1);

                vertex(buffer, matrix, x00, y0, z00, red, green, blue, alpha);
                vertex(buffer, matrix, x10, y1, z10, red, green, blue, alpha);
                vertex(buffer, matrix, x11, y1, z11, red, green, blue, alpha);

                vertex(buffer, matrix, x00, y0, z00, red, green, blue, alpha);
                vertex(buffer, matrix, x11, y1, z11, red, green, blue, alpha);
                vertex(buffer, matrix, x01, y0, z01, red, green, blue, alpha);
            }
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
                               float x, float y, float z,
                               float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private static float smooth(float from, float to, float value) {
        float p = Mth.clamp((value - from) / Math.max(0.0001F, to - from), 0.0F, 1.0F);
        return p * p * (3.0F - 2.0F * p);
    }
}
