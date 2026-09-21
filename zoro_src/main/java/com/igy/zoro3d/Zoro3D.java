package com.igy.zoro3d;

import com.igy.zoro3d.network.ZoroNetwork;
import net.minecraftforge.fml.common.Mod;

@Mod(Zoro3D.MOD_ID)
public final class Zoro3D {
    public static final String MOD_ID = "zoro3d";

    public Zoro3D() {
        ZoroNetwork.register();
    }
}
