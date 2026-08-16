package igy.purpure.network;

import igy.purpure.PurpureMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    private static int id = 0;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PurpureMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private ModNetwork() {}
    public static void register() {
        CHANNEL.registerMessage(id++, PurpureEffectPacket.class,
                PurpureEffectPacket::encode, PurpureEffectPacket::decode, PurpureEffectPacket::handle);
    }
}
