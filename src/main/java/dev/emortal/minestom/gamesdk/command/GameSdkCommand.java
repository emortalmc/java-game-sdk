package dev.emortal.minestom.gamesdk.command;

import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class GameSdkCommand extends Command {

    private final @NotNull GameManager gameManager;

    public GameSdkCommand(@NotNull GameManager gameManager) {
        super("gamesdk");
        this.gameManager = gameManager;

        this.setCondition(this.hasPermission("command.gamesdk"));

        // /gamesdk start
        this.addConditionalSyntax(this.hasPermission("command.gamesdk.start"), this::executeStart, new ArgumentLiteral("start"));
    }

    private @NotNull CommandCondition hasPermission(@NotNull String permission) {
        return (sender, command) -> sender.hasPermission(permission);
    }

    private void executeStart(@NotNull CommandSender sender, @NotNull CommandContext context) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("You must be a player to use this command");
            return;
        }

        Game game = this.gameManager.findGame(player);
        if (game == null) {
            sender.sendMessage("You must be in a game to use this command!");
            return;
        }

        sender.sendMessage("Starting game...");
        game.start();
    }
}
