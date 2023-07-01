package dev.emortal.minestom.gamesdk.command;

import dev.emortal.minestom.core.utils.command.ExtraConditions;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class GameSdkCommand extends Command {

    private final GameManager gameManager;

    public GameSdkCommand(@NotNull GameManager gameManager) {
        super("gamesdk");
        this.gameManager = gameManager;

        // /gamesdk start
        addConditionalSyntax(ExtraConditions.hasPermission("command.gamesdk.start"), this::executeStart, new ArgumentLiteral("start"));
    }

    private void executeStart(CommandSender sender, CommandContext context) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("You must be a player to use this command");
            return;
        }

        final Game game = gameManager.findGame(player);
        if (game == null) {
            sender.sendMessage("You must be in a game to use this command!");
            return;
        }

        sender.sendMessage("Starting game...");
        game.start();
    }
}
