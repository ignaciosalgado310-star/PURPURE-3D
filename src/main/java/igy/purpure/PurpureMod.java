package igy.purpure;

import igy.purpure.network.ModNetwork;
import net.minecraftforge.fml.common.Mod;

@Mod(PurpureMod.MODID)
public final class PurpureMod {
    public static final String MODID = "purpure";
    public PurpureMod() { ModNetwork.register(); }
}
