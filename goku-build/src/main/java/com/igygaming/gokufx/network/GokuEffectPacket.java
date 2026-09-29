package com.igygaming.gokufx.network;

import com.igygaming.gokufx.client.ClientGokuEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record GokuEffectPacket(byte type, UUID target, int hits) {
    public static final byte KAMEHAMEHA = 1;
    public static final byte GENKI_DAMA = 2;
    public static final byte KAMEHAMEHA_STOP = 3;
    public static final byte GENKI_DAMA_STOP = 4;

    public static void encode(GokuEffectPacket message, FriendlyByteBuf buffer) {
        buffer.writeByte(message.type);
        buffer.writeUUID(message.target);
        buffer.writeVarInt(message.hits);
    }

    public static GokuEffectPacket decode(FriendlyByteBuf buffer) {
        return new GokuEffectPacket(buffer.readByte(), buffer.readUUID(), buffer.readVarInt());
    }

    public static void handle(GokuEffectPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientGokuEffects.accept(message)
        ));
        context.setPacketHandled(true);
    }
}
