package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GokuFxMod;
import com.igygaming.gokufx.KameTiming;
import com.igygaming.gokufx.network.GokuEffectPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Kame V14 portado del visor aprobado:
 * Goku SSB con skin, pose de Kame, esfera 3D mas rica, rayos y brillitos
 * desde las manos, y Kamehameha mas detallado y ancho. Sin aura corporal.
 *
 * Esta clase solo cambia visuales del Kame. La distancia de bloques, arrastre,
 * daño y totems siguen en AttackManager sin cambios.
 */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientKameV12Renderer {
    private static final float TAU = (float)(Math.PI * 2.0D);
    private static final double GOKU_X = -15.75D;
    private static final float GOKU_SCALE = 0.38F;
    private static final Vec3 BEAM_SOURCE = new Vec3(-14.0D, 1.71D, 0.0D);
    private static final ResourceLocation GOKU_SKIN =
            new ResourceLocation(GokuFxMod.MODID, "textures/entity/goku_ssb.png");

    private ClientKameV12Renderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();

        for (AbstractClientPlayer target : mc.level.players()) {
            int raw = ClientGokuEffects.rawEffectTick(target.getUUID(), GokuEffectPacket.KAMEHAMEHA);
            if (raw < 0) continue;
            int hits = Math.max(1, ClientGokuEffects.hits(target.getUUID(), GokuEffectPacket.KAMEHAMEHA));
            if (raw > KameTiming.totalTicks(hits) + 3) continue;
            if (target.position().distanceToSqr(cameraPos) > 300.0D * 300.0D) continue;

            Vec3 origin = ClientGokuEffects.origin(target.getUUID(), GokuEffectPacket.KAMEHAMEHA);
            if (origin == null) origin = target.position();
            Vec3 targetOffset = target.position().subtract(origin);

            pose.pushPose();
            pose.translate(origin.x - cameraPos.x, origin.y - cameraPos.y, origin.z - cameraPos.z);
            drawKame(pose, raw + partial, hits, targetOffset,
                    target.position().distanceToSqr(cameraPos) < 125.0D * 125.0D);
            pose.popPose();
        }
        restoreState();
    }

    /**
     * Port directo del visor final aprobado "Goku SSB • Kame V14+ Traveling Core".
     * Se conservan sus capas, proporciones, colores, esfera de carga, esfera de salida,
     * jaula energetica, pulsos, destellos y segunda esfera/frente viajero.
     */
    private static void drawKame(PoseStack pose, float t, int hits, Vec3 targetOffset, boolean detail) {
        float setup = smooth(0.0F, 34.0F, t);
        float charge = smoother(Mth.clamp(t / KameTiming.CHARGE_TICKS, 0.0F, 1.0F));
        float thrust = smooth(KameTiming.CHARGE_TICKS, KameTiming.CHARGE_TICKS + 14.0F, t);

        int beamTicks = KameTiming.beamTicks(hits);
        float beamAge = Math.max(0.0F, t - KameTiming.CHARGE_TICKS);
        float fade = t <= KameTiming.CHARGE_TICKS ? 1.0F
                : 1.0F - smooth(Math.max(17.0F, beamTicks - KameTiming.FADE_TICKS), beamTicks, beamAge);

        // Goku permanece hasta que ya terminaron daño/totems y empieza la disipacion final.
        if (fade > 0.01F) {
            drawGokuScene(pose, t, setup, thrust, charge, fade, detail);
        }

        Vec3 cup = localToWorld(new Vec3(0.0D, 3.05D, -2.45D));
        Vec3 source = cup.lerp(BEAM_SOURCE, thrust);

        // Carga exacta del visor final: escala 0.12 -> 1.57, seis capas y pulsacion.
        float spherePulse = 1.0F + Mth.sin(t * 0.24F) * 0.060F;
        float sphereRadius = (0.12F + 1.45F * charge) * spherePulse;
        float chargeRelease = t <= KameTiming.CHARGE_TICKS ? 1.0F
                : 1.0F - smooth(KameTiming.CHARGE_TICKS, KameTiming.CHARGE_TICKS + 14.0F, t);

        if (chargeRelease > 0.01F) {
            float radius = sphereRadius * (0.30F + 0.70F * chargeRelease);
            pose.pushPose();
            pose.translate(source.x, source.y, source.z);
            drawApprovedEnergySphere(pose, radius, chargeRelease, detail);
            drawApprovedSphereCage(pose, radius, t, 12, detail ? 14 : 10,
                    (0.22F + 0.48F * charge) * chargeRelease);
            radialRays(pose, radius, t, detail ? 28 : 16,
                    (0.30F + 0.36F * charge) * chargeRelease);
            drawApprovedOrbitalBands(pose, Vec3.ZERO, radius, t, 4,
                    (0.34F + 0.30F * charge) * chargeRelease, detail);
            pose.popPose();

            Vec3 leftHand = handWorld(-1.0F, setup, thrust);
            Vec3 rightHand = handWorld(1.0F, setup, thrust);
            int handBolts = detail ? 8 : 5;
            for (int i = 0; i < handBolts; i++) {
                drawJaggedBolt(pose, leftHand, source, t, 40 + i * 11,
                        0.030F + i * 0.004F, 0.58F, 0.96F, 1.0F,
                        (0.48F + charge * 0.38F) * chargeRelease);
                drawJaggedBolt(pose, rightHand, source, t, 130 + i * 11,
                        0.030F + i * 0.004F, 0.58F, 0.96F, 1.0F,
                        (0.48F + charge * 0.38F) * chargeRelease);
            }
            drawApprovedHandGlints(pose, leftHand, rightHand, source, t, charge, chargeRelease, detail);
        }

        if (t <= KameTiming.CHARGE_TICKS || fade <= 0.005F) return;

        float launch = smooth(0.0F, 14.0F, beamAge);

        // Esfera fija entre las manos: no desaparece al salir el ataque.
        float muzzleRadius = (0.28F + 0.88F * launch)
                * (0.88F + 0.10F * Mth.sin(t * 0.40F));
        pose.pushPose();
        pose.translate(BEAM_SOURCE.x, BEAM_SOURCE.y, BEAM_SOURCE.z);
        drawApprovedEnergySphere(pose, muzzleRadius, fade, detail);
        drawApprovedOrbitalBands(pose, Vec3.ZERO, muzzleRadius, t, 5,
                (0.36F + 0.26F * launch) * fade, detail);
        pose.popPose();

        Vec3 startFront = BEAM_SOURCE.add(new Vec3(1.0D, -0.04D, 0.0D).normalize().scale(2.55D));
        Vec3 initialTargetFront = new Vec3(0.0D, 1.15D, 0.0D);
        Vec3 front;

        if (beamAge < KameTiming.FRONT_CONTACT_DELAY_TICKS) {
            float travel = smooth(0.0F, KameTiming.FRONT_CONTACT_DELAY_TICKS, beamAge);
            front = startFront.lerp(initialTargetFront, travel);
        } else {
            // Tras el contacto, el frente visible permanece pegado al objetivo que esta empujando.
            front = targetOffset.add(0.0D, 1.15D, 0.0D)
                    .add(new Vec3(1.0D, -0.04D, 0.0D).normalize().scale(0.65D));
        }

        if (front.distanceToSqr(BEAM_SOURCE) > 0.04D) {
            drawApprovedBeam(pose, BEAM_SOURCE, front, t, fade, detail);
        }

        // Segunda esfera aprobada: aparece desde el primer instante del disparo y viaja en la punta.
        float travelRadius = (0.50F + 0.58F * launch)
                * (0.94F + 0.07F * Mth.sin(t * 0.50F));
        pose.pushPose();
        pose.translate(front.x, front.y, front.z);
        drawApprovedEnergySphere(pose, travelRadius, fade, detail);
        drawApprovedOrbitalBands(pose, Vec3.ZERO, travelRadius, t, 6,
                (0.30F + 0.35F * launch) * fade, detail);
        drawApprovedSphereCage(pose, travelRadius, t + 11.0F, detail ? 8 : 5,
                detail ? 12 : 8, (0.18F + 0.34F * launch) * fade);
        radialRays(pose, travelRadius, t + 23.0F, detail ? 18 : 10,
                (0.20F + 0.32F * launch) * fade);
        pose.popPose();

        // Brillo/impacto alrededor del jugador durante la fase de empuje.
        if (beamAge >= KameTiming.FRONT_CONTACT_DELAY_TICKS) {
            Vec3 impactCenter = targetOffset.add(0.0D, 1.15D, 0.0D);
            pose.pushPose();
            pose.translate(impactCenter.x, impactCenter.y, impactCenter.z);
            float impactRadius = 0.82F + Mth.sin(t * 0.54F) * 0.08F;
            drawApprovedEnergySphere(pose, impactRadius, 0.48F * fade, detail);
            radialRays(pose, impactRadius, t + 37.0F, detail ? 14 : 8, 0.24F * fade);
            pose.popPose();
        }
    }

    private static void drawApprovedEnergySphere(PoseStack pose, float radius, float alpha, boolean detail) {
        int lon = detail ? 44 : 28;
        int lat = detail ? 26 : 16;
        solidAlphaSphere(pose, radius * 0.40F, 0.99F, 1.00F, 1.00F, 0.97F * alpha, lon, lat);
        glowSphere(pose, radius * 0.66F, 0.76F, 0.99F, 1.00F, 0.64F * alpha, lon, lat);
        glowSphere(pose, radius * 0.84F, 0.22F, 0.90F, 1.00F, 0.48F * alpha, lon, lat);
        glowSphere(pose, radius * 1.04F, 0.04F, 0.68F, 1.00F, 0.35F * alpha, lon, lat);
        glowSphere(pose, radius * 1.22F, 0.01F, 0.28F, 1.00F, 0.18F * alpha,
                detail ? 38 : 24, detail ? 21 : 13);
        glowSphere(pose, radius * 1.42F, 0.00F, 0.10F, 1.00F, 0.08F * alpha,
                detail ? 34 : 20, detail ? 19 : 11);
    }

    private static void drawApprovedSphereCage(PoseStack pose, float radius, float t,
                                                int arcs, int segs, float alpha) {
        if (alpha <= 0.005F) return;
        for (int i = 0; i < arcs; i++) {
            Vec3 prev = null;
            float baseLat = -0.70F + (i % 5) * 0.34F;
            float phase = i * 1.731F + t * (0.041F + (i % 3) * 0.006F);
            for (int j = 0; j <= segs; j++) {
                float q = j / (float) segs;
                float lon = phase + q * (1.45F + (i % 2) * 0.55F);
                float lat = baseLat + Mth.sin(q * 10.0F + t * 0.10F + i * 0.77F) * 0.17F;
                float rr = radius * (1.08F + Mth.sin(j * 4.13F + t * 0.13F + i) * 0.04F);
                float c = Mth.cos(lat);
                Vec3 p = new Vec3(rr * c * Mth.cos(lon), rr * Mth.sin(lat), rr * c * Mth.sin(lon));
                if (prev != null) {
                    glowRibbon(pose, prev, p,
                            i % 4 == 0 ? 1.0F : 0.72F,
                            0.98F, 1.0F, alpha,
                            Math.max(0.020F, radius * 0.010F));
                }
                prev = p;
            }
        }
    }

    private static void drawApprovedOrbitalBands(PoseStack pose, Vec3 center, float radius, float t,
                                                  int count, float alpha, boolean detail) {
        if (alpha <= 0.005F || radius <= 0.01F) return;
        for (int i = 0; i < count; i++) {
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            pose.mulPose(Axis.XP.rotation(i * 0.52F));
            pose.mulPose(Axis.YP.rotation((float) Math.PI * 0.5F + i * 0.34F));
            pose.mulPose(Axis.ZP.rotation(i * 0.37F + t * 0.045F * ((i & 1) == 0 ? 1.0F : -1.0F)));
            float major = radius * (0.78F + i * 0.085F);
            float minor = Math.max(0.012F, radius * (0.018F + (i % 2) * 0.006F));
            glowTorusLocal(pose, major, minor,
                    i % 3 == 0 ? 1.0F : (i % 2 == 0 ? 0.25F : 0.63F),
                    i % 3 == 0 ? 1.0F : 0.96F,
                    1.0F, alpha,
                    detail ? 64 : 40, detail ? 8 : 5);
            pose.popPose();
        }
    }

    private static void drawApprovedHandGlints(PoseStack pose, Vec3 leftHand, Vec3 rightHand,
                                                Vec3 source, float t, float charge,
                                                float release, boolean detail) {
        int glints = detail ? 22 : 12;
        Vec3 middle = leftHand.lerp(rightHand, 0.5D);
        for (int i = 0; i < glints; i++) {
            double angle = i * 2.399963D + t * 0.085D;
            double ring = 0.18D + (i % 6) * 0.052D;
            Vec3 center = middle.add(
                    Math.cos(angle) * ring,
                    Math.sin(angle * 1.43D) * ring * 0.40D,
                    Math.sin(angle) * ring * 0.52D
            );
            float pulse = 0.055F + (i % 3) * 0.018F
                    + 0.025F * Mth.abs(Mth.sin(t * 0.60F + i));
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            glowSphere(pose, pulse,
                    i % 4 == 0 ? 1.0F : 0.50F,
                    0.96F, 1.0F,
                    (0.38F + charge * 0.45F) * release,
                    detail ? 12 : 8, detail ? 8 : 6);
            pose.popPose();
        }

        // Destellos blancos exactamente entre las palmas y la esfera.
        for (int i = 0; i < (detail ? 6 : 4); i++) {
            float flicker = 0.5F + 0.5F * Mth.sin(t * (0.75F + i * 0.05F) + i * 2.1F);
            Vec3 p = middle.lerp(source, 0.28D + i * 0.09D);
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            glowSphere(pose, 0.075F + flicker * 0.055F,
                    0.92F, 1.0F, 1.0F,
                    (0.48F + flicker * 0.28F) * release,
                    detail ? 12 : 8, detail ? 8 : 6);
            pose.popPose();
        }
    }

    private static void drawApprovedBeam(PoseStack pose, Vec3 start, Vec3 end,
                                         float t, float alpha, boolean detail) {
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 0.08D || alpha <= 0.005F) return;

        float breath = 1.0F + Mth.sin(t * 0.45F) * 0.035F;

        // Seis capas exactas del cuerpo aprobado.
        solidAlphaCylinder(pose, start, end, 0.34F * breath, 1.00F, 1.00F, 1.00F, 0.98F * alpha, 44);
        glowCylinder(pose, start, end, 0.52F * breath, 0.84F, 1.00F, 1.00F, 0.78F * alpha, 44);
        glowCylinder(pose, start, end, 0.72F * breath, 0.32F, 0.90F, 1.00F, 0.52F * alpha, 44);
        glowCylinder(pose, start, end, 0.92F * breath, 0.055F, 0.71F, 1.00F, 0.34F * alpha, 44);
        glowCylinder(pose, start, end, 1.12F * breath, 0.031F, 0.294F, 1.00F, 0.17F * alpha, 42);
        glowCylinder(pose, start, end, 1.30F * breath, 0.00F, 0.11F, 1.00F, 0.075F * alpha, 40);

        Vec3 dir = axis.normalize();
        Vec3 helper = Math.abs(dir.y) < 0.90D ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();

        // Ocho lineas de jaula longitudinal; mismo lenguaje de la esfera de carga.
        int cageLines = detail ? 8 : 5;
        int cageSteps = detail ? 36 : 22;
        for (int k = 0; k < cageLines; k++) {
            Vec3 prev = null;
            for (int j = 0; j <= cageSteps; j++) {
                double q = j / (double) cageSteps;
                double ang = k * TAU / cageLines + q * Math.PI * 4.1D
                        + t * 0.09D * ((k & 1) == 0 ? 1.0D : -1.0D);
                double rr = 1.08D + 0.08D * Math.sin(q * 18.0D + k + t * 0.20D);
                Vec3 center = start.add(dir.scale(length * q));
                Vec3 p = center.add(u.scale(Math.cos(ang) * rr)).add(v.scale(Math.sin(ang) * rr));
                if (prev != null) {
                    glowRibbon(pose, prev, p,
                            k % 3 == 0 ? 1.0F : 0.62F,
                            0.96F, 1.0F,
                            (0.16F + 0.30F * alpha), 0.020F);
                }
                prev = p;
            }
        }

        // Dieciocho pulsos/orbitas que corren por el haz.
        int rings = detail ? 18 : 10;
        for (int i = 0; i < rings; i++) {
            double q = positiveMod(i / (double) rings + t * 0.030D, 1.0D);
            Vec3 center = start.add(dir.scale(length * q));
            float major = (1.00F + (i % 3) * 0.06F)
                    * (0.92F + 0.11F * Mth.sin(t * 0.75F + i));
            glowTorusAxis(pose, center, dir, major, 0.022F + (i % 2) * 0.006F,
                    i % 4 == 0 ? 1.0F : (i % 2 == 0 ? 0.24F : 0.55F),
                    i % 4 == 0 ? 1.0F : 0.95F,
                    1.0F, (0.16F + 0.34F * alpha),
                    detail ? 56 : 36, detail ? 7 : 5);
        }

        // Catorce descargas radiales espaciadas: nada de telarana continua.
        int radials = detail ? 14 : 8;
        for (int i = 0; i < radials; i++) {
            double q = positiveMod((i * 0.71D) / Math.max(0.1D, length) + t * 0.014D, 1.0D);
            double ang = i * 2.399963D + t * 0.09D;
            Vec3 radial = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang))).normalize();
            Vec3 center = start.add(dir.scale(length * q));
            Vec3 a = center.add(radial.scale(1.00D));
            Vec3 b = center.add(radial.scale(1.34D + 0.13D * Math.sin(t * 0.30D + i)));
            drawJaggedBolt(pose, a, b, t, 700 + i * 17,
                    0.025F, i % 4 == 0 ? 1.0F : 0.45F,
                    0.91F, 1.0F, (0.18F + 0.35F * alpha));
        }

        // 42 puntos de energia internos del visor final.
        int glints = detail ? 42 : 22;
        for (int i = 0; i < glints; i++) {
            double dist = positiveMod(i * 0.37D + t * (0.35D + (i % 4) * 0.042D), length);
            double ang = i * 2.399963D + t * 0.08D;
            double rr = 0.18D + (i % 7) * 0.10D;
            Vec3 center = start.add(dir.scale(dist))
                    .add(u.scale(Math.cos(ang) * rr))
                    .add(v.scale(Math.sin(ang) * rr));
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            float size = 0.026F + (i % 3) * 0.008F;
            glowSphere(pose, size,
                    i % 6 == 0 ? 1.0F : 0.55F,
                    0.93F, 1.0F,
                    (0.18F + 0.52F * alpha * Mth.abs(Mth.sin(t * 0.55F + i))),
                    detail ? 8 : 6, detail ? 6 : 4);
            pose.popPose();
        }
    }

    private static double positiveMod(double value, double modulo) {
        if (modulo <= 0.0D) return 0.0D;
        double r = value % modulo;
        return r < 0.0D ? r + modulo : r;
    }

    private static void glowTorusLocal(PoseStack pose, float major, float minor,
                                       float r, float g, float b, float a,
                                       int majorSegments, int minorSegments) {
        if (a <= 0.005F || major <= 0.01F || minor <= 0.001F) return;
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < majorSegments; i++) {
            double a0 = i * Math.PI * 2.0D / majorSegments;
            double a1 = (i + 1) * Math.PI * 2.0D / majorSegments;
            for (int j = 0; j < minorSegments; j++) {
                double b0 = j * Math.PI * 2.0D / minorSegments;
                double b1 = (j + 1) * Math.PI * 2.0D / minorSegments;
                torusVertex(q, m, major, minor, a0, b0, r, g, b, a);
                torusVertex(q, m, major, minor, a1, b0, r, g, b, a);
                torusVertex(q, m, major, minor, a1, b1, r, g, b, a);
                torusVertex(q, m, major, minor, a0, b1, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(q.end());
    }

    private static void torusVertex(BufferBuilder q, Matrix4f m, float major, float minor,
                                    double a, double b, float red, float green, float blue, float alpha) {
        float ring = major + minor * (float) Math.cos(b);
        float x = ring * (float) Math.cos(a);
        float y = ring * (float) Math.sin(a);
        float z = minor * (float) Math.sin(b);
        vertex(q, m, x, y, z, red, green, blue, alpha);
    }

    private static void glowTorusAxis(PoseStack pose, Vec3 center, Vec3 normal,
                                      float major, float minor,
                                      float r, float g, float b, float a,
                                      int majorSegments, int minorSegments) {
        if (a <= 0.005F || major <= 0.01F || minor <= 0.001F) return;
        Vec3 n = normal.normalize();
        Vec3 helper = Math.abs(n.y) < 0.90D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = n.cross(helper).normalize();
        Vec3 v = n.cross(u).normalize();

        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < majorSegments; i++) {
            double a0 = i * Math.PI * 2.0D / majorSegments;
            double a1 = (i + 1) * Math.PI * 2.0D / majorSegments;
            for (int j = 0; j < minorSegments; j++) {
                double b0 = j * Math.PI * 2.0D / minorSegments;
                double b1 = (j + 1) * Math.PI * 2.0D / minorSegments;
                axisTorusVertex(q, m, center, n, u, v, major, minor, a0, b0, r, g, b, a);
                axisTorusVertex(q, m, center, n, u, v, major, minor, a1, b0, r, g, b, a);
                axisTorusVertex(q, m, center, n, u, v, major, minor, a1, b1, r, g, b, a);
                axisTorusVertex(q, m, center, n, u, v, major, minor, a0, b1, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(q.end());
    }

    private static void axisTorusVertex(BufferBuilder q, Matrix4f m,
                                        Vec3 center, Vec3 n, Vec3 u, Vec3 v,
                                        float major, float minor, double a, double b,
                                        float red, float green, float blue, float alpha) {
        Vec3 radial = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
        Vec3 p = center
                .add(radial.scale(major + minor * Math.cos(b)))
                .add(n.scale(minor * Math.sin(b)));
        vertex(q, m, p, red, green, blue, alpha);
    }

    private static void drawGokuScene(PoseStack pose, float t, float setup, float thrust,
                                      float charge, float alpha, boolean detail) {
        pose.pushPose();
        pose.translate(GOKU_X, 0.0D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.scale(GOKU_SCALE, GOKU_SCALE, GOKU_SCALE);

        // Aura desactivada: conservar exclusivamente el modelo con su skin.
        drawGoku(pose, t, setup, thrust, alpha);
        pose.popPose();
    }

    private static void drawGoku(PoseStack pose, float t, float setup, float thrust, float alpha) {
        float breathe = Mth.sin(t * 0.13F) * 0.035F;

        // Piernas, torso, cabeza y segunda capa: misma topologia de skin Minecraft que Genkidama V13.
        skinBox(pose, -0.72F, 0.12F, -0.40F, -0.08F, 2.02F, 0.40F,
                0,16,4,12,4,alpha);
        skinBox(pose, 0.08F, 0.12F, -0.40F, 0.72F, 2.02F, 0.40F,
                16,48,4,12,4,alpha);
        skinBox(pose, -0.76F, 0.08F, -0.44F, -0.04F, 2.06F, 0.44F,
                0,32,4,12,4,alpha);
        skinBox(pose, 0.04F, 0.08F, -0.44F, 0.76F, 2.06F, 0.44F,
                0,48,4,12,4,alpha);

        skinBox(pose, -0.96F, 1.92F, -0.50F, 0.96F, 4.28F+breathe, 0.50F,
                16,16,8,12,4,alpha);
        skinBox(pose, -1.01F, 1.87F, -0.55F, 1.01F, 4.33F+breathe, 0.55F,
                16,32,8,12,4,alpha);

        skinBox(pose, -0.70F, 4.52F+breathe, -0.55F, 0.70F, 5.92F+breathe, 0.55F,
                0,0,8,8,8,alpha);
        skinBox(pose, -0.755F, 4.465F+breathe, -0.605F, 0.755F, 5.975F+breathe, 0.605F,
                32,0,8,8,8,alpha);

        drawKameArm(pose,-1.0F,t,setup,thrust,alpha,40,16,40,32);
        drawKameArm(pose, 1.0F,t,setup,thrust,alpha,32,48,48,48);
    }

    private static void drawKameArm(PoseStack pose, float side, float t, float setup, float thrust,
                                    float alpha, int baseU, int baseV, int overlayU, int overlayV) {
        Vec3 shoulder=new Vec3(side*0.96D,3.94D,-0.02D);
        Vec3 restElbow=new Vec3(side*1.55D,3.10D,-0.10D);
        Vec3 restHand=new Vec3(side*1.72D,2.35D,-0.18D);

        // Manos muy juntas al costado durante la concentracion.
        Vec3 cupElbow=new Vec3(side*0.70D,3.22D,-0.82D);
        Vec3 cupHand=new Vec3(side*0.13D,3.05D,-2.45D);

        // Al disparar, ambas manos quedan casi pegadas y avanzan juntas.
        Vec3 fireElbow=new Vec3(side*0.46D,4.05D,-2.55D);
        Vec3 fireHand=new Vec3(side*0.11D,4.50D,-4.60D);

        Vec3 elbow=restElbow.lerp(cupElbow,setup).lerp(fireElbow,thrust);
        Vec3 hand=restHand.lerp(cupHand,setup).lerp(fireHand,thrust);
        skinLimb(pose,shoulder,elbow,0.72F,baseU,baseV,overlayU,overlayV,alpha);
        skinLimb(pose,elbow,hand,0.66F,baseU,baseV,overlayU,overlayV,alpha);
    }

    private static Vec3 handWorld(float side, float setup, float thrust) {
        Vec3 rest=new Vec3(side*1.72D,2.35D,-0.18D);
        Vec3 cup=new Vec3(side*0.13D,3.05D,-2.45D);
        Vec3 fire=new Vec3(side*0.11D,4.50D,-4.60D);
        return localToWorld(rest.lerp(cup,setup).lerp(fire,thrust));
    }

    private static Vec3 localToWorld(Vec3 p) {
        // Con rotacion Y -90: local -Z apunta hacia +X mundial.
        return new Vec3(
                GOKU_X - p.z * GOKU_SCALE,
                p.y * GOKU_SCALE,
                -p.x * GOKU_SCALE
        );
    }

    private static void drawAura(PoseStack pose, float t, float strength, boolean detail) {
        strength=Mth.clamp(strength,0.0F,1.25F);
        if(strength<=0.01F)return;
        float pulse=1.0F+Mth.sin(t*0.25F)*0.035F;

        // Masa continua azul tipo Genkidama: blanco/cian dentro, azul fuera.
        auraEllipsoid(pose,0.0F,3.20F,0.0F,1.35F*pulse,2.55F*pulse,1.18F*pulse,
                0.80F,1.00F,1.00F,0.090F*strength,detail);
        auraEllipsoid(pose,0.0F,3.24F,0.0F,1.70F*pulse,3.02F*pulse,1.48F*pulse,
                0.12F,0.86F,1.00F,0.085F*strength,detail);
        auraEllipsoid(pose,0.0F,3.32F,0.0F,1.98F*pulse,3.36F*pulse,1.72F*pulse,
                0.01F,0.48F,1.00F,0.048F*strength,detail);

        int rim=detail?24:14;
        for(int i=0;i<rim;i++){
            float a=i*TAU/rim+t*0.006F;
            float x=Mth.cos(a)*(1.72F+0.14F*Mth.sin(i*2.13F+t*0.12F));
            float y=3.15F+Mth.sin(a)*(3.00F+0.17F*Mth.cos(i*1.77F+t*0.08F));
            float z=Mth.sin(a*1.73F+i)*0.52F;
            pose.pushPose();
            pose.translate(x,y,z);
            pose.scale(0.56F,1.20F+0.36F*Mth.sin(t*0.19F+i),0.56F);
            glowSphere(pose,0.34F,0.55F,0.95F,1.00F,
                    (0.12F+0.07F*(0.5F+0.5F*Mth.sin(t*0.31F+i)))*strength,
                    detail?12:8,detail?8:6);
            pose.popPose();
        }

        int lobes=detail?12:7;
        for(int i=0;i<lobes;i++){
            float side=(i&1)==0?-1.0F:1.0F;
            float lane=i/2.0F;
            float y=0.45F+(lane/Math.max(1.0F,lobes*0.5F-1.0F))*5.25F;
            float x=side*(1.17F+(i%3)*0.15F+Mth.sin(t*0.13F+i)*0.08F);
            float z=Mth.sin(i*1.19F+t*0.10F)*0.48F;
            pose.pushPose();
            pose.translate(x,y,z);
            pose.scale(0.46F,1.58F+0.48F*(0.5F+0.5F*Mth.sin(t*0.22F+i)),0.46F);
            glowSphere(pose,0.40F,0.22F,0.88F,1.00F,
                    (0.08F+0.06F*(0.5F+0.5F*Mth.sin(t*0.33F+i)))*strength,
                    detail?11:8,detail?7:5);
            pose.popPose();
        }

        // Energia ascendente por encima del cabello, sin picos triangulares.
        for(int i=0;i<4;i++){
            pose.pushPose();
            pose.translate(Mth.sin(t*0.08F+i)*0.30F,6.12F+i*0.48F,Mth.cos(t*0.07F+i)*0.18F);
            float s=1.0F-i*0.15F;
            pose.scale(0.78F*s,1.20F*s,0.68F*s);
            glowSphere(pose,0.62F,0.20F,0.88F,1.0F,0.085F*strength,
                    detail?14:9,detail?9:6);
            pose.popPose();
        }

        int motes=detail?24:12;
        for(int i=0;i<motes;i++){
            float a=i*2.399963F+t*(0.018F+(i%4)*0.003F);
            float r=0.38F+(i%7)*0.19F;
            float y=((t*(0.045F+(i%5)*0.004F)+i*0.37F)%5.9F)+0.05F;
            pose.pushPose();
            pose.translate(Mth.cos(a)*r,y,Mth.sin(a)*r*0.62F);
            glowSphere(pose,0.070F+(i%3)*0.017F,0.88F,1.0F,1.0F,
                    0.28F*strength,9,6);
            pose.popPose();
        }
    }

    private static void auraEllipsoid(PoseStack pose,float x,float y,float z,
                                      float sx,float sy,float sz,
                                      float r,float g,float b,float a,boolean detail){
        pose.pushPose();
        pose.translate(x,y,z);
        pose.scale(sx,sy,sz);
        glowSphere(pose,1.0F,r,g,b,a,detail?24:16,detail?14:9);
        pose.popPose();
    }

    private static void energySphere(PoseStack pose,float radius,float alpha,boolean detail){
        int lon=detail?42:28, lat=detail?23:15;
        solidAlphaSphere(pose,radius*0.40F,0.99F,1.0F,1.0F,0.95F*alpha,lon,lat);
        glowSphere(pose,radius*0.66F,0.76F,0.99F,1.0F,0.58F*alpha,lon,lat);
        glowSphere(pose,radius*0.84F,0.22F,0.90F,1.0F,0.42F*alpha,lon,lat);
        glowSphere(pose,radius*1.04F,0.04F,0.68F,1.0F,0.30F*alpha,lon,lat);
        glowSphere(pose,radius*1.22F,0.01F,0.28F,1.0F,0.14F*alpha,
                detail?36:24,detail?19:13);
        glowSphere(pose,radius*1.38F,0.00F,0.10F,1.0F,0.060F*alpha,
                detail?32:20,detail?17:11);
    }

    private static void drawSphereCage(PoseStack pose,float radius,float t,float strength,boolean detail){
        int arcs=detail?10:6, segs=detail?12:8;
        for(int i=0;i<arcs;i++){
            Vec3 prev=null;
            float baseLat=-0.70F+(i%5)*0.34F;
            float phase=i*1.731F+t*(0.035F+(i%3)*0.006F);
            for(int j=0;j<=segs;j++){
                float u=j/(float)segs;
                float lon=phase+u*(1.35F+(i%2)*0.55F);
                float lat=baseLat+Mth.sin(u*9.0F+t*0.13F+i*0.77F)*0.16F;
                float rr=radius*(1.08F+Mth.sin(j*4.13F+t*0.17F+i)*0.035F);
                float c=Mth.cos(lat);
                Vec3 p=new Vec3(rr*c*Mth.cos(lon),rr*Mth.sin(lat),rr*c*Mth.sin(lon));
                if(prev!=null) glowRibbon(pose,prev,p,0.78F,0.98F,1.0F,
                        0.26F*strength,Math.max(0.035F,radius*0.008F));
                prev=p;
            }
        }
    }

    private static void radialRays(PoseStack pose,float radius,float t,int rays,float alpha){
        if(alpha<=0.01F)return;
        double golden=Math.PI*(3.0D-Math.sqrt(5.0D));
        for(int i=0;i<rays;i++){
            double y=1.0D-2.0D*((i+0.5D)/rays);
            double h=Math.sqrt(Math.max(0.0D,1.0D-y*y));
            double ang=i*golden+t*0.045D;
            Vec3 dir=new Vec3(Math.cos(ang)*h,y,Math.sin(ang)*h).normalize();
            Vec3 a=dir.scale(radius*0.78D);
            Vec3 b=dir.scale(radius*(1.50D+0.18D*Math.sin(t*0.27D+i)));
            drawJaggedBolt(pose,a,b,t,300+i*13,0.026F+radius*0.006F,
                    0.48F,0.94F,1.0F,alpha);
        }
    }

    private static void drawHandGlints(PoseStack pose, Vec3 leftHand, Vec3 rightHand,
                                       Vec3 source, float t, float charge, boolean detail) {
        int glints = detail ? 18 : 10;
        Vec3 middle = leftHand.lerp(rightHand, 0.5D);

        for (int i = 0; i < glints; i++) {
            double angle = i * 2.399963D + t * 0.075D;
            double ring = 0.18D + (i % 5) * 0.055D;
            Vec3 center = middle.add(
                    Math.cos(angle) * ring,
                    Math.sin(angle * 1.43D) * ring * 0.38D,
                    Math.sin(angle) * ring * 0.48D
            );

            float pulse = 0.065F + (i % 3) * 0.020F
                    + 0.020F * (0.5F + 0.5F * Mth.sin(t * 0.65F + i));
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            glowSphere(pose, pulse,
                    i % 4 == 0 ? 1.0F : 0.50F,
                    0.96F, 1.0F,
                    (0.34F + charge * 0.38F),
                    detail ? 12 : 8, detail ? 8 : 6);
            pose.popPose();

            if (i < (detail ? 10 : 6)) {
                Vec3 to = source.lerp(center, 0.18D);
                glowRibbon(pose, center, to,
                        i % 3 == 0 ? 1.0F : 0.42F,
                        0.94F, 1.0F,
                        0.28F + charge * 0.25F,
                        0.022F + (i % 3) * 0.004F);
            }
        }

        // Small white/cyan flashes that appear literally between the palms.
        for (int i = 0; i < (detail ? 6 : 4); i++) {
            float flicker = 0.5F + 0.5F * Mth.sin(t * (0.75F + i * 0.05F) + i * 2.1F);
            Vec3 p = middle.lerp(source, 0.28D + i * 0.09D);
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            glowSphere(pose, 0.085F + flicker * 0.055F,
                    0.92F, 1.0F, 1.0F,
                    0.48F + flicker * 0.28F,
                    detail ? 12 : 8, detail ? 8 : 6);
            pose.popPose();
        }
    }

    private static void drawBeamLightning(PoseStack pose,Vec3 start,Vec3 end,float radius,
                                          float t,float alpha,boolean detail){
        if(alpha<=0.01F)return;
        Vec3 axis=end.subtract(start);
        double length=axis.length();
        Vec3 dir=axis.normalize();
        Vec3 helper=Math.abs(dir.y)<0.90D?new Vec3(0,1,0):new Vec3(0,0,1);
        Vec3 u=dir.cross(helper).normalize();
        Vec3 v=dir.cross(u).normalize();

        int chains=detail?10:6;
        int steps=detail?19:12;
        for(int c=0;c<chains;c++){
            Vec3 prev=null;
            double phase=c*TAU/chains+t*0.105D;
            for(int i=0;i<=steps;i++){
                double q=i/(double)steps;
                double ang=phase+q*Math.PI*6.0D;
                Vec3 center=start.add(dir.scale(length*q));
                Vec3 p=center.add(u.scale(Math.cos(ang)*radius*0.96D))
                        .add(v.scale(Math.sin(ang)*radius*0.96D));
                if(prev!=null) glowRibbon(pose,prev,p,0.18F,0.84F,1.0F,
                        0.23F*alpha,0.045F+radius*0.004F);
                prev=p;
            }
        }

        int bursts=detail?18:10;
        for(int i=0;i<bursts;i++){
            double q=((t*0.020D+i*(1.0D/bursts))%1.0D);
            double ang=i*TAU/bursts-t*0.13D;
            Vec3 radial=u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang))).normalize();
            Vec3 center=start.add(dir.scale(length*q));
            Vec3 a=center.add(radial.scale(radius*0.80D));
            Vec3 b=center.add(radial.scale(radius*(1.28D+0.16D*Math.sin(t*0.37D+i))));
            drawJaggedBolt(pose,a,b,t,600+i*19,0.035F+radius*0.003F,
                    0.38F,0.91F,1.0F,0.46F*alpha);
        }
    }

    private static void drawJaggedBolt(PoseStack pose,Vec3 start,Vec3 end,float t,int seed,
                                       float width,float r,float g,float b,float a){
        if(a<=0.005F)return;
        Vec3 axis=end.subtract(start);
        if(axis.lengthSqr()<0.0001D)return;
        Vec3 dir=axis.normalize();
        Vec3 helper=Math.abs(dir.y)<0.90D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 u=dir.cross(helper).normalize();
        Vec3 v=dir.cross(u).normalize();
        Vec3 prev=start;
        double frame=Math.floor(t*0.72D);
        for(int s=1;s<=6;s++){
            double q=s/6.0D;
            Vec3 cur;
            if(s==6)cur=end;
            else{
                double env=Math.sin(Math.PI*q);
                double jitter=Math.max(0.05D,width*6.0D)*env;
                double j1=Math.sin(frame*1.91D+seed*0.73D+s*2.37D)*jitter;
                double j2=Math.cos(frame*1.47D+seed*1.11D+s*1.83D)*jitter;
                cur=start.add(axis.scale(q)).add(u.scale(j1)).add(v.scale(j2));
            }
            glowRibbon(pose,prev,cur,r,g,b,a,width);
            prev=cur;
        }
    }

    private static void skinLimb(PoseStack pose,Vec3 from,Vec3 to,float width,
                                 int baseU,int baseV,int overlayU,int overlayV,float alpha){
        Vec3 delta=to.subtract(from);
        double length=delta.length();
        if(length<0.001D)return;
        Vector3f direction=new Vector3f((float)delta.x,(float)delta.y,(float)delta.z).normalize();
        Quaternionf rotation=new Quaternionf().rotationTo((Vector3fc)new Vector3f(0,1,0),(Vector3fc)direction);

        pose.pushPose();
        pose.translate(from.x,from.y,from.z);
        pose.mulPose(rotation);
        float h=width*0.5F;
        skinBox(pose,-h,0,-h,h,(float)length,h,baseU,baseV,4,12,4,alpha);
        float oh=h*1.08F;
        skinBox(pose,-oh,-0.025F,-oh,oh,(float)length+0.025F,oh,
                overlayU,overlayV,4,12,4,alpha);
        pose.popPose();
    }

    /**
     * Render de skin robusto: misma geometría y UV del modelo existente,
     * pero usando RenderType/VertexConsumer (igual que el fix probado de PURPURE-3D).
     * Evita que el modelo salga negro por el estado global de shaders/blend.
     */
    private static void skinBox(PoseStack pose,
                                float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ,
                                int u, int v, int pixelW, int pixelH, int pixelD,
                                float alpha) {
        if (alpha <= 0.005F) return;

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType skinType = RenderType.entityTranslucent(GOKU_SKIN);
        VertexConsumer skin = buffers.getBuffer(skinType);

        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int faceY = v + pixelD;

        skinFace(skin,m,n,
                minX,minY,minZ, maxX,minY,minZ, maxX,maxY,minZ, minX,maxY,minZ,
                u+pixelD,faceY,u+pixelD+pixelW,faceY+pixelH, 0,0,-1,alpha);
        skinFace(skin,m,n,
                maxX,minY,maxZ, minX,minY,maxZ, minX,maxY,maxZ, maxX,maxY,maxZ,
                u+pixelD+pixelW+pixelD,faceY,u+pixelD+pixelW+pixelD+pixelW,faceY+pixelH,
                0,0,1,alpha);
        skinFace(skin,m,n,
                minX,minY,maxZ, minX,minY,minZ, minX,maxY,minZ, minX,maxY,maxZ,
                u,faceY,u+pixelD,faceY+pixelH, -1,0,0,alpha);
        skinFace(skin,m,n,
                maxX,minY,minZ, maxX,minY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ,
                u+pixelD+pixelW,faceY,u+pixelD+pixelW+pixelD,faceY+pixelH,
                1,0,0,alpha);
        skinFace(skin,m,n,
                minX,maxY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ, minX,maxY,maxZ,
                u+pixelD,v,u+pixelD+pixelW,v+pixelD, 0,1,0,alpha);
        skinFace(skin,m,n,
                minX,minY,maxZ, maxX,minY,maxZ, maxX,minY,minZ, minX,minY,minZ,
                u+pixelD+pixelW,v,u+pixelD+pixelW+pixelW,v+pixelD,
                0,-1,0,alpha);

        buffers.endBatch(skinType);
    }

    private static void skinFace(VertexConsumer c, Matrix4f m, Matrix3f n,
                                 float x0,float y0,float z0, float x1,float y1,float z1,
                                 float x2,float y2,float z2, float x3,float y3,float z3,
                                 int u0,int v0,int u1,int v1,
                                 float nx,float ny,float nz,float alpha) {
        float fu0=u0/64.0F, fv0=v0/64.0F;
        float fu1=u1/64.0F, fv1=v1/64.0F;
        int a=Mth.clamp((int)(alpha*255.0F),0,255);

        skinVertex(c,m,n,x0,y0,z0,fu0,fv1,nx,ny,nz,a);
        skinVertex(c,m,n,x1,y1,z1,fu1,fv1,nx,ny,nz,a);
        skinVertex(c,m,n,x2,y2,z2,fu1,fv0,nx,ny,nz,a);
        skinVertex(c,m,n,x3,y3,z3,fu0,fv0,nx,ny,nz,a);
    }

    private static void skinVertex(VertexConsumer c, Matrix4f m, Matrix3f n,
                                   float x,float y,float z,float u,float v,
                                   float nx,float ny,float nz,int alpha) {
        c.vertex(m,x,y,z)
                .color(255,255,255,alpha)
                .uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n,nx,ny,nz)
                .endVertex();
    }

    private static void solidAlphaCylinder(PoseStack pose,Vec3 start,Vec3 end,float radius,
                                           float r,float g,float b,float a,int sides){
        if(a<=0.005F||radius<=0.01F)return;
        setSolidAlpha(a);
        cylinder(pose,start,end,radius,r,g,b,a,sides);
    }

    private static void glowCylinder(PoseStack pose,Vec3 start,Vec3 end,float radius,
                                     float r,float g,float b,float a,int sides){
        if(a<=0.005F||radius<=0.01F)return;
        setGlow();
        cylinder(pose,start,end,radius,r,g,b,a,sides);
    }

    private static void cylinder(PoseStack pose,Vec3 start,Vec3 end,float radius,
                                 float r,float g,float b,float a,int sides){
        Vec3 axis=end.subtract(start);
        if(axis.lengthSqr()<0.0001D)return;
        axis=axis.normalize();
        Vec3 helper=Math.abs(axis.y)<0.95D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 u=axis.cross(helper).normalize();
        Vec3 v=axis.cross(u).normalize();
        Matrix4f m=pose.last().pose();
        BufferBuilder q=Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int i=0;i<sides;i++){
            double a0=i*Math.PI*2.0D/sides,a1=(i+1)*Math.PI*2.0D/sides;
            Vec3 o0=u.scale(Math.cos(a0)*radius).add(v.scale(Math.sin(a0)*radius));
            Vec3 o1=u.scale(Math.cos(a1)*radius).add(v.scale(Math.sin(a1)*radius));
            vertex(q,m,start.add(o0),r,g,b,a); vertex(q,m,end.add(o0),r,g,b,a);
            vertex(q,m,end.add(o1),r,g,b,a); vertex(q,m,start.add(o1),r,g,b,a);
        }
        BufferUploader.drawWithShader(q.end());
    }

    private static void solidAlphaSphere(PoseStack pose,float radius,float r,float g,float b,float a,int lon,int lat){
        if(radius<=0.02F||a<=0.005F)return;
        setSolidAlpha(a); sphere(pose,radius,r,g,b,a,lon,lat);
    }

    private static void glowSphere(PoseStack pose,float radius,float r,float g,float b,float a,int lon,int lat){
        if(radius<=0.02F||a<=0.005F)return;
        setGlow(); sphere(pose,radius,r,g,b,a,lon,lat);
    }

    private static void sphere(PoseStack pose,float radius,float r,float g,float b,float a,int lon,int lat){
        Matrix4f m=pose.last().pose();
        BufferBuilder q=Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int iy=0;iy<lat;iy++){
            float p0=((float)iy/lat-0.5F)*Mth.PI;
            float p1=((float)(iy+1)/lat-0.5F)*Mth.PI;
            for(int ix=0;ix<lon;ix++){
                float a0=ix*TAU/lon,a1=(ix+1)*TAU/lon;
                sphereV(q,m,radius,p0,a0,r,g,b,a); sphereV(q,m,radius,p0,a1,r,g,b,a);
                sphereV(q,m,radius,p1,a1,r,g,b,a); sphereV(q,m,radius,p1,a0,r,g,b,a);
            }
        }
        BufferUploader.drawWithShader(q.end());
    }

    private static void sphereV(BufferBuilder q,Matrix4f m,float radius,float lat,float lon,
                                float r,float g,float b,float a){
        float c=Mth.cos(lat);
        vertex(q,m,radius*c*Mth.cos(lon),radius*Mth.sin(lat),radius*c*Mth.sin(lon),r,g,b,a);
    }

    private static void glowRibbon(PoseStack pose,Vec3 start,Vec3 end,
                                   float r,float g,float b,float a,float w){
        if(a<=0.005F)return;
        setGlow();
        Matrix4f m=pose.last().pose();
        BufferBuilder q=Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        float x0=(float)start.x,y0=(float)start.y,z0=(float)start.z;
        float x1=(float)end.x,y1=(float)end.y,z1=(float)end.z;
        vertex(q,m,x0,y0-w,z0,r,g,b,a); vertex(q,m,x0,y0+w,z0,r,g,b,a);
        vertex(q,m,x1,y1+w,z1,r,g,b,a); vertex(q,m,x1,y1-w,z1,r,g,b,a);
        vertex(q,m,x0-w,y0,z0,r,g,b,a*0.78F); vertex(q,m,x0+w,y0,z0,r,g,b,a*0.78F);
        vertex(q,m,x1+w,y1,z1,r,g,b,a*0.78F); vertex(q,m,x1-w,y1,z1,r,g,b,a*0.78F);
        BufferUploader.drawWithShader(q.end());
    }

    private static void vertex(BufferBuilder q,Matrix4f m,Vec3 p,float r,float g,float b,float a){
        vertex(q,m,(float)p.x,(float)p.y,(float)p.z,r,g,b,a);
    }

    private static void vertex(BufferBuilder q,Matrix4f m,float x,float y,float z,
                               float r,float g,float b,float a){
        q.vertex(m,x,y,z).color(color(r),color(g),color(b),color(a)).endVertex();
    }

    private static void setSolidAlpha(float a){
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.enableDepthTest();
        if(a<0.995F){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}else RenderSystem.disableBlend();
        RenderSystem.depthMask(true); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setGlow(){
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.enableDepthTest(); RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void restoreState(){
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        RenderSystem.enableCull(); RenderSystem.enableDepthTest();
    }

    private static int color(float v){return Mth.clamp((int)(v*255.0F),0,255);}
    private static float smooth(float a,float b,float v){
        if(b<=a)return v>=b?1.0F:0.0F;
        float x=Mth.clamp((v-a)/(b-a),0,1); return x*x*(3-2*x);
    }
    private static float smoother(float x){
        x=Mth.clamp(x,0,1); return x*x*x*(x*(x*6-15)+10);
    }
}
