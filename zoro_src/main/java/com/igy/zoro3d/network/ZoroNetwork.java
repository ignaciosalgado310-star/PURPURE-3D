package com.igy.zoro3d.network;

import com.igy.zoro3d.Zoro3D;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ZoroNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Zoro3D.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(
                nextId++,
                ZoroSyncPacket.class,
                ZoroSyncPacket::encode,
                ZoroSyncPacket::decode,
                ZoroSyncPacket::handle
        );
    }

    public static void sendToAll(ZoroSyncPacket packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    private ZoroNetwork() {}
}
