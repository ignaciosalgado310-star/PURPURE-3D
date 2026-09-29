package com.igygaming.gokufx;

import com.igygaming.gokufx.network.GokuEffectPacket;
import com.igygaming.gokufx.network.GokuNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Clean Genki Dama rebuild. The command/network contract and real totem
 * consumption are preserved, while the actual attack sequence is independent
 * from Kamehameha.
 */
public final class GenkiManager {
    private static final List<GenkiAttack> ACTIVE = new ArrayList<>();
    private static final Map<UUID, ArrayDeque<QueuedGenki>> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> RESTART_DELAY = new HashMap<>();

    private static final int MAX_QUEUE_PER_TARGET = 100;
    private static final int QUEUE_GAP_TICKS = 10;
    private static final int TRAVEL_BLOCK_BUDGET = 520;
    private static final int IMPACT_BLOCK_BUDGET = 1900;

    private GenkiManager() {}

    /**
     * Starts immediately when the target has no active Genki Dama.
     * Otherwise it is queued FIFO and begins automatically after the current
     * cinematic finishes. Returns 0 when started now, a positive queue
     * position when queued, and -1 when the safety queue is full.
     */
    public static int start(ServerPlayer player, int totems) {
        int hits = Math.max(1, totems);
        UUID targetId = player.getUUID();

        ArrayDeque<QueuedGenki> queue = PENDING.computeIfAbsent(targetId, ignored -> new ArrayDeque<>());
        boolean busy = hasActive(targetId)
                || RESTART_DELAY.getOrDefault(targetId, 0) > 0
                || !queue.isEmpty();

        if (busy) {
            if (queue.size() >= MAX_QUEUE_PER_TARGET) {
                return -1;
            }
            queue.addLast(new QueuedGenki(targetId, hits));
            return queue.size();
        }

        activate(player, hits);
        return 0;
    }

    public static int queuedCount(UUID targetId) {
        Queue<QueuedGenki> queue = PENDING.get(targetId);
        return queue == null ? 0 : queue.size();
    }

    private static boolean hasActive(UUID targetId) {
        for (GenkiAttack attack : ACTIVE) {
            if (attack.targets(targetId)) {
                return true;
            }
        }
        return false;
    }

