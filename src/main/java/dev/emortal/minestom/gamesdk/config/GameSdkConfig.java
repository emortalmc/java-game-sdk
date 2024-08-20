package dev.emortal.minestom.gamesdk.config;

import dev.emortal.minestom.gamesdk.game.GameCreator;
import dev.emortal.minestom.gamesdk.internal.GameTracker;
import org.jetbrains.annotations.NotNull;

/**
 * The configuration that the game manager will use to create and manage games.
 *
 * @param minPlayers  the minimum players required for a game to start
 * @param gameCreator a function that can be called to create a game instance
 */
public record GameSdkConfig(int minPlayers, int minTrackingInterval, int maxTrackingInterval,
                            FinishBehaviour finishBehaviour, @NotNull GameCreator gameCreator) {

    public static @NotNull Builder builder() {
        return new BuilderImpl();
    }

    public interface Builder {

        @NotNull GameCreatorStep minPlayers(int minPlayers);

        interface GameCreatorStep {

            @NotNull GameCreatorStep minTrackingInterval(int interval);

            @NotNull GameCreatorStep maxTrackingInterval(int interval);

            @NotNull GameCreatorStep finishBehaviour(FinishBehaviour finishBehaviour);

            @NotNull EndStep gameCreator(@NotNull GameCreator creator);
        }

        interface EndStep {

            @NotNull GameSdkConfig build();
        }
    }

    private static final class BuilderImpl implements Builder, Builder.GameCreatorStep, Builder.EndStep {

        private int minPlayers;
        private int minTrackingInterval = GameTracker.DEFAULT_MIN_UPDATE_INTERVAL;
        private int maxTrackingInterval = GameTracker.DEFAULT_MAX_UPDATE_INTERVAL;
        private FinishBehaviour finishBehaviour = FinishBehaviour.LOBBY;
        private GameCreator gameCreator;

        @Override
        public @NotNull GameCreatorStep minPlayers(int minPlayers) {
            this.minPlayers = minPlayers;
            return this;
        }

        @Override
        public @NotNull GameCreatorStep minTrackingInterval(int interval) {
            this.minTrackingInterval = interval;
            return this;
        }

        @Override
        public @NotNull GameCreatorStep maxTrackingInterval(int interval) {
            this.maxTrackingInterval = interval;
            return this;
        }

        @Override
        public @NotNull GameCreatorStep finishBehaviour(FinishBehaviour finishBehaviour) {
            this.finishBehaviour = finishBehaviour;
            return this;
        }

        @Override
        public @NotNull EndStep gameCreator(@NotNull GameCreator creator) {
            this.gameCreator = creator;
            return this;
        }

        @Override
        public @NotNull GameSdkConfig build() {
            return new GameSdkConfig(this.minPlayers, this.minTrackingInterval, this.maxTrackingInterval,
                    this.finishBehaviour, this.gameCreator);
        }
    }

    public enum FinishBehaviour {
        LOBBY, REQUEUE
    }
}
