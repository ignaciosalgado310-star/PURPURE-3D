package com.igy.zoro3d.client;

import com.igy.zoro3d.Zoro3D;
import com.igy.zoro3d.server.ZoroAttack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Zoro3D.MOD_ID, value = Dist.CLIENT)
public final class ZoroHudRenderer {

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        ZoroClientState.VisualState state = ZoroClientState.localState();
        if (state == null) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int cx = w / 2;
        int cy = h / 2;

        if (state.stage() == ZoroAttack.STAGE_INTRO) {
            drawIntro(g, cx, cy, w, h, state.age());
        } else if (state.stage() == ZoroAttack.STAGE_RING) {
            drawRing(g, cx, cy, w, h, state.age() - ZoroAttack.INTRO_END);
        } else {
            int slashAge = state.age() - ZoroAttack.RING_END;
            int cycle = Math.floorMod(slashAge, 72);
            if (cycle >= 52) drawLogoBreak(g, cx, cy, w, h, slashAge);
            else drawSlashStorm(g, cx, cy, w, h, slashAge);
        }

        drawTotemCounter(g, cx, state);
    }

    private static void drawIntro(GuiGraphics g, int cx, int cy, int w, int h, int age) {
        float p = Math.min(1.0F, age / 50.0F);
        int alpha = 175 + (int) (55 * p);
        g.fill(0, 0, w, h, (alpha << 24));

        PoseStack pose = g.pose();
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-32 + i * 32 + age * (i == 1 ? -0.3F : 0.22F)));
            int glow = (int) (8 + 10 * p);
            g.fill(-w / 3, -glow, w / 3, glow, 0x6600FF66);
            g.fill(-w / 3, -2, w / 3, 2, 0xEEB8FFD0);
            pose.popPose();
        }

        drawZoroLogo(g, cx, cy, 1.0F + 0.05F * (float) Math.sin(age * 0.25F));
    }

    private static void drawRing(GuiGraphics g, int cx, int cy, int w, int h, int ringAge) {
        g.fill(0, 0, w, h, 0xB9000000);
        float p = Math.min(1.0F, ringAge / 55.0F);
        float baseR = Math.min(w, h) * (0.13F + 0.25F * p);
        PoseStack pose = g.pose();

        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2.0 / 48.0 + ringAge * 0.015;
            float wave = 0.82F + 0.18F * (float) Math.sin(i * 1.77 + ringAge * 0.16);
            float r = baseR * wave;
            int x = cx + (int) (Math.cos(a) * r);
            int y = cy + (int) (Math.sin(a) * r);
            float length = 18.0F + 34.0F * p + 12.0F * (float) Math.sin(i * 0.93 + ringAge * 0.11);

            pose.pushPose();
            pose.translate(x, y, 0);
            pose.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(a) + 90.0F));
            g.fill(-6, -(int) length, 6, (int) length, 0x5522DFFF);
            g.fill(-2, -(int) (length * 0.86F), 2, (int) (length * 0.86F), 0xD8C9FFFF);
            pose.popPose();
        }

        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2.0 / 18.0 - ringAge * 0.025;
            float r = baseR * 0.56F;
            int x = cx + (int) (Math.cos(a) * r);
            int y = cy + (int) (Math.sin(a) * r);
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(a)));
            g.fill(-14, -3, 14, 3, 0x8811A8FF);
            g.fill(-8, -1, 8, 1, 0xFFFFFFFF);
            pose.popPose();
        }

        int flash = (int) (40 + 80 * Math.abs(Math.sin(ringAge * 0.22)));
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, (flash << 24) | 0x00DFFFFF);
    }

    private static void drawSlashStorm(GuiGraphics g, int cx, int cy, int w, int h, int age) {
        int shade = 90 + (int) (45 * Math.abs(Math.sin(age * 0.12)));
        g.fill(0, 0, w, h, (shade << 24));
        PoseStack pose = g.pose();
        float diag = (float) Math.hypot(w, h);

        for (int i = 0; i < 28; i++) {
            float angle = i * 137.507F + age * (i % 2 == 0 ? 2.35F : -1.72F);
            float osc = (float) Math.sin(age * 0.09 + i * 1.31);
            int ox = (int) (Math.cos(i * 2.17 + age * 0.025) * w * 0.16);
            int oy = (int) (Math.sin(i * 1.63 - age * 0.021) * h * 0.16);
            int len = (int) (diag * (0.34F + 0.22F * (0.5F + 0.5F * osc)));
            int thick = 5 + (i % 5);

            pose.pushPose();
            pose.translate(cx + ox, cy + oy, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            g.fill(-len / 2, -thick * 3, len / 2, thick * 3, 0x3B00FF55);
            g.fill(-len / 2, -thick, len / 2, thick, 0xB91BFF6B);
            g.fill(-len / 2, -1, len / 2, 1, 0xFFFFFFFF);
            pose.popPose();
        }

        for (int i = 0; i < 10; i++) {
            float angle = i * 36.0F - age * 3.1F;
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            g.fill(0, -7, (int) (diag * 0.43F), 7, 0x4600FF3C);
            g.fill(0, -2, (int) (diag * 0.39F), 2, 0xD7CFFFFF);
            pose.popPose();
        }

        if (age % 18 < 3) g.fill(0, 0, w, h, 0x3A7DFFB0);
    }

    private static void drawLogoBreak(GuiGraphics g, int cx, int cy, int w, int h, int age) {
        g.fill(0, 0, w, h, 0xEA000000);
        drawZoroLogo(g, cx, cy, 1.0F + 0.07F * (float) Math.sin(age * 0.33F));
        PoseStack pose = g.pose();
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(cx, cy + 34, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-19 + i * 19));
            g.fill(-95, -3, 95, 3, 0x9A00E95F);
            g.fill(-78, -1, 78, 1, 0xFFFFFFFF);
            pose.popPose();
        }
    }

    private static void drawZoroLogo(GuiGraphics g, int cx, int cy, float pulse) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(3.1F * pulse, 3.1F * pulse, 1.0F);
        Component text = Component.literal("ZORO");
        g.drawCenteredString(mc.font, text, -2, -5, 0xFF003814);
        g.drawCenteredString(mc.font, text, 2, -5, 0xFF003814);
        g.drawCenteredString(mc.font, text, 0, -7, 0xFF003814);
        g.drawCenteredString(mc.font, text, 0, -5, 0xFF28FF63);
        pose.popPose();
    }

    private static void drawTotemCounter(GuiGraphics g, int cx, ZoroClientState.VisualState state) {
        Minecraft mc = Minecraft.getInstance();
        int remaining = Math.max(0, state.requestedTotems() - state.usedTotems());
        int color = remaining <= 30 ? 0xFFFF2727 : 0xFF24FF36;
        PoseStack pose = g.pose();

        pose.pushPose();
        pose.translate(cx - 58, 15, 0);
        pose.scale(1.65F, 1.65F, 1.0F);
        g.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), 0, 0);
        pose.popPose();

        String num = Integer.toString(remaining);
        pose.pushPose();
        pose.translate(cx + 15, 18, 0);
        pose.scale(2.45F, 2.45F, 1.0F);
        g.drawCenteredString(mc.font, Component.literal(num), 0, 0, 0xFF000000);
        g.drawCenteredString(mc.font, Component.literal(num), -1, -1, color);
        pose.popPose();
    }

    private ZoroHudRenderer() {}
}
