package dev.emortal.minestom.gamesdk.internal.listener;

import dev.emortal.minestom.gamesdk.game.Game;
import org.jetbrains.annotations.NotNull;

public interface GameStatusListener {

    default void onGameAdded(@NotNull Game game) {
    }

    default void onGameStart(@NotNull Game game) {
    }

    default void onGameFinish(@NotNull Game game) {
    }

    default void onGameRemoved(@NotNull Game game) {
    }
}
