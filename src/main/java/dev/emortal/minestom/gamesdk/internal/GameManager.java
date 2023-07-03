package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.api.kurushimi.KurushimiMinestomUtils;
import dev.emortal.minestom.gamesdk.MinestomGameServer;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.GameFinishedEvent;
import dev.emortal.minestom.gamesdk.internal.listener.GameUpdateListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GameManager {

    private final GameSdkConfig config;
    private final GameUpdateListener updateListener;

    // The event node used by the game manager to listen for events.
    private final EventNode<Event> eventNode = EventNode.all("game-manager");
    // The event node that is the parent of all the game event nodes, for an easy tree view when looking for game nodes.
    private final EventNode<Event> gamesEventNode = EventNode.all("games");

    private final Set<Game> games = Collections.synchronizedSet(new HashSet<>());

    public GameManager(@NotNull GameSdkConfig config, @NotNull GameUpdateListener updateListener) {
        this.config = config;
        this.updateListener = updateListener;

        MinecraftServer.getGlobalEventHandler().addChild(this.eventNode);
        MinecraftServer.getGlobalEventHandler().addChild(this.gamesEventNode);

        if (MinestomGameServer.TEST_MODE) {
            this.initTestMode();
        } else {
            this.initProductionMode();
        }

        this.eventNode.addListener(GameFinishedEvent.class, this::onGameFinish);
    }

    private void initTestMode() {
        new TestGameHandler(this, this.eventNode);
    }

    private void initProductionMode() {
        this.eventNode.addListener(PlayerLoginEvent.class, event -> {
            // Wait for games to be ready before allowing players to join
            while (this.games.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            }

            Player player = event.getPlayer();
            for (var game : this.games) {
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
        final Game game = this.config.gameCreator().createGame(creationInfo);
        this.gamesEventNode.addChild(game.getEventNode());
        this.registerGame(game);
        return game;
    }

    private void registerGame(@NotNull Game game) {
        boolean added = this.games.add(game);
        if (added) this.updateListener.onGameAdded(game);
    }

    void removeGame(@NotNull Game game) {
        boolean removed = this.games.remove(game);
        if (removed) this.updateListener.onGameRemoved(game);
    }

    private void onGameFinish(@NotNull GameFinishedEvent event) {
        Game game = event.game();
        this.removeGame(game);
        KurushimiMinestomUtils.sendToLobby(game.getPlayers(), () -> this.cleanUpGame(game), () -> this.cleanUpGame(game));
    }

    void cleanUpGame(@NotNull Game game) {
        for (var player : game.getPlayers()) {
            player.kick(Component.text("The game ended but we weren't able to connect you to a lobby. Please reconnect.", NamedTextColor.RED));
        }
        game.cleanUp();
    }

    public @Nullable Game findGame(@NotNull Player player) {
        for (var game : this.games) {
            if (game.getPlayers().contains(player)) return game;
        }
        return null;
    }
}
