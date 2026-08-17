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

    public static float effectTick(UUID playerId) {
        FX fx = ACTIVE.get(playerId);
        return fx == null ? -1.0f : fx.t;
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
            if (distanceSq > 3600.0 || t < 112.0f) continue;

            float distanceFactor = (float) Math.max(0.0, 1.0 - Math.sqrt(distanceSq) / 60.0);
            float fusionKick = smooth(112.0f, 145.0f, t) * (1.0f - smooth(150.0f, 175.0f, t));
            float impactKick = smooth(180.0f, 195.0f, t);
            float power = 0.12f + fusionKick * 0.22f + impactKick * 0.55f;

            event.setYaw(event.getYaw() + Mth.sin(t * 0.91f) * 0.40f * distanceFactor * power);
            event.setPitch(event.getPitch() + Mth.cos(t * 1.11f) * 0.34f * distanceFactor * power);
            event.setRoll(event.getRoll() + Mth.sin(t * 0.67f) * 0.62f * distanceFactor * power);
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
        // V5: intro mas rapida, orbes lisos, fusion visible Azul + Rojo -> Morado.
        if (t < 145.0f) {
            float appear = smooth(5.0f, 28.0f, t);
            float convergence = smooth(67.0f, 124.0f, t);
            float fusionColor = smooth(82.0f, 132.0f, t);
            float disappear = 1.0f - smooth(126.0f, 145.0f, t);

            float orbitRadius = Mth.lerp(convergence, 6.9f, 0.0f);
            float attractionBoost = 1.0f + smooth(88.0f, 120.0f, t) * 0.72f;
            float angle = t * 0.075f * attractionBoost;

            float size = Mth.lerp(appear, 0.42f, 2.35f);
            size *= Mth.lerp(convergence, 1.0f, 0.70f) * disappear;

            float baseY = Mth.lerp(smooth(0.0f, 38.0f, t), -0.6f, 3.05f);
            float blueY = Mth.lerp(convergence, baseY + Mth.sin(t * 0.075f) * 0.28f, 2.55f);
            float redY = Mth.lerp(convergence, baseY + Mth.cos(t * 0.072f + 1.2f) * 0.28f, 2.55f);

            float blueR = Mth.lerp(fusionColor, 0.025f, 0.52f);
            float blueG = Mth.lerp(fusionColor, 0.30f, 0.07f);
            float blueB = Mth.lerp(fusionColor, 1.00f, 0.96f);

            float redR = Mth.lerp(fusionColor, 1.00f, 0.52f);
            float redG = Mth.lerp(fusionColor, 0.025f, 0.07f);
            float redB = Mth.lerp(fusionColor, 0.070f, 0.96f);

            drawEnergyOrb(
                    pose,
                    Mth.cos(angle) * orbitRadius,
                    blueY,
                    Mth.sin(angle) * orbitRadius,
                    size,
                    blueR, blueG, blueB
            );

            drawEnergyOrb(
                    pose,
                    Mth.cos(angle + Mth.PI) * orbitRadius,
                    redY,
                    Mth.sin(angle + Mth.PI) * orbitRadius,
                    size,
                    redR, redG, redB
            );

            // La zona de contacto nace violeta antes de que desaparezcan los dos orbes.
            if (t >= 82.0f) {
                float contact = smooth(82.0f, 128.0f, t) * disappear;
                pose.pushPose();
                pose.translate(0.0, 2.55, 0.0);
                float contactRadius = Mth.lerp(contact, 0.12f, 1.65f);
                drawSolidSphere(pose, contactRadius, 0.58f, 0.06f, 0.98f);
                drawGlowShell(pose, contactRadius * 1.08f, 0.82f, 0.18f, 1.0f, 0.16f * contact);
                pose.popPose();
            }
        }

        // Morado nace pequeno, pulsa y luego crece: no aparece de golpe.
        if (t >= 102.0f) {
            float born = smooth(102.0f, 136.0f, t);
            float expansion = smooth(132.0f, 176.0f, t);
            float impactHoldEnd = Math.max(330.0f, 190.0f + fx.hits * 2.0f + 90.0f);
            float fade = 1.0f - smooth(impactHoldEnd, impactHoldEnd + 35.0f, t);

            float smallRadius = Mth.lerp(born, 0.12f, 2.15f);
            float radius = Mth.lerp(expansion, smallRadius, 6.95f);
            if (t >= 132.0f && t <= 158.0f) {
                float pulse = 1.0f + Mth.sin((t - 132.0f) * 0.42f) * 0.055f * (1.0f - smooth(148.0f, 160.0f, t));
                radius *= pulse;
            }
            radius *= fade;

            pose.pushPose();
            pose.translate(0.0, Mth.lerp(smooth(102.0f, 176.0f, t), 2.55f, 1.85f), 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.30f));
            drawPurpleCore(pose, radius);

            if (t >= 160.0f && t <= 205.0f) {
                float flash = 1.0f - Math.abs((t - 185.0f) / 25.0f);
                flash = Mth.clamp(flash, 0.0f, 1.0f);
                drawGlowShell(pose, radius * (1.04f + flash * 0.10f), 1.0f, 0.78f, 1.0f, 0.18f * flash);
            }

            if (t > 176.0f && fade > 0.02f) {
                drawEnergyColumn(pose, radius, t, fade);
            }
            pose.popPose();
        }
    }

    private static void drawEnergyOrb(PoseStack pose, float x, float y, float z, float radius,
                                      float red, float green, float blue) {
        if (radius <= 0.02f) return;

        pose.pushPose();
        pose.translate(x, y, z);

        drawSolidSphere(pose, radius, red, green, blue);
        drawSolidSphere(pose, radius * 0.28f, 0.96f, 0.98f, 1.0f);
        drawGlowShell(pose, radius * 1.035f, red, green, blue, 0.15f);

        pose.popPose();
    }

    private static void drawPurpleCore(PoseStack pose, float radius) {
        if (radius <= 0.05f) return;

        drawSolidSphere(pose, radius, 0.47f, 0.025f, 0.92f);
        drawSolidSphere(pose, radius * 0.70f, 0.72f, 0.10f, 1.0f);
        drawSolidSphere(pose, radius * 0.18f, 1.0f, 0.95f, 1.0f);
        drawGlowShell(pose, radius * 1.045f, 0.84f, 0.10f, 1.0f, 0.18f);
        drawGlowShell(pose, radius * 1.075f, 0.34f, 0.06f, 1.0f, 0.09f);
    }

    private static void drawEnergyColumn(PoseStack pose, float radius, float t, float fade) {
        setGlowState();
        for (int i = 0; i < 5; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 36.0f + t * (i % 2 == 0 ? 0.20f : -0.16f)));
            Matrix4f matrix = pose.last().pose();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            float width = Math.max(1.8f, radius * (0.17f + i * 0.02f));
            float height = 44.0f;
            float alphaBottom = (0.075f - i * 0.006f) * fade;
            float alphaTop = (0.18f - i * 0.014f) * fade;

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
        drawSphereMesh(pose, radius, red, green, blue, 1.0f);
    }

    private static void drawGlowShell(PoseStack pose, float radius,
                                      float red, float green, float blue, float alpha) {
        if (radius <= 0.02f || alpha <= 0.01f) return;
        setGlowState();
        drawSphereMesh(pose, radius, red, green, blue, alpha);
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
                                       float red, float green, float blue, float alpha) {
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Geometria lisa: sin warping/deformaciones, con bastante detalle para evitar cortes.
        final int longitude = 112;
        final int latitude = 60;

        for (int iy = 0; iy < latitude; iy++) {
            float p0 = ((float) iy / latitude - 0.5f) * Mth.PI;
            float p1 = ((float) (iy + 1) / latitude - 0.5f) * Mth.PI;

            for (int ix = 0; ix < longitude; ix++) {
                float a0 = ix * TAU / longitude;
                float a1 = (ix + 1) * TAU / longitude;

                sphereVertex(builder, matrix, radius, p0, a0, red, green, blue, alpha);
                sphereVertex(builder, matrix, radius, p0, a1, red, green, blue, alpha);
                sphereVertex(builder, matrix, radius, p1, a1, red, green, blue, alpha);
                sphereVertex(builder, matrix, radius, p1, a0, red, green, blue, alpha);
            }
        }

        BufferUploader.drawWithShader(builder.end());
    }

    private static void sphereVertex(BufferBuilder builder, Matrix4f matrix,
                                     float radius, float latitude, float longitude,
                                     float red, float green, float blue, float alpha) {
        float cosLat = Mth.cos(latitude);
        vertex(
                builder,
                matrix,
                radius * cosLat * Mth.cos(longitude),
                radius * Mth.sin(latitude),
                radius * cosLat * Mth.sin(longitude),
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
