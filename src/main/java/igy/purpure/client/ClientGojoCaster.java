package igy.purpure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Gojo estilo Minecraft, construido con geometria propia para no requerir
 * resource pack ni entidades extra. Tiene articulaciones, pelo 3D, antifaz,
 * ojos y poses por fases inspiradas en una secuencia de lanzamiento.
 */
public final class ClientGojoCaster {
    public static final float X = 3.45f;
    public static final float Y = 0.0f;
    public static final float Z = 0.0f;
    public static final float FUSION_X = 2.15f;
    public static final float FUSION_Y = 2.05f;

    private ClientGojoCaster() {}

    public static void render(PoseStack pose, float t) {
        if (t < 0.0f || t > 236.0f) return;

        float appear = smooth(0.0f, 18.0f, t);
        float vanish = 1.0f - smooth(218.0f, 236.0f, t);
        float alpha = Mth.clamp(appear * vanish, 0.0f, 1.0f);
        if (alpha <= 0.01f) return;

        float idle = Mth.sin(t * 0.055f) * 0.025f;
        float rise = Mth.lerp(appear, -0.70f, 0.0f);
        float bodyTurn = 0.0f;
        float lean = 0.0f;

        if (t < 72.0f) {
            bodyTurn = Mth.lerp(smooth(18.0f, 60.0f, t), -8.0f, 8.0f);
            lean = -2.0f;
        } else if (t < 145.0f) {
            bodyTurn = Mth.lerp(smooth(72.0f, 130.0f, t), 8.0f, -5.0f);
            lean = -4.0f;
        } else {
            float q = smooth(145.0f, 195.0f, t);
            bodyTurn = Mth.lerp(q, -5.0f, 11.0f);
            lean = Mth.lerp(q, -4.0f, -10.0f);
        }

        pose.pushPose();
        pose.translate(X, Y + rise + idle, Z);
        // Esta rotacion hace que el frente del modelo mire hacia el jugador en el origen.
        pose.mulPose(Axis.YP.rotationDegrees(90.0f));
        pose.mulPose(Axis.YP.rotationDegrees(bodyTurn));
        pose.mulPose(Axis.XP.rotationDegrees(lean));
        pose.scale(1.08f, 1.08f, 1.08f);

        // Piernas articuladas. La postura cambia ligeramente para que no parezca estatua.
        float stance = smooth(18.0f, 70.0f, t);
        leg(pose, -0.16f, 0.78f, Mth.lerp(stance, 2.0f, 8.0f),  Mth.lerp(stance, 0.0f, -7.0f), alpha);
        leg(pose,  0.16f, 0.78f, Mth.lerp(stance, -2.0f, -6.0f), Mth.lerp(stance, 0.0f, 5.0f), alpha);

        // Cadera, torso y cuello alto de uniforme.
        part(pose, 0.0f, 0.91f, 0.0f, 0.48f, 0.20f, 0.28f, 0.020f, 0.024f, 0.040f, alpha);
        part(pose, 0.0f, 1.31f, 0.0f, 0.56f, 0.72f, 0.31f, 0.022f, 0.027f, 0.045f, alpha);
        part(pose, 0.0f, 1.69f, -0.01f, 0.40f, 0.17f, 0.29f, 0.018f, 0.022f, 0.038f, alpha);

        ArmPose right = rightArmPose(t);
        ArmPose left = leftArmPose(t);
        jointedArm(pose, -0.39f, 1.51f, right, alpha);
        jointedArm(pose,  0.39f, 1.51f, left, alpha);

        // Cabeza con un poco de seguimiento hacia el centro de fusion.
        float headYaw = Mth.lerp(smooth(95.0f, 165.0f, t), -5.0f, 6.0f);
        float headPitch = Mth.lerp(smooth(120.0f, 190.0f, t), -2.0f, -8.0f);
        pose.pushPose();
        pose.translate(0.0f, 1.98f, 0.0f);
        pose.mulPose(Axis.YP.rotationDegrees(headYaw));
        pose.mulPose(Axis.XP.rotationDegrees(headPitch));
        box(pose, 0.52f, 0.52f, 0.52f, 0.94f, 0.80f, 0.71f, alpha);

        // Ojos: aparecen poco a poco cerca de la fase final.
        float reveal = smooth(148.0f, 174.0f, t);
        float blindAlpha = alpha * (1.0f - reveal);
        if (blindAlpha > 0.01f) {
            pose.pushPose();
            pose.translate(0.0f, 0.035f, -0.282f);
            box(pose, 0.55f, 0.15f, 0.035f, 0.008f, 0.010f, 0.016f, blindAlpha);
            pose.popPose();
        }
        if (reveal > 0.01f) {
            eye(pose, -0.115f, reveal * alpha);
            eye(pose,  0.115f, reveal * alpha);
        }

        // Pelo blanco volumetrico, mas parecido a un skin/modelo Minecraft.
        hair(pose, alpha);
        pose.popPose();

        // Brillo compacto en la mano de lanzamiento. Geometria, no particulas.
        if (t >= 166.0f && t <= 218.0f) {
            float q = smooth(166.0f, 184.0f, t) * (1.0f - smooth(207.0f, 220.0f, t));
            pose.pushPose();
            pose.translate(-0.54f, 1.56f, -0.66f);
            box(pose, 0.11f + q * 0.09f, 0.11f + q * 0.09f, 0.11f + q * 0.09f,
                    0.32f, 0.68f, 1.0f, alpha * (0.42f + 0.48f * q));
            pose.popPose();
        }

        pose.popPose();
    }

