package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.api.kurushimi.KurushimiMinestomUtils;
import dev.emortal.minestom.gamesdk.GameSdkModule;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.GameFinishedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GameManager {

    private final GameSdkConfig config;
    // The event node used by the game manager to listen for events.
    private final EventNode<Event> eventNode = EventNode.all("game-manager");
    // The event node that is the parent of all the game event nodes, for an easy tree view when looking for game nodes.
    private final EventNode<Event> gamesEventNode = EventNode.all("games");

    private final Set<Game> games = Collections.synchronizedSet(new HashSet<>());

    public GameManager(@NotNull GameSdkConfig config) {
        this.config = config;

        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        MinecraftServer.getGlobalEventHandler().addChild(gamesEventNode);

        if (GameSdkModule.TEST_MODE) {
            initTestMode();
        } else {
            initProductionMode();
        }

        eventNode.addListener(GameFinishedEvent.class, this::onGameFinish);
    }

    private void initTestMode() {
        // When we are in test mode, we only create and use a single game, and the creation info won't be passed on from Agones, so we have
        // to manually create it and populate it with bogus defaults.

        final var creationInfo = new GameCreationInfo(null, "unknown", new HashSet<>(), Instant.now());
        final Game game = createGame(creationInfo);

        eventNode.addListener(PlayerLoginEvent.class, event -> {
            final Player player = event.getPlayer();
            player.sendMessage(Component.text("The server is in test mode. Use /gamesdk start to start a game."));

            game.getCreationInfo().playerIds().add(player.getUuid());
            game.onJoin(player);
            game.getPlayers().add(player);
            event.setSpawningInstance(game.getInstance());
        });
    }

    private void initProductionMode() {
        eventNode.addListener(PlayerLoginEvent.class, event -> {
            // Wait for games to be ready before allowing players to join
            while (games.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException exception) {
                    exception.printStackTrace();
                }
            }

            final Player player = event.getPlayer();
            for (final Game game : games) {
                if (!game.getCreationInfo().playerIds().contains(player.getUuid())) {
                    // The player is not for this game
                    continue;
                }

                game.onJoin(player);
                game.getPlayers().add(player);
                event.setSpawningInstance(game.getInstance());
                break;
            }
        });
    }

    @NotNull Game createGame(@NotNull GameCreationInfo creationInfo) {
        final Game game = config.gameCreator().createGame(creationInfo);
        gamesEventNode.addChild(game.getEventNode());
        registerGame(game);
        return game;
    }

    private void registerGame(@NotNull Game game) {
        final boolean added = games.add(game);
        if (added) updateGameCount();
    }

    private void removeGame(@NotNull Game game) {
        final boolean removed = games.remove(game);
        if (removed) updateGameCount();
    }

    private void updateGameCount() {
        MinecraftServer.getGlobalEventHandler().call(new GameCountUpdatedEvent(games.size()));
    }

    private void onGameFinish(@NotNull GameFinishedEvent event) {
        final Game game = event.game();
        removeGame(game);
        KurushimiMinestomUtils.sendToLobby(game.getPlayers(), () -> cleanUpGame(game), () -> cleanUpGame(game));
    }

    private void cleanUpGame(@NotNull Game game) {
        for (final Player player : game.getPlayers()) {
            player.kick(Component.text("The game ended but we weren't able to connect you to a lobby. Please reconnect.", NamedTextColor.RED));
        }
        game.cleanUp();
    }

    public @Nullable Game findGame(@NotNull Player player) {
        for (final Game game : games) {
            if (game.getPlayers().contains(player)) return game;
        }
        return null;
    }
}
