package igy.purpure.client;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * V15 - fusión continua Blue + Red -> Hollow Purple y fase de viaje.
 * Sin partículas 2D: toda la energía es geometría 3D procedural.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoAttack {
    private static final float TAU = (float) (Math.PI * 2.0);

    private static final float GOJO_X = 4.0f;
    private static final float FUSION_X = 2.65f;
    private static final float FUSION_Y = 2.08f;
    // Tras el impacto el renderer está anclado al jugador: +X deja al jugador
    // en la zona frontal de la esfera mientras ambos viajan hacia -X.
    private static final float PURPLE_END_X = 0.75f;

    private static final float CONTACT_TICK = 150.0f;
    private static final float ORBS_END_TICK = 178.0f;
    private static final float PURPLE_BIRTH_TICK = 150.0f;
    private static final float PURPLE_GROW_END = 195.0f;
    private static final float LAUNCH_START = 194.0f;
    private static final float LAUNCH_END = 220.0f;

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

            float camX = (float) (camera.getPosition().x - target.getX());
            float camY = (float) (camera.getPosition().y - target.getY());
            float camZ = (float) (camera.getPosition().z - target.getZ());

            pose.pushPose();
            pose.translate(
                    target.getX() - camera.getPosition().x,
                    target.getY() - camera.getPosition().y,
                    target.getZ() - camera.getPosition().z
            );
            drawAttack(pose, t, camX, camY, camZ);
            pose.popPose();
        }

        restoreState();
    }

    private static void drawAttack(PoseStack pose, float t, float camX, float camY, float camZ) {
        // Blue y Red permanecen visibles durante el contacto mientras el violeta
        // nace justo en la zona donde ambos volúmenes se interpenetran.
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
                    0.018f, 0.17f, 1.00f
            );
            Orb red = new Orb(
                    centerX - xWobble,
                    centerY - yWobble,
                    separation,
                    size,
                    1.00f, 0.018f, 0.045f
            );

            drawOrb(pose, blue, t, 0.0f, true);
            drawOrb(pose, red, t, 2.35f, false);

            if (t >= 132.0f) {
                float q = smooth(132.0f, CONTACT_TICK, t)
                        * (1.0f - smooth(170.0f, ORBS_END_TICK, t));
                drawFusion(pose, blue, red, t, q);
            }
        }

        // Es EL MISMO Purple que nace de la mezcla. Una vez lanzado queda a una
        // posición estable respecto al jugador y viaja junto con él.
        if (t >= PURPLE_BIRTH_TICK) {
            float born = smooth(PURPLE_BIRTH_TICK, 168.0f, t);
            float grow = smooth(160.0f, PURPLE_GROW_END, t);
            float launch = smooth(LAUNCH_START, LAUNCH_END, t);

            float small = Mth.lerp(born, 0.06f, 0.78f);
            float radius = Mth.lerp(grow, small, 3.25f);

            if (t >= 164.0f && t <= 194.0f) {
                float pulseFade = 1.0f - smooth(187.0f, 198.0f, t);
                radius *= 1.0f + Mth.sin((t - 164.0f) * 0.42f) * 0.040f * pulseFade;
            }

            radius *= Mth.lerp(launch, 1.0f, 0.88f);

            float px = Mth.lerp(launch, FUSION_X, PURPLE_END_X);
            float py = Mth.lerp(launch, FUSION_Y, 1.58f);

            float dx = camX - px;
            float dy = camY - py;
            float dz = camZ;
            float cameraDistance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            float cameraFade = cameraProtection(cameraDistance, radius);

            pose.pushPose();
            pose.translate(px, py, 0.0f);
            drawPurple(pose, radius, t, cameraFade, launch);
            pose.popPose();
        }
    }

    private static void drawOrb(PoseStack pose, Orb o, float t, float phase, boolean blue) {
        if (o.size <= 0.02f) return;

        pose.pushPose();
        pose.translate(o.x, o.y, o.z);

        animeSphere(pose, o.size, o.r, o.g, o.b, 1.0f,
                t * 0.055f + phase, false, 0.055f);

        float ir = blue ? 0.10f : 1.00f;
        float ig = blue ? 0.62f : 0.12f;
        float ib = blue ? 1.00f : 0.18f;
        animeSphere(pose, o.size * 0.58f, ir, ig, ib, 0.98f,
                -t * 0.075f + phase, false, 0.030f);

        animeSphere(pose, o.size * 1.035f,
                Mth.clamp(o.r * 1.25f + 0.04f, 0.0f, 1.0f),
                Mth.clamp(o.g * 1.22f + 0.05f, 0.0f, 1.0f),
                Mth.clamp(o.b * 1.15f + 0.04f, 0.0f, 1.0f),
                0.25f, t * 0.12f + phase, true, 0.035f);
        animeSphere(pose, o.size * 1.075f, o.r, o.g, o.b,
                0.105f, -t * 0.09f + phase, true, 0.022f);
        animeSphere(pose, o.size * 1.14f,
                blue ? 0.10f : 1.0f,
                blue ? 0.38f : 0.06f,
                blue ? 1.0f : 0.16f,
                0.035f, t * 0.065f + phase, true, 0.010f);

        drawOrbArcs(pose, o.size, o.r, o.g, o.b, t, phase);
        pose.popPose();
    }

    private static void drawOrbArcs(PoseStack pose, float radius,
                                    float r, float g, float b, float t, float phase) {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(32.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(t * 0.72f + phase * 18.0f));
        surfaceArc(pose, radius * 1.075f, 0.040f, 1.55f, 0.22f + phase,
                r, g, b, 0.29f, t * 0.030f);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-48.0f));
        pose.mulPose(Axis.XP.rotationDegrees(-t * 0.51f + phase * 24.0f));
        surfaceArc(pose, radius * 1.09f, 0.030f, 1.18f, 2.55f + phase,
                Mth.clamp(r + 0.20f, 0.0f, 1.0f),
                Mth.clamp(g + 0.20f, 0.0f, 1.0f),
                Mth.clamp(b + 0.16f, 0.0f, 1.0f),
                0.22f, -t * 0.024f);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(61.0f));
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.43f - phase * 31.0f));
        surfaceArc(pose, radius * 1.065f, 0.026f, 0.92f, 4.15f + phase,
                r, g, b, 0.18f, t * 0.018f);
        pose.popPose();
    }

    private static void drawFusion(PoseStack pose, Orb blue, Orb red, float t, float q) {
        if (q <= 0.01f) return;

        for (int strand = 0; strand < 12; strand++) {
            float phase = strand * 0.61f + t * 0.13f;
            float px = blue.x;
            float py = blue.y;
            float pz = blue.z;

            for (int i = 1; i <= 18; i++) {
                float u = i / 18.0f;
                float bulge = Mth.sin(u * Mth.PI) * q;
                float x = Mth.lerp(u, blue.x, red.x)
                        + Mth.cos(phase + u * TAU * 1.45f) * 0.17f * bulge;
                float y = Mth.lerp(u, blue.y, red.y)
                        + Mth.sin(phase * 0.78f + u * TAU * 1.15f) * 0.14f * bulge;
                float z = Mth.lerp(u, blue.z, red.z)
                        + Mth.sin(phase + u * TAU * 1.55f) * 0.17f * bulge;

                float cr;
                float cg;
                float cb;
                if (u < 0.5f) {
                    float k = smooth(0.0f, 1.0f, u * 2.0f);
                    cr = Mth.lerp(k, 0.04f, 0.68f);
                    cg = Mth.lerp(k, 0.32f, 0.06f);
                    cb = 1.00f;
                } else {
                    float k = smooth(0.0f, 1.0f, (u - 0.5f) * 2.0f);
                    cr = Mth.lerp(k, 0.68f, 1.00f);
                    cg = Mth.lerp(k, 0.06f, 0.025f);
                    cb = Mth.lerp(k, 1.00f, 0.10f);
                }

                float center = 1.0f - Math.abs(u * 2.0f - 1.0f);
                ribbon(pose, px, py, pz, x, y, z,
                        cr, cg, cb,
                        (0.07f + center * 0.095f) * q,
                        0.018f + center * 0.038f);
                px = x;
                py = y;
                pz = z;
            }
        }

        float mx = (blue.x + red.x) * 0.5f;
        float my = (blue.y + red.y) * 0.5f;
        float mz = (blue.z + red.z) * 0.5f;
        pose.pushPose();
        pose.translate(mx, my, mz);

        // Este núcleo no se sustituye por otro asset: se apaga a la vez que el
        // Hollow Purple principal nace en exactamente el mismo punto.
        float coreFade = 1.0f - smooth(PURPLE_BIRTH_TICK, 168.0f, t);
        mixedSphere(pose, 0.11f + q * 0.43f, t * 0.095f,
                0.92f * q * coreFade, false, 0.060f);
        mixedSphere(pose, 0.16f + q * 0.58f, -t * 0.075f,
                0.28f * q * coreFade, true, 0.045f);
        animeSphere(pose, 0.055f + q * 0.18f,
                0.96f, 0.62f, 1.0f, 0.90f * q * coreFade,
                t * 0.18f, true, 0.025f);
        pose.popPose();
    }

    private static void drawPurple(PoseStack pose, float r, float t, float cameraFade, float launch) {
        if (r <= 0.04f) return;

        float bodyAlpha = Mth.lerp(cameraFade, 0.12f, 1.0f);
        float glowFade = Mth.clamp(cameraFade * cameraFade, 0.015f, 1.0f);

        if (cameraFade > 0.94f) {
            mixedSphere(pose, r, t * 0.030f, 1.0f, false, 0.060f);
            animeSphere(pose, r * 0.73f, 0.57f, 0.025f, 0.98f, 0.94f,
                    -t * 0.064f, false, 0.030f);
        } else {
            mixedSphereSoft(pose, r, t * 0.030f, bodyAlpha, 0.060f);
            animeSphereSoft(pose, r * 0.73f, 0.57f, 0.025f, 0.98f,
                    bodyAlpha * 0.82f, -t * 0.064f, 0.030f);
        }

        animeSphere(pose, r * 0.36f, 0.78f, 0.12f, 1.00f,
                0.72f * cameraFade, t * 0.085f, true, 0.022f);
        animeSphere(pose, r * 0.13f, 0.98f, 0.63f, 1.00f,
                0.82f * cameraFade, -t * 0.11f, true, 0.012f);

        mixedSphere(pose, r * 1.018f, -t * 0.052f,
                0.23f * glowFade, true, 0.050f);
        mixedSphere(pose, r * 1.048f, t * 0.041f,
                0.12f * glowFade, true, 0.034f);
        animeSphere(pose, r * 1.075f, 0.61f, 0.035f, 1.00f,
                0.075f * glowFade, -t * 0.033f, true, 0.020f);
        animeSphere(pose, r * 1.115f, 0.27f, 0.025f, 0.92f,
                0.030f * glowFade, t * 0.027f, true, 0.010f);

        drawPurpleArcs(pose, r, t, glowFade);
        drawChromaticVeins(pose, r, t, glowFade, launch);
    }

    private static void drawPurpleArcs(PoseStack pose, float r, float t, float fade) {
        for (int i = 0; i < 7; i++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(14.0f + i * 24.0f));
            pose.mulPose(Axis.YP.rotationDegrees(-31.0f + i * 37.0f));
            pose.mulPose(Axis.ZP.rotationDegrees(t * (0.25f + i * 0.031f) + i * 49.0f));
            surfaceArc(pose,
                    r * (1.017f + i * 0.005f),
                    Math.max(0.026f, r * 0.010f),
                    0.74f + i * 0.095f,
                    0.35f + i * 0.91f,
                    0.84f, 0.10f, 1.0f,
                    0.15f * fade,
                    t * (0.013f + i * 0.002f));
            pose.popPose();
        }

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(37.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(-t * 0.22f));
        surfaceArc(pose, r * 1.030f, Math.max(0.022f, r * 0.009f),
                1.08f, 2.0f, 0.05f, 0.34f, 1.0f, 0.12f * fade, t * 0.016f);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-46.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(t * 0.20f));
        surfaceArc(pose, r * 1.034f, Math.max(0.022f, r * 0.009f),
                1.06f, 4.2f, 1.0f, 0.045f, 0.10f, 0.11f * fade, -t * 0.015f);
        pose.popPose();
    }

    private static void drawChromaticVeins(PoseStack pose, float r, float t, float fade, float launch) {
        if (fade <= 0.02f) return;

        for (int i = 0; i < 6; i++) {
            float shift = i * 1.03f;
            float mix = 0.5f + 0.5f * Mth.sin(t * 0.052f + shift);
            float cr = Mth.lerp(mix, 0.05f, 1.0f);
            float cg = Mth.lerp(mix, 0.31f, 0.04f);
            float cb = Mth.lerp(mix, 1.0f, 0.12f);

            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(-62.0f + i * 23.0f));
            pose.mulPose(Axis.YP.rotationDegrees(19.0f + i * 31.0f));
            pose.mulPose(Axis.ZP.rotationDegrees(t * (0.31f + i * 0.017f)));
            surfaceArc(pose,
                    r * (1.010f + i * 0.003f),
                    Math.max(0.020f, r * 0.0075f),
                    0.52f + 0.18f * Mth.sin(t * 0.02f + shift),
                    shift,
                    cr, cg, cb,
                    (0.10f + launch * 0.035f) * fade,
                    t * 0.017f + shift);
            pose.popPose();
        }
    }

    private static void animeSphere(PoseStack pose,
                                    float radius,
                                    float r, float g, float b,
                                    float alpha,
                                    float phase,
                                    boolean glow,
                                    float surfaceEnergy) {
        if (radius <= 0.02f || alpha <= 0.005f) return;
        if (glow) setGlow();
        else setSolid();
        drawAnimeSphereMesh(pose, radius, r, g, b, alpha, phase, surfaceEnergy, glow);
    }

    private static void animeSphereSoft(PoseStack pose,
                                        float radius,
                                        float r, float g, float b,
                                        float alpha,
                                        float phase,
                                        float surfaceEnergy) {
        if (radius <= 0.02f || alpha <= 0.005f) return;
        setTranslucent();
        drawAnimeSphereMesh(pose, radius, r, g, b, alpha, phase, surfaceEnergy, true);
    }

    private static void drawAnimeSphereMesh(PoseStack pose,
                                            float radius,
                                            float r, float g, float b,
                                            float alpha,
                                            float phase,
                                            float surfaceEnergy,
                                            boolean lowPoly) {
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int lon = lowPoly ? 56 : 72;
        final int lat = lowPoly ? 32 : 40;

        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float) iy / lat - 0.5f) * Mth.PI;
            float p1 = ((float) (iy + 1) / lat - 0.5f) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                sphereVertex(bb, m, radius, p0, a0, r, g, b, alpha, phase, surfaceEnergy);
                sphereVertex(bb, m, radius, p0, a1, r, g, b, alpha, phase, surfaceEnergy);
                sphereVertex(bb, m, radius, p1, a1, r, g, b, alpha, phase, surfaceEnergy);
                sphereVertex(bb, m, radius, p1, a0, r, g, b, alpha, phase, surfaceEnergy);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void mixedSphere(PoseStack pose,
                                    float radius,
                                    float phase,
                                    float alpha,
                                    boolean glow,
                                    float surfaceEnergy) {
        if (radius <= 0.02f || alpha <= 0.005f) return;
        if (glow) setGlow();
        else setSolid();
        drawMixedSphereMesh(pose, radius, phase, alpha, surfaceEnergy, glow);
    }

    private static void mixedSphereSoft(PoseStack pose,
                                        float radius,
                                        float phase,
                                        float alpha,
                                        float surfaceEnergy) {
        if (radius <= 0.02f || alpha <= 0.005f) return;
        setTranslucent();
        drawMixedSphereMesh(pose, radius, phase, alpha, surfaceEnergy, true);
    }

    private static void drawMixedSphereMesh(PoseStack pose,
                                            float radius,
                                            float phase,
                                            float alpha,
                                            float surfaceEnergy,
                                            boolean lowPoly) {
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int lon = lowPoly ? 60 : 76;
        final int lat = lowPoly ? 34 : 42;

        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float) iy / lat - 0.5f) * Mth.PI;
            float p1 = ((float) (iy + 1) / lat - 0.5f) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                mixedSphereVertex(bb, m, radius, p0, a0, alpha, phase, surfaceEnergy);
                mixedSphereVertex(bb, m, radius, p0, a1, alpha, phase, surfaceEnergy);
                mixedSphereVertex(bb, m, radius, p1, a1, alpha, phase, surfaceEnergy);
                mixedSphereVertex(bb, m, radius, p1, a0, alpha, phase, surfaceEnergy);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void sphereVertex(BufferBuilder bb,
                                     Matrix4f m,
                                     float radius,
                                     float lat,
                                     float lon,
                                     float r, float g, float b, float alpha,
                                     float phase,
                                     float surfaceEnergy) {
        float c = Mth.cos(lat);
        float nx = c * Mth.cos(lon);
        float ny = Mth.sin(lat);
        float nz = c * Mth.sin(lon);

        float wave = Mth.sin(lon * 5.0f + lat * 7.0f + phase) * 0.55f
                + Mth.sin(lon * 11.0f - lat * 4.0f - phase * 0.65f) * 0.45f;
        float rr = radius * (1.0f + surfaceEnergy * wave * 0.16f);

        float rim = 1.0f - Math.abs(nx);
        rim = rim * rim;
        float highlight = Mth.clamp(-nx * 0.18f + ny * 0.35f - nz * 0.18f, 0.0f, 1.0f);
        highlight *= highlight;
        float energy = 0.5f + 0.5f * Mth.sin(lon * 4.0f + lat * 8.0f + phase);
        float shade = 0.70f + rim * 0.28f + highlight * 0.23f + energy * surfaceEnergy * 0.55f;
        shade = Mth.clamp(shade, 0.56f, 1.24f);

        bb.vertex(m, rr * nx, rr * ny, rr * nz)
                .color(toColor(r * shade), toColor(g * shade), toColor(b * shade), toColor(alpha))
                .endVertex();
    }

    private static void mixedSphereVertex(BufferBuilder bb,
                                          Matrix4f m,
                                          float radius,
                                          float lat,
                                          float lon,
                                          float alpha,
                                          float phase,
                                          float surfaceEnergy) {
        float c = Mth.cos(lat);
        float nx = c * Mth.cos(lon);
        float ny = Mth.sin(lat);
        float nz = c * Mth.sin(lon);

        float wave = Mth.sin(lon * 5.0f + lat * 7.0f + phase) * 0.52f
                + Mth.sin(lon * 10.0f - lat * 5.0f - phase * 0.73f) * 0.48f;
        float rr = radius * (1.0f + surfaceEnergy * wave * 0.18f);

        float field = 0.5f + 0.5f * Mth.sin(lon * 1.85f + lat * 2.55f + phase);
        float fine = 0.5f + 0.5f * Mth.sin(lon * 5.2f - lat * 3.7f - phase * 1.35f);
        float mix = Mth.clamp(field * 0.78f + fine * 0.22f, 0.0f, 1.0f);
        float violet = 1.0f - Math.abs(mix * 2.0f - 1.0f);
        violet *= violet;

        float cr = Mth.lerp(mix, 0.035f, 1.00f) + violet * 0.28f;
        float cg = Mth.lerp(mix, 0.30f, 0.035f) + violet * 0.02f;
        float cb = Mth.lerp(mix, 1.00f, 0.10f) + violet * 0.32f;

        float rim = 1.0f - Math.abs(nx);
        rim = rim * rim;
        float highlight = Mth.clamp(-nx * 0.20f + ny * 0.34f - nz * 0.16f, 0.0f, 1.0f);
        highlight *= highlight;
        float energy = 0.5f + 0.5f * Mth.sin(lon * 4.5f + lat * 7.5f + phase * 1.4f);
        float shade = 0.68f + rim * 0.31f + highlight * 0.24f + energy * surfaceEnergy * 0.65f;
        shade = Mth.clamp(shade, 0.54f, 1.28f);

        bb.vertex(m, rr * nx, rr * ny, rr * nz)
                .color(
                        toColor(Mth.clamp(cr * shade, 0.0f, 1.0f)),
                        toColor(Mth.clamp(cg * shade, 0.0f, 1.0f)),
                        toColor(Mth.clamp(cb * shade, 0.0f, 1.0f)),
                        toColor(alpha)
                )
                .endVertex();
    }

    private static void surfaceArc(PoseStack pose,
                                   float radius,
                                   float width,
                                   float span,
                                   float start,
                                   float r, float g, float b, float alpha,
                                   float phase) {
        if (alpha <= 0.005f || radius <= 0.02f) return;

        final int segments = 24;
        float px = 0.0f;
        float py = 0.0f;
        float pz = 0.0f;
        boolean have = false;

        for (int i = 0; i <= segments; i++) {
            float s = i / (float) segments;
            float a = start + span * s + phase;
            float rr = radius * (1.0f + Mth.sin(a * 3.0f + phase) * 0.018f);
            float x = Mth.cos(a) * rr;
            float y = Mth.sin(a) * rr;
            float z = Mth.sin(s * Mth.PI) * radius * 0.085f * Mth.sin(phase + a * 0.7f);

            if (have) {
                float fade = Mth.sin(s * Mth.PI);
                ribbon(pose, px, py, pz, x, y, z,
                        r, g, b, alpha * (0.30f + fade * 0.70f), width);
            }
            px = x;
            py = y;
            pz = z;
            have = true;
        }
    }

    private static void ribbon(PoseStack pose,
                               float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float r, float g, float b, float a, float w) {
        if (a <= 0.005f) return;
        setGlow();

        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        vertex(bb, m, x0, y0 - w, z0, r, g, b, a);
        vertex(bb, m, x0, y0 + w, z0, r, g, b, a);
        vertex(bb, m, x1, y1 + w, z1, r, g, b, a);
        vertex(bb, m, x1, y1 - w, z1, r, g, b, a);

        vertex(bb, m, x0, y0, z0 - w, r, g, b, a * 0.78f);
        vertex(bb, m, x0, y0, z0 + w, r, g, b, a * 0.78f);
        vertex(bb, m, x1, y1, z1 + w, r, g, b, a * 0.78f);
        vertex(bb, m, x1, y1, z1 - w, r, g, b, a * 0.78f);

        BufferUploader.drawWithShader(bb.end());
    }

    private static void vertex(BufferBuilder bb, Matrix4f m,
                               float x, float y, float z,
                               float r, float g, float b, float a) {
        bb.vertex(m, x, y, z)
                .color(toColor(r), toColor(g), toColor(b), toColor(a))
                .endVertex();
    }

    private static float cameraProtection(float cameraDistance, float radius) {
        float surfaceDistance = cameraDistance - radius;
        if (surfaceDistance >= 2.10f) return 1.0f;
        if (surfaceDistance <= 0.18f) return 0.08f;
        return Mth.lerp(smooth(0.18f, 2.10f, surfaceDistance), 0.08f, 1.0f);
    }

    private static void setSolid() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setTranslucent() {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setGlow() {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE
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
