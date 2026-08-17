package igy.purpure.client;

import igy.purpure.PurpureMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientCasterAnimation {
    private static final Map<UUID, PoseBackup> BACKUPS = new HashMap<>();

    private ClientCasterAnimation() {}

    @SubscribeEvent
    public static void onPlayerRenderPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;

        float tick = ClientPurpureEffects.effectTick(player.getUUID());
        if (tick < 0.0f || tick > 205.0f) return;

        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        BACKUPS.put(player.getUUID(), new PoseBackup(model));
        applyCasterPose(model, tick);
    }

    @SubscribeEvent
    public static void onPlayerRenderPost(RenderPlayerEvent.Post event) {
        PoseBackup backup = BACKUPS.remove(event.getEntity().getUUID());
        if (backup != null) backup.restore(event.getRenderer().getModel());
    }

    private static void applyCasterPose(PlayerModel<AbstractClientPlayer> model, float t) {
        if (t < 48.0f) {
            float q = smooth(0.0f, 42.0f, t);
            setPart(model.rightArm, Mth.lerp(q, model.rightArm.xRot, -1.35f), -0.72f * q, 0.34f * q);
            setPart(model.leftArm, Mth.lerp(q, model.leftArm.xRot, -1.05f), 0.55f * q, -0.28f * q);
            model.body.yRot = 0.11f * q;
            model.head.xRot = -0.10f * q;
            model.head.yRot = -0.10f * q;
        } else if (t < 96.0f) {
            float q = smooth(48.0f, 88.0f, t);
            setPart(model.rightArm, Mth.lerp(q, -1.35f, -1.56f), Mth.lerp(q, -0.72f, -0.92f), Mth.lerp(q, 0.34f, 0.47f));
            setPart(model.leftArm, Mth.lerp(q, -1.05f, -1.50f), Mth.lerp(q, 0.55f, 0.88f), Mth.lerp(q, -0.28f, -0.47f));
            model.body.yRot = Mth.lerp(q, 0.11f, 0.0f);
            model.head.xRot = -0.12f;
            model.head.yRot = Mth.sin(t * 0.035f) * 0.10f;
        } else if (t < 136.0f) {
            float q = smooth(96.0f, 128.0f, t);
            setPart(model.rightArm, Mth.lerp(q, -1.56f, -1.72f), Mth.lerp(q, -0.92f, -0.20f), Mth.lerp(q, 0.47f, 0.16f));
            setPart(model.leftArm, Mth.lerp(q, -1.50f, -1.72f), Mth.lerp(q, 0.88f, 0.20f), Mth.lerp(q, -0.47f, -0.16f));
            model.body.yRot = 0.0f;
            model.head.xRot = -0.16f;
            model.head.yRot = 0.0f;
        } else if (t < 170.0f) {
            float q = smooth(136.0f, 160.0f, t);
            setPart(model.rightArm, Mth.lerp(q, -1.72f, -1.86f), Mth.lerp(q, -0.20f, 0.0f), Mth.lerp(q, 0.16f, 0.0f));
            setPart(model.leftArm, Mth.lerp(q, -1.72f, -0.72f), Mth.lerp(q, 0.20f, 0.32f), Mth.lerp(q, -0.16f, -0.22f));
            model.body.yRot = Mth.lerp(q, 0.0f, -0.08f);
            model.head.xRot = Mth.lerp(q, -0.16f, -0.04f);
        } else {
            float q = smooth(170.0f, 195.0f, t);
            setPart(model.rightArm, Mth.lerp(q, -1.86f, -1.52f), 0.0f, 0.0f);
            setPart(model.leftArm, Mth.lerp(q, -0.72f, -0.42f), 0.20f, -0.14f);
            model.body.yRot = -0.06f;
            model.head.xRot = -0.02f;
        }

        copyRot(model.rightArm, model.rightSleeve);
        copyRot(model.leftArm, model.leftSleeve);
        copyRot(model.head, model.hat);
    }

    private static void setPart(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    private static void copyRot(net.minecraft.client.model.geom.ModelPart from, net.minecraft.client.model.geom.ModelPart to) {
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static final class PoseBackup {
        final float rX, rY, rZ;
        final float lX, lY, lZ;
        final float rsX, rsY, rsZ;
        final float lsX, lsY, lsZ;
        final float hX, hY, hZ;
        final float hatX, hatY, hatZ;
        final float bX, bY, bZ;

        PoseBackup(PlayerModel<AbstractClientPlayer> model) {
            rX = model.rightArm.xRot; rY = model.rightArm.yRot; rZ = model.rightArm.zRot;
            lX = model.leftArm.xRot; lY = model.leftArm.yRot; lZ = model.leftArm.zRot;
            rsX = model.rightSleeve.xRot; rsY = model.rightSleeve.yRot; rsZ = model.rightSleeve.zRot;
            lsX = model.leftSleeve.xRot; lsY = model.leftSleeve.yRot; lsZ = model.leftSleeve.zRot;
            hX = model.head.xRot; hY = model.head.yRot; hZ = model.head.zRot;
            hatX = model.hat.xRot; hatY = model.hat.yRot; hatZ = model.hat.zRot;
            bX = model.body.xRot; bY = model.body.yRot; bZ = model.body.zRot;
        }

        void restore(PlayerModel<AbstractClientPlayer> model) {
            setPart(model.rightArm, rX, rY, rZ);
            setPart(model.leftArm, lX, lY, lZ);
            setPart(model.rightSleeve, rsX, rsY, rsZ);
            setPart(model.leftSleeve, lsX, lsY, lsZ);
            setPart(model.head, hX, hY, hZ);
            setPart(model.hat, hatX, hatY, hatZ);
            setPart(model.body, bX, bY, bZ);
        }
    }
}