    private static ArmPose rightArmPose(float t) {
        if (t < 48.0f) {
            float q = smooth(12.0f, 48.0f, t);
            return new ArmPose(
                    Mth.lerp(q, 5.0f, -54.0f), Mth.lerp(q, 0.0f, -23.0f), Mth.lerp(q, 3.0f, -18.0f),
                    Mth.lerp(q, -4.0f, -28.0f), Mth.lerp(q, 0.0f, -12.0f), 0.0f
            );
        }
        if (t < 104.0f) {
            float q = smooth(48.0f, 92.0f, t);
            return new ArmPose(
                    Mth.lerp(q, -54.0f, -78.0f), Mth.lerp(q, -23.0f, -43.0f), Mth.lerp(q, -18.0f, -28.0f),
                    Mth.lerp(q, -28.0f, -50.0f), Mth.lerp(q, -12.0f, -8.0f), Mth.lerp(q, 0.0f, -5.0f)
            );
        }
        if (t < 158.0f) {
            float q = smooth(104.0f, 150.0f, t);
            return new ArmPose(
                    Mth.lerp(q, -78.0f, -102.0f), Mth.lerp(q, -43.0f, -11.0f), Mth.lerp(q, -28.0f, -5.0f),
                    Mth.lerp(q, -50.0f, -72.0f), Mth.lerp(q, -8.0f, 0.0f), Mth.lerp(q, -5.0f, 0.0f)
            );
        }
        float q = smooth(158.0f, 196.0f, t);
        return new ArmPose(
                Mth.lerp(q, -102.0f, -86.0f), Mth.lerp(q, -11.0f, 0.0f), Mth.lerp(q, -5.0f, 1.0f),
                Mth.lerp(q, -72.0f, -10.0f), Mth.lerp(q, 0.0f, 0.0f), 0.0f
        );
    }

