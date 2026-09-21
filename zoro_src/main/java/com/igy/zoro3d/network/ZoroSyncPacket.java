package com.igy.zoro3d.network;

import com.igy.zoro3d.client.ZoroClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ZoroSyncPacket(
        UUID attackId,
        UUID targetId,
        String dimension,
        int operation,
        int stage,
        int age,
        double x,
        double y,
        double z,
        int usedTotems,
        int requestedTotems
) {
    public static final int OP_UPDATE = 0;
    public static final int OP_END = 1;

    public static void encode(ZoroSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.attackId);
        buf.writeUUID(msg.targetId);
        buf.writeUtf(msg.dimension);
        buf.writeVarInt(msg.operation);
        buf.writeVarInt(msg.stage);
        buf.writeVarInt(msg.age);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeVarInt(msg.usedTotems);
        buf.writeVarInt(msg.requestedTotems);
    }

    public static ZoroSyncPacket decode(FriendlyByteBuf buf) {
        return new ZoroSyncPacket(
                buf.readUUID(),
                buf.readUUID(),
                buf.readUtf(256),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public static void handle(ZoroSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ZoroClientState.handle(msg)
        ));
        ctx.setPacketHandled(true);
    }
}
