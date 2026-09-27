package igy.purpure.client;

import com.mojang.blaze3d.platform.GlStateManager;
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
 * Port directo del visor final aprobado "GOJO Hollow Purple — Fusión limpia V14".
 *
 * La escena del visor dura 11.8 s = 236 ticks. Se conservaron sus valores de
 * tamaño, color, posiciones y tiempos. Ejes del visor -> mundo:
 *   viewer X -> world Z
 *   viewer Y -> world Y
 *   viewer Z -> world -X desde Gojo
 *
 * No dibuja las líneas/filamentos circulares descartados en la V14 final.
 * Las motas de fusión/disparo son cubos 3D diminutos, no partículas 2D.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoAttack {
    private static final float TAU = (float)(Math.PI * 2.0);
    private static final float GOJO_X = 4.0f;
    private static final float TIMELINE_TICKS = 236.0f;
    private static final float POST_TIMELINE_PURPLE_X = 1.83f;

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

            double ox = ClientPurpureEffects.effectX(target.getUUID());
            double oy = ClientPurpureEffects.effectY(target.getUUID());
            double oz = ClientPurpureEffects.effectZ(target.getUUID());
            if (Double.isNaN(ox) || Double.isNaN(oy) || Double.isNaN(oz)) continue;

            float t = base + partial;
            float camX = (float)(camera.getPosition().x - ox);
            float camY = (float)(camera.getPosition().y - oy);
            float camZ = (float)(camera.getPosition().z - oz);

            pose.pushPose();
            pose.translate(
                    ox - camera.getPosition().x,
                    oy - camera.getPosition().y,
                    oz - camera.getPosition().z
            );
            drawAttack(pose, target, ox, oy, oz, t, camX, camY, camZ);
            pose.popPose();
        }

        restoreState();
    }

    private static void drawAttack(PoseStack pose,
                                   AbstractClientPlayer target,
                                   double ox, double oy, double oz,
                                   float t,
                                   float camX, float camY, float camZ) {
        float p = Mth.clamp(t / TIMELINE_TICKS, 0.0f, 1.0f);

        float blueAppear = smooth(0.07f, 0.20f, p);
        float redAppear = smooth(0.27f, 0.40f, p);
        float approach = smooth(0.44f, 0.56f, p);
        float intertwine = smooth(0.53f, 0.70f, p);
        float fuse = smooth(0.59f, 0.77f, p);
        float colorMix = smooth(0.62f, 0.82f, p);
        float purpleStable = smooth(0.78f, 0.87f, p);
        float launch = smooth(0.87f, 0.985f, p);
        float cleanOut = smooth(0.79f, 0.88f, p);

        float baseSep = Mth.lerp(approach, 3.55f, 1.24f);
        float turns = intertwine * Mth.PI * 6.2f;
        float orbitR = Mth.lerp(fuse, 0.82f, 0.12f) * intertwine;
        float centerSep = Mth.lerp(fuse, baseSep, 0.06f);
        float wobble = 0.12f * Mth.sin(turns * 0.7f) * (1.0f - fuse * 0.65f);

        float viewerBx = -centerSep + Mth.cos(turns) * orbitR;
        float viewerRx = centerSep + Mth.cos(turns + Mth.PI) * orbitR;
        float viewerBy = 2.72f + Mth.sin(turns) * orbitR * 0.66f + wobble;
        float viewerRy = 2.72f + Mth.sin(turns + Mth.PI) * orbitR * 0.66f - wobble;
        float viewerBz = 1.62f + Mth.sin(turns * 0.75f) * orbitR * 0.48f;
        float viewerRz = 1.62f + Mth.sin((turns + Mth.PI) * 0.75f) * orbitR * 0.48f;

        float sideFade = smooth(0.70f, 0.82f, p);
        float blueSize = 1.16f * blueAppear * (1.0f - sideFade * 0.96f);
        float redSize = 1.16f * redAppear * (1.0f - sideFade * 0.96f);
        float stretch = 1.0f + 0.46f * fuse * (1.0f - colorMix * 0.60f);
        float squeeze = 1.0f - 0.20f * fuse;

        float tint = colorMix * 0.82f;
        float purpleR = 0x8e / 255.0f;
        float purpleG = 0x2f / 255.0f;
        float purpleB = 1.0f;

        float blueR = Mth.lerp(tint, 0x08 / 255.0f, purpleR);
        float blueG = Mth.lerp(tint, 0x7d / 255.0f, purpleG);
        float blueB = Mth.lerp(tint, 1.0f, purpleB);
        float redR = Mth.lerp(tint, 1.0f, purpleR);
        float redG = Mth.lerp(tint, 0x17 / 255.0f, purpleG);
        float redB = Mth.lerp(tint, 0x3f / 255.0f, purpleB);

        // Mapeo literal del visor: X lateral -> world Z, Z frontal -> world -X.
        Orb blue = new Orb(
                GOJO_X - viewerBz,
                viewerBy,
                viewerBx,
                blueSize,
                blueR, blueG, blueB
        );
        Orb red = new Orb(
                GOJO_X - viewerRz,
                viewerRy,
                viewerRx,
                redSize,
                redR, redG, redB
        );

        drawOrb(pose, blue, t, 1.6f, true, stretch, squeeze, 1.0f + 0.10f * fuse);
        drawOrb(pose, red, t + 6.0f, 4.8f, false, stretch, squeeze, 1.0f + 0.10f * fuse);

        float fusionBirth = smooth(0.57f, 0.73f, p);
        float fusionLife = 1.0f - cleanOut;
        if (fusionBirth > 0.01f && fusionLife > 0.01f) {
            float fusionScale = Mth.lerp(fusionBirth, 0.001f, 1.62f)
                    * fusionLife
                    * (1.0f + 0.045f * Mth.sin(t * 0.40f));
            drawFusionMass(pose, t, fusionScale, colorMix, fusionBirth, fusionLife);
        }

        boolean fusionParticles = p > 0.57f && p < 0.84f && fusionLife > 0.05f;
        if (fusionParticles) {
            drawFusionFlow(pose, t, fuse, colorMix, fusionLife);
        }

        if (purpleStable > 0.015f) {
            float purpleSize = 1.68f * purpleStable
                    * (1.0f + 0.04f * Mth.sin(t * 0.40f))
                    * Mth.lerp(launch, 1.0f, 1.08f);

            float viewerPz = Mth.lerp(launch, 1.62f, 8.25f);
            float px;
            float py;
            float pz;

            if (t <= TIMELINE_TICKS) {
                px = GOJO_X - viewerPz;
                py = 2.72f;
                pz = 0.0f;
            } else {
                px = (float)(target.getX() - ox) + POST_TIMELINE_PURPLE_X;
                py = (float)(target.getY() - oy) + 2.72f;
                pz = (float)(target.getZ() - oz);
            }

            float dx = camX - px;
            float dy = camY - py;
            float dz = camZ - pz;
            float cameraDistance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            float cameraFade = cameraProtection(cameraDistance, purpleSize);

            pose.pushPose();
            pose.translate(px, py, pz);
            drawPurple(pose, purpleSize, t, cameraFade, launch);
            pose.popPose();
        }
    }

    private static void drawOrb(PoseStack pose,
                                Orb o,
                                float t,
                                float seed,
                                boolean blue,
                                float sx,
                                float sy,
                                float sz) {
        if (o.size <= 0.01f) return;

        pose.pushPose();
        pose.translate(o.x, o.y, o.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(t * (blue ? 0.012f : -0.011f)));
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(t * (blue ? 0.009f : -0.010f)));
        pose.scale(sx, sy, sz);

        float pulse = 1.0f + 0.035f * Mth.sin(t * 0.26f + seed);
        float r = o.size * pulse;

        // Mismas capas de createAura() del visor V14: core, deep shell,
        // membrane, aura y segunda envoltura; sin líneas/tori.
        animeSphere(pose, r, o.r, o.g, o.b, 0.98f,
                t * 0.055f + seed, false, 0.060f, seed);

        animeSphere(pose, r * 1.055f, o.r, o.g, o.b, 0.10f,
                -t * 0.080f + seed, true, 0.050f, seed + 1.9f);

        float hr = blue ? 0.66f : 1.00f;
        float hg = blue ? 0.93f : 0.63f;
        float hb = blue ? 1.00f : 0.69f;
        float purpleAmount = Mth.clamp((o.r + o.b - 1.0f) * 0.75f, 0.0f, 1.0f);
        hr = Mth.lerp(purpleAmount, hr, 0.96f);
        hg = Mth.lerp(purpleAmount, hg, 0.65f);
        hb = Mth.lerp(purpleAmount, hb, 1.00f);

        animeSphere(pose, r * 1.24f, hr, hg, hb, 0.13f,
                t * 0.070f + seed, true, 0.030f, seed + 4.0f);

        animeSphere(pose, r * 1.46f, o.r, o.g, o.b, 0.075f,
                -t * 0.050f + seed, true, 0.010f, seed + 6.0f);

        animeSphere(pose, r * 1.31f, hr, hg, hb, 0.09f,
                t * 0.085f + seed, true, 0.040f, seed + 8.0f);

        // Capa cromática del visor, pero como volumen continuo.
        float ar = blue ? 0.0f : 1.0f;
        float ag = blue ? 0.85f : 0.16f;
        float ab = blue ? 1.0f : 0.16f;
        float br = blue ? 0.23f : 1.0f;
        float bg = blue ? 0.36f : 0.31f;
        float bb = blue ? 1.0f : 0.64f;
        chromaSphere(pose, r * 1.075f, ar, ag, ab, br, bg, bb,
                0.21f, t * 0.060f, seed + 12.0f);

        pose.popPose();
    }

    private static void drawFusionMass(PoseStack pose,
                                       float t,
                                       float scale,
                                       float mixAmount,
                                       float birth,
                                       float life) {
        pose.pushPose();
        pose.translate(GOJO_X - 1.62f, 2.72f, 0.0f);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(t * -0.0115f));
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(t * 0.0085f));

        transitionSphere(
                pose,
                scale,
                mixAmount,
                (0.18f + 0.78f * birth) * life,
                t * 0.050f,
                25.2f
        );

        float[][] shellColors = {
                {0x24 / 255.0f, 0xcf / 255.0f, 1.0f},
                {1.0f, 0x31 / 255.0f, 0x5e / 255.0f},
                {0xb1 / 255.0f, 0x4c / 255.0f, 1.0f}
        };
        float purpleR = 0xb1 / 255.0f;
        float purpleG = 0x4c / 255.0f;
        float purpleB = 1.0f;

        for (int i = 0; i < 3; i++) {
            float k = mixAmount * (0.45f + i * 0.22f);
            float cr = Mth.lerp(k, shellColors[i][0], purpleR);
            float cg = Mth.lerp(k, shellColors[i][1], purpleG);
            float cb = Mth.lerp(k, shellColors[i][2], purpleB);
            float shellScale = scale * (1.15f + i * 0.14f)
                    * (1.0f + 0.035f * Mth.sin(t * (0.275f + i * 0.05f) + i));
            float alpha = (0.04f + 0.055f * (2 - i)) * life * (0.55f + 0.45f * birth);
            animeSphere(pose, shellScale, cr, cg, cb, alpha,
                    t * (0.030f + i * 0.008f), true, 0.012f, 29.0f + i);
        }
        pose.popPose();
    }

    private static void drawFusionFlow(PoseStack pose,
                                       float t,
                                       float fuse,
                                       float mixAmount,
                                       float life) {
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int count = 210;
        float alpha = (0.20f + 0.52f * fuse * (1.0f - mixAmount * 0.45f)) * life;

        for (int i = 0; i < count; i++) {
            float side = (i & 1) == 0 ? -1.0f : 1.0f;
            float u = (i % 105) / 104.0f;
            float a = t * (0.125f + (i % 7) * 0.00175f) + u * 8.0f + i * 0.31f;
            float inward = (float)Math.pow(fuse, 0.78f) * u;
            float startX = side * Mth.lerp(fuse, 2.70f, 0.22f);
            float radius = Mth.lerp(mixAmount, 0.52f, 0.13f) * (1.0f - u * 0.42f);

            float vx = Mth.lerp(inward, startX, 0.0f) + Mth.cos(a) * radius;
            float vy = 2.72f + Mth.sin(a * 1.23f) * radius * 0.72f;
            float vz = 1.62f + Mth.cos(a * 0.78f) * radius * 0.48f;

            float baseR = side < 0.0f ? 0x19 / 255.0f : 1.0f;
            float baseG = side < 0.0f ? 0xd8 / 255.0f : 0x35 / 255.0f;
            float baseB = side < 0.0f ? 1.0f : 0x58 / 255.0f;
            float purpleK = mixAmount * (0.25f + 0.75f * u);
            float cr = Mth.lerp(purpleK, baseR, 0xb2 / 255.0f);
            float cg = Mth.lerp(purpleK, baseG, 0x4c / 255.0f);
            float cb = Mth.lerp(purpleK, baseB, 1.0f);

            // viewer -> world
            float wx = GOJO_X - vz;
            float wy = vy;
            float wz = vx;
            tinyCube(bb, m, wx, wy, wz, 0.022f, cr, cg, cb, alpha);
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void drawPurple(PoseStack pose,
                                   float r,
                                   float t,
                                   float cameraFade,
                                   float launch) {
        if (r <= 0.02f) return;

        float bodyAlpha = Mth.lerp(cameraFade, 0.12f, 1.0f);
        float glow = Mth.clamp(cameraFade * cameraFade, 0.015f, 1.0f);

        // createAura(0x7024ff, 0xf5a6ff, 8.2, 0x7b35ff, 0xef5cff)
        animeSphere(pose, r, 0x70 / 255.0f, 0x24 / 255.0f, 1.0f,
                bodyAlpha, t * 0.040f, false, 0.070f, 8.2f);
        animeSphere(pose, r * 1.055f, 0x70 / 255.0f, 0x24 / 255.0f, 1.0f,
                0.09f * glow, -t * 0.050f, true, 0.050f, 10.1f);
        animeSphere(pose, r * 1.24f, 0xf5 / 255.0f, 0xa6 / 255.0f, 1.0f,
                0.13f * glow, t * 0.060f, true, 0.035f, 12.2f);
        animeSphere(pose, r * 1.46f, 0x70 / 255.0f, 0x24 / 255.0f, 1.0f,
                0.075f * glow, -t * 0.042f, true, 0.010f, 14.2f);
        animeSphere(pose, r * 1.31f, 0xf5 / 255.0f, 0xa6 / 255.0f, 1.0f,
                0.09f * glow, t * 0.070f, true, 0.040f, 16.2f);
        chromaSphere(pose, r * 1.075f,
                0x7b / 255.0f, 0x35 / 255.0f, 1.0f,
                0xef / 255.0f, 0x5c / 255.0f, 1.0f,
                0.22f * glow, t * 0.055f, 20.2f);

        if (launch > 0.015f && launch < 0.999f) {
            drawShotFx(pose, r, t, launch, cameraFade);
        }
    }

    private static void drawShotFx(PoseStack pose,
                                   float r,
                                   float t,
                                   float launch,
                                   float cameraFade) {
        float pulse = 0.5f + 0.5f * Mth.sin(t * 0.42f);
        float fade = Mth.clamp(cameraFade, 0.08f, 1.0f);

        animeSphere(pose, r * (0.56f * (0.80f + 0.12f * pulse)),
                0.97f, 0.92f, 1.0f,
                (0.68f + 0.25f * pulse) * fade,
                t * 0.10f, true, 0.012f, 31.0f);

        animeSphere(pose, r * 0.82f,
                0xdc / 255.0f, 0xa7 / 255.0f, 1.0f,
                (0.26f + 0.20f * pulse) * fade,
                -t * 0.17f, true, 0.055f, 31.4f);

        animeSphere(pose, r * (1.52f * (1.04f + 0.07f * Mth.sin(t * 0.275f))),
                0xa0 / 255.0f, 0x44 / 255.0f, 1.0f,
                (0.10f + 0.07f * pulse) * fade,
                t * 0.045f, true, 0.010f, 35.0f);

        animeSphere(pose, r * 1.18f,
                0xeb / 255.0f, 0x8d / 255.0f, 1.0f,
                (0.13f + 0.10f * pulse) * fade,
                -t * 0.065f, true, 0.050f, 37.2f);

        drawShotParticles(pose, r, t, (0.62f + 0.20f * pulse) * fade);
    }

    private static void drawShotParticles(PoseStack pose, float r, float t, float alpha) {
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int count = 150;
        float rotation = t * 0.0275f;
        float cr = 0xe7 / 255.0f;
        float cg = 0x9c / 255.0f;
        float cb = 1.0f;

        for (int i = 0; i < count; i++) {
            float a = i * 2.399963f + rotation;
            float z = 1.0f - 2.0f * (i + 0.5f) / count;
            float rr2 = Mth.sqrt(Math.max(0.0f, 1.0f - z * z));
            float radial = 1.25f + 0.55f * ((i * 37) % 31) / 31.0f;

            float x = Mth.cos(a) * rr2 * radial * r;
            float y = z * radial * r;
            float zz = Mth.sin(a) * rr2 * radial * r;
            tinyCube(bb, m, x, y, zz, Math.max(0.012f, r * 0.018f), cr, cg, cb, alpha);
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void chromaSphere(PoseStack pose,
                                     float radius,
                                     float ar, float ag, float ab,
                                     float br, float bg, float bb,
                                     float alpha,
                                     float phase,
                                     float seed) {
        if (radius <= 0.01f || alpha <= 0.004f) return;
        setGlow();

        Matrix4f m = pose.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int lon = 42;
        final int lat = 24;
        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float)iy / lat - 0.5f) * Mth.PI;
            float p1 = ((float)(iy + 1) / lat - 0.5f) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                chromaVertex(buf, m, radius, p0, a0, ar,ag,ab,br,bg,bb,alpha,phase,seed);
                chromaVertex(buf, m, radius, p0, a1, ar,ag,ab,br,bg,bb,alpha,phase,seed);
                chromaVertex(buf, m, radius, p1, a1, ar,ag,ab,br,bg,bb,alpha,phase,seed);
                chromaVertex(buf, m, radius, p1, a0, ar,ag,ab,br,bg,bb,alpha,phase,seed);
            }
        }
        BufferUploader.drawWithShader(buf.end());
    }

    private static void chromaVertex(BufferBuilder bb, Matrix4f m,
                                     float radius, float lat, float lon,
                                     float ar,float ag,float ab,
                                     float br,float bg,float bcol,
                                     float alpha,float phase,float seed) {
        float c = Mth.cos(lat);
        float nx = c * Mth.cos(lon);
        float ny = Mth.sin(lat);
        float nz = c * Mth.sin(lon);
        float shape = refinedShape(nx, ny, nz, seed);
        float rr = radius * shape;
        float s = 0.5f + 0.5f * Mth.sin(nx * 15.0f + ny * 21.0f - nz * 11.0f + phase * 5.0f);
        float v = (float)Math.pow(0.5f + 0.5f * Mth.sin((nx + ny + nz) * 29.0f - phase * 7.0f), 5.0);
        float r = Mth.clamp(Mth.lerp(s, ar, br) + v * 0.32f, 0.0f, 1.0f);
        float g = Mth.clamp(Mth.lerp(s, ag, bg) + v * 0.32f, 0.0f, 1.0f);
        float b = Mth.clamp(Mth.lerp(s, ab, bcol) + v * 0.32f, 0.0f, 1.0f);
        bb.vertex(m, rr * nx, rr * ny, rr * nz)
                .color(toColor(r),toColor(g),toColor(b),toColor(alpha * (0.55f + 0.45f * s)))
                .endVertex();
    }

    private static void transitionSphere(PoseStack pose,
                                         float radius,
                                         float mixAmount,
                                         float alpha,
                                         float phase,
                                         float seed) {
        if (radius <= 0.01f || alpha <= 0.004f) return;
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int lon = 54;
        final int lat = 30;
        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float)iy / lat - 0.5f) * Mth.PI;
            float p1 = ((float)(iy + 1) / lat - 0.5f) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                transitionVertex(bb,m,radius,p0,a0,mixAmount,alpha,phase,seed);
                transitionVertex(bb,m,radius,p0,a1,mixAmount,alpha,phase,seed);
                transitionVertex(bb,m,radius,p1,a1,mixAmount,alpha,phase,seed);
                transitionVertex(bb,m,radius,p1,a0,mixAmount,alpha,phase,seed);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void transitionVertex(BufferBuilder bb, Matrix4f m,
                                         float radius,float lat,float lon,
                                         float mixAmount,float alpha,float phase,float seed) {
        float c=Mth.cos(lat);
        float nx=c*Mth.cos(lon), ny=Mth.sin(lat), nz=c*Mth.sin(lon);
        float shape=refinedShape(nx,ny,nz,seed);
        float wave1=.055f*Mth.sin(nx*15.0f+ny*9.0f+phase*4.0f);
        float wave2=.040f*Mth.sin(ny*22.0f-nz*10.0f-phase*5.0f);
        float wave3=.025f*Mth.sin((nx+nz)*33.0f+phase*3.3f);
        float rr=radius*shape*(1.0f+wave1+wave2+wave3);

        float angle=(float)Math.atan2(ny,nx);
        float flow=.5f+.5f*Mth.sin(angle*6.0f+Mth.sqrt(nx*nx+ny*ny)*13.0f-phase*6.2f);
        float flow2=.5f+.5f*Mth.sin(nx*18.0f-ny*14.0f+nz*12.0f+phase*4.8f);
        float blueR=Mth.lerp(flow2,.01f,0.0f);
        float blueG=Mth.lerp(flow2,.38f,.95f);
        float blueB=1.0f;
        float redR=1.0f;
        float redG=Mth.lerp(1.0f-flow2,.02f,.16f);
        float redB=Mth.lerp(1.0f-flow2,.11f,.48f);
        float split=.5f+.5f*Mth.sin(angle*4.0f+nz*8.0f-phase*3.6f);
        float biR=Mth.lerp(split,blueR,redR);
        float biG=Mth.lerp(split,blueG,redG);
        float biB=Mth.lerp(split,blueB,redB);
        float purR=Mth.lerp(flow,.40f,.86f);
        float purG=Mth.lerp(flow,.03f,.20f);
        float purB=1.0f;
        float r=Mth.lerp(mixAmount,biR,purR);
        float g=Mth.lerp(mixAmount,biG,purG);
        float b=Mth.lerp(mixAmount,biB,purB);
        float vein=(float)Math.pow(.5f+.5f*Mth.sin((nx-ny+nz)*31.0f+phase*9.0f),8.0);
        r=Mth.clamp(r+vein*.75f,0.0f,1.0f);
        g=Mth.clamp(g+vein*.54f,0.0f,1.0f);
        b=Mth.clamp(b+vein*.75f,0.0f,1.0f);
        bb.vertex(m,rr*nx,rr*ny,rr*nz)
                .color(toColor(r),toColor(g),toColor(b),toColor(alpha))
                .endVertex();
    }

    private static void animeSphere(PoseStack pose,
                                    float radius,
                                    float r,float g,float b,
                                    float alpha,
                                    float phase,
                                    boolean glow,
                                    float surfaceEnergy,
                                    float seed) {
        if (radius <= 0.01f || alpha <= 0.004f) return;
        if (glow) setGlow(); else setSolid();

        Matrix4f m=pose.last().pose();
        BufferBuilder bb=Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);

        final int lon=glow?42:56;
        final int lat=glow?24:32;
        for(int iy=0;iy<lat;iy++){
            float p0=((float)iy/lat-.5f)*Mth.PI;
            float p1=((float)(iy+1)/lat-.5f)*Mth.PI;
            for(int ix=0;ix<lon;ix++){
                float a0=ix*TAU/lon;
                float a1=(ix+1)*TAU/lon;
                sphereVertex(bb,m,radius,p0,a0,r,g,b,alpha,phase,surfaceEnergy,seed);
                sphereVertex(bb,m,radius,p0,a1,r,g,b,alpha,phase,surfaceEnergy,seed);
                sphereVertex(bb,m,radius,p1,a1,r,g,b,alpha,phase,surfaceEnergy,seed);
                sphereVertex(bb,m,radius,p1,a0,r,g,b,alpha,phase,surfaceEnergy,seed);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void sphereVertex(BufferBuilder bb,Matrix4f m,
                                     float radius,float lat,float lon,
                                     float r,float g,float b,float alpha,
                                     float phase,float surfaceEnergy,float seed){
        float c=Mth.cos(lat);
        float nx=c*Mth.cos(lon),ny=Mth.sin(lat),nz=c*Mth.sin(lon);
        float base=refinedShape(nx,ny,nz,seed);
        float wave=Mth.sin(nx*19.0f+ny*13.0f+phase)*.55f
                +Mth.sin(ny*27.0f-nz*17.0f-phase*.7f)*.45f;
        float rr=radius*base*(1.0f+surfaceEnergy*wave*.12f);
        float rim=1.0f-Math.abs(nx);rim*=rim;
        float hi=Mth.clamp(-nx*.18f+ny*.35f-nz*.18f,0.0f,1.0f);hi*=hi;
        float shade=Mth.clamp(.72f+rim*.24f+hi*.22f,0.58f,1.18f);
        bb.vertex(m,rr*nx,rr*ny,rr*nz)
                .color(toColor(Mth.clamp(r*shade,0,1)),toColor(Mth.clamp(g*shade,0,1)),toColor(Mth.clamp(b*shade,0,1)),toColor(alpha))
                .endVertex();
    }

    private static float refinedShape(float nx,float ny,float nz,float seed){
        float broad=.052f*Mth.sin(nx*11.0f+ny*8.0f+seed)
                +.034f*Mth.sin(ny*17.0f-nz*6.0f+seed*1.6f)
                +.023f*Mth.sin((nx+ny+nz)*27.0f-seed);
        float medium=.014f*Mth.cos((nx-nz)*39.0f+seed*2.1f)
                +.011f*Mth.sin((nx*ny+nz)*54.0f+seed*2.6f);
        float micro=.006f*Mth.sin(nx*83.0f+ny*61.0f+nz*47.0f+seed*4.1f)
                +.004f*Mth.cos(ny*101.0f-nz*73.0f+seed);
        return 1.0f+broad+medium+micro;
    }

    private static void tinyCube(BufferBuilder bb, Matrix4f m,
                                 float x,float y,float z,float s,
                                 float r,float g,float b,float a){
        float x0=x-s,x1=x+s,y0=y-s,y1=y+s,z0=z-s,z1=z+s;
        quad(bb,m,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,r,g,b,a);
        quad(bb,m,x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0,r,g,b,a);
        quad(bb,m,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,r,g,b,a);
        quad(bb,m,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1,r,g,b,a);
        quad(bb,m,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,r,g,b,a);
        quad(bb,m,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,r,g,b,a);
    }

    private static void quad(BufferBuilder bb,Matrix4f m,
                             float x0,float y0,float z0,
                             float x1,float y1,float z1,
                             float x2,float y2,float z2,
                             float x3,float y3,float z3,
                             float r,float g,float b,float a){
        bb.vertex(m,x0,y0,z0).color(toColor(r),toColor(g),toColor(b),toColor(a)).endVertex();
        bb.vertex(m,x1,y1,z1).color(toColor(r),toColor(g),toColor(b),toColor(a)).endVertex();
        bb.vertex(m,x2,y2,z2).color(toColor(r),toColor(g),toColor(b),toColor(a)).endVertex();
        bb.vertex(m,x3,y3,z3).color(toColor(r),toColor(g),toColor(b),toColor(a)).endVertex();
    }

    private static float cameraProtection(float cameraDistance,float radius){
        float surfaceDistance=cameraDistance-radius;
        if(surfaceDistance>=2.10f)return 1.0f;
        if(surfaceDistance<=0.18f)return 0.08f;
        return Mth.lerp(smooth(0.18f,2.10f,surfaceDistance),0.08f,1.0f);
    }

    private static void setSolid(){
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setGlow(){
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

    private static void restoreState(){
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static int toColor(float v){
        return Mth.clamp((int)(v*255.0f),0,255);
    }

    private static float smooth(float start,float end,float value){
        float x=Mth.clamp((value-start)/(end-start),0.0f,1.0f);
        return x*x*(3.0f-2.0f*x);
    }

    private record Orb(float x,float y,float z,float size,float r,float g,float b){}
}
