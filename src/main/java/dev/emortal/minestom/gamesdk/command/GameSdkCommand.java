package dev.emortal.minestom.gamesdk.command;

import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.game.GameManager;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class GameSdkCommand extends Command {
    private final @NotNull GameManager gameManager;

    public GameSdkCommand(@NotNull GameManager gameManager) {
        super("gamesdk");
        this.gameManager = gameManager;

        this.setCondition(this.hasPermission(null));

        ArgumentLiteral start = new ArgumentLiteral("start");
        this.addConditionalSyntax(
                (sender, commandString) -> this.hasPermission("start").canUse(sender, commandString),
                this::executeStart,
                start
        );
    }

    private void executeStart(CommandSender sender, CommandContext context) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("You must be a player to use this command");
            return;
        }

        Optional<Game> optionalGame = this.gameManager.findGame(player);
        if (optionalGame.isEmpty()) {
            sender.sendMessage("You must be in a game to use this command");
            return;
        }

        Game game = optionalGame.get();
        game.start();
    }

    private CommandCondition hasPermission(@Nullable String subCommand) {
        return (sender, commandString) -> {
            if (!sender.hasPermission("command.gamesdk")) return false;
            if (subCommand == null) return true;
            return sender.hasPermission("command.gamesdk." + subCommand);
        };
    }
}
