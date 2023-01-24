package dev.emortal.minestom.gamesdk.command;

import dev.emortal.minestom.gamesdk.GameSdkModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.game.GameManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameSdkCommand extends Command {
    private final @NotNull GameManager gameManager;
    private final @NotNull GameSdkConfig config;

    public GameSdkCommand(@NotNull GameManager gameManager, @NotNull GameSdkConfig config) {
        super("gamesdk");

        this.gameManager = gameManager;
        this.config = config;

        this.setCondition(this.hasPermission(null));

        ArgumentLiteral create = new ArgumentLiteral("create");
        ArgumentLiteral start = new ArgumentLiteral("start");

        this.addConditionalSyntax(
                (sender, commandString) -> GameSdkModule.TEST_MODE || this.hasPermission("create").canUse(sender, commandString),
                this::executeCreate,
                create
        );

        this.addConditionalSyntax(
                (sender, commandString) -> this.hasPermission("start").canUse(sender, commandString),
                this::executeStart,
                start
        );
    }

    private void executeCreate(CommandSender sender, CommandContext context) {
        Set<UUID> playerIds = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .map(Player::getUuid)
                .collect(Collectors.toSet());

        Game game = this.config.gameCreator().apply(new GameCreationInfo(playerIds, Instant.now()));
        this.gameManager.registerGame(game);

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            PlayerLoginEvent event = new PlayerLoginEvent(player);
            MinecraftServer.getGlobalEventHandler().callCancellable(event, () -> {
                if (event.getSpawningInstance() == null) throw new IllegalStateException("Spawning instance is null");
                player.setInstance(event.getSpawningInstance(), event.getPlayer().getRespawnPoint());
            });
        }
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
