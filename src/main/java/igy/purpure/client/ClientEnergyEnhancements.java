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

@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEnergyEnhancements {
    private static final float TAU = (float) (Math.PI * 2.0);

    private ClientEnergyEnhancements() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();

        setGlowState();

        for (AbstractClientPlayer player : mc.level.players()) {
            float baseTick = ClientPurpureEffects.effectTick(player.getUUID());
            if (baseTick < 0.0f) continue;
            float t = baseTick + partial;

            pose.pushPose();
            pose.translate(
                    player.getX() - camera.getPosition().x,
                    player.getY() - camera.getPosition().y,
                    player.getZ() - camera.getPosition().z
            );

            if (t < 138.0f) {
                drawOrbTrail(pose, t, true);
                drawOrbTrail(pose, t, false);
            }

            if (t >= 82.0f && t <= 150.0f) {
                drawFusionWeb(pose, t);
            }

            if (t >= 118.0f && t <= 220.0f) {
                drawPurpleSpirals(pose, t);
            }

            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void drawOrbTrail(PoseStack pose, float t, boolean blue) {
        for (int i = 0; i < 12; i++) {
            float ta = t - i * 2.6f;
            float tb = t - (i + 1) * 2.6f;
            if (tb < 5.0f) break;

            OrbState a = orbState(ta, blue);
            OrbState b = orbState(tb, blue);
            float fade = 1.0f - i / 12.0f;
            float width = 0.10f + fade * 0.18f;
            drawRibbonSegment(pose, a.x, a.y, a.z, b.x, b.y, b.z,
                    a.r, a.g, a.b, 0.16f * fade, width);
        }
    }

    private static void drawFusionWeb(PoseStack pose, float t) {
        OrbState blue = orbState(t, true);
        OrbState red = orbState(t, false);
        float q = smooth(82.0f, 128.0f, t) * (1.0f - smooth(136.0f, 150.0f, t));
        if (q <= 0.01f) return;

        for (int strand = 0; strand < 5; strand++) {
            float phase = strand * 1.7f + t * 0.08f;
            float prevX = blue.x;
            float prevY = blue.y;
            float prevZ = blue.z;

            for (int i = 1; i <= 14; i++) {
                float s = i / 14.0f;
                float mid = Mth.sin(s * Mth.PI);
                float x = Mth.lerp(s, blue.x, red.x) + Mth.cos(phase + s * TAU) * 0.28f * mid * q;
                float y = Mth.lerp(s, blue.y, red.y) + Mth.sin(phase * 0.8f + s * TAU) * 0.24f * mid * q;
                float z = Mth.lerp(s, blue.z, red.z) + Mth.sin(phase + s * TAU) * 0.28f * mid * q;

                float center = 1.0f - Math.abs(s * 2.0f - 1.0f);
                float r = Mth.lerp(center, blue.r, 0.72f);
                float g = Mth.lerp(center, blue.g, 0.07f);
                float b = 1.0f;
                drawRibbonSegment(pose, prevX, prevY, prevZ, x, y, z,
                        r, g, b, 0.11f * q, 0.055f + center * 0.05f);
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        }
    }

    private static void drawPurpleSpirals(PoseStack pose, float t) {
        float born = smooth(104.0f, 133.0f, t);
        float expansion = smooth(132.0f, 176.0f, t);
        float radius = Mth.lerp(expansion, Mth.lerp(born, 0.10f, 2.05f), 6.95f);
        if (radius < 0.25f) return;

        float yCenter = Mth.lerp(smooth(104.0f, 176.0f, t), 2.55f, 1.85f);
        float fade = 1.0f - smooth(202.0f, 220.0f, t);

        for (int arm = 0; arm < 4; arm++) {
            float phase = arm * TAU / 4.0f + t * (arm % 2 == 0 ? 0.026f : -0.022f);
            float px = 0, py = 0, pz = 0;
            boolean havePrevious = false;

            for (int i = 0; i <= 20; i++) {
                float s = i / 20.0f;
                float angle = phase + s * TAU * 0.86f;
                float rr = radius * (1.10f + Mth.sin(s * Mth.PI) * 0.10f);
                float x = Mth.cos(angle) * rr;
                float y = yCenter + (s - 0.5f) * radius * 1.35f;
                float z = Mth.sin(angle) * rr;

                if (havePrevious) {
                    drawRibbonSegment(pose, px, py, pz, x, y, z,
                            0.84f, 0.16f, 1.0f, 0.07f * fade, Math.max(0.045f, radius * 0.018f));
                }
                px = x;
                py = y;
                pz = z;
                havePrevious = true;
            }
        }
    }

    private static OrbState orbState(float t, boolean blue) {
        float appear = smooth(5.0f, 28.0f, t);
        float convergence = smooth(67.0f, 124.0f, t);
        float fusionColor = smooth(82.0f, 132.0f, t);
        float disappear = 1.0f - smooth(126.0f, 145.0f, t);
        float orbitRadius = Mth.lerp(convergence, 6.9f, 0.0f);
        float attractionBoost = 1.0f + smooth(88.0f, 120.0f, t) * 0.72f;
        float angle = t * 0.075f * attractionBoost + (blue ? 0.0f : Mth.PI);
        float size = Mth.lerp(appear, 0.42f, 2.35f) * Mth.lerp(convergence, 1.0f, 0.70f) * disappear;
        float baseY = Mth.lerp(smooth(0.0f, 38.0f, t), -0.6f, 3.05f);
        float y = blue
                ? Mth.lerp(convergence, baseY + Mth.sin(t * 0.075f) * 0.28f, 2.55f)
                : Mth.lerp(convergence, baseY + Mth.cos(t * 0.072f + 1.2f) * 0.28f, 2.55f);

        float r = blue ? Mth.lerp(fusionColor, 0.025f, 0.52f) : Mth.lerp(fusionColor, 1.00f, 0.52f);
        float g = blue ? Mth.lerp(fusionColor, 0.30f, 0.07f) : Mth.lerp(fusionColor, 0.025f, 0.07f);
        float b = blue ? Mth.lerp(fusionColor, 1.00f, 0.96f) : Mth.lerp(fusionColor, 0.070f, 0.96f);
        return new OrbState(Mth.cos(angle) * orbitRadius, y, Mth.sin(angle) * orbitRadius, size, r, g, b);
    }

    private static void drawRibbonSegment(PoseStack pose,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          float r, float g, float b, float alpha, float w) {
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        vertex(builder, matrix, x0, y0 - w, z0, r, g, b, alpha);
        vertex(builder, matrix, x0, y0 + w, z0, r, g, b, alpha);
        vertex(builder, matrix, x1, y1 + w, z1, r, g, b, alpha);
        vertex(builder, matrix, x1, y1 - w, z1, r, g, b, alpha);

        vertex(builder, matrix, x0 - w, y0, z0, r, g, b, alpha * 0.75f);
        vertex(builder, matrix, x0 + w, y0, z0, r, g, b, alpha * 0.75f);
        vertex(builder, matrix, x1 + w, y1, z1, r, g, b, alpha * 0.75f);
        vertex(builder, matrix, x1 - w, y1, z1, r, g, b, alpha * 0.75f);

        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               float x, float y, float z,
                               float r, float g, float b, float a) {
        builder.vertex(matrix, x, y, z)
                .color(toColor(r), toColor(g), toColor(b), toColor(a))
                .endVertex();
    }

    private static void setGlowState() {
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

    private static int toColor(float value) {
        return Mth.clamp((int) (value * 255.0f), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private record OrbState(float x, float y, float z, float size, float r, float g, float b) {}
}
