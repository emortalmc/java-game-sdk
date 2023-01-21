package dev.emortal.minestom.gamesdk.game;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public abstract class Game {
    private final @NotNull GameCreationInfo gameCreationInfo;

    protected Game(@NotNull GameCreationInfo creationInfo) {
        this.gameCreationInfo = creationInfo;
    }

    public @NotNull GameCreationInfo getGameCreationInfo() {
        return this.gameCreationInfo;
    }

    public abstract @NotNull Set<UUID> getPlayers();

    /**
     * Tells the Game to cancel itself either before or during the game.
     * This should only be called in an erroneous circumstance.
     * Also Used by the Game SDK:
     * if a player doesn't connect in time.
     */
    // todo send players to lobby on call
    public abstract void cancel();

    /**
     * Called by the {@link GameManager} when all expected players have connected.
     * If this is not called, the game can follow its normal starting pattern but
     * must still abide by the {@link #cancel()} method if called.
     */
    public abstract void fastStart();

    public abstract void load();
}
