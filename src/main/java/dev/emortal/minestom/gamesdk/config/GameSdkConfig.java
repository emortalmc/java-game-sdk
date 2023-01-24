package dev.emortal.minestom.gamesdk.config;

import dev.emortal.minestom.gamesdk.game.Game;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record GameSdkConfig(int maxGames, int minPlayers,
                            @NotNull Function<GameCreationInfo, ? extends Game> gameCreator) {

    @SuppressWarnings("unused")
    public static final class Builder {
        private int maxGames = -1;
        private int minPlayers = -1;
        private Function<GameCreationInfo, ? extends Game> gameSupplier = null;

        public @NotNull Builder maxGames(int maxGames) {
            this.maxGames = maxGames;
            return this;
        }

        public @NotNull Builder minPlayers(int minPlayers) {
            this.minPlayers = minPlayers;
            return this;
        }

        public @NotNull Builder gameSupplier(Function<GameCreationInfo, ? extends Game> gameSupplier) {
            this.gameSupplier = gameSupplier;
            return this;
        }

        public @NotNull GameSdkConfig build() {
            if (this.maxGames == -1) throw new IllegalStateException("maxGames must be set");

            if (this.gameSupplier == null) throw new IllegalStateException("gameCreator must be set");

            return new GameSdkConfig(this.maxGames, this.minPlayers, this.gameSupplier);
        }
    }
}
