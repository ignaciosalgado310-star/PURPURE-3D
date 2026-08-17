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
 * V8: Azul y Rojo se acercan lentamente y SOLO crean morado cuando ya se tocan.
 * Sin nucleo blanco y sin columna/rayo vertical al final.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoAttack {
    private static final float TAU = (float)(Math.PI * 2.0);

    // Gojo/NPC esta a +4 bloques del objetivo.
    private static final float GOJO_X = 4.0f;
    private static final float GOJO_Y = 0.0f;
    private static final float FUSION_X = 2.60f;
    private static final float FUSION_Y = 2.08f;

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
        // ------------------------------------------------------------
        // 1) AZUL + ROJO: aparecen, orbitan y se acercan MUY poco a poco.
        // ------------------------------------------------------------
        if (t < 260.0f) {
            float appear = smooth(16.0f, 48.0f, t);
            float converge = smooth(82.0f, 238.0f, t);

            // El violeta NO empieza hasta que practicamente ya se tocaron.
            float fusionColor = smooth(218.0f, 250.0f, t);
            float vanish = 1.0f - smooth(244.0f, 260.0f, t);

            float centerX = Mth.lerp(converge, GOJO_X, FUSION_X);
            float centerY = Mth.lerp(converge, 2.05f, FUSION_Y);
            float orbit = Mth.lerp(converge, 2.55f, 0.0f);

            // Giro tranquilo al principio y un poco mas rapido al final.
            float attractionBoost = 1.0f + smooth(190.0f, 235.0f, t) * 0.48f;
            float angle = t * 0.055f * attractionBoost;

            float size = Mth.lerp(appear, 0.28f, 1.10f);
            size *= Mth.lerp(converge, 1.0f, 0.80f) * vanish;

            Orb blue = orb(centerX, centerY, orbit, angle, size, true, fusionColor, t, converge);
            Orb red = orb(centerX, centerY, orbit, angle + Mth.PI, size, false, fusionColor, t, converge);

            drawOrb(pose, blue);
            drawOrb(pose, red);
            drawTrail(pose, t, true);
            drawTrail(pose, t, false);

            // Filamentos morados SOLO en el contacto final.
            if (t >= 222.0f) {
                float q = smooth(222.0f, 250.0f, t) * vanish;
                drawFusionWeb(pose, blue, red, t, q);
            }
        }

        // ------------------------------------------------------------
        // 2) PURPLE: nace DESPUES del contacto, pequeno -> pulso -> crece.
        // ------------------------------------------------------------
        if (t >= 242.0f) {
            float born = smooth(242.0f, 260.0f, t);
            float grow = smooth(258.0f, 318.0f, t);
            float launch = smooth(304.0f, 340.0f, t);
            float fade = 1.0f - smooth(372.0f, 402.0f, t);

            float small = Mth.lerp(born, 0.08f, 0.78f);
            float radius = Mth.lerp(grow, small, 4.15f) * fade;

            // Pulso uniforme, siempre esferico.
            if (t >= 258.0f && t <= 286.0f) {
                float pulseFade = 1.0f - smooth(274.0f, 288.0f, t);
                radius *= 1.0f + Mth.sin((t - 258.0f) * 0.43f) * 0.045f * pulseFade;
            }

            float px = Mth.lerp(launch, FUSION_X, 0.0f);
            float py = Mth.lerp(launch, FUSION_Y, 1.58f);

            pose.pushPose();
            pose.translate(px, py, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.22f));
            purple(pose, radius);
            pose.popPose();
        }
    }

    private static Orb orb(float centerX, float centerY, float orbit, float angle, float size,
                           boolean blue, float fusionColor, float t, float converge) {
        float x = centerX + Mth.cos(angle) * orbit;
        float z = Mth.sin(angle) * orbit;
        float bob = (1.0f - converge) * 0.18f;
        float y = centerY + (blue ? Mth.sin(t * 0.070f) : Mth.cos(t * 0.068f + 1.15f)) * bob;

        float r = blue ? Mth.lerp(fusionColor, 0.025f, 0.53f) : Mth.lerp(fusionColor, 1.00f, 0.53f);
        float g = blue ? Mth.lerp(fusionColor, 0.27f, 0.055f) : Mth.lerp(fusionColor, 0.018f, 0.055f);
        float b = blue ? Mth.lerp(fusionColor, 1.00f, 0.98f) : Mth.lerp(fusionColor, 0.055f, 0.98f);
        return new Orb(x, y, z, size, r, g, b);
    }

    private static Orb orbAt(float t, boolean blue) {
        float appear = smooth(16.0f, 48.0f, t);
        float converge = smooth(82.0f, 238.0f, t);
        float fusionColor = smooth(218.0f, 250.0f, t);
        float vanish = 1.0f - smooth(244.0f, 260.0f, t);
        float centerX = Mth.lerp(converge, GOJO_X, FUSION_X);
        float centerY = Mth.lerp(converge, 2.05f, FUSION_Y);
        float orbit = Mth.lerp(converge, 2.55f, 0.0f);
        float attractionBoost = 1.0f + smooth(190.0f, 235.0f, t) * 0.48f;
        float angle = t * 0.055f * attractionBoost + (blue ? 0.0f : Mth.PI);
        float size = Mth.lerp(appear, 0.28f, 1.10f) * Mth.lerp(converge, 1.0f, 0.80f) * vanish;
        return orb(centerX, centerY, orbit, angle, size, blue, fusionColor, t, converge);
    }

    private static void drawOrb(PoseStack pose, Orb o) {
        if (o.size <= 0.02f) return;
        pose.pushPose();
        pose.translate(o.x, o.y, o.z);

        solidSphere(pose, o.size, o.r, o.g, o.b);

        // Nucleo del MISMO color, mas brillante. Nada blanco.
        float cr = Mth.clamp(o.r * 1.20f + 0.035f, 0.0f, 1.0f);
        float cg = Mth.clamp(o.g * 1.20f + 0.035f, 0.0f, 1.0f);
        float cb = Mth.clamp(o.b * 1.08f + 0.020f, 0.0f, 1.0f);
        solidSphere(pose, o.size * 0.22f, cr, cg, cb);
        glowSphere(pose, o.size * 1.045f, o.r, o.g, o.b, 0.15f);

        pose.popPose();
    }

    private static void drawTrail(PoseStack pose, float t, boolean blue) {
        // Estela corta, curva y muy tenue: nunca forma una columna final.
        for (int i = 0; i < 7; i++) {
            float ta = t - i * 2.0f;
            float tb = t - (i + 1) * 2.0f;
            if (tb < 16.0f || ta > 244.0f) break;
            Orb a = orbAt(ta, blue);
            Orb b = orbAt(tb, blue);
            float f = 1.0f - i / 7.0f;
            ribbon(pose, a.x,a.y,a.z, b.x,b.y,b.z,
                    a.r,a.g,a.b, 0.085f*f, 0.038f + 0.050f*f);
        }
    }

    private static void drawFusionWeb(PoseStack pose, Orb blue, Orb red, float t, float q) {
        if (q <= 0.01f) return;
        for (int strand = 0; strand < 4; strand++) {
            float phase = strand * 1.55f + t * 0.08f;
            float px = blue.x;
            float py = blue.y;
            float pz = blue.z;

            for (int i = 1; i <= 10; i++) {
                float u = i / 10.0f;
                float bulge = Mth.sin(u * Mth.PI) * q;
                float x = Mth.lerp(u, blue.x, red.x) + Mth.cos(phase + u * TAU) * 0.12f * bulge;
                float y = Mth.lerp(u, blue.y, red.y) + Mth.sin(phase * 0.75f + u * TAU) * 0.10f * bulge;
                float z = Mth.lerp(u, blue.z, red.z) + Mth.sin(phase + u * TAU) * 0.12f * bulge;
                float center = 1.0f - Math.abs(u * 2.0f - 1.0f);

                ribbon(pose, px,py,pz, x,y,z,
                        Mth.lerp(center, blue.r, 0.70f), 0.045f, 1.0f,
                        0.075f * q, 0.026f + 0.025f * center);
                px=x; py=y; pz=z;
            }
        }
    }

    private static void purple(PoseStack pose, float r) {
        if (r <= 0.04f) return;

        // Todo morado/violeta: cero blanco.
        solidSphere(pose, r, 0.43f, 0.018f, 0.90f);
        solidSphere(pose, r * 0.72f, 0.66f, 0.055f, 1.00f);
        solidSphere(pose, r * 0.16f, 0.88f, 0.30f, 1.00f);
        glowSphere(pose, r * 1.045f, 0.82f, 0.09f, 1.0f, 0.16f);
        glowSphere(pose, r * 1.075f, 0.34f, 0.04f, 1.0f, 0.075f);
    }

    private static void solidSphere(PoseStack pose, float radius, float r, float g, float b) {
        if (radius <= 0.02f) return;
        setSolid();
        sphere(pose, radius, r,g,b,1.0f);
    }

    private static void glowSphere(PoseStack pose, float radius, float r, float g, float b, float a) {
        if (radius <= 0.02f || a <= 0.005f) return;
        setGlow();
        sphere(pose, radius, r,g,b,a);
    }

    private static void sphere(PoseStack pose, float radius, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int lon = 88;
        final int lat = 48;
        for (int iy=0; iy<lat; iy++) {
            float p0 = ((float)iy/lat - 0.5f) * Mth.PI;
            float p1 = ((float)(iy+1)/lat - 0.5f) * Mth.PI;
            for (int ix=0; ix<lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix+1) * TAU / lon;
                sv(bb,m,radius,p0,a0,r,g,b,a);
                sv(bb,m,radius,p0,a1,r,g,b,a);
                sv(bb,m,radius,p1,a1,r,g,b,a);
                sv(bb,m,radius,p1,a0,r,g,b,a);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void sv(BufferBuilder bb, Matrix4f m, float radius, float lat, float lon,
                           float r,float g,float b,float a) {
        float c = Mth.cos(lat);
        vertex(bb,m,
                radius*c*Mth.cos(lon), radius*Mth.sin(lat), radius*c*Mth.sin(lon),
                r,g,b,a);
    }

    private static void ribbon(PoseStack pose,
                               float x0,float y0,float z0,float x1,float y1,float z1,
                               float r,float g,float b,float a,float w) {
        if (a <= 0.005f) return;
        setGlow();
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        vertex(bb,m,x0,y0-w,z0,r,g,b,a);
        vertex(bb,m,x0,y0+w,z0,r,g,b,a);
        vertex(bb,m,x1,y1+w,z1,r,g,b,a);
        vertex(bb,m,x1,y1-w,z1,r,g,b,a);

        vertex(bb,m,x0-w,y0,z0,r,g,b,a*0.70f);
        vertex(bb,m,x0+w,y0,z0,r,g,b,a*0.70f);
        vertex(bb,m,x1+w,y1,z1,r,g,b,a*0.70f);
        vertex(bb,m,x1-w,y1,z1,r,g,b,a*0.70f);
        BufferUploader.drawWithShader(bb.end());
    }

    private static void vertex(BufferBuilder bb, Matrix4f m,
                               float x,float y,float z,float r,float g,float b,float a) {
        bb.vertex(m,x,y,z)
                .color(toColor(r),toColor(g),toColor(b),toColor(a))
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
        return Mth.clamp((int)(v*255.0f),0,255);
    }

    private static float smooth(float start,float end,float value) {
        float x = Mth.clamp((value-start)/(end-start),0.0f,1.0f);
        return x*x*(3.0f-2.0f*x);
    }

    private record Orb(float x,float y,float z,float size,float r,float g,float b) {}
}
