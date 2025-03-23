package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.minestom.gamesdk.game.Game;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

final class GamePlayerTracker {

    static void addPlayer(@NotNull Game game, @NotNull Player player) {
        game.getPlayers().add(player);
        game.onPreJoin(player);
    }

    static void removePlayer(@NotNull Game game, @NotNull Player player) {
        game.getPlayers().remove(player);
        game.onLeave(player);

        if (game.getPlayers().isEmpty()) {
            // There is literally no reason why we would ever not want to finish a game that has no players playing it
            game.finish();
        }
    }

    private GamePlayerTracker() {
    }
}
