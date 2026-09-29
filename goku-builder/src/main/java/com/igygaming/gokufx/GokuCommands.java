package com.igygaming.gokufx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GokuCommands {
    private GokuCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerKamehameha(dispatcher, "kamehameha");
        registerKamehameha(dispatcher, "kame");
        registerGenki(dispatcher, "genkidama");
        registerGenki(dispatcher, "genki");

        dispatcher.register(Commands.literal("gokufx")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§bGOKU FX §7| §f/kamehameha <jugador> <totems> §7| §f/genkidama <jugador> <totems>"), false);
                    return 1;
                }));
    }

    private static void registerKamehameha(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(Commands.literal(literal)
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> executeSelfKame(ctx.getSource(), AttackManager.DEFAULT_KAME_TOTEMS))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
                            return launchKame(ctx.getSource(), player, AttackManager.DEFAULT_KAME_TOTEMS);
                        })
                        .then(Commands.argument("totems", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
                                    int totems = IntegerArgumentType.getInteger(ctx, "totems");
                                    return launchKame(ctx.getSource(), player, totems);
                                }))));
    }

    private static void registerGenki(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(Commands.literal(literal)
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> executeSelfGenki(ctx.getSource(), AttackManager.DEFAULT_GENKI_TOTEMS))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
                            return launchGenki(ctx.getSource(), player, AttackManager.DEFAULT_GENKI_TOTEMS);
                        })
                        .then(Commands.argument("totems", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
                                    int totems = IntegerArgumentType.getInteger(ctx, "totems");
                                    return launchGenki(ctx.getSource(), player, totems);
                                }))));
    }

    private static int launchKame(CommandSourceStack source, ServerPlayer player, int totems) {
        AttackManager.startKamehameha(player, totems);
        source.sendSuccess(() -> Component.literal(
                "§1§lKAMEHAMEHA 3D §7• §b" + player.getGameProfile().getName()
                        + " §7• §f" + totems + " tótems"), true);
        return 1;
    }

    private static int launchGenki(CommandSourceStack source, ServerPlayer player, int totems) {
        int queuePosition = GenkiManager.start(player, totems);

        if (queuePosition < 0) {
            source.sendFailure(Component.literal(
                    "§cGENKI DAMA §7• §fLa cola de " + player.getGameProfile().getName()
                            + " está llena. Espera a que termine una activación."));
            return 0;
        }

        if (queuePosition == 0) {
            source.sendSuccess(() -> Component.literal(
                    "§b§lGENKI DAMA 3D §7• §f" + player.getGameProfile().getName()
                            + " §7• §b" + totems + " tótems §7• §aINICIADA"), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "§b§lGENKI DAMA 3D §7• §f" + player.getGameProfile().getName()
                            + " §7• §b" + totems + " tótems §7• §eEN COLA #" + queuePosition), true);
        }
        return 1;
    }

    private static int executeSelfKame(CommandSourceStack source, int totems) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return launchKame(source, player, totems);
    }

    private static int executeSelfGenki(CommandSourceStack source, int totems) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return launchGenki(source, player, totems);
    }
}