    private static ArmPose leftArmPose(float t) {
        if (t < 48.0f) {
            float q = smooth(12.0f, 48.0f, t);
            return new ArmPose(
                    Mth.lerp(q, -2.0f, -40.0f), Mth.lerp(q, 0.0f, 20.0f), Mth.lerp(q, -3.0f, 14.0f),
                    Mth.lerp(q, 0.0f, -18.0f), Mth.lerp(q, 0.0f, 10.0f), 0.0f
            );
        }
        if (t < 104.0f) {
            float q = smooth(48.0f, 92.0f, t);
            return new ArmPose(
                    Mth.lerp(q, -40.0f, -78.0f), Mth.lerp(q, 20.0f, 43.0f), Mth.lerp(q, 14.0f, 28.0f),
                    Mth.lerp(q, -18.0f, -50.0f), Mth.lerp(q, 10.0f, 8.0f), Mth.lerp(q, 0.0f, 5.0f)
            );
        }
        if (t < 158.0f) {
            float q = smooth(104.0f, 150.0f, t);
            return new ArmPose(
                    Mth.lerp(q, -78.0f, -102.0f), Mth.lerp(q, 43.0f, 11.0f), Mth.lerp(q, 28.0f, 5.0f),
                    Mth.lerp(q, -50.0f, -72.0f), Mth.lerp(q, 8.0f, 0.0f), Mth.lerp(q, 5.0f, 0.0f)
            );
        }
        float q = smooth(158.0f, 200.0f, t);
        return new ArmPose(
                Mth.lerp(q, -102.0f, -26.0f), Mth.lerp(q, 11.0f, 12.0f), Mth.lerp(q, 5.0f, 11.0f),
                Mth.lerp(q, -72.0f, -8.0f), Mth.lerp(q, 0.0f, 5.0f), 0.0f
        );
    }

    private static void jointedArm(PoseStack pose, float shoulderX, float shoulderY, ArmPose a, float alpha) {
        pose.pushPose();
        pose.translate(shoulderX, shoulderY, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(a.upperZ));
        pose.mulPose(Axis.YP.rotationDegrees(a.upperY));
        pose.mulPose(Axis.XP.rotationDegrees(a.upperX));
        pose.translate(0.0f, -0.205f, 0.0f);
        box(pose, 0.22f, 0.43f, 0.24f, 0.024f, 0.029f, 0.048f, alpha);

        pose.translate(0.0f, -0.215f, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(a.lowerZ));
        pose.mulPose(Axis.YP.rotationDegrees(a.lowerY));
        pose.mulPose(Axis.XP.rotationDegrees(a.lowerX));
        pose.translate(0.0f, -0.18f, 0.0f);
        box(pose, 0.205f, 0.38f, 0.22f, 0.026f, 0.031f, 0.050f, alpha);
        pose.translate(0.0f, -0.245f, -0.015f);
        box(pose, 0.20f, 0.18f, 0.21f, 0.94f, 0.80f, 0.71f, alpha);
        pose.popPose();
    }

    private static void leg(PoseStack pose, float hipX, float hipY, float upperX, float lowerX, float alpha) {
        pose.pushPose();
        pose.translate(hipX, hipY, 0.0f);
        pose.mulPose(Axis.XP.rotationDegrees(upperX));
        pose.translate(0.0f, -0.20f, 0.0f);
        box(pose, 0.225f, 0.42f, 0.25f, 0.026f, 0.030f, 0.047f, alpha);
        pose.translate(0.0f, -0.21f, 0.0f);
        pose.mulPose(Axis.XP.rotationDegrees(lowerX));
        pose.translate(0.0f, -0.20f, 0.0f);
        box(pose, 0.22f, 0.42f, 0.245f, 0.028f, 0.032f, 0.048f, alpha);
        pose.popPose();
    }

    private static void eye(PoseStack pose, float x, float alpha) {
        pose.pushPose();
        pose.translate(x, 0.035f, -0.284f);
        box(pose, 0.095f, 0.055f, 0.025f, 0.42f, 0.86f, 1.0f, alpha);
        pose.translate(0.0f, 0.0f, -0.016f);
        box(pose, 0.032f, 0.032f, 0.018f, 0.91f, 0.98f, 1.0f, alpha);
        pose.popPose();
    }

