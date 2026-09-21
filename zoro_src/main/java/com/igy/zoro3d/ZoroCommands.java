package com.igy.zoro3d;

import com.igy.zoro3d.server.ZoroManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Zoro3D.MOD_ID)
public final class ZoroCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("zoro")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            boolean cancelled = ZoroManager.cancel(target.getUUID());
                                            ctx.getSource().sendSuccess(() -> Component.literal(cancelled
                                                    ? "Ataque ZORO cancelado para " + target.getGameProfile().getName()
                                                    : "Ese jugador no tiene un ataque ZORO activo."), false);
                                            return cancelled ? 1 : 0;
                                        })))
                        .then(Commands.literal("cancelall")
                                .executes(ctx -> {
                                    int count = ZoroManager.cancelAll();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Ataques ZORO cancelados: " + count), true);
                                    return count;
                                }))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("totems", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            int totems = IntegerArgumentType.getInteger(ctx, "totems");
                                            ZoroManager.start(target, totems);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "ZORO iniciado contra " + target.getGameProfile().getName()
                                                            + " con " + totems + " totems."), true);
                                            return 1;
                                        })))
        );
    }

    private ZoroCommands() {}
}
