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
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * V11 - anime visual pass.
 * Visual-only: keeps V9/V10 timing, positions, command, damage and totem logic.
 * No particles: all energy is procedural 3D geometry.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoAttack {
    private static final float TAU = (float) (Math.PI * 2.0);

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
        // AZUL + ROJO: conservan exactamente el recorrido y momento de contacto.
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

            if (t >= 144.0f) {
                float q = smooth(144.0f, CONTACT_TICK, t)
                        * (1.0f - smooth(160.0f, ORBS_END_TICK, t));
                drawFusion(pose, blue, red, t, q);
            }
        }

        // HOLLOW PURPLE: nace solamente despues del contacto, igual que antes.
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
            drawPurple(pose, radius, t);
            pose.popPose();
        }
    }

    private static void drawOrb(PoseStack pose, Orb o, float t, float phase, boolean blue) {
        if (o.size <= 0.02f) return;

        pose.pushPose();
        pose.translate(o.x, o.y, o.z);

        // Cuerpo limpio y saturado, mas cercano a la animacion que una textura rocosa.
        animeSphere(pose, o.size, o.r, o.g, o.b, 1.0f, t * 0.055f + phase, false, 0.06f);

        float ir = blue ? 0.16f : 1.00f;
        float ig = blue ? 0.56f : 0.16f;
        float ib = blue ? 1.00f : 0.20f;
        animeSphere(pose, o.size * 0.57f, ir, ig, ib, 0.98f,
                -t * 0.075f + phase, false, 0.035f);

        // Halo volumetrico: geometria 3D aditiva, no particulas.
        animeSphere(pose, o.size * 1.055f,
                Mth.clamp(o.r * 1.18f + 0.02f, 0.0f, 1.0f),
                Mth.clamp(o.g * 1.20f + 0.03f, 0.0f, 1.0f),
                Mth.clamp(o.b * 1.10f + 0.02f, 0.0f, 1.0f),
                0.18f, t * 0.11f + phase, true, 0.03f);
        animeSphere(pose, o.size * 1.11f, o.r, o.g, o.b,
                0.055f, -t * 0.08f + phase, true, 0.015f);

        // Trazos curvos incompletos; evitan el aspecto de aros completos.
        drawOrbArcs(pose, o.size, o.r, o.g, o.b, t, phase);
        pose.popPose();
    }

    private static void drawOrbArcs(PoseStack pose, float radius,
                                    float r, float g, float b, float t, float phase) {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(32.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(t * 0.72f + phase * 18.0f));
        surfaceArc(pose, radius * 1.075f, 0.035f, 1.55f, 0.22f + phase,
                r, g, b, 0.22f, t * 0.030f);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-48.0f));
        pose.mulPose(Axis.XP.rotationDegrees(-t * 0.51f + phase * 24.0f));
        surfaceArc(pose, radius * 1.09f, 0.026f, 1.18f, 2.55f + phase,
                Mth.clamp(r + 0.16f, 0.0f, 1.0f),
                Mth.clamp(g + 0.16f, 0.0f, 1.0f),
                Mth.clamp(b + 0.12f, 0.0f, 1.0f),
                0.16f, -t * 0.024f);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(61.0f));
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.43f - phase * 31.0f));
        surfaceArc(pose, radius * 1.065f, 0.022f, 0.92f, 4.15f + phase,
                r, g, b, 0.13f, t * 0.018f);
        pose.popPose();
    }

    private static void drawFusion(PoseStack pose, Orb blue, Orb red, float t, float q) {
        if (q <= 0.01f) return;

        // Filamentos azul -> violeta -> rojo alrededor del punto donde chocan.
        for (int strand = 0; strand < 8; strand++) {
            float phase = strand * 0.82f + t * 0.12f;
            float px = blue.x;
            float py = blue.y;
            float pz = blue.z;

            for (int i = 1; i <= 16; i++) {
                float u = i / 16.0f;
                float bulge = Mth.sin(u * Mth.PI) * q;
                float x = Mth.lerp(u, blue.x, red.x)
                        + Mth.cos(phase + u * TAU * 1.35f) * 0.14f * bulge;
                float y = Mth.lerp(u, blue.y, red.y)
                        + Mth.sin(phase * 0.74f + u * TAU * 1.10f) * 0.115f * bulge;
                float z = Mth.lerp(u, blue.z, red.z)
                        + Mth.sin(phase + u * TAU * 1.45f) * 0.14f * bulge;

                float cr;
                float cg;
                float cb;
                if (u < 0.5f) {
                    float k = u * 2.0f;
                    cr = Mth.lerp(k, 0.05f, 0.72f);
                    cg = Mth.lerp(k, 0.30f, 0.05f);
                    cb = Mth.lerp(k, 1.00f, 1.00f);
                } else {
                    float k = (u - 0.5f) * 2.0f;
                    cr = Mth.lerp(k, 0.72f, 1.00f);
                    cg = Mth.lerp(k, 0.05f, 0.025f);
                    cb = Mth.lerp(k, 1.00f, 0.08f);
                }

                float center = 1.0f - Math.abs(u * 2.0f - 1.0f);
                ribbon(pose, px, py, pz, x, y, z,
                        cr, cg, cb,
                        (0.055f + center * 0.055f) * q,
                        0.014f + center * 0.029f);
                px = x;
                py = y;
                pz = z;
            }
        }

        // Punto de fusion violeta corto y concentrado.
        float mx = (blue.x + red.x) * 0.5f;
        float my = (blue.y + red.y) * 0.5f;
        float mz = (blue.z + red.z) * 0.5f;
        pose.pushPose();
        pose.translate(mx, my, mz);
        animeSphere(pose, 0.09f + q * 0.28f, 0.67f, 0.035f, 1.0f,
                0.72f * q, t * 0.18f, true, 0.05f);
        pose.popPose();
    }

    private static void drawPurple(PoseStack pose, float r, float t) {
        if (r <= 0.04f) return;

        // Nucleo oscuro-violeta y volumen luminoso, inspirado en el look limpio del anime.
        animeSphere(pose, r, 0.34f, 0.008f, 0.70f, 1.0f,
                t * 0.040f, false, 0.055f);
        animeSphere(pose, r * 0.74f, 0.66f, 0.035f, 1.00f, 0.98f,
                -t * 0.065f, false, 0.035f);
        animeSphere(pose, r * 0.39f, 0.82f, 0.16f, 1.00f, 0.96f,
                t * 0.085f, false, 0.022f);
        animeSphere(pose, r * 0.15f, 0.96f, 0.58f, 1.00f, 0.96f,
                -t * 0.11f, false, 0.012f);

        // Capas de energia translucida alrededor de Purple.
        animeSphere(pose, r * 1.022f, 0.76f, 0.055f, 1.00f, 0.19f,
                -t * 0.078f, true, 0.045f);
        animeSphere(pose, r * 1.060f, 0.60f, 0.018f, 1.00f, 0.105f,
                t * 0.058f, true, 0.025f);
        animeSphere(pose, r * 1.095f, 0.31f, 0.015f, 0.92f, 0.045f,
                -t * 0.037f, true, 0.012f);

        drawPurpleArcs(pose, r, t);
    }

    private static void drawPurpleArcs(PoseStack pose, float r, float t) {
        // Violetas principales.
        for (int i = 0; i < 5; i++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(18.0f + i * 29.0f));
            pose.mulPose(Axis.YP.rotationDegrees(-26.0f + i * 41.0f));
            pose.mulPose(Axis.ZP.rotationDegrees(t * (0.24f + i * 0.035f) + i * 53.0f));
            surfaceArc(pose,
                    r * (1.018f + i * 0.006f),
                    Math.max(0.025f, r * 0.010f),
                    0.82f + i * 0.11f,
                    0.35f + i * 1.09f,
                    0.82f, 0.09f, 1.0f,
                    0.11f,
                    t * (0.013f + i * 0.002f));
            pose.popPose();
        }

        // Ecos azul y rojo de las dos tecnicas que se fusionaron; muy sutiles.
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(37.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(-t * 0.19f));
        surfaceArc(pose, r * 1.028f, Math.max(0.020f, r * 0.008f),
                0.72f, 2.0f, 0.08f, 0.28f, 1.0f, 0.075f, t * 0.015f);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-46.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(t * 0.17f));
        surfaceArc(pose, r * 1.032f, Math.max(0.020f, r * 0.008f),
                0.76f, 4.2f, 1.0f, 0.055f, 0.10f, 0.068f, -t * 0.014f);
        pose.popPose();
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

        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int lon = glow ? 56 : 72;
        final int lat = glow ? 32 : 40;

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

        // Ondulacion muy pequena: conserva esfera limpia pero evita aspecto plastico.
        float wave = Mth.sin(lon * 5.0f + lat * 7.0f + phase) * 0.55f
                + Mth.sin(lon * 11.0f - lat * 4.0f - phase * 0.65f) * 0.45f;
        float rr = radius * (1.0f + surfaceEnergy * wave * 0.16f);

        // El jugador mira el ataque aproximadamente sobre el eje X; este rim crea
        // la silueta brillante caracteristica del anime sin necesitar shader externo.
        float rim = 1.0f - Math.abs(nx);
        rim = rim * rim;
        float highlight = Mth.clamp(-nx * 0.18f + ny * 0.35f - nz * 0.18f, 0.0f, 1.0f);
        highlight *= highlight;
        float energy = 0.5f + 0.5f * Mth.sin(lon * 4.0f + lat * 8.0f + phase);
        float shade = 0.72f + rim * 0.24f + highlight * 0.20f + energy * surfaceEnergy * 0.45f;
        shade = Mth.clamp(shade, 0.58f, 1.18f);

        bb.vertex(m, rr * nx, rr * ny, rr * nz)
                .color(
                        toColor(r * shade),
                        toColor(g * shade),
                        toColor(b * shade),
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

        final int segments = 22;
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
            float z = Mth.sin(s * Mth.PI) * radius * 0.075f * Mth.sin(phase + a * 0.7f);

            if (have) {
                float fade = Mth.sin(s * Mth.PI);
                ribbon(pose, px, py, pz, x, y, z,
                        r, g, b, alpha * (0.35f + fade * 0.65f), width);
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

        // Dos planos cruzados = filamento visible desde varios angulos y realmente 3D.
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

    private static void setSolid() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
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
