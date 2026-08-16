package igy.purpure.network;

import igy.purpure.client.ClientPurpureEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record PurpureEffectPacket(byte mode, UUID target, String dimension, double x, double y, double z, int hits, long seed) {
    public static final byte START=0, EXTEND=1, STOP=2;
    public static void encode(PurpureEffectPacket m, FriendlyByteBuf b) {
        b.writeByte(m.mode); b.writeUUID(m.target); b.writeUtf(m.dimension,128);
        b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeVarInt(m.hits); b.writeLong(m.seed);
    }
    public static PurpureEffectPacket decode(FriendlyByteBuf b) {
        return new PurpureEffectPacket(b.readByte(),b.readUUID(),b.readUtf(128),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readLong());
    }
    public static void handle(PurpureEffectPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context c=s.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPurpureEffects.accept(m)));
        c.setPacketHandled(true);
    }
}
