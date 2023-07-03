package dev.emortal.minestom.gamesdk.config;

import dev.emortal.minestom.gamesdk.game.GameCreator;
import org.jetbrains.annotations.NotNull;

public record GameSdkConfig(int maxGames, int minPlayers, @NotNull GameCreator gameCreator) {

    public static @NotNull Builder builder() {
        return new BuilderImpl();
    }

    public interface Builder {

        @NotNull Builder minPlayers(int minPlayers);

        @NotNull GameCreatorStep maxGames(int maxGames);

        interface GameCreatorStep {

            @NotNull EndStep gameCreator(@NotNull GameCreator creator);
        }

        interface EndStep {

            @NotNull GameSdkConfig build();
        }
    }

    private static final class BuilderImpl implements Builder, Builder.GameCreatorStep, Builder.EndStep {

        private int maxGames;
        private int minPlayers;
        private GameCreator gameCreator;

        @Override
        public @NotNull Builder minPlayers(int minPlayers) {
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
