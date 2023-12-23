package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.api.model.matchmaker.Match;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.game.Game;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/*
 * When we are in test mode, we only create and use a single game, and we recreate that game
 * when a player joins. The recreation makes it possible to get a fresh game without having
 * to restart the server test server.
 *
 * This is a separate class because we can't reassign the game with lambdas.
 */
final class TestGameHandler {

    private final @NotNull GameManager gameManager;

    private @Nullable GameHolder holder;

    TestGameHandler(@NotNull GameManager gameManager) {
        this.gameManager = gameManager;

        EventNode<Event> eventNode = GameEventNodes.GAME_MANAGER;
        eventNode.addListener(PlayerLoginEvent.class, this::onJoin);
        eventNode.addListener(PlayerDisconnectEvent.class, this::onLeave);
    }

    private void onJoin(@NotNull PlayerLoginEvent event) {
        if (this.holder == null) {
            this.holder = new GameHolder(this.gameManager);
        }

        this.holder.onJoin(event);
    }

    private void onLeave(@NotNull PlayerDisconnectEvent event) {
        if (this.holder == null) return;

        this.holder.onLeave(event.getPlayer());
        if (this.holder.shouldReset()) {
            // Reset the game when everyone has left
            this.holder = null;
        }
    }

    private static final class GameHolder {

        private final @NotNull Game game;

        private final @NotNull Set<UUID> players = new HashSet<>();

        GameHolder(@NotNull GameManager gameManager) {
            GameCreationInfo creationInfo = new GameCreationInfo(Match.getDefaultInstance(), Collections.unmodifiableSet(this.players));
            this.game = gameManager.createGame(creationInfo);
        }

        void onJoin(@NotNull PlayerLoginEvent event) {
            Player player = event.getPlayer();
            player.sendMessage(Component.text("The server is in test mode. Use /gamesdk start to start a game."));

            this.players.add(player.getUuid());
            GamePlayerTracker.addPlayer(this.game, player);

            event.setSpawningInstance(this.game.getSpawningInstance(player));
        }

        void onLeave(@NotNull Player player) {
            this.players.remove(player.getUuid());
            GamePlayerTracker.removePlayer(this.game, player);
        }

        boolean shouldReset() {
            return this.players.isEmpty();
        }
    }
}
