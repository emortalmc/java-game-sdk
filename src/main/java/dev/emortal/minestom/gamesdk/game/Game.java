package dev.emortal.minestom.gamesdk.game;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import net.kyori.adventure.audience.Audience;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class Game {
    private final @NotNull GameCreationInfo gameCreationInfo;
    protected final @NotNull Set<@NotNull Player> players = Collections.synchronizedSet(new HashSet<>());
    protected final Audience audience = Audience.audience(this.players);

    protected Game(@NotNull GameCreationInfo creationInfo) {
        this.gameCreationInfo = creationInfo;
    }

    public @NotNull GameCreationInfo getGameCreationInfo() {
        return this.gameCreationInfo;
    }

    public @NotNull Set<@NotNull Player> getPlayers() {
        return this.players;
    }

    public Audience getAudience() {
        return audience;
    }

    public abstract void load();

    /**
     * Called by the {@link GameManager} when all expected players have connected.
     * If this is not called, the game can follow its normal starting pattern but
     * must still abide by the {@link #cancel()} method if called.
     */
    public abstract void fastStart();

    /**
     * Tells the Game to cancel itself either before or during the game.
     * This should only be called in an erroneous circumstance.
     * Also Used by the Game SDK:
     * if a player doesn't connect in time.
     */
    // todo send players to lobby on call
    public abstract void cancel();
}
