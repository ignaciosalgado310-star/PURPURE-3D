package igy.purpure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
 * V15 anime polish. Geometría 3D alrededor de Hollow Purple sincronizada con
 * la nueva fusión y, tras el impacto, anclada al jugador para viajar con él.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAnimePolish {
    private static final float FUSION_X = 2.65f;
    private static final float FUSION_Y = 2.08f;
    private static final float PURPLE_BIRTH_TICK = 150.0f;
    private static final float PURPLE_GROW_END = 195.0f;
    private static final float LAUNCH_START = 194.0f;
    private static final float LAUNCH_END = 220.0f;
    private static final float TRAVEL_X = 0.75f;

    private ClientAnimePolish() {}

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
            if (t < PURPLE_BIRTH_TICK) continue;

            float born = smooth(PURPLE_BIRTH_TICK, 168.0f, t);
            float grow = smooth(160.0f, PURPLE_GROW_END, t);
            float launch = smooth(LAUNCH_START, LAUNCH_END, t);
            float small = Mth.lerp(born, 0.06f, 0.78f);
            float radius = Mth.lerp(grow, small, 3.62f);

            if (t >= 164.0f && t <= 194.0f) {
                float pulseFade = 1.0f - smooth(187.0f, 198.0f, t);
                radius *= 1.0f + Mth.sin((t - 164.0f) * 0.42f) * 0.040f * pulseFade;
            }

            radius *= Mth.lerp(launch, 1.0f, 0.88f);
            float px = Mth.lerp(launch, FUSION_X, TRAVEL_X);
            float py = Mth.lerp(launch, FUSION_Y, 1.58f);

            pose.pushPose();
            pose.translate(
                    target.getX() - camera.getPosition().x + px,
                    target.getY() - camera.getPosition().y + py,
                    target.getZ() - camera.getPosition().z
            );
            drawCorona(pose, radius, t);
            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void drawCorona(PoseStack pose, float r, float t) {
        if (r < 0.20f) return;

        float strength = smooth(156.0f, 184.0f, t);
        float birthFlash = smooth(150.0f, 164.0f, t) * (1.0f - smooth(178.0f, 196.0f, t));

        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int strand = 0; strand < 9; strand++) {
            float phase = strand * 0.73f + t * (0.010f + strand * 0.0008f);
            float latBase = -0.72f + strand * 0.18f;
            float span = 1.35f + (strand % 3) * 0.28f;
            float start = phase + strand * 0.41f;

            float px = 0, py = 0, pz = 0;
            boolean have = false;
            for (int i = 0; i <= 15; i++) {
                float s = i / 15.0f;
                float theta = start + span * s;
                float phi = latBase
                        + Mth.sin(theta * 1.65f + phase) * 0.24f
                        + Mth.sin(s * Mth.PI) * 0.12f;
                float rr = r * (1.018f + Mth.sin(theta * 3.0f + phase) * 0.010f);

                float cp = Mth.cos(phi);
                float x = Mth.cos(theta) * cp * rr;
                float y = Mth.sin(phi) * rr;
                float z = Mth.sin(theta) * cp * rr;

                if (have) {
                    float fade = Mth.sin(s * Mth.PI);
                    float cr = 0.82f, cg = 0.075f, cb = 1.0f;
                    if (strand == 1 || strand == 7) {
                        cr = 0.08f; cg = 0.30f; cb = 1.0f;
                    } else if (strand == 3 || strand == 8) {
                        cr = 1.0f; cg = 0.045f; cb = 0.12f;
                    }
                    addRibbon(bb, m, px, py, pz, x, y, z,
                            cr, cg, cb,
                            (0.045f + 0.075f * fade) * strength,
                            Math.max(0.012f, r * 0.0055f));
                }
                px = x; py = y; pz = z;
                have = true;
            }
        }

        int spikes = 12;
        for (int i = 0; i < spikes; i++) {
            float y = -0.82f + (1.64f * i / (spikes - 1.0f));
            float radial = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
            float a = i * 2.3999632f + t * (i % 2 == 0 ? 0.006f : -0.005f);
            float dx = Mth.cos(a) * radial;
            float dy = y;
            float dz = Mth.sin(a) * radial;

            float len = 1.07f + 0.045f * (0.5f + 0.5f * Mth.sin(t * 0.09f + i));
            float x0 = dx * r * 1.01f;
            float y0 = dy * r * 1.01f;
            float z0 = dz * r * 1.01f;
            float x1 = dx * r * len;
            float y1 = dy * r * len;
            float z1 = dz * r * len;

            addRibbon(bb, m, x0, y0, z0, x1, y1, z1,
                    0.90f, 0.16f, 1.0f,
                    (0.035f + birthFlash * 0.12f) * strength,
                    Math.max(0.010f, r * 0.0045f));
        }

        BufferUploader.drawWithShader(bb.end());
    }

    private static void addRibbon(BufferBuilder bb, Matrix4f m,
                                  float x0, float y0, float z0,
                                  float x1, float y1, float z1,
                                  float r, float g, float b, float a, float w) {
        vertex(bb, m, x0, y0 - w, z0, r, g, b, a);
        vertex(bb, m, x0, y0 + w, z0, r, g, b, a);
        vertex(bb, m, x1, y1 + w, z1, r, g, b, a);
        vertex(bb, m, x1, y1 - w, z1, r, g, b, a);

        vertex(bb, m, x0, y0, z0 - w, r, g, b, a * 0.75f);
        vertex(bb, m, x0, y0, z0 + w, r, g, b, a * 0.75f);
        vertex(bb, m, x1, y1, z1 + w, r, g, b, a * 0.75f);
        vertex(bb, m, x1, y1, z1 - w, r, g, b, a * 0.75f);
    }

    private static void vertex(BufferBuilder bb, Matrix4f m,
                               float x, float y, float z,
                               float r, float g, float b, float a) {
        bb.vertex(m, x, y, z)
                .color(toColor(r), toColor(g), toColor(b), toColor(a))
                .endVertex();
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
        return Mth.clamp((int)(v * 255.0f), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
