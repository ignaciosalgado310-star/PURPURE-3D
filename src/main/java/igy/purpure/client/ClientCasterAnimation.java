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
        if (tick < 0.0f || tick > 215.0f) return;

        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        BACKUPS.put(player.getUUID(), new PoseBackup(model));
        applyCasterPose(model, tick);
    }

    @SubscribeEvent
    public static void onPlayerRenderPost(RenderPlayerEvent.Post event) {
        PoseBackup backup = BACKUPS.remove(event.getEntity().getUUID());
        if (backup != null) {
            backup.restore(event.getRenderer().getModel());
        }
    }

    private static void applyCasterPose(PlayerModel<AbstractClientPlayer> model, float t) {
        if (t < 55.0f) {
            float q = smooth(0.0f, 45.0f, t);
            setPart(model.rightArm, Mth.lerp(q, model.rightArm.xRot, -1.02f), -0.24f * q, 0.16f * q);
            setPart(model.leftArm, Mth.lerp(q, model.leftArm.xRot, -0.88f), 0.30f * q, -0.12f * q);
            model.body.yRot = Mth.lerp(q, model.body.yRot, 0.07f);
            model.head.xRot = Mth.lerp(q, model.head.xRot, -0.06f);
        } else if (t < 105.0f) {
            float q = smooth(55.0f, 95.0f, t);
            setPart(model.rightArm, Mth.lerp(q, -1.02f, -1.28f), Mth.lerp(q, -0.24f, -0.58f), Mth.lerp(q, 0.16f, 0.23f));
            setPart(model.leftArm, Mth.lerp(q, -0.88f, -1.22f), Mth.lerp(q, 0.30f, 0.58f), Mth.lerp(q, -0.12f, -0.23f));
            model.body.yRot = 0.0f;
            model.head.xRot = -0.08f;
        } else if (t < 150.0f) {
            float q = smooth(105.0f, 142.0f, t);
            setPart(model.rightArm, Mth.lerp(q, -1.28f, -1.48f), Mth.lerp(q, -0.58f, -0.18f), Mth.lerp(q, 0.23f, 0.42f));
            setPart(model.leftArm, Mth.lerp(q, -1.22f, -1.48f), Mth.lerp(q, 0.58f, 0.18f), Mth.lerp(q, -0.23f, -0.42f));
            model.body.yRot = 0.0f;
            model.head.xRot = -0.10f;
        } else {
            float q = smooth(150.0f, 185.0f, t);
            setPart(model.rightArm, Mth.lerp(q, -1.48f, -1.56f), Mth.lerp(q, -0.18f, -0.03f), Mth.lerp(q, 0.42f, 0.02f));
            setPart(model.leftArm, Mth.lerp(q, -1.48f, -0.52f), Mth.lerp(q, 0.18f, 0.16f), Mth.lerp(q, -0.42f, -0.16f));
            model.body.yRot = Mth.lerp(q, 0.0f, -0.05f);
            model.head.xRot = Mth.lerp(q, -0.10f, -0.03f);
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
