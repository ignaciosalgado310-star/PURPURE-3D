package com.igygaming.gokufx.network;

import com.igygaming.gokufx.GokuFxMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GokuNetwork {
    private static final String PROTOCOL = "1";
    private static int id = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GokuFxMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private GokuNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(id++, GokuEffectPacket.class,
                GokuEffectPacket::encode,
                GokuEffectPacket::decode,
                GokuEffectPacket::handle);
    }
}
