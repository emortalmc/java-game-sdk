package dev.emortal.minestom.gamesdk.config;

import dev.emortal.minestom.gamesdk.game.GameCreator;
import org.jetbrains.annotations.NotNull;

/**
 * The configuration that the game manager will use to create and manage games.
 *
 * @param maxGames the maximum games that may run on the server at one time
 * @param minPlayers the minimum players required for a game to start
 * @param gameCreator a function that can be called to create a game instance
 */
public record GameSdkConfig(int maxGames, int minPlayers, @NotNull GameCreator gameCreator) {

    public static @NotNull Builder builder() {
        return new BuilderImpl();
    }

    public interface Builder {

        @NotNull MaxGamesStep minPlayers(int minPlayers);

        interface MaxGamesStep {

            @NotNull GameCreatorStep maxGames(int maxGames);
        }

        interface GameCreatorStep {

            @NotNull EndStep gameCreator(@NotNull GameCreator creator);
        }

        interface EndStep {

            @NotNull GameSdkConfig build();
        }
    }

    private static final class BuilderImpl implements Builder, Builder.MaxGamesStep, Builder.GameCreatorStep, Builder.EndStep {

        private int maxGames;
        private int minPlayers;
        private GameCreator gameCreator;

        @Override
        public @NotNull MaxGamesStep minPlayers(int minPlayers) {
            this.minPlayers = minPlayers;
            return this;
        }

        @Override
        public @NotNull GameCreatorStep maxGames(int maxGames) {
            this.maxGames = maxGames;
            return this;
        }

        @Override
        public @NotNull EndStep gameCreator(@NotNull GameCreator creator) {
            this.gameCreator = creator;
            return this;
        }

        @Override
        public @NotNull GameSdkConfig build() {
            return new GameSdkConfig(this.maxGames, this.minPlayers, this.gameCreator);
        }
    }
}
