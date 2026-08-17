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
 * V9: secuencia sincronizada con el video de referencia.
 * Azul y Rojo permanecen puros y visibles a ambos lados de Gojo,
 * se acercan de frente, se tocan y SOLO entonces nace Hollow Purple.
 * Sin columna/rayo vertical ni estelas largas.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoAttack {
    private static final float TAU = (float) (Math.PI * 2.0);

    private static final float GOJO_X = 4.0f;
    private static final float FUSION_X = 2.65f;
    private static final float FUSION_Y = 2.08f;

    // Ritmo aproximado del video gojokunk(2).mp4 (17.886 s).
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

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void drawAttack(PoseStack pose, float t) {
        // 1) AZUL + ROJO: los dos se ven a la vez y se juntan en el centro.
        if (t < ORBS_END_TICK) {
            float appear = smooth(10.0f, 34.0f, t);
            float converge = smooth(38.0f, CONTACT_TICK, t);
            float vanish = 1.0f - smooth(CONTACT_TICK, ORBS_END_TICK, t);

            float centerX = Mth.lerp(converge, GOJO_X - 0.12f, FUSION_X);
            float centerY = Mth.lerp(converge, 2.16f, FUSION_Y);

            // Separacion sobre Z: desde la camara quedan lado a lado y no uno detras del otro.
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

            drawOrb(pose, blue);
            drawOrb(pose, red);

            // Violeta solamente en el instante de contacto.
            if (t >= 144.0f) {
                float q = smooth(144.0f, CONTACT_TICK, t)
                        * (1.0f - smooth(160.0f, ORBS_END_TICK, t));
                drawFusionWeb(pose, blue, red, t, q);
            }
        }

        // 2) MORADO: no existe antes de que Azul y Rojo se hayan tocado.
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

            // ~11 s a ~17 s: viaja hacia el jugador. Luego permanece mientras duren los golpes.
            float px = Mth.lerp(launch, FUSION_X, 0.0f);
            float py = Mth.lerp(launch, FUSION_Y, 1.58f);

            pose.pushPose();
            pose.translate(px, py, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.22f));
            purple(pose, radius);
            pose.popPose();
        }
    }

    private static void drawOrb(PoseStack pose, Orb o) {
        if (o.size <= 0.02f) return;
        pose.pushPose();
        pose.translate(o.x, o.y, o.z);

        solidSphere(pose, o.size, o.r, o.g, o.b);

        float cr = Mth.clamp(o.r * 1.18f + 0.025f, 0.0f, 1.0f);
        float cg = Mth.clamp(o.g * 1.18f + 0.025f, 0.0f, 1.0f);
        float cb = Mth.clamp(o.b * 1.08f + 0.018f, 0.0f, 1.0f);
        solidSphere(pose, o.size * 0.22f, cr, cg, cb);
        glowSphere(pose, o.size * 1.05f, o.r, o.g, o.b, 0.14f);

        pose.popPose();
    }

    private static void drawFusionWeb(PoseStack pose, Orb blue, Orb red, float t, float q) {
        if (q <= 0.01f) return;

        for (int strand = 0; strand < 4; strand++) {
            float phase = strand * 1.55f + t * 0.11f;
            float px = blue.x;
            float py = blue.y;
            float pz = blue.z;

            for (int i = 1; i <= 10; i++) {
                float u = i / 10.0f;
                float bulge = Mth.sin(u * Mth.PI) * q;
                float x = Mth.lerp(u, blue.x, red.x) + Mth.cos(phase + u * TAU) * 0.10f * bulge;
                float y = Mth.lerp(u, blue.y, red.y) + Mth.sin(phase * 0.75f + u * TAU) * 0.09f * bulge;
                float z = Mth.lerp(u, blue.z, red.z) + Mth.sin(phase + u * TAU) * 0.10f * bulge;
                float center = 1.0f - Math.abs(u * 2.0f - 1.0f);

                ribbon(
                        pose,
                        px, py, pz,
                        x, y, z,
                        Mth.lerp(center, 0.10f, 0.70f),
                        0.035f,
                        1.0f,
                        0.070f * q,
                        0.022f + 0.022f * center
                );
                px = x;
                py = y;
                pz = z;
            }
        }
    }

    private static void purple(PoseStack pose, float r) {
        if (r <= 0.04f) return;

        // Morado puro, sin nucleo blanco y sin cilindro vertical.
        solidSphere(pose, r, 0.43f, 0.018f, 0.90f);
        solidSphere(pose, r * 0.72f, 0.66f, 0.055f, 1.00f);
        solidSphere(pose, r * 0.16f, 0.88f, 0.30f, 1.00f);
        glowSphere(pose, r * 1.045f, 0.82f, 0.09f, 1.0f, 0.16f);
        glowSphere(pose, r * 1.075f, 0.34f, 0.04f, 1.0f, 0.075f);
    }

    private static void solidSphere(PoseStack pose, float radius, float r, float g, float b) {
        if (radius <= 0.02f) return;
        setSolid();
        sphere(pose, radius, r, g, b, 1.0f);
    }

    private static void glowSphere(PoseStack pose, float radius, float r, float g, float b, float a) {
        if (radius <= 0.02f || a <= 0.005f) return;
        setGlow();
        sphere(pose, radius, r, g, b, a);
    }

    private static void sphere(PoseStack pose, float radius, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int lon = 88;
        final int lat = 48;
        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float) iy / lat - 0.5f) * Mth.PI;
            float p1 = ((float) (iy + 1) / lat - 0.5f) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                sv(bb, m, radius, p0, a0, r, g, b, a);
                sv(bb, m, radius, p0, a1, r, g, b, a);
                sv(bb, m, radius, p1, a1, r, g, b, a);
                sv(bb, m, radius, p1, a0, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void sv(BufferBuilder bb, Matrix4f m, float radius, float lat, float lon,
                           float r, float g, float b, float a) {
        float c = Mth.cos(lat);
        vertex(
                bb,
                m,
                radius * c * Mth.cos(lon),
                radius * Mth.sin(lat),
                radius * c * Mth.sin(lon),
                r, g, b, a
        );
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

        vertex(bb, m, x0 - w, y0, z0, r, g, b, a * 0.70f);
        vertex(bb, m, x0 + w, y0, z0, r, g, b, a * 0.70f);
        vertex(bb, m, x1 + w, y1, z1, r, g, b, a * 0.70f);
        vertex(bb, m, x1 - w, y1, z1, r, g, b, a * 0.70f);
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

    private static int toColor(float v) {
        return Mth.clamp((int) (v * 255.0f), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private record Orb(float x, float y, float z, float size, float r, float g, float b) {}
}
