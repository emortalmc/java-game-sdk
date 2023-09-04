package dev.emortal.minestom.gamesdk.internal.listener;

import dev.emortal.minestom.gamesdk.game.Game;
import org.jetbrains.annotations.NotNull;

public interface GameUpdateListener {
    @NotNull GameUpdateListener NO_OP = new GameUpdateListener() {
        @Override
        public void onGameAdded(@NotNull Game game) {
        }

        @Override
        public void onGameRemoved(@NotNull Game game) {
        }
    };

    void onGameAdded(@NotNull Game game);

    void onGameRemoved(@NotNull Game game);
}
