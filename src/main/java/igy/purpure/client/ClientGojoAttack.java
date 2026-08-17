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

@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoAttack {
    private static final float TAU = (float)(Math.PI * 2.0);

    private ClientGojoAttack() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();

        for (AbstractClientPlayer player : mc.level.players()) {
            float base = ClientPurpureEffects.effectTick(player.getUUID());
            if (base < 0.0f) continue;
            float t = base + partial;

            pose.pushPose();
            pose.translate(
                    player.getX() - camera.getPosition().x,
                    player.getY() - camera.getPosition().y,
                    player.getZ() - camera.getPosition().z
            );

            ClientGojoCaster.render(pose, t);
            drawAttack(pose, t);
            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void drawAttack(PoseStack pose, float t) {
        if (t < 145.0f) {
            float appear = smooth(5.0f, 24.0f, t);
            float converge = smooth(58.0f, 124.0f, t);
            float fusion = smooth(78.0f, 132.0f, t);
            float vanish = 1.0f - smooth(126.0f, 145.0f, t);

            float cx = Mth.lerp(converge, ClientGojoCaster.X, ClientGojoCaster.FUSION_X);
            float cy = Mth.lerp(converge, 1.95f, ClientGojoCaster.FUSION_Y);
            float orbit = Mth.lerp(converge, 2.35f, 0.0f);
            float boost = 1.0f + smooth(88.0f, 120.0f, t) * 0.78f;
            float angle = t * 0.086f * boost;
            float size = Mth.lerp(appear, 0.22f, 1.18f) * Mth.lerp(converge, 1.0f, 0.76f) * vanish;

            Orb blue = orb(cx, cy, orbit, angle, size, true, fusion, t, converge);
            Orb red = orb(cx, cy, orbit, angle + Mth.PI, size, false, fusion, t, converge);

            drawOrb(pose, blue);
            drawOrb(pose, red);
            drawTrail(pose, t, true);
            drawTrail(pose, t, false);

            if (t >= 78.0f) {
                float q = smooth(78.0f, 128.0f, t) * vanish;
                drawFusionWeb(pose, blue, red, t, q);

                pose.pushPose();
                pose.translate(ClientGojoCaster.FUSION_X, ClientGojoCaster.FUSION_Y, 0.0f);
                float r = Mth.lerp(q, 0.08f, 0.88f);
                solidSphere(pose, r, 0.57f, 0.055f, 0.98f);
                glowSphere(pose, r * 1.12f, 0.88f, 0.18f, 1.0f, 0.20f * q);
                pose.popPose();
            }
        }

        if (t >= 102.0f) {
            float born = smooth(102.0f, 136.0f, t);
            float grow = smooth(132.0f, 176.0f, t);
            float launch = smooth(148.0f, 190.0f, t);
            float fade = 1.0f - smooth(330.0f, 365.0f, t);

            float small = Mth.lerp(born, 0.10f, 1.80f);
            float radius = Mth.lerp(grow, small, 6.95f) * fade;
            if (t >= 132.0f && t <= 158.0f) {
                radius *= 1.0f + Mth.sin((t - 132.0f) * 0.42f) * 0.055f * (1.0f - smooth(148.0f, 160.0f, t));
            }

            float px = Mth.lerp(launch, ClientGojoCaster.FUSION_X, 0.0f);
            float py = Mth.lerp(launch, ClientGojoCaster.FUSION_Y, 1.55f);

            // Cinta de lanzamiento desde Gojo hasta el Purple.
            if (t >= 145.0f && t <= 198.0f) {
                float q = smooth(145.0f, 160.0f, t) * (1.0f - smooth(190.0f, 202.0f, t));
                ribbon(pose,
                        ClientGojoCaster.X - 0.42f, 1.56f, 0.0f,
                        px, py, 0.0f,
                        0.68f, 0.12f, 1.0f, 0.13f * q, 0.10f + q * 0.18f);
            }

            pose.pushPose();
            pose.translate(px, py, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.31f));
            purple(pose, radius);
            drawPurpleSpirals(pose, radius, t, fade);
            pose.popPose();
        }
    }

    private static Orb orb(float cx, float cy, float orbit, float angle, float size,
                           boolean blue, float fusion, float t, float converge) {
        float x = cx + Mth.cos(angle) * orbit;
        float z = Mth.sin(angle) * orbit;
        float y = cy + (blue ? Mth.sin(t * 0.090f) : Mth.cos(t * 0.087f + 1.2f)) * 0.22f * (1.0f - converge);

        float r = blue ? Mth.lerp(fusion, 0.025f, 0.52f) : Mth.lerp(fusion, 1.00f, 0.52f);
        float g = blue ? Mth.lerp(fusion, 0.30f, 0.07f) : Mth.lerp(fusion, 0.025f, 0.07f);
        float b = blue ? Mth.lerp(fusion, 1.00f, 0.96f) : Mth.lerp(fusion, 0.070f, 0.96f);
        return new Orb(x, y, z, size, r, g, b);
    }

    private static Orb orbAt(float t, boolean blue) {
        float appear = smooth(5.0f, 24.0f, t);
        float converge = smooth(58.0f, 124.0f, t);
        float fusion = smooth(78.0f, 132.0f, t);
        float vanish = 1.0f - smooth(126.0f, 145.0f, t);
        float cx = Mth.lerp(converge, ClientGojoCaster.X, ClientGojoCaster.FUSION_X);
        float cy = Mth.lerp(converge, 1.95f, ClientGojoCaster.FUSION_Y);
        float orbit = Mth.lerp(converge, 2.35f, 0.0f);
        float boost = 1.0f + smooth(88.0f, 120.0f, t) * 0.78f;
        float angle = t * 0.086f * boost + (blue ? 0.0f : Mth.PI);
        float size = Mth.lerp(appear, 0.22f, 1.18f) * Mth.lerp(converge, 1.0f, 0.76f) * vanish;
        return orb(cx, cy, orbit, angle, size, blue, fusion, t, converge);
    }

    private static void drawOrb(PoseStack pose, Orb o) {
        if (o.size <= 0.02f) return;
        pose.pushPose();
        pose.translate(o.x, o.y, o.z);
        solidSphere(pose, o.size, o.r, o.g, o.b);
        solidSphere(pose, o.size * 0.25f, 0.96f, 0.98f, 1.0f);
        glowSphere(pose, o.size * 1.05f, o.r, o.g, o.b, 0.18f);
        pose.popPose();
    }

    private static void drawTrail(PoseStack pose, float t, boolean blue) {
        for (int i = 0; i < 10; i++) {
            float ta = t - i * 2.2f;
            float tb = t - (i + 1) * 2.2f;
            if (tb < 5.0f) break;
            Orb a = orbAt(ta, blue);
            Orb b = orbAt(tb, blue);
            float f = 1.0f - i / 10.0f;
            ribbon(pose, a.x,a.y,a.z, b.x,b.y,b.z, a.r,a.g,a.b, 0.15f*f, 0.06f + 0.08f*f);
        }
    }

    private static void drawFusionWeb(PoseStack pose, Orb blue, Orb red, float t, float q) {
        if (q <= 0.01f) return;
        for (int s = 0; s < 6; s++) {
            float phase = s * 1.17f + t * 0.11f;
            float px = blue.x;
            float py = blue.y;
            float pz = blue.z;
            for (int i = 1; i <= 12; i++) {
                float u = i / 12.0f;
                float bulge = Mth.sin(u * Mth.PI) * q;
                float x = Mth.lerp(u, blue.x, red.x) + Mth.cos(phase + u * TAU) * 0.20f * bulge;
                float y = Mth.lerp(u, blue.y, red.y) + Mth.sin(phase * 0.7f + u * TAU) * 0.18f * bulge;
                float z = Mth.lerp(u, blue.z, red.z) + Mth.sin(phase + u * TAU) * 0.20f * bulge;
                float center = 1.0f - Math.abs(u * 2.0f - 1.0f);
                ribbon(pose, px,py,pz, x,y,z,
                        Mth.lerp(center, blue.r, 0.72f), 0.07f, 1.0f,
                        0.10f * q, 0.035f + 0.045f * center);
                px=x; py=y; pz=z;
            }
        }
    }

    private static void purple(PoseStack pose, float r) {
        if (r <= 0.04f) return;
        solidSphere(pose, r, 0.47f, 0.025f, 0.92f);
        solidSphere(pose, r * 0.70f, 0.72f, 0.10f, 1.0f);
        solidSphere(pose, r * 0.17f, 1.0f, 0.95f, 1.0f);
        glowSphere(pose, r * 1.05f, 0.84f, 0.10f, 1.0f, 0.18f);
        glowSphere(pose, r * 1.08f, 0.34f, 0.06f, 1.0f, 0.09f);
    }

    private static void drawPurpleSpirals(PoseStack pose, float r, float t, float fade) {
        if (r < 0.3f) return;
        for (int arm = 0; arm < 4; arm++) {
            float phase = arm * TAU / 4.0f + t * (arm % 2 == 0 ? 0.035f : -0.030f);
            float px = 0, py = 0, pz = 0;
            boolean prev = false;
            for (int i = 0; i <= 18; i++) {
                float u = i / 18.0f;
                float ang = phase + u * TAU * 0.92f;
                float rr = r * (1.08f + Mth.sin(u * Mth.PI) * 0.08f);
                float x = Mth.cos(ang) * rr;
                float y = (u - 0.5f) * r * 1.18f;
                float z = Mth.sin(ang) * rr;
                if (prev) {
                    ribbon(pose, px,py,pz, x,y,z, 0.86f,0.15f,1.0f,0.075f*fade, Math.max(0.035f,r*0.015f));
                }
                px=x; py=y; pz=z; prev=true;
            }
        }
    }

    private static void solidSphere(PoseStack pose, float radius, float r, float g, float b) {
        setSolid();
        sphere(pose, radius, r,g,b,1.0f);
    }

    private static void glowSphere(PoseStack pose, float radius, float r, float g, float b, float a) {
        setGlow();
        sphere(pose, radius, r,g,b,a);
    }

    private static void sphere(PoseStack pose, float radius, float r, float g, float b, float a) {
        if (radius <= 0.02f) return;
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        final int lon = 80;
        final int lat = 44;
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
        vertex(bb,m,x0-w,y0,z0,r,g,b,a*0.75f);
        vertex(bb,m,x0+w,y0,z0,r,g,b,a*0.75f);
        vertex(bb,m,x1+w,y1,z1,r,g,b,a*0.75f);
        vertex(bb,m,x1-w,y1,z1,r,g,b,a*0.75f);
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
