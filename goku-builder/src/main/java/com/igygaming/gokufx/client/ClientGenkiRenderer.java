package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GenkiTiming;
import com.igygaming.gokufx.GokuFxMod;
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
 * Genkidama V16 portada desde el visor interactivo aprobado:
 * - skin original del usuario, sin cabello/geometría inventada;
 * - aura de Ki continua cian/blanca, con borde vivo, motas y arcos;
 * - energía del entorno entra visiblemente a la esfera;
 * - esfera crece hacia arriba sin comerse a Goku;
 * - brazos pasan de carga a lanzamiento frontal;
 * - impacto con doble detonación, flash, ondas, rayos y fragmentos.
 *
 * Este renderer es EXCLUSIVO de Genkidama. Kamehameha no se modifica aquí.
 */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGenkiRenderer {
    private static final float TAU = (float) (Math.PI * 2.0D);
    private static final float START_RADIUS = 0.82F;
    private static final double CHARGE_BOTTOM_Y = 6.80D;
    private static final ResourceLocation GENKI_SKIN =
            new ResourceLocation(GokuFxMod.MODID, "textures/entity/genki_goku.png");

    private ClientGenkiRenderer() {}

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
            int raw = ClientGokuEffects.rawEffectTick(target.getUUID(), GokuEffectPacket.GENKI_DAMA);
            if (raw < 0) continue;

            int hits = Math.max(1, ClientGokuEffects.hits(target.getUUID(), GokuEffectPacket.GENKI_DAMA));
            if (raw > GenkiTiming.totalTicks(hits)) continue;

            Vec3 origin = ClientGokuEffects.origin(target.getUUID(), GokuEffectPacket.GENKI_DAMA);
            if (origin == null) origin = target.position();
            if (origin.distanceToSqr(cameraPos) > 340.0D * 340.0D) continue;

            boolean detail = origin.distanceToSqr(cameraPos) < 125.0D * 125.0D;
            float t = raw + partial;

            pose.pushPose();
            pose.translate(origin.x - cameraPos.x, origin.y - cameraPos.y, origin.z - cameraPos.z);

            if (t < GenkiTiming.THROW_START_TICK) {
                drawCharge(pose, t, detail);
            } else if (t < GenkiTiming.CONTACT_TICK) {
                drawApproach(pose, t, detail);
            } else if (t < GenkiTiming.IMPACT_TICK) {
                drawDrag(pose, t, detail);
            } else {
                drawImpact(pose, t, hits, detail);
            }

            pose.popPose();
        }

        restoreState();
    }

    private static void drawCharge(PoseStack pose, float t, boolean detail) {
        float p = GenkiTiming.smoother(GenkiTiming.chargeProgress(t));
        float appear = smooth(0.0F, 15.0F, t);
        float arms = smooth(8.0F, 48.0F, t);
        float breathe = Mth.sin(t * 0.15F) * 0.025F;

        drawGokuScene(pose, t, arms, 0.0F, appear, 0.42F + p * 0.58F, breathe, detail);

        float grow = 0.055F * p + 0.945F * p * p;
        float radius = START_RADIUS + (GenkiTiming.FINAL_RADIUS - START_RADIUS) * grow;
        radius *= 1.0F + Mth.sin(t * 0.20F) * (0.006F + p * 0.010F);

        Vec3 center = chargeCenter(radius);
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        drawGenkiSphere(pose, radius, t, 1.0F, detail);
        drawElectricCage(pose, radius, t, 0.22F + 0.78F * p, detail);
        drawEnergyWaves(pose, radius, t, p, detail);
        pose.popPose();

        drawGatheringEnergy(pose, center, radius, t, p, detail);
    }

    private static void drawApproach(PoseStack pose, float t, boolean detail) {
        float rawP = GenkiTiming.approachProgress(t);
        float p = GenkiTiming.heavy(rawP);
        float throwPose = smooth(GenkiTiming.THROW_START_TICK,
                GenkiTiming.THROW_START_TICK + 13.0F, t);

        drawGokuScene(pose, t, 1.0F, throwPose, 1.0F,
                0.92F * (1.0F - rawP * 0.22F), 0.0F, detail);

        Vec3 start = sphereStart();
        Vec3 contact = contactCenter();
        Vec3 orb = start.lerp(contact, p);
        float radius = GenkiTiming.FINAL_RADIUS * (1.0F + Mth.sin(t * 0.24F) * 0.010F);
        drawFlyingOrb(pose, orb, start, rawP, radius, t, detail, false);
    }

    private static void drawDrag(PoseStack pose, float t, boolean detail) {
        float rawP = GenkiTiming.dragProgress(t);
        float p = GenkiTiming.dragEase(rawP);
        Vec3 contact = contactCenter();
        Vec3 impact = impactCenter();
        Vec3 orb = contact.lerp(impact, p);
        float radius = GenkiTiming.FINAL_RADIUS * (1.0F + Mth.sin(t * 0.29F) * 0.012F);

        float gokuAlpha = 1.0F - smooth(GenkiTiming.CONTACT_TICK + 2.0F,
                GenkiTiming.CONTACT_TICK + 22.0F, t);
        if (gokuAlpha > 0.01F) {
            drawGokuScene(pose, t, 1.0F, 1.0F, gokuAlpha,
                    0.72F * gokuAlpha, 0.0F, detail);
        }

        drawFlyingOrb(pose, orb, contact, rawP, radius, t, detail, true);
    }

    private static void drawGokuScene(PoseStack pose, float t, float armsUp, float throwPose,
                                      float alpha, float auraStrength, float breathe, boolean detail) {
        pose.pushPose();
        pose.translate(GenkiTiming.GOKU_X, GenkiTiming.GOKU_Y + breathe, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.scale(0.30F, 0.30F, 0.30F);

        // Aura suave del visor aprobado; no sustituye ni tapa la skin.
        drawGokuAura(pose, t, auraStrength, detail);
        drawGoku(pose, t, armsUp, throwPose, alpha);

        pose.popPose();
    }

    private static void drawGoku(PoseStack pose, float t, float armsUp, float throwPose, float alpha) {
        alpha = Mth.clamp(alpha, 0.0F, 1.0F);
        if (alpha <= 0.01F) return;

        float breathe = Mth.sin(t * 0.13F) * 0.035F;
        float legSwing = Mth.sin(t * 0.09F) * 0.025F;

        // Piernas: skin original.
        skinBox(pose, -0.72F, 0.12F + legSwing, -0.40F, -0.08F, 2.02F, 0.40F,
                0, 16, 4, 12, 4, alpha);
        skinBox(pose, 0.08F, 0.12F - legSwing, -0.40F, 0.72F, 2.02F, 0.40F,
                16, 48, 4, 12, 4, alpha);
        skinBox(pose, -0.76F, 0.08F + legSwing, -0.44F, -0.04F, 2.06F, 0.44F,
                0, 32, 4, 12, 4, alpha);
        skinBox(pose, 0.04F, 0.08F - legSwing, -0.44F, 0.76F, 2.06F, 0.44F,
                0, 48, 4, 12, 4, alpha);

        // Torso y segunda capa de la skin.
        skinBox(pose, -0.96F, 1.92F, -0.50F, 0.96F, 4.28F + breathe, 0.50F,
                16, 16, 8, 12, 4, alpha);
        skinBox(pose, -1.01F, 1.87F, -0.55F, 1.01F, 4.33F + breathe, 0.55F,
                16, 32, 8, 12, 4, alpha);

        // Cabeza + capa exterior. Sin cabello añadido: solo la skin entregada.
        skinBox(pose, -0.70F, 4.52F + breathe, -0.55F, 0.70F, 5.92F + breathe, 0.55F,
                0, 0, 8, 8, 8, alpha);
        skinBox(pose, -0.755F, 4.465F + breathe, -0.605F, 0.755F, 5.975F + breathe, 0.605F,
                32, 0, 8, 8, 8, alpha);

        drawAnimatedSkinArm(pose, -1.0F, t, armsUp, throwPose, alpha,
                40, 16, 40, 32);
        drawAnimatedSkinArm(pose, 1.0F, t, armsUp, throwPose, alpha,
                32, 48, 48, 48);
    }

    private static void drawAnimatedSkinArm(PoseStack pose, float side, float t,
                                            float armsUp, float throwPose, float alpha,
                                            int baseU, int baseV, int overlayU, int overlayV) {
        Vec3 shoulder = new Vec3(side * 0.96D, 3.94D, -0.02D);
        double sway = Math.sin(t * 0.14D + side) * 0.065D;

        Vec3 elbowLow = new Vec3(side * 1.70D, 3.16D + sway, -0.05D);
        Vec3 handLow = new Vec3(side * 1.92D, 2.31D + sway, -0.14D);

        Vec3 elbowUp = new Vec3(side * 1.22D, 5.35D + sway, -0.05D);
        Vec3 handUp = new Vec3(side * 0.84D, 7.20D + sway, -0.10D);

        // Local -Z se convierte en dirección mundial +X por la rotación -90° de Goku:
        // es exactamente hacia donde viaja la Genkidama en este mod.
        Vec3 elbowThrow = new Vec3(side * 0.38D, 2.65D, -1.58D);
        Vec3 handThrow = new Vec3(side * 0.22D, 1.05D, -3.55D);

        Vec3 elbow = elbowLow.lerp(elbowUp, armsUp).lerp(elbowThrow, throwPose);
        Vec3 hand = handLow.lerp(handUp, armsUp).lerp(handThrow, throwPose);

        skinLimb(pose, shoulder, elbow, 0.72F, baseU, baseV, overlayU, overlayV, alpha);
        skinLimb(pose, elbow, hand, 0.66F, baseU, baseV, overlayU, overlayV, alpha);
    }

    private static void skinLimb(PoseStack pose, Vec3 from, Vec3 to, float width,
                                 int baseU, int baseV, int overlayU, int overlayV, float alpha) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.001D) return;

        Vector3f direction = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize();
        Quaternionf rotation = new Quaternionf().rotationTo((Vector3fc) new Vector3f(0, 1, 0), (Vector3fc) direction);

        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(rotation);

        float h = width * 0.5F;
        skinBox(pose, -h, 0.0F, -h, h, (float) length, h,
                baseU, baseV, 4, 12, 4, alpha);
        float oh = h * 1.08F;
        skinBox(pose, -oh, -0.025F, -oh, oh, (float) length + 0.025F, oh,
                overlayU, overlayV, 4, 12, 4, alpha);

        pose.popPose();
    }

    private static void drawGokuAura(PoseStack pose, float t, float strength, boolean detail) {
        strength = Mth.clamp(strength, 0.0F, 1.2F);
        if (strength <= 0.01F) return;

        float pulse = 1.0F + Mth.sin(t * 0.25F) * 0.035F;

        // Masa continua: núcleo blanco, capa cian y envolvente azul.
        pose.pushPose();
        pose.translate(0.0D, 3.20D, 0.0D);
        pose.scale(1.34F * pulse, 2.55F * pulse, 1.16F * pulse);
        glowSphere(pose, 1.0F, 0.86F, 1.00F, 1.00F,
                0.105F * strength, detail ? 32 : 22, detail ? 18 : 12);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.0D, 3.24D, 0.0D);
        pose.scale(1.68F * pulse, 3.00F * pulse, 1.46F * pulse);
        glowSphere(pose, 1.0F, 0.16F, 0.86F, 1.00F,
                0.090F * strength, detail ? 30 : 20, detail ? 17 : 11);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.0D, 3.32D, 0.0D);
        pose.scale(1.95F * pulse, 3.32F * pulse, 1.70F * pulse);
        glowSphere(pose, 1.0F, 0.02F, 0.52F, 1.00F,
                0.045F * strength, detail ? 28 : 18, detail ? 15 : 10);
        pose.popPose();

        // Borde irregular blanco/cálido, formado por blobs suaves (sin triángulos).
        int rim = detail ? 34 : 20;
        for (int i = 0; i < rim; i++) {
            float a = i * TAU / rim + t * 0.006F;
            float irregular = 0.16F * Mth.sin(i * 2.13F + t * 0.12F);
            float rx = 1.70F + irregular;
            float ry = 3.00F + 0.20F * Mth.cos(i * 1.77F + t * 0.08F);
            float x = Mth.cos(a) * rx;
            float y = 3.15F + Mth.sin(a) * ry;
            float z = Mth.sin(a * 1.73F + i) * 0.52F;

            pose.pushPose();
            pose.translate(x, y, z);
            pose.scale(0.58F, 1.25F + 0.45F * Mth.sin(t * 0.19F + i), 0.58F);
            boolean warm = i % 5 == 0;
            glowSphere(pose, 0.34F,
                    warm ? 1.00F : 0.72F,
                    warm ? 0.98F : 0.96F,
                    warm ? 0.78F : 1.00F,
                    (0.18F + 0.08F * Mth.sin(t * 0.31F + i)) * strength,
                    detail ? 14 : 10, detail ? 9 : 7);
            pose.popPose();
        }

        // Lenguas de Ki suaves ascendiendo a los lados.
        int lobes = detail ? 18 : 10;
        for (int i = 0; i < lobes; i++) {
            float side = (i & 1) == 0 ? -1.0F : 1.0F;
            float lane = i / 2.0F;
            float y = 0.35F + (lane / Math.max(1.0F, lobes * 0.5F - 1.0F)) * 5.30F;
            float x = side * (1.18F + (i % 3) * 0.15F + Mth.sin(t * 0.13F + i) * 0.08F);
            float z = Mth.sin(i * 1.19F + t * 0.10F) * 0.50F;

            pose.pushPose();
            pose.translate(x, y, z);
            pose.scale(0.46F, 1.70F + 0.55F * (0.5F + 0.5F * Mth.sin(t * 0.22F + i)), 0.46F);
            glowSphere(pose, 0.40F, 0.32F, 0.90F, 1.00F,
                    (0.10F + 0.08F * (0.5F + 0.5F * Mth.sin(t * 0.33F + i))) * strength,
                    detail ? 13 : 9, detail ? 8 : 6);
            pose.popPose();
        }

        // Corona superior del Ki.
        pose.pushPose();
        pose.translate(0.0D, 6.15D, 0.0D);
        pose.scale(1.35F, 2.00F + Mth.sin(t * 0.20F) * 0.20F, 1.10F);
        glowSphere(pose, 0.70F, 0.40F, 0.93F, 1.00F,
                0.11F * strength, detail ? 20 : 14, detail ? 12 : 8);
        pose.popPose();

        // Motas blancas dentro del aura.
        int motes = detail ? 28 : 16;
        for (int i = 0; i < motes; i++) {
            float a = i * 2.399963F + t * (0.018F + (i % 4) * 0.003F);
            float r = 0.35F + (i % 7) * 0.19F;
            float y = ((t * (0.045F + (i % 5) * 0.004F) + i * 0.37F) % 5.9F) + 0.05F;
            pose.pushPose();
            pose.translate(Mth.cos(a) * r, y, Mth.sin(a) * r * 0.62F);
            glowSphere(pose, 0.075F + (i % 3) * 0.018F,
                    0.96F, 1.00F, 1.00F, 0.34F * strength,
                    10, 7);
            pose.popPose();
        }

        // Arcos eléctricos cortos, pegados al cuerpo.
        int arcs = detail ? 6 : 3;
        int segs = detail ? 12 : 8;
        for (int i = 0; i < arcs; i++) {
            Vec3 previous = null;
            for (int j = 0; j <= segs; j++) {
                float q = j / (float) segs;
                float ang = q * 1.55F * Mth.PI + t * (0.035F + i * 0.002F) + i * 0.93F;
                float rr = 1.02F + i * 0.07F + Mth.sin(j * 3.7F + t * 0.21F) * 0.07F;
                float y = 0.70F + i * 0.55F + q * 0.60F + Mth.sin(j * 2.8F + t * 0.18F) * 0.07F;
                Vec3 point = new Vec3(Mth.cos(ang) * rr, y, Mth.sin(ang) * rr);
                if (previous != null) {
                    glowRibbon(pose, previous, point, 0.78F, 0.98F, 1.00F,
                            0.22F * strength, 0.035F);
                }
                previous = point;
            }
        }
    }

    private static void drawGenkiSphere(PoseStack pose, float radius, float t,
                                        float alpha, boolean detail) {
        if (alpha <= 0.005F) return;
        int lon = detail ? 56 : 36;
        int lat = detail ? 28 : 18;
        float pulse = 1.0F + Mth.sin(t * 0.21F) * 0.008F;
        radius *= pulse;

        // Núcleo blanco-azulado + volumen cian + capas profundas.
        solidSphere(pose, radius * 0.80F, 0.94F, 1.00F, 1.00F, lon, lat);
        glowSphere(pose, radius * 0.88F, 0.68F, 0.98F, 1.00F,
                0.48F * alpha, lon, lat);
        glowSphere(pose, radius * 0.98F, 0.12F, 0.78F, 1.00F,
                0.30F * alpha, lon, lat);
        glowSphere(pose, radius * 1.07F, 0.02F, 0.48F, 1.00F,
                0.18F * alpha, detail ? 46 : 30, detail ? 24 : 16);
        glowSphere(pose, radius * 1.17F, 0.06F, 0.32F, 1.00F,
                0.085F * alpha, detail ? 38 : 26, detail ? 20 : 14);

        // Relieve visible: pequeñas crestas/blobs de plasma pegados a la superficie.
        // Mantiene la misma esfera y aura, pero evita el aspecto completamente liso.
        int reliefNodes = detail ? 30 : 16;
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < reliefNodes; i++) {
            double yy = 1.0D - 2.0D * ((i + 0.5D) / reliefNodes);
            double hh = Math.sqrt(Math.max(0.0D, 1.0D - yy * yy));
            double aa = i * golden + t * 0.010D;
            float wobble = 1.0F
                    + Mth.sin(t * 0.31F + i * 1.71F) * 0.028F
                    + Mth.sin(t * 0.17F + i * 2.37F) * 0.018F;
            float rr = radius * 1.015F * wobble;
            pose.pushPose();
            pose.translate(Math.cos(aa) * hh * rr, yy * rr, Math.sin(aa) * hh * rr);
            float node = Math.max(0.075F, radius * (0.018F + (i % 4) * 0.0025F));
            glowSphere(pose, node,
                    i % 5 == 0 ? 0.96F : 0.26F,
                    i % 5 == 0 ? 1.00F : 0.88F,
                    1.00F,
                    (0.24F + (i % 3) * 0.045F) * alpha,
                    detail ? 14 : 10, detail ? 9 : 7);
            pose.popPose();
        }

        // Bandas orbitales, como en el visor.
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(28.0F + i * 47.0F));
            pose.mulPose(Axis.ZP.rotation(t * (0.010F + i * 0.002F) + i * 1.2F));
            ring(pose, radius * (1.03F + i * 0.035F),
                    Math.max(0.07F, radius * (0.011F + i * 0.001F)),
                    i == 1 ? 0.95F : 0.42F,
                    i == 1 ? 1.00F : 0.92F,
                    1.00F,
                    (0.21F - i * 0.035F) * alpha,
                    detail ? 76 : 48);
            pose.popPose();
        }
    }

    private static void drawElectricCage(PoseStack pose, float radius, float t,
                                         float strength, boolean detail) {
        int arcs = detail ? 10 : 6;
        int segments = detail ? 13 : 8;
        for (int i = 0; i < arcs; i++) {
            Vec3 previous = null;
            float baseLat = -0.76F + (i % 6) * 0.29F;
            float phase = i * 1.731F + t * (0.032F + (i % 3) * 0.006F);
            for (int j = 0; j <= segments; j++) {
                float u = j / (float) segments;
                float lon = phase + u * (1.28F + (i % 2) * 0.52F);
                float lat = baseLat
                        + Mth.sin(u * 9.0F + t * 0.13F + i * 0.77F) * 0.16F
                        + Mth.sin(u * 21.0F + i) * 0.045F;
                float rr = radius * (1.10F + Mth.sin(j * 4.13F + t * 0.17F + i) * 0.028F);
                float c = Mth.cos(lat);
                Vec3 point = new Vec3(rr * c * Mth.cos(lon), rr * Mth.sin(lat), rr * c * Mth.sin(lon));
                if (previous != null) {
                    glowRibbon(pose, previous, point, 0.86F, 1.00F, 1.00F,
                            0.28F * strength, Math.max(0.045F, radius * 0.0075F));
                }
                previous = point;
            }
        }
    }

    private static void drawEnergyWaves(PoseStack pose, float radius, float t,
                                        float progress, boolean detail) {
        if (progress < 0.18F) return;
        int count = detail ? 4 : 2;
        for (int i = 0; i < count; i++) {
            float cycle = ((t * 0.58F + i * 14.0F) % 42.0F) / 42.0F;
            float r = radius * (1.10F + cycle * 0.62F);
            float a = (1.0F - cycle) * (0.07F + progress * 0.11F);
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(24.0F + i * 38.0F));
            pose.mulPose(Axis.ZP.rotation(t * 0.009F + i));
            ring(pose, r, Math.max(0.07F, radius * 0.010F),
                    0.46F, 0.97F, 1.00F, a, detail ? 68 : 42);
            pose.popPose();
        }
    }

    private static void drawGatheringEnergy(PoseStack pose, Vec3 center, float radius,
                                            float t, float progress, boolean detail) {
        // V15: absorción mucho más notoria sin tocar el modelo de Goku,
        // la trayectoria, el lanzamiento ni la explosión.
        int count = detail ? 72 : 40;
        for (int i = 0; i < count; i++) {
            float cycle = ((t * (0.98F + (i % 6) * 0.072F) + i * 5.15F) % 72.0F) / 72.0F;
            float q = GenkiTiming.smoother(cycle);

            double angle = i * 2.399963D + t * 0.018D;
            double outer = 14.0D + (i % 8) * 2.45D;
            double startY = -10.0D + (i % 11) * 2.05D;

            Vec3 start = center.add(Math.cos(angle) * outer, startY, Math.sin(angle) * outer);
            Vec3 control = center.add(
                    Math.cos(angle + 0.62D) * outer * 0.50D,
                    6.8D + (i % 5) * 1.35D + progress * 2.0D,
                    Math.sin(angle + 0.62D) * outer * 0.50D
            );

            double targetAngle = angle + 1.02D;
            double surface = radius * 0.95D;
            Vec3 target = center.add(
                    Math.cos(targetAngle) * surface * 0.60D,
                    Math.sin(i * 1.37D) * surface * 0.44D,
                    Math.sin(targetAngle) * surface * 0.60D
            );

            Vec3 head = quadraticBezier(start, control, target, q);
            float oldQ = GenkiTiming.smoother(Math.max(0.0F, cycle - 0.105F));
            Vec3 tail = quadraticBezier(start, control, target, oldQ);

            // Cabezas más grandes y brillantes, especialmente al entrar en la esfera.
            float arrival = Mth.clamp((q - 0.68F) / 0.32F, 0.0F, 1.0F);
            pose.pushPose();
            pose.translate(head.x, head.y, head.z);
            float headSize = 0.27F + 0.29F * progress + (i % 4) * 0.036F + arrival * 0.25F;
            boolean white = i % 5 == 0 || arrival > 0.82F;
            glowSphere(pose, headSize,
                    white ? 1.00F : 0.38F,
                    white ? 1.00F : 0.90F,
                    1.00F,
                    0.55F + 0.36F * progress,
                    detail ? 16 : 11, detail ? 10 : 8);
            pose.popPose();

            // Cola más larga, gruesa y visible para marcar claramente la dirección.
            glowRibbon(pose, tail, head,
                    0.34F, 0.92F, 1.00F,
                    0.30F + 0.38F * progress,
                    0.100F + 0.060F * progress);
        }

        // Grandes corrientes desde el terreno/entorno hacia la Genkidama.
        // Son adicionales a las partículas pequeñas para que la absorción se lea a distancia.
        int columns = detail ? 22 : 12;
        for (int i = 0; i < columns; i++) {
            double a = i * (Math.PI * 2.0D / columns) + t * 0.010D;
            double dist = 9.0D + (i % 5) * 2.8D;
            float pulse = 0.5F + 0.5F * Mth.sin(t * (0.20F + (i % 3) * 0.025F) + i);

            Vec3 ground = center.add(
                    Math.cos(a) * dist,
                    -12.0D + (i % 3) * 1.2D,
                    Math.sin(a) * dist
            );
            Vec3 mid = center.add(
                    Math.cos(a + 0.38D) * dist * 0.46D,
                    1.8D + (i % 4) * 1.1D,
                    Math.sin(a + 0.38D) * dist * 0.46D
            );
            double s = radius * (0.56D + (i % 3) * 0.07D);
            Vec3 intake = center.add(
                    Math.cos(a + 0.85D) * s,
                    -radius * 0.34D + (i % 4) * radius * 0.15D,
                    Math.sin(a + 0.85D) * s
            );

            // Dos tramos curvos aproximados con cintas superpuestas para un flujo más grueso.
            Vec3 bend = quadraticBezier(ground, mid, intake, 0.52F);
            float alpha = (0.14F + 0.22F * pulse) * (0.40F + 0.60F * progress);
            float width = 0.10F + 0.055F * pulse + 0.045F * progress;

            glowRibbon(pose, ground, bend, 0.22F, 0.82F, 1.00F, alpha, width);
            glowRibbon(pose, bend, intake, 0.58F, 0.96F, 1.00F, alpha * 1.30F, width * 0.78F);

            // Destello grande justo antes de ser absorbido.
            pose.pushPose();
            pose.translate(intake.x, intake.y, intake.z);
            glowSphere(pose,
                    0.18F + pulse * 0.18F + progress * 0.10F,
                    0.86F, 0.99F, 1.00F,
                    0.34F + 0.36F * progress,
                    detail ? 14 : 10, detail ? 9 : 7);
            pose.popPose();
        }

        // Pulsos de absorción alrededor de la esfera para remarcar el punto de entrada.
        int intakeRings = detail ? 3 : 2;
        for (int i = 0; i < intakeRings; i++) {
            float cycle = ((t * (0.74F + i * 0.08F) + i * 11.0F) % 30.0F) / 30.0F;
            float rr = radius * (1.06F + cycle * 0.22F);
            float alpha = (1.0F - cycle) * (0.16F + progress * 0.18F);
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            pose.mulPose(Axis.XP.rotationDegrees(18.0F + i * 51.0F));
            pose.mulPose(Axis.ZP.rotation(t * (0.016F + i * 0.004F) + i));
            ring(pose, rr,
                    Math.max(0.10F, radius * 0.013F),
                    0.70F, 0.99F, 1.00F,
                    alpha,
                    detail ? 80 : 52);
            pose.popPose();
        }
    }

    private static void drawFlyingOrb(PoseStack pose, Vec3 orb, Vec3 trailOrigin,
                                      float rawProgress, float radius, float t,
                                      boolean detail, boolean dragging) {
        pose.pushPose();
        pose.translate(orb.x, orb.y, orb.z);
        drawGenkiSphere(pose, radius, t, 1.0F, detail);
        drawElectricCage(pose, radius, t, 0.96F, detail);
        pose.popPose();

        int trails = detail ? 6 : 4;
        for (int i = 1; i <= trails; i++) {
            float old = Math.max(0.0F, rawProgress - i * 0.045F);
            Vec3 oldPos = dragging
                    ? contactCenter().lerp(impactCenter(), GenkiTiming.dragEase(old))
                    : sphereStart().lerp(contactCenter(), GenkiTiming.heavy(old));
            pose.pushPose();
            pose.translate(oldPos.x, oldPos.y, oldPos.z);
            glowSphere(pose, radius * (0.90F - i * 0.065F), 0.18F, 0.86F, 1.00F,
                    Math.max(0.018F, 0.12F - i * 0.014F),
                    detail ? 34 : 22, detail ? 18 : 12);
            pose.popPose();
        }

        if (orb.distanceToSqr(trailOrigin) > 0.04D) {
            glowRibbon(pose, trailOrigin, orb, 0.24F, 0.86F, 1.00F,
                    0.12F, Math.max(0.13F, radius * 0.060F));
        }
    }

    private static void drawImpact(PoseStack pose, float t, int hits, boolean detail) {
        float age = t - GenkiTiming.IMPACT_TICK;
        float fade = GenkiTiming.fade(t, hits);
        float active = GenkiTiming.activeProgress(t, hits);
        Vec3 impact = impactCenter();

        pose.pushPose();
        pose.translate(impact.x, impact.y, impact.z);

        // DETONACIÓN 1: flash blanco brutal.
        if (age < 9.0F) {
            float q = Mth.clamp(age / 9.0F, 0.0F, 1.0F);
            float flash = 1.0F - q;
            solidAlphaSphere(pose,
                    GenkiTiming.FINAL_RADIUS * (0.92F + q * 0.78F),
                    0.97F, 1.00F, 1.00F,
                    0.92F * flash + 0.07F,
                    detail ? 54 : 34, detail ? 28 : 18);
            glowSphere(pose,
                    GenkiTiming.FINAL_RADIUS * (1.10F + q * 1.60F),
                    0.52F, 0.96F, 1.00F,
                    0.34F * flash,
                    detail ? 48 : 30, detail ? 24 : 16);
        }

        // Núcleo que se expande y luego permanece como energía residual.
        float first = GenkiTiming.smoother(Mth.clamp(age / 24.0F, 0.0F, 1.0F));
        float coreR = 4.0F + first * 13.5F;
        glowSphere(pose, coreR, 0.15F, 0.82F, 1.00F,
                Math.max(0.035F, 0.28F * (1.0F - first * 0.58F)) * fade,
                detail ? 48 : 30, detail ? 24 : 16);
        glowSphere(pose, coreR * 0.66F, 0.90F, 1.00F, 1.00F,
                Math.max(0.04F, 0.36F * (1.0F - first * 0.62F)) * fade,
                detail ? 44 : 28, detail ? 22 : 14);

        // DETONACIÓN 2: segundo pulso, un instante después.
        if (age >= 7.0F && age < 38.0F) {
            float q = GenkiTiming.smoother(Mth.clamp((age - 7.0F) / 31.0F, 0.0F, 1.0F));
            float a = (1.0F - q) * 0.28F * fade;
            glowSphere(pose, 5.0F + q * 28.0F,
                    0.30F, 0.90F, 1.00F, a,
                    detail ? 44 : 28, detail ? 22 : 14);
            glowSphere(pose, 3.0F + q * 21.0F,
                    0.92F, 1.00F, 1.00F, a * 0.72F,
                    detail ? 38 : 24, detail ? 20 : 12);
        }

        // Corona de supernova azul: borde irregular brillante alrededor del núcleo.
        if (age >= 3.0F && age < 46.0F) {
            float q = GenkiTiming.smoother(Mth.clamp((age - 3.0F) / 43.0F, 0.0F, 1.0F));
            float coronaFade = (1.0F - q) * fade;
            int lobes = detail ? 28 : 16;
            double golden = Math.PI * (3.0D - Math.sqrt(5.0D));

            for (int i = 0; i < lobes; i++) {
                double yy = 1.0D - 2.0D * ((i + 0.5D) / lobes);
                double hh = Math.sqrt(Math.max(0.0D, 1.0D - yy * yy));
                double aa = i * golden + t * 0.026D;
                Vec3 dir = new Vec3(Math.cos(aa) * hh, yy, Math.sin(aa) * hh);
                double distance = 7.0D + q * (15.0D + (i % 5) * 1.8D);
                Vec3 p = dir.scale(distance);

                pose.pushPose();
                pose.translate(p.x, p.y, p.z);
                float plume = (1.35F + q * 2.6F) * (0.82F + (i % 4) * 0.08F);
                pose.scale(plume, plume * (1.15F + (i % 3) * 0.16F), plume);
                glowSphere(pose, 1.0F,
                        i % 6 == 0 ? 0.96F : 0.18F,
                        i % 6 == 0 ? 1.00F : 0.78F,
                        1.00F,
                        (0.10F + (i % 4) * 0.018F) * coronaFade,
                        detail ? 16 : 11, detail ? 10 : 8);
                pose.popPose();
            }
        }

        // Ocho ondas expansivas con orientaciones distintas.
        int rings = detail ? 8 : 5;
        for (int i = 0; i < rings; i++) {
            float local = Mth.clamp((age - i * 1.55F) / 34.0F, 0.0F, 1.0F);
            if (local <= 0.0F || local >= 1.0F) continue;
            float q = GenkiTiming.smoother(local);
            float r = 5.5F + q * (22.0F + i * 2.7F);
            float a = (1.0F - q) * (0.24F - i * 0.012F) * fade;
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(13.0F + i * 24.0F));
            pose.mulPose(Axis.YP.rotationDegrees(i * 31.0F));
            pose.mulPose(Axis.ZP.rotation(t * (0.008F + i * 0.0008F) + i));
            ring(pose, r, 0.16F + r * 0.010F,
                    i % 3 == 0 ? 0.92F : 0.22F,
                    0.94F, 1.00F, a,
                    detail ? 74 : 46);
            pose.popPose();
        }

        drawExplosionRays(pose, t, age, fade, detail);
        drawImpactShards(pose, t, age, fade, detail);

        // Energía residual para ataques con muchos tótems.
        if (age > 44.0F) {
            float pulse = 1.0F + Mth.sin(t * 0.32F) * 0.045F;
            float residual = (5.7F - active * 0.65F) * pulse;
            drawGenkiSphere(pose, residual, t * 1.25F, 0.58F * fade, detail);
        }

        pose.popPose();
    }

    private static void drawExplosionRays(PoseStack pose, float t, float age,
                                          float fade, boolean detail) {
        if (age < 0.0F || age > 52.0F) return;
        int rays = detail ? 44 : 24;
        float p = Mth.clamp(age / 38.0F, 0.0F, 1.0F);
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));

        for (int i = 0; i < rays; i++) {
            double y = 1.0D - 2.0D * ((i + 0.5D) / rays);
            double h = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double angle = i * golden + t * 0.020D;
            Vec3 dir = new Vec3(Math.cos(angle) * h, y, Math.sin(angle) * h).normalize();

            double inner = 3.0D + p * 4.0D;
            double outer = 8.0D + p * (20.0D + (i % 6) * 2.1D);
            Vec3 a = dir.scale(inner);
            Vec3 b = dir.scale(outer);
            glowRibbon(pose, a, b,
                    i % 5 == 0 ? 1.0F : 0.34F,
                    0.92F, 1.00F,
                    (1.0F - p) * 0.34F * fade,
                    0.07F + p * 0.08F);
        }
    }

    private static void drawImpactShards(PoseStack pose, float t, float age,
                                         float fade, boolean detail) {
        if (age < 0.0F || age > 58.0F) return;
        float p = Mth.clamp(age / 58.0F, 0.0F, 1.0F);
        int count = detail ? 54 : 28;

        for (int i = 0; i < count; i++) {
            double angle = i * 2.399963D + t * 0.006D;
            double radial = 4.0D + p * (15.0D + (i % 7) * 2.1D);
            double y = -1.0D + p * (-2.0D + (i % 9) * 1.35D);
            pose.pushPose();
            pose.translate(Math.cos(angle) * radial, y, Math.sin(angle) * radial);
            pose.mulPose(Axis.YP.rotation(t * 0.05F + i));
            float s = 0.11F + (i % 4) * 0.040F;
            box(pose, -s, -s, -s, s, s, s,
                    i % 5 == 0 ? 0.95F : 0.35F,
                    0.94F, 1.00F,
                    (1.0F - p) * 0.72F * fade);
            pose.popPose();
        }
    }

    private static Vec3 quadraticBezier(Vec3 a, Vec3 b, Vec3 c, float t) {
        double u = 1.0D - t;
        return a.scale(u * u)
                .add(b.scale(2.0D * u * t))
                .add(c.scale(t * t));
    }

    private static Vec3 chargeCenter(float radius) {
        // La parte inferior queda fija: la esfera crece hacia ARRIBA, nunca sobre Goku.
        return new Vec3(GenkiTiming.GOKU_X, CHARGE_BOTTOM_Y + radius, 0.0D);
    }

    private static Vec3 sphereStart() {
        return new Vec3(GenkiTiming.GOKU_X, GenkiTiming.SPHERE_Y, 0.0D);
    }

    private static Vec3 contactCenter() {
        return new Vec3(0.0D, GenkiTiming.CONTACT_Y, 0.0D);
    }

    private static Vec3 impactCenter() {
        return new Vec3(GenkiTiming.DRAG_DISTANCE, GenkiTiming.CONTACT_Y, 0.0D);
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
        RenderType skinType = RenderType.entityTranslucent(GENKI_SKIN);
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

    private static void box(PoseStack pose,
                            float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ,
                            float r, float g, float b, float a) {
        if (a <= 0.005F) return;
        setSolidAlpha(a);
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        quad(q,m,minX,minY,maxZ, maxX,minY,maxZ, maxX,maxY,maxZ, minX,maxY,maxZ,r,g,b,a);
        quad(q,m,maxX,minY,minZ, minX,minY,minZ, minX,maxY,minZ, maxX,maxY,minZ,r,g,b,a);
        quad(q,m,minX,minY,minZ, minX,minY,maxZ, minX,maxY,maxZ, minX,maxY,minZ,r,g,b,a);
        quad(q,m,maxX,minY,maxZ, maxX,minY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ,r,g,b,a);
        quad(q,m,minX,maxY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ, minX,maxY,minZ,r,g,b,a);
        quad(q,m,minX,minY,minZ, maxX,minY,minZ, maxX,minY,maxZ, minX,minY,maxZ,r,g,b,a);

        BufferUploader.drawWithShader(q.end());
    }

    private static void quad(BufferBuilder q, Matrix4f m,
                             float x0,float y0,float z0, float x1,float y1,float z1,
                             float x2,float y2,float z2, float x3,float y3,float z3,
                             float r,float g,float b,float a) {
        vertex(q,m,x0,y0,z0,r,g,b,a);
        vertex(q,m,x1,y1,z1,r,g,b,a);
        vertex(q,m,x2,y2,z2,r,g,b,a);
        vertex(q,m,x3,y3,z3,r,g,b,a);
    }

    private static void solidSphere(PoseStack pose, float radius,
                                    float r, float g, float b,
                                    int lon, int lat) {
        if (radius <= 0.02F) return;
        setSolid();
        sphere(pose, radius, r, g, b, 1.0F, lon, lat);
    }

    private static void solidAlphaSphere(PoseStack pose, float radius,
                                         float r, float g, float b, float a,
                                         int lon, int lat) {
        if (radius <= 0.02F || a <= 0.005F) return;
        setSolidAlpha(a);
        sphere(pose, radius, r, g, b, a, lon, lat);
    }

    private static void glowSphere(PoseStack pose, float radius,
                                   float r, float g, float b, float a,
                                   int lon, int lat) {
        if (radius <= 0.02F || a <= 0.005F) return;
        setGlow();
        sphere(pose, radius, r, g, b, a, lon, lat);
    }

    private static void sphere(PoseStack pose, float radius,
                               float r, float g, float b, float a,
                               int lon, int lat) {
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float) iy / lat - 0.5F) * Mth.PI;
            float p1 = ((float) (iy + 1) / lat - 0.5F) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                sphereV(q,m,radius,p0,a0,r,g,b,a);
                sphereV(q,m,radius,p0,a1,r,g,b,a);
                sphereV(q,m,radius,p1,a1,r,g,b,a);
                sphereV(q,m,radius,p1,a0,r,g,b,a);
            }
        }

        BufferUploader.drawWithShader(q.end());
    }

    private static void sphereV(BufferBuilder q, Matrix4f m, float radius,
                                float lat, float lon,
                                float r, float g, float b, float a) {
        float c = Mth.cos(lat);
        vertex(q,m,
                radius * c * Mth.cos(lon),
                radius * Mth.sin(lat),
                radius * c * Mth.sin(lon),
                r,g,b,a);
    }

    private static void ring(PoseStack pose, float radius, float width,
                             float r, float g, float b, float a, int segments) {
        if (a <= 0.005F || radius <= width) return;
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float inner = radius - width;
        float outer = radius + width;
        for (int i = 0; i < segments; i++) {
            float a0 = i * TAU / segments;
            float a1 = (i + 1) * TAU / segments;
            vertex(q,m,Mth.cos(a0)*inner,0,Mth.sin(a0)*inner,r,g,b,a);
            vertex(q,m,Mth.cos(a0)*outer,0,Mth.sin(a0)*outer,r,g,b,a);
            vertex(q,m,Mth.cos(a1)*outer,0,Mth.sin(a1)*outer,r,g,b,a);
            vertex(q,m,Mth.cos(a1)*inner,0,Mth.sin(a1)*inner,r,g,b,a);
        }

        BufferUploader.drawWithShader(q.end());
    }

    private static void glowRibbon(PoseStack pose, Vec3 start, Vec3 end,
                                   float r, float g, float b, float a, float w) {
        if (a <= 0.005F) return;
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float x0=(float)start.x, y0=(float)start.y, z0=(float)start.z;
        float x1=(float)end.x, y1=(float)end.y, z1=(float)end.z;

        vertex(q,m,x0,y0-w,z0,r,g,b,a);
        vertex(q,m,x0,y0+w,z0,r,g,b,a);
        vertex(q,m,x1,y1+w,z1,r,g,b,a);
        vertex(q,m,x1,y1-w,z1,r,g,b,a);

        vertex(q,m,x0-w,y0,z0,r,g,b,a*0.78F);
        vertex(q,m,x0+w,y0,z0,r,g,b,a*0.78F);
        vertex(q,m,x1+w,y1,z1,r,g,b,a*0.78F);
        vertex(q,m,x1-w,y1,z1,r,g,b,a*0.78F);

        BufferUploader.drawWithShader(q.end());
    }

    private static void vertex(BufferBuilder q, Matrix4f m,
                               float x,float y,float z,
                               float r,float g,float b,float a) {
        q.vertex(m,x,y,z).color(color(r),color(g),color(b),color(a)).endVertex();
    }

    private static void setSolid() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setSolidAlpha(float a) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        if (a < 0.995F) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setGlow() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void restoreState() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static int color(float v) {
        return Mth.clamp((int) (v * 255.0F), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        if (end <= start) return value >= end ? 1.0F : 0.0F;
        float x = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }
}
