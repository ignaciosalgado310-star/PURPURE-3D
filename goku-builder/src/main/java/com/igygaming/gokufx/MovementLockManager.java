package com.igygaming.gokufx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Locks a target to the exact position where an attack began.
 * It runs after the attack managers, so even legacy push/drag code cannot move the player.
 */
public final class MovementLockManager {
    private static final Map<UUID, LockState> ACTIVE = new HashMap<>();

    private MovementLockManager() {}

    public static void lock(ServerPlayer player, int ticks) {
        UUID id = player.getUUID();
        LockState previous = ACTIVE.get(id);
        boolean originalNoGravity = previous != null
                ? previous.originalNoGravity
                : player.isNoGravity();

        ACTIVE.put(id, new LockState(
                player.serverLevel(),
                player.position(),
                Math.max(1, ticks),
                originalNoGravity
        ));

        hold(player, player.position());
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, LockState>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LockState> entry = iterator.next();
            LockState state = entry.getValue();
            ServerPlayer player = state.level.getServer().getPlayerList().getPlayer(entry.getKey());

            if (player == null || !player.isAlive() || player.serverLevel() != state.level) {
                restore(player, state);
                iterator.remove();
                continue;
            }

            hold(player, state.anchor);
            state.remainingTicks--;

            if (state.remainingTicks <= 0) {
                restore(player, state);
                iterator.remove();
            }
        }
    }

    private static void hold(ServerPlayer player, Vec3 anchor) {
        player.setNoGravity(true);

        if (player.position().distanceToSqr(anchor) > 0.0004D) {
            player.connection.teleport(
                    anchor.x,
                    anchor.y,
                    anchor.z,
                    player.getYRot(),
                    player.getXRot()
            );
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
    }

    private static void restore(ServerPlayer player, LockState state) {
        if (player == null) return;
        player.setNoGravity(state.originalNoGravity);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
    }

    private static final class LockState {
        final ServerLevel level;
        final Vec3 anchor;
        final boolean originalNoGravity;
        int remainingTicks;

        LockState(ServerLevel level, Vec3 anchor, int remainingTicks, boolean originalNoGravity) {
            this.level = level;
            this.anchor = anchor;
            this.remainingTicks = remainingTicks;
            this.originalNoGravity = originalNoGravity;
        }
    }
}
