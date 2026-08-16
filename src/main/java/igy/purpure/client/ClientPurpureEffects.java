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
import igy.purpure.network.PurpureEffectPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientPurpureEffects {
    private static final Map<UUID, FX> ACTIVE = new LinkedHashMap<>();
    private static final float TAU = (float) (Math.PI * 2.0);

    private ClientPurpureEffects() {}

    public static void accept(PurpureEffectPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().toString().equals(packet.dimension())) return;

        if (packet.mode() == PurpureEffectPacket.START) {
            ACTIVE.put(packet.target(), new FX(packet));
        } else if (packet.mode() == PurpureEffectPacket.EXTEND) {
            FX fx = ACTIVE.get(packet.target());
            if (fx != null) fx.hits = Math.min(2000, fx.hits + packet.hits());
        } else {
            ACTIVE.remove(packet.target());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null && !Minecraft.getInstance().isPaused()) {
            ACTIVE.values().forEach(fx -> fx.t++);
        }
    }

    @SubscribeEvent
    public static void shake(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (FX fx : ACTIVE.values()) {
            float t = fx.t + (float) event.getPartialTick();
            double distanceSq = mc.player.distanceToSqr(fx.x, fx.y, fx.z);
            if (distanceSq > 3600.0 || t < 235.0f) continue;

            float distanceFactor = (float) Math.max(0.0, 1.0 - Math.sqrt(distanceSq) / 60.0);
            float power = t < 410.0f ? 0.18f : 0.72f;
            event.setYaw(event.getYaw() + Mth.sin(t * 0.83f) * 0.45f * distanceFactor * power);
            event.setPitch(event.getPitch() + Mth.cos(t * 1.04f) * 0.38f * distanceFactor * power);
            event.setRoll(event.getRoll() + Mth.sin(t * 0.61f) * 0.72f * distanceFactor * power);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;

        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        float partialTick = event.getPartialTick();

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (FX fx : ACTIVE.values()) {
            pose.pushPose();
            pose.translate(
                    fx.x - camera.getPosition().x,
                    fx.y - camera.getPosition().y,
                    fx.z - camera.getPosition().z
            );
            drawEffect(pose, fx, fx.t + partialTick);
            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void drawEffect(PoseStack pose, FX fx, float t) {
        if (t < 275.0f) {
            float appear = smooth(15.0f, 70.0f, t);
            float converge = smooth(205.0f, 275.0f, t);
            float orbitRadius = Mth.lerp(converge, 9.3f, 0.55f);
            float angle = t * 0.043f;
            float size = Mth.lerp(appear, 0.55f, 3.8f);

            float blueY = 2.0f + Mth.sin(t * 0.028f) * 0.75f;
            float redY = 2.0f + Mth.cos(t * 0.026f + 1.3f) * 0.75f;

            drawEnergyOrb(
                    pose,
                    Mth.cos(angle) * orbitRadius,
                    blueY,
                    Mth.sin(angle) * orbitRadius,
                    size,
                    0.035f, 0.28f, 1.0f,
                    t,
                    0.0f
            );

            drawEnergyOrb(
                    pose,
                    Mth.cos(angle + Mth.PI) * orbitRadius,
                    redY,
                    Mth.sin(angle + Mth.PI) * orbitRadius,
                    size,
                    1.0f, 0.035f, 0.075f,
                    t,
                    1.7f
            );
        }

        if (t >= 175.0f) {
            float growth = smooth(175.0f, 285.0f, t);
            float impactHoldEnd = Math.max(500.0f, 410.0f + fx.hits * 2.0f + 20.0f);
            float fade = 1.0f - smooth(impactHoldEnd, impactHoldEnd + 40.0f, t);
            float radius = Mth.lerp(growth, 0.5f, 11.5f) * fade;

            pose.pushPose();
            pose.translate(0.0, Mth.lerp(smooth(175.0f, 300.0f, t), 3.6f, 2.0f), 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.36f));
            drawPurpleCore(pose, radius, t);

            if (t >= 235.0f && t <= 285.0f) {
                float flash = 1.0f - Math.abs((t - 260.0f) / 25.0f);
                flash = Mth.clamp(flash, 0.0f, 1.0f);
                drawGlowShell(pose, radius * (1.08f + flash * 0.22f), 1.0f, 0.78f, 1.0f, 0.30f * flash);
            }

            if (t > 285.0f && fade > 0.02f) {
                drawEnergyColumn(pose, radius, t, fade);
            }
            pose.popPose();
        }
    }

    private static void drawEnergyOrb(PoseStack pose, float x, float y, float z, float radius,
                                      float red, float green, float blue, float t, float seed) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.68f + seed * 51.0f));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin((t + seed * 15.0f) * 0.045f) * 13.0f));

        drawSolidWarpedSphere(
                pose,
                radius,
                red, green, blue,
                t * 0.31f + seed * 9.0f,
                0.055f, 0.035f,
                3.1f, 4.7f
        );

        drawSolidSphere(pose, radius * 0.34f, 0.96f, 0.98f, 1.0f);

        drawGlowWarpedSphere(
                pose,
                radius * 1.095f,
                red, green, blue,
                0.34f,
                t * 0.27f + seed * 13.0f,
                0.075f, 0.050f,
                4.3f, 6.1f
        );
        pose.popPose();
    }

    private static void drawPurpleCore(PoseStack pose, float radius, float t) {
        if (radius <= 0.05f) return;

        drawSolidWarpedSphere(
                pose,
                radius,
                0.49f, 0.035f, 0.95f,
                t * 0.20f,
                0.045f, 0.030f,
                3.7f, 5.4f
        );

        drawSolidWarpedSphere(
                pose,
                radius * 0.69f,
                0.76f, 0.12f, 1.0f,
                -t * 0.24f,
                0.035f, 0.025f,
                4.8f, 6.6f
        );

        drawSolidSphere(pose, radius * 0.24f, 1.0f, 0.94f, 1.0f);

        drawGlowWarpedSphere(
                pose,
                radius * 1.07f,
                0.87f, 0.10f, 1.0f,
                0.34f,
                t * 0.18f,
                0.070f, 0.052f,
                5.2f, 7.1f
        );

        drawGlowWarpedSphere(
                pose,
                radius * 1.14f,
                0.38f, 0.08f, 1.0f,
                0.18f,
                -t * 0.15f,
                0.085f, 0.055f,
                6.0f, 8.0f
        );
    }

    private static void drawEnergyColumn(PoseStack pose, float radius, float t, float fade) {
        setGlowState();
        for (int i = 0; i < 6; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 30.0f + t * (i % 2 == 0 ? 0.22f : -0.18f)));
            Matrix4f matrix = pose.last().pose();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            float width = Math.max(2.3f, radius * (0.20f + i * 0.025f));
            float height = 52.0f;
            float alphaBottom = (0.10f - i * 0.009f) * fade;
            float alphaTop = (0.24f - i * 0.018f) * fade;

            vertex(builder, matrix, -width, -height, 0.0f, 0.42f, 0.08f, 1.0f, alphaBottom);
            vertex(builder, matrix,  width, -height, 0.0f, 0.42f, 0.08f, 1.0f, alphaBottom);
            vertex(builder, matrix,  width,  height, 0.0f, 0.95f, 0.48f, 1.0f, alphaTop);
            vertex(builder, matrix, -width,  height, 0.0f, 0.95f, 0.48f, 1.0f, alphaTop);
            BufferUploader.drawWithShader(builder.end());
            pose.popPose();
        }
    }

    private static void drawSolidSphere(PoseStack pose, float radius, float red, float green, float blue) {
        if (radius <= 0.02f) return;
        setSolidState();
        drawSphereMesh(pose, radius, red, green, blue, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, false);
    }

    private static void drawSolidWarpedSphere(PoseStack pose, float radius,
                                               float red, float green, float blue,
                                               float time, float ampA, float ampB, float freqA, float freqB) {
        if (radius <= 0.02f) return;
        setSolidState();
        drawSphereMesh(pose, radius, red, green, blue, 1.0f, time, ampA, ampB, freqA, freqB, true);
    }

    private static void drawGlowShell(PoseStack pose, float radius,
                                      float red, float green, float blue, float alpha) {
        if (radius <= 0.02f || alpha <= 0.01f) return;
        setGlowState();
        drawSphereMesh(pose, radius, red, green, blue, alpha, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, false);
    }

    private static void drawGlowWarpedSphere(PoseStack pose, float radius,
                                              float red, float green, float blue, float alpha,
                                              float time, float ampA, float ampB, float freqA, float freqB) {
        if (radius <= 0.02f || alpha <= 0.01f) return;
        setGlowState();
        drawSphereMesh(pose, radius, red, green, blue, alpha, time, ampA, ampB, freqA, freqB, true);
    }

    private static void setSolidState() {
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
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

    private static void drawSphereMesh(PoseStack pose, float radius,
                                       float red, float green, float blue, float alpha,
                                       float time, float ampA, float ampB, float freqA, float freqB,
                                       boolean warped) {
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int longitude = 96;
        final int latitude = 52;

        for (int iy = 0; iy < latitude; iy++) {
            float p0 = ((float) iy / latitude - 0.5f) * Mth.PI;
            float p1 = ((float) (iy + 1) / latitude - 0.5f) * Mth.PI;

            for (int ix = 0; ix < longitude; ix++) {
                float a0 = ix * TAU / longitude;
                float a1 = (ix + 1) * TAU / longitude;

                sphereVertex(builder, matrix, radius, p0, a0, red, green, blue, alpha, time, ampA, ampB, freqA, freqB, warped);
                sphereVertex(builder, matrix, radius, p0, a1, red, green, blue, alpha, time, ampA, ampB, freqA, freqB, warped);
                sphereVertex(builder, matrix, radius, p1, a1, red, green, blue, alpha, time, ampA, ampB, freqA, freqB, warped);
                sphereVertex(builder, matrix, radius, p1, a0, red, green, blue, alpha, time, ampA, ampB, freqA, freqB, warped);
            }
        }

        BufferUploader.drawWithShader(builder.end());
    }

    private static void sphereVertex(BufferBuilder builder, Matrix4f matrix,
                                     float radius, float latitude, float longitude,
                                     float red, float green, float blue, float alpha,
                                     float time, float ampA, float ampB, float freqA, float freqB,
                                     boolean warped) {
        float adjustedRadius = radius;
        if (warped) {
            float texture =
                    Mth.sin(longitude * freqA + time * 0.11f) * ampA +
                    Mth.cos(latitude * freqB - time * 0.13f) * ampB +
                    Mth.sin((longitude + latitude) * (freqA * 0.68f) + time * 0.07f) * ampA * 0.32f;
            adjustedRadius *= 1.0f + texture;
        }

        float cosLat = Mth.cos(latitude);
        vertex(
                builder,
                matrix,
                adjustedRadius * cosLat * Mth.cos(longitude),
                adjustedRadius * Mth.sin(latitude),
                adjustedRadius * cosLat * Mth.sin(longitude),
                red, green, blue, alpha
        );
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               float x, float y, float z,
                               float red, float green, float blue, float alpha) {
        builder.vertex(matrix, x, y, z)
                .color(toColor(red), toColor(green), toColor(blue), toColor(alpha))
                .endVertex();
    }

    private static int toColor(float value) {
        return Mth.clamp((int) (value * 255.0f), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static final class FX {
        final double x;
        final double y;
        final double z;
        final long seed;
        int hits;
        int t;

        FX(PurpureEffectPacket packet) {
            x = packet.x();
            y = packet.y();
            z = packet.z();
            seed = packet.seed();
            hits = packet.hits();
        }
    }
}
