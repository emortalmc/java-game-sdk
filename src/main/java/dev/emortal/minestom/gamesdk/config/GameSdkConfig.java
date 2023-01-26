package dev.emortal.minestom.gamesdk.config;

import dev.emortal.minestom.gamesdk.game.Game;
import java.util.function.BiFunction;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

public record GameSdkConfig(int maxGames, int minPlayers,
                            @NotNull BiFunction<GameCreationInfo, EventNode<Event>, ? extends Game> gameCreator) {

    @SuppressWarnings("unused")
    public static final class Builder {
        private int maxGames = -1;
        private int minPlayers = -1;
        private BiFunction<GameCreationInfo, EventNode<Event>, ? extends Game> gameSupplier = null;

        public @NotNull Builder maxGames(int maxGames) {
            this.maxGames = maxGames;
            return this;
        }

        public @NotNull Builder minPlayers(int minPlayers) {
            this.minPlayers = minPlayers;
            return this;
        }

        public @NotNull Builder gameSupplier(BiFunction<GameCreationInfo, EventNode<Event>, ? extends Game> gameSupplier) {
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