    private static void hair(PoseStack pose, float alpha) {
        // Base de pelo.
        pose.pushPose();
        pose.translate(0.0f, 0.28f, 0.04f);
        box(pose, 0.54f, 0.20f, 0.52f, 0.93f, 0.95f, 1.0f, alpha);
        pose.popPose();

        spike(pose, -0.22f, 0.39f,  0.03f, -28.0f, -13.0f, alpha);
        spike(pose, -0.10f, 0.43f, -0.03f, -12.0f, -5.0f, alpha);
        spike(pose,  0.03f, 0.45f, -0.05f,   3.0f,  1.0f, alpha);
        spike(pose,  0.16f, 0.42f, -0.01f,  17.0f,  7.0f, alpha);
        spike(pose,  0.25f, 0.36f,  0.07f,  30.0f, 14.0f, alpha);
        spike(pose, -0.27f, 0.29f,  0.14f, -38.0f, -4.0f, alpha);
        spike(pose,  0.28f, 0.29f,  0.14f,  38.0f,  4.0f, alpha);
        spike(pose, -0.15f, 0.33f,  0.23f, -19.0f, 16.0f, alpha);
        spike(pose,  0.14f, 0.34f,  0.23f,  18.0f, 17.0f, alpha);
    }

    private static void spike(PoseStack pose, float x, float y, float z, float zRot, float xRot, float alpha) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.ZP.rotationDegrees(zRot));
        pose.mulPose(Axis.XP.rotationDegrees(xRot));
        box(pose, 0.14f, 0.31f, 0.16f, 0.94f, 0.96f, 1.0f, alpha);
        pose.popPose();
    }

    private static void part(PoseStack pose, float x, float y, float z,
                             float sx, float sy, float sz,
                             float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(x, y, z);
        box(pose, sx, sy, sz, r, g, b, a);
        pose.popPose();
    }

    private static void box(PoseStack pose, float sx, float sy, float sz,
                            float r, float g, float b, float a) {
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        if (a < 0.999f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float x = sx * 0.5f;
        float y = sy * 0.5f;
        float z = sz * 0.5f;
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        quad(bb,m,-x,-y,-z, x,-y,-z, x,y,-z,-x,y,-z,r,g,b,a);
        quad(bb,m, x,-y, z,-x,-y, z,-x,y, z, x,y, z,r,g,b,a);
        quad(bb,m,-x,-y, z,-x,-y,-z,-x,y,-z,-x,y, z,r,g,b,a);
        quad(bb,m, x,-y,-z, x,-y, z, x,y, z, x,y,-z,r,g,b,a);
        quad(bb,m,-x,-y, z, x,-y, z, x,-y,-z,-x,-y,-z,r,g,b,a);
        quad(bb,m,-x, y,-z, x, y,-z, x, y, z,-x, y, z,r,g,b,a);
        BufferUploader.drawWithShader(bb.end());
    }

    private static void quad(BufferBuilder bb, Matrix4f m,
                             float x0,float y0,float z0,float x1,float y1,float z1,
                             float x2,float y2,float z2,float x3,float y3,float z3,
                             float r,float g,float b,float a) {
        vertex(bb,m,x0,y0,z0,r,g,b,a);
        vertex(bb,m,x1,y1,z1,r,g,b,a);
        vertex(bb,m,x2,y2,z2,r,g,b,a);
        vertex(bb,m,x3,y3,z3,r,g,b,a);
    }

    private static void vertex(BufferBuilder bb, Matrix4f m,
                               float x,float y,float z,float r,float g,float b,float a) {
        bb.vertex(m,x,y,z).color(toColor(r),toColor(g),toColor(b),toColor(a)).endVertex();
    }

    private static int toColor(float v) {
        return Mth.clamp((int)(v * 255.0f),0,255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private record ArmPose(float upperX,float upperY,float upperZ,float lowerX,float lowerY,float lowerZ) {}
}
