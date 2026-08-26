package igy.purpure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import igy.purpure.PurpureMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * V10 visual-only upgrade.
 * Keeps the exact V9 sequence/timing/positions and only improves client-side
 * geometry/materials: textured relief spheres, layered translucent shells,
 * per-vertex fake lighting and denser 3D meshes.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoAttack {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final ResourceLocation ENERGY_TEXTURE =
            new ResourceLocation(PurpureMod.MODID, "textures/effect/energy_surface.png");

    private static final float GOJO_X = 4.0f;
    private static final float FUSION_X = 2.65f;
    private static final float FUSION_Y = 2.08f;

    private static final float CONTACT_TICK = 150.0f;
    private static final float ORBS_END_TICK = 166.0f;
    private static final float PURPLE_BIRTH_TICK = 152.0f;
    private static final float PURPLE_GROW_END = 220.0f;
    private static final float LAUNCH_START = 218.0f;
    private static final float LAUNCH_END = 340.0f;

    private ClientGojoAttack() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();

        for (AbstractClientPlayer target : mc.level.players()) {
            float base = ClientPurpureEffects.effectTick(target.getUUID());
            if (base < 0.0f) continue;
            float t = base + partial;

            pose.pushPose();
            pose.translate(
                    target.getX() - camera.getPosition().x,
                    target.getY() - camera.getPosition().y,
                    target.getZ() - camera.getPosition().z
            );
            drawAttack(pose, t);
            pose.popPose();
        }

        restoreState();
    }

    private static void drawAttack(PoseStack pose, float t) {
        if (t < ORBS_END_TICK) {
            float appear = smooth(10.0f, 34.0f, t);
            float converge = smooth(38.0f, CONTACT_TICK, t);
            float vanish = 1.0f - smooth(CONTACT_TICK, ORBS_END_TICK, t);

            float centerX = Mth.lerp(converge, GOJO_X - 0.12f, FUSION_X);
            float centerY = Mth.lerp(converge, 2.16f, FUSION_Y);
            float separation = Mth.lerp(converge, 2.30f, 0.0f);
            float wobble = (1.0f - converge) * 0.16f;
            float xWobble = Mth.sin(t * 0.070f) * wobble;
            float yWobble = Mth.sin(t * 0.090f) * wobble * 0.60f;

            float size = Mth.lerp(appear, 0.30f, 1.12f);
            size *= Mth.lerp(converge, 1.0f, 0.86f) * vanish;

            Orb blue = new Orb(
                    centerX + xWobble,
                    centerY + yWobble,
                    -separation,
                    size,
                    0.025f, 0.24f, 1.00f
            );
            Orb red = new Orb(
                    centerX - xWobble,
                    centerY - yWobble,
                    separation,
                    size,
                    1.00f, 0.018f, 0.040f
            );

            drawOrb(pose, blue, t, 0.0f);
            drawOrb(pose, red, t, 173.0f);

            if (t >= 144.0f) {
                float q = smooth(144.0f, CONTACT_TICK, t)
                        * (1.0f - smooth(160.0f, ORBS_END_TICK, t));
                drawFusionWeb(pose, blue, red, t, q);
            }
        }

        if (t >= PURPLE_BIRTH_TICK) {
            float born = smooth(PURPLE_BIRTH_TICK, 170.0f, t);
            float grow = smooth(168.0f, PURPLE_GROW_END, t);
            float launch = smooth(LAUNCH_START, LAUNCH_END, t);

            float small = Mth.lerp(born, 0.06f, 0.78f);
            float radius = Mth.lerp(grow, small, 4.15f);

            if (t >= 166.0f && t <= 205.0f) {
                float pulseFade = 1.0f - smooth(192.0f, 207.0f, t);
                radius *= 1.0f + Mth.sin((t - 166.0f) * 0.42f) * 0.045f * pulseFade;
            }

            float px = Mth.lerp(launch, FUSION_X, 0.0f);
            float py = Mth.lerp(launch, FUSION_Y, 1.58f);

            pose.pushPose();
            pose.translate(px, py, 0.0f);
            purple(pose, radius, t);
            pose.popPose();
        }
    }

    private static void drawOrb(PoseStack pose, Orb o, float t, float phase) {
        if (o.size <= 0.02f) return;

        pose.pushPose();
        pose.translate(o.x, o.y, o.z);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.82f + phase));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.025f + phase) * 7.0f));
        texturedSphere(pose, o.size, o.r, o.g, o.b, 1.0f,
                t * 0.0025f, 0.0f, 0.025f, false);
        pose.popPose();

        float cr = Mth.clamp(o.r * 1.18f + 0.030f, 0.0f, 1.0f);
        float cg = Mth.clamp(o.g * 1.18f + 0.030f, 0.0f, 1.0f);
        float cb = Mth.clamp(o.b * 1.10f + 0.022f, 0.0f, 1.0f);

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-t * 0.58f - phase));
        texturedSphere(pose, o.size * 0.70f, cr, cg, cb, 0.98f,
                -t * 0.0032f, 0.11f, 0.018f, false);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-t * 0.65f + phase * 0.35f));
        texturedSphere(
                pose,
                o.size * 1.055f,
                Mth.clamp(o.r * 1.14f + 0.03f, 0.0f, 1.0f),
                Mth.clamp(o.g * 1.14f + 0.03f, 0.0f, 1.0f),
                Mth.clamp(o.b * 1.08f + 0.02f, 0.0f, 1.0f),
                0.17f,
                t * 0.0041f, 0.19f, 0.040f, true
        );
        pose.popPose();

        texturedSphere(pose, o.size * 1.12f, o.r, o.g, o.b, 0.065f,
                -t * 0.0017f, 0.31f, 0.015f, true);

        pose.popPose();
    }

    private static void drawFusionWeb(PoseStack pose, Orb blue, Orb red, float t, float q) {
        if (q <= 0.01f) return;

        for (int strand = 0; strand < 6; strand++) {
            float phase = strand * 1.07f + t * 0.115f;
            float px = blue.x;
            float py = blue.y;
            float pz = blue.z;

            for (int i = 1; i <= 14; i++) {
                float u = i / 14.0f;
                float bulge = Mth.sin(u * Mth.PI) * q;
                float x = Mth.lerp(u, blue.x, red.x)
                        + Mth.cos(phase + u * TAU * 1.5f) * 0.13f * bulge;
                float y = Mth.lerp(u, blue.y, red.y)
                        + Mth.sin(phase * 0.72f + u * TAU) * 0.105f * bulge;
                float z = Mth.lerp(u, blue.z, red.z)
                        + Mth.sin(phase + u * TAU * 1.25f) * 0.13f * bulge;
                float center = 1.0f - Math.abs(u * 2.0f - 1.0f);

                ribbon(
                        pose,
                        px, py, pz,
                        x, y, z,
                        Mth.lerp(center, 0.12f, 0.73f),
                        0.035f,
                        1.0f,
                        0.075f * q,
                        0.018f + 0.026f * center
                );
                px = x;
                py = y;
                pz = z;
            }
        }
    }

    private static void purple(PoseStack pose, float r, float t) {
        if (r <= 0.04f) return;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.30f));
        pose.mulPose(Axis.XP.rotationDegrees(-10.0f + Mth.sin(t * 0.018f) * 5.0f));
        texturedSphere(pose, r, 0.43f, 0.018f, 0.90f, 1.0f,
                t * 0.0016f, 0.0f, 0.030f, false);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-t * 0.24f));
        texturedSphere(pose, r * 0.73f, 0.67f, 0.055f, 1.00f, 1.0f,
                -t * 0.0024f, 0.14f, 0.022f, false);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-t * 0.46f));
        pose.mulPose(Axis.ZP.rotationDegrees(17.0f));
        texturedSphere(pose, r * 1.018f, 0.78f, 0.085f, 1.00f, 0.18f,
                t * 0.0040f, 0.26f, 0.052f, true);
        pose.popPose();

        texturedSphere(pose, r * 1.065f, 0.82f, 0.09f, 1.00f, 0.115f,
                -t * 0.0012f, 0.34f, 0.018f, true);
        texturedSphere(pose, r * 1.105f, 0.34f, 0.04f, 1.00f, 0.050f,
                t * 0.0009f, 0.48f, 0.010f, true);
        texturedSphere(pose, r * 0.17f, 0.88f, 0.30f, 1.00f, 1.0f,
                t * 0.0048f, 0.52f, 0.015f, false);
    }

    private static void texturedSphere(
            PoseStack pose,
            float radius,
            float r, float g, float b,
            float alpha,
            float uShift,
            float vShift,
            float relief,
            boolean glow
    ) {
        if (radius <= 0.02f || alpha <= 0.005f) return;

        if (glow) setTexturedGlow();
        else setTexturedSolid();

        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        final int lon = glow ? 72 : 96;
        final int lat = glow ? 40 : 54;

        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float) iy / lat - 0.5f) * Mth.PI;
            float p1 = ((float) (iy + 1) / lat - 0.5f) * Mth.PI;
            float v0 = (float) iy / lat + vShift;
            float v1 = (float) (iy + 1) / lat + vShift;

            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                float u0 = (float) ix / lon + uShift;
                float u1 = (float) (ix + 1) / lon + uShift;

                texturedVertex(bb, m, radius, p0, a0, u0, v0,
                        r, g, b, alpha, relief, uShift);
                texturedVertex(bb, m, radius, p0, a1, u1, v0,
                        r, g, b, alpha, relief, uShift);
                texturedVertex(bb, m, radius, p1, a1, u1, v1,
                        r, g, b, alpha, relief, uShift);
                texturedVertex(bb, m, radius, p1, a0, u0, v1,
                        r, g, b, alpha, relief, uShift);
            }
        }

        BufferUploader.drawWithShader(bb.end());
    }

    private static void texturedVertex(
            BufferBuilder bb,
            Matrix4f m,
            float radius,
            float lat,
            float lon,
            float u,
            float v,
            float r, float g, float b, float alpha,
            float relief,
            float phase
    ) {
        float c = Mth.cos(lat);
        float nx = c * Mth.cos(lon);
        float ny = Mth.sin(lat);
        float nz = c * Mth.sin(lon);

        float polarFade = c * c;
        float reliefWave =
                Mth.sin(lon * 5.0f + lat * 3.0f + phase * 8.0f) * 0.58f
                        + Mth.sin(lon * 13.0f - lat * 7.0f - phase * 5.0f) * 0.28f
                        + Mth.sin(lon * 23.0f + lat * 11.0f + phase * 3.0f) * 0.14f;
        float rr = radius * (1.0f + relief * polarFade * reliefWave);

        float directional = 0.76f + nx * 0.075f + ny * 0.135f - nz * 0.055f;
        float highlightDir = Mth.clamp(nx * 0.28f + ny * 0.46f + nz * 0.66f, 0.0f, 1.0f);
        float spec = highlightDir * highlightDir;
        spec *= spec;
        float shade = Mth.clamp(directional + spec * 0.34f, 0.54f, 1.16f);

        bb.vertex(m, rr * nx, rr * ny, rr * nz)
                .uv(u, v)
                .color(
                        toColor(r * shade),
                        toColor(g * shade),
                        toColor(b * shade),
                        toColor(alpha)
                )
                .endVertex();
    }

    private static void ribbon(
            PoseStack pose,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float r, float g, float b, float a, float w
    ) {
        if (a <= 0.005f) return;

        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        vertex(bb, m, x0, y0 - w, z0, r, g, b, a);
        vertex(bb, m, x0, y0 + w, z0, r, g, b, a);
        vertex(bb, m, x1, y1 + w, z1, r, g, b, a);
        vertex(bb, m, x1, y1 - w, z1, r, g, b, a);

        vertex(bb, m, x0 - w, y0, z0, r, g, b, a * 0.70f);
        vertex(bb, m, x0 + w, y0, z0, r, g, b, a * 0.70f);
        vertex(bb, m, x1 + w, y1, z1, r, g, b, a * 0.70f);
        vertex(bb, m, x1 - w, y1, z1, r, g, b, a * 0.70f);

        BufferUploader.drawWithShader(bb.end());
    }

    private static void vertex(
            BufferBuilder bb,
            Matrix4f m,
            float x, float y, float z,
            float r, float g, float b, float a
    ) {
        bb.vertex(m, x, y, z)
                .color(toColor(r), toColor(g), toColor(b), toColor(a))
                .endVertex();
    }

    private static void setTexturedSolid() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, ENERGY_TEXTURE);
    }

    private static void setTexturedGlow() {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE
        );
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, ENERGY_TEXTURE);
    }

    private static void setGlow() {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE
        );
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void restoreState() {
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static int toColor(float v) {
        return Mth.clamp((int) (v * 255.0f), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private record Orb(float x, float y, float z, float size, float r, float g, float b) {}
}