    private static void activate(ServerPlayer player, int hits) {
        GenkiAttack attack = new GenkiAttack(player, hits);
        ACTIVE.add(attack);

        // The target is only locked once its own queued cinematic actually begins.
        MovementLockManager.lock(player, GenkiTiming.totalTicks(hits) + 12);

        GokuNetwork.CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> player.serverLevel().dimension()),
                new GokuEffectPacket(GokuEffectPacket.GENKI_DAMA, player.getUUID(), hits)
        );
    }

    public static void tick() {
        Set<UUID> finishedTargets = new HashSet<>();
        Iterator<GenkiAttack> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            GenkiAttack attack = iterator.next();
            try {
                if (attack.tick()) {
                    UUID targetId = attack.targetId();
                    attack.cleanup();
                    iterator.remove();
                    finishedTargets.add(targetId);
                }
            } catch (Throwable t) {
                UUID targetId = attack.targetId();
                attack.cleanup();
                iterator.remove();
                finishedTargets.add(targetId);
                t.printStackTrace();
            }
        }

        for (UUID targetId : finishedTargets) {
            RESTART_DELAY.put(targetId, QUEUE_GAP_TICKS);
        }

        tickQueues();
    }

    private static void tickQueues() {
        Set<UUID> ids = new HashSet<>();
        ids.addAll(PENDING.keySet());
        ids.addAll(RESTART_DELAY.keySet());

        for (UUID targetId : ids) {
            if (hasActive(targetId)) {
                continue;
            }

            int delay = RESTART_DELAY.getOrDefault(targetId, 0);
            if (delay > 0) {
                RESTART_DELAY.put(targetId, delay - 1);
                continue;
            }
            RESTART_DELAY.remove(targetId);

            ArrayDeque<QueuedGenki> queue = PENDING.get(targetId);
            if (queue == null || queue.isEmpty()) {
                PENDING.remove(targetId);
                continue;
            }

            ServerPlayer player = queueTarget(targetId);
            if (player == null || !player.isAlive()) {
                queue.clear();
                PENDING.remove(targetId);
                continue;
            }

            QueuedGenki next = queue.removeFirst();
            if (queue.isEmpty()) {
                PENDING.remove(targetId);
            }
            activate(player, next.hits());
        }
    }

    private static ServerPlayer queueTarget(UUID targetId) {
        for (GenkiAttack attack : ACTIVE) {
            ServerPlayer player = attack.level.getServer().getPlayerList().getPlayer(targetId);
            if (player != null) {
                return player;
            }
        }

        // No active attack exists here, so use any live server reachable from a queued player's UUID.
        // Minecraft's player list is global; the first active level gives us the server when available.
        if (!ACTIVE.isEmpty()) {
            return ACTIVE.get(0).level.getServer().getPlayerList().getPlayer(targetId);
        }
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null
                ? null
                : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(targetId);
    }

    private record QueuedGenki(UUID targetId, int hits) {}

    private static final class GenkiAttack {
        private final ServerLevel level;
        private final UUID targetId;
        private final int requestedHits;
        private final Vec3 origin;
        private final Vec3 contactCenter;
        private final Vec3 impactCenter;
        private final boolean originalNoGravity;

        private final Queue<BlockPos> blockQueue = new ArrayDeque<>();
        private final Set<Long> queuedBlocks = new HashSet<>();

        private int age;
        private int completedHits;
        private boolean impactPrepared;
        private boolean cleaned;

        private GenkiAttack(ServerPlayer target, int requestedHits) {
            this.level = target.serverLevel();
            this.targetId = target.getUUID();
            this.requestedHits = requestedHits;
            this.origin = target.position();
            this.contactCenter = origin.add(0.0D, GenkiTiming.CONTACT_Y, 0.0D);
            this.impactCenter = contactCenter.add(GenkiTiming.DRAG_DISTANCE, 0.0D, 0.0D);
            this.originalNoGravity = target.isNoGravity();
        }

        private boolean targets(UUID id) {
            return targetId.equals(id);
        }

        private UUID targetId() {
            return targetId;
        }

        private ServerPlayer target() {
            return level.getServer().getPlayerList().getPlayer(targetId);
        }

        private boolean tick() {
            ServerPlayer player = target();
            if (player == null || !player.isAlive() || player.serverLevel() != level) {
                return true;
            }

            age++;
            playTimelineAudio(player);

            if (age >= GenkiTiming.CONTACT_TICK && age < GenkiTiming.IMPACT_TICK) {
                holdTarget(player);

                if ((age - GenkiTiming.CONTACT_TICK) % 2 == 0) {
                    enqueueTravelSlice(expectedOrbCenter(age));
                }
            } else if (age >= GenkiTiming.IMPACT_TICK && age <= GenkiTiming.fadeStartTick(requestedHits)) {
                holdTarget(player);
            }

            if (age == GenkiTiming.IMPACT_TICK) {
                prepareImpact(player);
            }

            if (age >= GenkiTiming.hitStartTick()) {
                applyRequestedHits(
                        player,
                        GenkiTiming.hitStartTick(),
                        GenkiTiming.hitEndTick(requestedHits)
                );
            }

            if (age >= GenkiTiming.IMPACT_TICK && age <= GenkiTiming.fadeStartTick(requestedHits)) {
                if (age % 5 == 0) {
                    damageNearby(impactCenter, 17.5D, 44.0F, player);
                }
                if (age % 30 == 0) {
                    level.playSound(
                            null,
                            impactCenter.x, impactCenter.y, impactCenter.z,
                            SoundEvents.BEACON_AMBIENT,
                            SoundSource.HOSTILE,
                            1.0F,
                            0.50F
                    );
                }
            }

            processBlockBudget(age >= GenkiTiming.IMPACT_TICK
                    ? IMPACT_BLOCK_BUDGET
                    : TRAVEL_BLOCK_BUDGET);

            if (age > GenkiTiming.totalTicks(requestedHits) && completedHits >= requestedHits) {
                blockQueue.clear();
                queuedBlocks.clear();
                return true;
            }

            return false;
        }

        private void playTimelineAudio(ServerPlayer player) {
            if (age == 1) {
                level.playSound(
                        null,
                        origin.x + GenkiTiming.GOKU_X,
                        origin.y + GenkiTiming.GOKU_Y + 2.0D,
                        origin.z,
                        SoundEvents.BEACON_ACTIVATE,
                        SoundSource.HOSTILE,
                        0.75F,
                        0.62F
                );
            }

            if (age <= GenkiTiming.CHARGE_TICKS && age % 36 == 0) {
                float p = age / (float) GenkiTiming.CHARGE_TICKS;
                level.playSound(
                        null,
                        origin.x + GenkiTiming.GOKU_X,
                        origin.y + GenkiTiming.SPHERE_Y,
                        origin.z,
                        SoundEvents.RESPAWN_ANCHOR_CHARGE,
                        SoundSource.HOSTILE,
                        0.24F + p * 0.30F,
                        0.55F + p * 0.48F
                );
            }

            if (age == GenkiTiming.THROW_START_TICK) {
                level.playSound(
                        null,
                        origin.x + GenkiTiming.GOKU_X,
                        origin.y + GenkiTiming.GOKU_Y + 3.5D,
                        origin.z,
                        SoundEvents.WITHER_SHOOT,
                        SoundSource.HOSTILE,
                        2.6F,
                        0.52F
                );
            }

            if (age == GenkiTiming.CONTACT_TICK) {
                level.playSound(
                        null,
                        player.getX(),
                        player.getY() + 1.0D,
                        player.getZ(),
                        SoundEvents.WARDEN_SONIC_CHARGE,
                        SoundSource.HOSTILE,
                        2.8F,
                        0.46F
                );
            }
        

            if (age == GenkiTiming.IMPACT_TICK) {
                level.playSound(
                        null,
                        impactCenter.x, impactCenter.y, impactCenter.z,
                        SoundEvents.GENERIC_EXPLODE,
                        SoundSource.HOSTILE,
                        4.0F,
                        0.66F
                );
                level.playSound(
                        null,
                        impactCenter.x, impactCenter.y, impactCenter.z,
                        SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.HOSTILE,
                        2.8F,
                        0.72F
                );
            }
}

        private Vec3 expectedOrbCenter(float currentAge) {
            float p = GenkiTiming.dragEase(GenkiTiming.dragProgress(currentAge));
            return contactCenter.lerp(impactCenter, p);
        }

        private void holdTarget(ServerPlayer player) {
            // MovementLockManager already freezes the target for the cinematic.
            // Do not teleport, pull or add Genki-specific motion.
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
        }

        private void prepareImpact(ServerPlayer player) {
            holdTarget(player);

            level.playSound(
                    null,
                    impactCenter.x, impactCenter.y, impactCenter.z,
                    SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE,
                    4.0F,
                    0.28F
            );
            level.playSound(
                    null,
                    impactCenter.x, impactCenter.y, impactCenter.z,
                    SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.HOSTILE,
                    4.0F,
                    0.22F
            );
            level.playSound(
                    null,
                    impactCenter.x, impactCenter.y, impactCenter.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE,
                    2.8F,
                    0.50F
            );

            prepareImpactCrater();
        }

        private void applyRequestedHits(ServerPlayer player, int startTick, int endTick) {
            if (age < startTick || completedHits >= requestedHits) {
                return;
            }

            int duration = Math.max(1, endTick - startTick + 1);
            int elapsed = Math.min(duration, Math.max(1, age - startTick + 1));

            long dueLong = ((long) elapsed * (long) requestedHits + duration - 1L) / duration;
            int due = (int) Math.min((long) requestedHits, dueLong);

            int processed = 0;
            while (completedHits < due
                    && processed < GenkiTiming.MAX_TOTEMS_PER_TICK
                    && player.isAlive()) {

                if (consumeTotem(player)) {
                    level.broadcastEntityEvent(player, (byte) 35);
                } else {
                    player.invulnerableTime = 0;
                    player.hurt(level.damageSources().magic(), 1000.0F);
                }

                completedHits++;
                processed++;
            }
        }

        private boolean consumeTotem(ServerPlayer player) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.is(Items.TOTEM_OF_UNDYING)) {
                offhand.shrink(1);
                return true;
            }

            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(Items.TOTEM_OF_UNDYING)) {
                    stack.shrink(1);
                    return true;
                }
            }

            return false;
        }

        private void damageNearby(Vec3 center, double radius, float amount, ServerPlayer mainTarget) {
            AABB box = new AABB(center, center).inflate(radius);
            double radiusSqr = radius * radius;

            for (LivingEntity living :
                    level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {

                if (living == mainTarget) {
                    continue;
                }

                if (living.position().distanceToSqr(center) <= radiusSqr) {
                    living.invulnerableTime = 0;
                    living.hurt(level.damageSources().magic(), amount);
                }
            }
        }

        private void enqueueTravelSlice(Vec3 center) {
            enqueueEllipsoid(center, 5.4D, 4.1D, 5.4D);
        }

        private void prepareImpactCrater() {
            if (impactPrepared) {
                return;
            }
            impactPrepared = true;

            enqueueEllipsoid(
                    impactCenter.add(1.8D, -1.8D, 0.0D),
                    19.0D, 9.6D, 19.0D
            );
            enqueueEllipsoid(
                    impactCenter.add(5.2D, -6.0D, 0.0D),
                    14.0D, 7.5D, 14.0D
            );
            enqueueEllipsoid(
                    impactCenter.add(11.0D, -8.2D, 0.0D),
                    8.4D, 5.2D, 8.4D
            );
        }

        private void enqueueEllipsoid(Vec3 center, double rx, double ry, double rz) {
            int ix = (int) Math.ceil(rx);
            int iy = (int) Math.ceil(ry);
            int iz = (int) Math.ceil(rz);

            int cx = (int) Math.floor(center.x);
            int cy = (int) Math.floor(center.y);
            int cz = (int) Math.floor(center.z);

            for (int x = -ix; x <= ix; x++) {
                double nx = x / rx;
                double nx2 = nx * nx;

                for (int y = -iy; y <= iy; y++) {
                    double ny = y / ry;
                    double nxy = nx2 + ny * ny;
                    if (nxy > 1.0D) {
                        continue;
                    }

                    for (int z = -iz; z <= iz; z++) {
                        double nz = z / rz;
                        if (nxy + nz * nz > 1.0D) {
                            continue;
                        }

                        enqueue(new BlockPos(cx + x, cy + y, cz + z));
                    }
                }
            }
        }

        private void enqueue(BlockPos pos) {
            long key = pos.asLong();
            if (queuedBlocks.add(key)) {
                blockQueue.add(pos);
            }
        }

        private void processBlockBudget(int budget) {
            int destroyed = 0;
            int inspected = 0;
            int inspectLimit = budget * 3;

            while (destroyed < budget
                    && inspected < inspectLimit
                    && !blockQueue.isEmpty()) {

                BlockPos pos = blockQueue.poll();
                queuedBlocks.remove(pos.asLong());
                inspected++;

                if (destroyIfAllowed(pos)) {
                    destroyed++;
                }
            }
        }

        private boolean destroyIfAllowed(BlockPos pos) {
            if (pos.getY() <= level.getMinBuildHeight()) {
                return false;
            }
            if (!level.hasChunkAt(pos)) {
                return false;
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.BEDROCK)) {
                return false;
            }
            if (state.getDestroySpeed(level, pos) < 0.0F) {
                return false;
            }

            return level.destroyBlock(pos, false);
        }

        private void cleanup() {
            if (cleaned) {
                return;
            }
            cleaned = true;

            blockQueue.clear();
            queuedBlocks.clear();

            ServerPlayer player = target();
            if (player != null) {
                player.setDeltaMovement(Vec3.ZERO);
                player.setNoGravity(originalNoGravity);
                player.fallDistance = 0.0F;
                player.hurtMarked = true;
            }

            GokuNetwork.CHANNEL.send(
                    PacketDistributor.DIMENSION.with(level::dimension),
                    new GokuEffectPacket(
                            GokuEffectPacket.GENKI_DAMA_STOP,
                            targetId,
                            0
                    )
            );
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
