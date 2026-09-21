package com.igy.zoro3d.server;

import com.igy.zoro3d.Zoro3D;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Zoro3D.MOD_ID)
public final class ZoroManager {
    private static final Map<UUID, ZoroAttack> ACTIVE = new HashMap<>();

    public static void start(ServerPlayer target, int requestedTotems) {
        ZoroAttack current = ACTIVE.get(target.getUUID());
        if (current != null) {
            current.addTotems(requestedTotems);
            return;
        }
        ZoroAttack attack = new ZoroAttack(target, requestedTotems);
        ACTIVE.put(target.getUUID(), attack);
        attack.begin();
    }

    public static boolean cancel(UUID targetId) {
        ZoroAttack attack = ACTIVE.remove(targetId);
        if (attack == null) return false;
        attack.finish(true);
        return true;
    }

    public static int cancelAll() {
        int count = ACTIVE.size();
        for (ZoroAttack attack : ACTIVE.values()) attack.finish(true);
        ACTIVE.clear();
        return count;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, ZoroAttack>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue().tick()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        cancel(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        cancelAll();
    }

    private ZoroManager() {}
}
