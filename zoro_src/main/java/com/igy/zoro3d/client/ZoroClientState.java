package com.igy.zoro3d.client;

import com.igy.zoro3d.network.ZoroSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ZoroClientState {
    private static final Map<UUID, VisualState> ACTIVE = new ConcurrentHashMap<>();

    public static void handle(ZoroSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (msg.operation() == ZoroSyncPacket.OP_END) {
            ACTIVE.remove(msg.attackId());
            return;
        }

        String currentDimension = mc.level.dimension().location().toString();
        if (!currentDimension.equals(msg.dimension())) {
            ACTIVE.remove(msg.attackId());
            return;
        }

        ACTIVE.put(msg.attackId(), new VisualState(
                msg.attackId(), msg.targetId(), msg.stage(), msg.age(),
                new Vec3(msg.x(), msg.y(), msg.z()),
                msg.usedTotems(), msg.requestedTotems(),
                System.currentTimeMillis()
        ));
    }

    public static Collection<VisualState> active() {
        cleanup();
        return ACTIVE.values();
    }

    public static VisualState localState() {
        cleanup();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        UUID id = mc.player.getUUID();
        return ACTIVE.values().stream()
                .filter(s -> s.targetId().equals(id))
                .max(Comparator.comparingLong(VisualState::lastUpdateMillis))
                .orElse(null);
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(entry -> now - entry.getValue().lastUpdateMillis() > 5000L);
    }

    public record VisualState(
            UUID id,
            UUID targetId,
            int stage,
            int age,
            Vec3 center,
            int usedTotems,
            int requestedTotems,
            long lastUpdateMillis
    ) {}

    private ZoroClientState() {}
}
