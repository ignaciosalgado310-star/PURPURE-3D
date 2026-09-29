package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GenkiTiming;
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
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Extra high-detail 3D meshes for both GOKU-FX attacks. */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGokuFxOverhaulRenderer {
    private static final float TAU = (float) (Math.PI * 2.0D);

    private ClientGokuFxOverhaulRenderer() {}

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
            int kame = ClientGokuEffects.rawEffectTick(target.getUUID(), GokuEffectPacket.KAMEHAMEHA);
            int genki = ClientGokuEffects.rawEffectTick(target.getUUID(), GokuEffectPacket.GENKI_DAMA);

            if (kame >= 0 && target.position().distanceToSqr(cameraPos) < 260.0D * 260.0D) {
                pose.pushPose();
                pose.translate(target.getX() - cameraPos.x, target.getY() - cameraPos.y, target.getZ() - cameraPos.z);
                int hits = Math.max(1, ClientGokuEffects.hits(target.getUUID(), GokuEffectPacket.KAMEHAMEHA));
                // Kamehameha V12 se renderiza exclusivamente en ClientKameV12Renderer.
                pose.popPose();
            }

            if (genki >= 0) {
                // Genkidama V13 se renderiza EXCLUSIVAMENTE en ClientGenkiRenderer.
                // No dibujar la antigua segunda capa para evitar modelos duplicados.
            }
        }
        restoreState();
    }

    private static void drawKame(PoseStack pose, float age, int hits) {
        Vec3 source = new Vec3(-14.0D, 1.71D, 0.0D);
        if (age <= KameTiming.CHARGE_TICKS) {
            float p = smoother(age / KameTiming.CHARGE_TICKS);
            float radius = (0.72F + 2.64F * p) * (1.0F + Mth.sin(age * 0.31F) * 0.04F);
            pose.pushPose();
            pose.translate(source.x, source.y, source.z);
            glowSphere(pose, radius * 1.08F, 0.03F, 0.46F, 1.0F, 0.14F, 40, 22);
            corona(pose, radius, age, p);
            radialRays(pose, radius, age, 12, 0.48F * p, true);
            pose.popPose();
            return;
        }

        int beamTicks = KameTiming.beamTicks(hits);
        float beamAge = age - KameTiming.CHARGE_TICKS;
        float launch = smooth(0.0F, 16.0F, beamAge);
        float fade = 1.0F - smooth(Math.max(17.0F, beamTicks - KameTiming.FADE_TICKS), beamTicks, beamAge);
        float length = 6.0F + 51.0F * launch;
        float radius = 3.08F * (1.0F + Mth.sin(age * 0.43F) * 0.055F);
        Vec3 end = source.add(length, -length * 0.04D, 0.0D);

        pose.pushPose();
        pose.translate(source.x, source.y, source.z);
        corona(pose, radius, age, fade);
        pose.popPose();
        braidedBeam(pose, source, end, radius, age, fade);

        pose.pushPose();
        pose.translate(0.0D, 1.15D, 0.0D);
        float impact = 2.38F + Mth.sin(age * 0.58F) * 0.22F;
        glowSphere(pose, impact * 1.18F, 0.03F, 0.52F, 1.0F, 0.16F * fade, 40, 22);
        corona(pose, impact, age + 23.0F, fade);
        radialRays(pose, impact, age, 10, 0.42F * fade, true);
        pose.popPose();
    }

    private static void braidedBeam(PoseStack pose, Vec3 start, Vec3 end,
                                    float radius, float age, float alpha) {
        if (alpha <= 0.01F) return;
        cylinder(pose, start, end, radius * 1.16F, radius * 1.16F,
                0.0F, 0.18F, 1.0F, 0.075F * alpha, 42, true);
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        Vec3 dir = axis.normalize();
        Vec3 helper = Math.abs(dir.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();

        for (int strand = 0; strand < 7; strand++) {
            Vec3 previous = null;
            float phase = strand * TAU / 7.0F + age * 0.095F;
            for (int i = 0; i <= 20; i++) {
                double p = i / 20.0D;
                double angle = phase + p * Math.PI * 6.5D;
                Vec3 center = start.add(dir.scale(length * p));
                Vec3 point = center.add(u.scale(Math.cos(angle) * radius * 1.04D))
                        .add(v.scale(Math.sin(angle) * radius * 1.04D));
                if (previous != null) {
                    ribbon(pose, previous, point, 0.10F, 0.72F, 1.0F, 0.28F * alpha,
                            0.055F + radius * 0.005F);
                }
                previous = point;
            }
        }
    }

    private static void drawGenki(PoseStack pose, float age, int hits, boolean detail) {
        float gokuAlpha = age <= GenkiTiming.CONTACT_TICK + 2.0F ? 1.0F
                : 1.0F - smooth(GenkiTiming.CONTACT_TICK + 2.0F, GenkiTiming.CONTACT_TICK + 24.0F, age);
        if (gokuAlpha > 0.01F) {
            float arms = smooth(8.0F, 56.0F, age);
            float throwing = smooth(GenkiTiming.THROW_START_TICK, GenkiTiming.THROW_START_TICK + 11.0F, age);
            pose.pushPose();
            pose.translate(GenkiTiming.GOKU_X, GenkiTiming.GOKU_Y, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            pose.scale(0.30F, 0.30F, 0.30F);
            detailedGoku(pose, age, arms, throwing, gokuAlpha, detail);
            pose.popPose();
        }

        Vec3 orb;
        float radius;
        if (age < GenkiTiming.THROW_START_TICK) {
            float p = GenkiTiming.smoother(GenkiTiming.chargeProgress(age));
            orb = sphereStart();
            radius = 0.82F + (GenkiTiming.FINAL_RADIUS - 0.82F) * (0.06F * p + 0.94F * p * p);
        } else if (age < GenkiTiming.CONTACT_TICK) {
            float p = GenkiTiming.heavy(GenkiTiming.approachProgress(age));
            orb = sphereStart().lerp(contactCenter(), p);
            radius = GenkiTiming.FINAL_RADIUS;
        } else {
            float p = GenkiTiming.dragEase(GenkiTiming.dragProgress(age));
            orb = dragCenter(p);
            radius = GenkiTiming.FINAL_RADIUS;
        }

        float fade = age >= GenkiTiming.IMPACT_TICK ? GenkiTiming.fade(age, hits) : 1.0F;
        if (fade <= 0.01F) return;
        pose.pushPose();
        pose.translate(orb.x, orb.y, orb.z);
        glowSphere(pose, radius * 1.14F, 0.18F, 0.88F, 1.0F, 0.065F * fade,
                detail ? 46 : 30, detail ? 24 : 16);
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(24.0F + i * 41.0F));
            pose.mulPose(Axis.ZP.rotation(age * (0.010F + i * 0.0015F) + i));
            torus(pose, radius * (1.02F + i * 0.025F), Math.max(0.07F, radius * 0.012F),
                    0.28F, 0.90F, 1.0F, (0.17F - i * 0.022F) * fade,
                    detail ? 68 : 44, detail ? 9 : 7, true);
            pose.popPose();
        }
        radialRays(pose, radius, age * 1.17F, detail ? 18 : 10, 0.34F * fade, false);
        pose.popPose();
    }

    private static void detailedGoku(PoseStack pose, float age, float armsUp,
                                     float throwing, float alpha, boolean detail) {
        float breathe = Mth.sin(age * 0.14F) * 0.035F;
        float skinR = 0.94F, skinG = 0.66F, skinB = 0.43F;
        float orangeR = 1.0F, orangeG = 0.31F, orangeB = 0.025F;
        float blueR = 0.025F, blueG = 0.16F, blueB = 0.56F;

        pose.pushPose();
        pose.translate(0.0D, 3.45D, 0.0D);
        scaledSphere(pose, 2.18F, 3.95F, 1.72F, 0.30F, 0.88F, 1.0F,
                0.065F * alpha, detail ? 38 : 24, detail ? 20 : 12, true);
        pose.popPose();
        int flames = detail ? 12 : 7;
        for (int i = 0; i < flames; i++) {
            double angle = i * Math.PI * 2.0D / flames + age * 0.018D;
            Vec3 base = new Vec3(Math.cos(angle) * 1.22D, 0.25D, Math.sin(angle) * 1.0D);
            Vec3 tip = new Vec3(Math.cos(angle + 0.22D) * 0.68D,
                    6.7D + Math.sin(age * 0.16D + i) * 0.45D,
                    Math.sin(angle + 0.22D) * 0.58D);
            cylinder(pose, base, tip, 0.13F, 0.018F, 0.56F, 0.97F, 1.0F,
                    0.14F * alpha, 10, true);
        }

        leg(pose, -0.50F, orangeR, orangeG, orangeB, blueR, blueG, blueB, alpha);
        leg(pose, 0.50F, orangeR, orangeG, orangeB, blueR, blueG, blueB, alpha);
        pose.pushPose();
        pose.translate(0.0D, 3.20D + breathe, 0.0D);
        scaledSphere(pose, 1.04F, 1.42F, 0.64F, orangeR, orangeG, orangeB, alpha,
                detail ? 34 : 22, detail ? 18 : 12, false);
        pose.popPose();
        pose.pushPose();
        pose.translate(0.0D, 3.74D + breathe, -0.18D);
        scaledSphere(pose, 0.78F, 0.76F, 0.50F, blueR, blueG, blueB, alpha, 28, 16, false);
        pose.popPose();
        pose.pushPose();
        pose.translate(0.0D, 2.13D, 0.0D);
        scaledSphere(pose, 1.06F, 0.18F, 0.68F, blueR, blueG, blueB, alpha, 26, 12, false);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.0D, 5.30D + breathe, 0.0D);
        scaledSphere(pose, 0.76F, 0.91F, 0.67F, skinR, skinG, skinB, alpha,
                detail ? 36 : 24, detail ? 20 : 13, false);
        pose.popPose();
        for (float side : new float[]{-1.0F, 1.0F}) {
            pose.pushPose();
            pose.translate(side * 0.76F, 5.31F + breathe, 0.0D);
            scaledSphere(pose, 0.13F, 0.25F, 0.10F, skinR, skinG, skinB, alpha, 14, 9, false);
            pose.popPose();
            pose.pushPose();
            pose.translate(side * 0.28F, 5.40F + breathe, -0.665D);
            scaledSphere(pose, 0.18F, 0.22F, 0.035F, 0.97F, 0.99F, 1.0F, alpha, 14, 9, false);
            pose.translate(0.0D, -0.015D, -0.025D);
            scaledSphere(pose, 0.075F, 0.12F, 0.022F, 0.04F, 0.18F, 0.34F, alpha, 10, 7, false);
            pose.popPose();
        }

        pose.pushPose();
        pose.translate(0.0D, 5.90D + breathe, 0.07D);
        scaledSphere(pose, 0.79F, 0.49F, 0.71F, 0.008F, 0.010F, 0.018F, alpha, 28, 16, false);
        pose.popPose();
        hair(pose, breathe, alpha, detail);

        arm(pose, -1.0F, age, armsUp, throwing, alpha,
                orangeR, orangeG, orangeB, skinR, skinG, skinB, blueR, blueG, blueB);
        arm(pose, 1.0F, age, armsUp, throwing, alpha,
                orangeR, orangeG, orangeB, skinR, skinG, skinB, blueR, blueG, blueB);
    }

    private static void leg(PoseStack pose, float x, float or, float og, float ob,
                            float br, float bg, float bb, float alpha) {
        Vec3 hip = new Vec3(x, 2.18D, 0.0D);
        Vec3 knee = new Vec3(x * 1.04D, 1.18D, 0.03D);
        Vec3 ankle = new Vec3(x, 0.28D, 0.02D);
        cylinder(pose, hip, knee, 0.43F, 0.35F, or, og, ob, alpha, 15, false);
        cylinder(pose, knee, ankle, 0.37F, 0.31F, or, og, ob, alpha, 15, false);
        pose.pushPose();
        pose.translate(ankle.x, ankle.y - 0.18D, ankle.z - 0.12D);
        scaledSphere(pose, 0.42F, 0.39F, 0.58F, br, bg, bb, alpha, 22, 13, false);
        pose.popPose();
    }

    private static void hair(PoseStack pose, float breathe, float alpha, boolean detail) {
        Vec3[] bases = {
                new Vec3(-0.55D, 5.95D + breathe, 0.10D), new Vec3(-0.28D, 6.10D + breathe, 0.06D),
                new Vec3(0.0D, 6.17D + breathe, 0.02D), new Vec3(0.30D, 6.08D + breathe, 0.07D),
                new Vec3(0.57D, 5.93D + breathe, 0.12D), new Vec3(-0.65D, 5.80D + breathe, 0.32D),
                new Vec3(0.66D, 5.80D + breathe, 0.30D), new Vec3(-0.32D, 5.82D + breathe, 0.53D),
                new Vec3(0.34D, 5.82D + breathe, 0.52D)
        };
        Vec3[] tips = {
                new Vec3(-1.12D, 7.25D + breathe, 0.0D), new Vec3(-0.55D, 7.75D + breathe, -0.08D),
                new Vec3(0.02D, 8.02D + breathe, -0.12D), new Vec3(0.58D, 7.67D + breathe, -0.04D),
                new Vec3(1.15D, 7.20D + breathe, 0.02D), new Vec3(-1.24D, 6.70D + breathe, 0.58D),
                new Vec3(1.25D, 6.66D + breathe, 0.55D), new Vec3(-0.54D, 6.82D + breathe, 1.18D),
                new Vec3(0.58D, 6.80D + breathe, 1.16D)
        };
        int count = detail ? bases.length : 7;
        for (int i = 0; i < count; i++) {
            cylinder(pose, bases[i], tips[i], 0.35F, 0.025F,
                    0.008F, 0.010F, 0.018F, alpha, 11, false);
        }
    }

    private static void arm(PoseStack pose, float side, float age, float armsUp, float throwing,
                            float alpha, float or, float og, float ob,
                            float sr, float sg, float sb, float br, float bg, float bb) {
        double sway = Math.sin(age * 0.14D + side) * 0.07D;
        Vec3 shoulder = new Vec3(side * 0.93D, 3.92D, -0.02D);
        Vec3 elbow = new Vec3(side * 1.68D, 3.14D + sway, -0.04D)
                .lerp(new Vec3(side * 1.22D, 5.34D + sway, -0.05D), armsUp)
                .lerp(new Vec3(side * 0.76D, 4.65D, -1.42D), throwing);
        Vec3 hand = new Vec3(side * 1.88D, 2.28D + sway, -0.12D)
                .lerp(new Vec3(side * 0.84D, 7.30D + sway, -0.12D), armsUp)
                .lerp(new Vec3(side * 0.54D, 4.50D, -3.08D), throwing);
        Vec3 wristA = elbow.lerp(hand, 0.66D);
        Vec3 wristB = elbow.lerp(hand, 0.82D);
        cylinder(pose, shoulder, elbow, 0.48F, 0.40F, or, og, ob, alpha, 15, false);
        cylinder(pose, elbow, wristA, 0.39F, 0.31F, sr, sg, sb, alpha, 15, false);
        cylinder(pose, wristA, wristB, 0.42F, 0.40F, br, bg, bb, alpha, 15, false);
        cylinder(pose, wristB, hand, 0.33F, 0.27F, sr, sg, sb, alpha, 15, false);
        pose.pushPose();
        pose.translate(hand.x, hand.y, hand.z);
        scaledSphere(pose, 0.34F, 0.38F, 0.30F, sr, sg, sb, alpha, 20, 12, false);
        pose.popPose();
    }

    private static void corona(PoseStack pose, float radius, float age, float alpha) {
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(28.0F + i * 47.0F));
            pose.mulPose(Axis.ZP.rotation(age * (0.018F + i * 0.004F) + i * 0.8F));
            torus(pose, radius * (1.05F + i * 0.045F), Math.max(0.045F, radius * 0.015F),
                    0.12F + i * 0.10F, 0.68F + i * 0.07F, 1.0F,
                    (0.23F - i * 0.035F) * alpha, 56, 8, true);
            pose.popPose();
        }
    }

    private static void radialRays(PoseStack pose, float radius, float age,
                                   int rays, float alpha, boolean blue) {
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < rays; i++) {
            double y = 1.0D - 2.0D * ((i + 0.5D) / rays);
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double angle = i * golden + age * 0.037D;
            Vec3 dir = new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal).normalize();
            Vec3 a = dir.scale(radius * 0.92D);
            Vec3 b = dir.scale(radius * (1.38D + 0.18D * Math.sin(age * 0.51D + i)));
            ribbon(pose, a, b, blue ? 0.05F : 0.56F, blue ? 0.58F : 0.96F,
                    1.0F, alpha, 0.035F + radius * 0.006F);
        }
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

    private static Vec3 dragCenter(float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        Vec3 base = contactCenter().lerp(impactCenter(), p);
        double envelope = Math.sin(Math.PI * p);
        return base.add(0.0D,
                Math.sin(p * Math.PI * 2.0D) * 2.4D * envelope,
                Math.sin(p * Math.PI * 4.0D) * 3.8D * envelope);
    }

    private static void scaledSphere(PoseStack pose, float rx, float ry, float rz,
                                     float r, float g, float b, float a,
                                     int lon, int lat, boolean glow) {
        if (a <= 0.005F) return;
        pose.pushPose();
        pose.scale(rx, ry, rz);
        if (glow) setGlow(); else setSolid(a);
        sphere(pose, 1.0F, r, g, b, a, lon, lat);
        pose.popPose();
    }

    private static void glowSphere(PoseStack pose, float radius, float r, float g, float b,
                                   float a, int lon, int lat) {
        setGlow();
        sphere(pose, radius, r, g, b, a, lon, lat);
    }

    private static void sphere(PoseStack pose, float radius, float r, float g, float b,
                               float a, int lon, int lat) {
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float) iy / lat - 0.5F) * Mth.PI;
            float p1 = ((float) (iy + 1) / lat - 0.5F) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon, a1 = (ix + 1) * TAU / lon;
                sphereV(q, m, radius, p0, a0, r, g, b, a);
                sphereV(q, m, radius, p0, a1, r, g, b, a);
                sphereV(q, m, radius, p1, a1, r, g, b, a);
                sphereV(q, m, radius, p1, a0, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(q.end());
    }

    private static void sphereV(BufferBuilder q, Matrix4f m, float radius,
                                float lat, float lon, float r, float g, float b, float a) {
        float c = Mth.cos(lat);
        vertex(q, m, radius * c * Mth.cos(lon), radius * Mth.sin(lat),
                radius * c * Mth.sin(lon), r, g, b, a);
    }

    private static void cylinder(PoseStack pose, Vec3 from, Vec3 to, float radiusA, float radiusB,
                                 float r, float g, float b, float a, int sides, boolean glow) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.001D || a <= 0.005F) return;
        Vector3f direction = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction);
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(rotation);
        if (glow) setGlow(); else setSolid(a);
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < sides; i++) {
            float a0 = i * TAU / sides, a1 = (i + 1) * TAU / sides;
            vertex(q, m, Mth.cos(a0) * radiusA, 0, Mth.sin(a0) * radiusA, r, g, b, a);
            vertex(q, m, Mth.cos(a0) * radiusB, (float) length, Mth.sin(a0) * radiusB, r, g, b, a);
            vertex(q, m, Mth.cos(a1) * radiusB, (float) length, Mth.sin(a1) * radiusB, r, g, b, a);
            vertex(q, m, Mth.cos(a1) * radiusA, 0, Mth.sin(a1) * radiusA, r, g, b, a);
        }
        BufferUploader.drawWithShader(q.end());
        pose.popPose();
    }

    private static void torus(PoseStack pose, float major, float minor,
                              float r, float g, float b, float a,
                              int segments, int sides, boolean glow) {
        if (a <= 0.005F) return;
        if (glow) setGlow(); else setSolid(a);
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float u0 = i * TAU / segments, u1 = (i + 1) * TAU / segments;
            for (int j = 0; j < sides; j++) {
                float v0 = j * TAU / sides, v1 = (j + 1) * TAU / sides;
                torusV(q, m, major, minor, u0, v0, r, g, b, a);
                torusV(q, m, major, minor, u1, v0, r, g, b, a);
                torusV(q, m, major, minor, u1, v1, r, g, b, a);
                torusV(q, m, major, minor, u0, v1, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(q.end());
    }

    private static void torusV(BufferBuilder q, Matrix4f m, float major, float minor,
                               float u, float v, float r, float g, float b, float a) {
        float shell = major + minor * Mth.cos(v);
        vertex(q, m, shell * Mth.cos(u), minor * Mth.sin(v), shell * Mth.sin(u), r, g, b, a);
    }

    private static void ribbon(PoseStack pose, Vec3 start, Vec3 end,
                               float r, float g, float b, float a, float w) {
        if (a <= 0.005F) return;
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder q = Tesselator.getInstance().getBuilder();
        q.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float x0 = (float) start.x, y0 = (float) start.y, z0 = (float) start.z;
        float x1 = (float) end.x, y1 = (float) end.y, z1 = (float) end.z;
        vertex(q, m, x0, y0 - w, z0, r, g, b, a); vertex(q, m, x0, y0 + w, z0, r, g, b, a);
        vertex(q, m, x1, y1 + w, z1, r, g, b, a); vertex(q, m, x1, y1 - w, z1, r, g, b, a);
        vertex(q, m, x0 - w, y0, z0, r, g, b, a * 0.78F); vertex(q, m, x0 + w, y0, z0, r, g, b, a * 0.78F);
        vertex(q, m, x1 + w, y1, z1, r, g, b, a * 0.78F); vertex(q, m, x1 - w, y1, z1, r, g, b, a * 0.78F);
        BufferUploader.drawWithShader(q.end());
    }

    private static void vertex(BufferBuilder q, Matrix4f m, float x, float y, float z,
                               float r, float g, float b, float a) {
        q.vertex(m, x, y, z).color(color(r), color(g), color(b), color(a)).endVertex();
    }

    private static void setSolid(float alpha) {
        RenderSystem.enableDepthTest();
        if (alpha < 0.995F) { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }
        else RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setGlow() {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void restoreState() {
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static int color(float v) { return Mth.clamp((int) (v * 255.0F), 0, 255); }

    private static float smooth(float start, float end, float value) {
        if (end <= start) return value >= end ? 1.0F : 0.0F;
        float x = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float smoother(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }
}
