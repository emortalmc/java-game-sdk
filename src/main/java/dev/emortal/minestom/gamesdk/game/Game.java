package dev.emortal.minestom.gamesdk.game;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import net.kyori.adventure.audience.Audience;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class Game {
    private final @NotNull GameCreationInfo gameCreationInfo;
    protected final @NotNull Set<@NotNull Player> players = Collections.synchronizedSet(new HashSet<>());
    protected final @NotNull Audience audience = Audience.audience(this.players);

    protected Game(@NotNull GameCreationInfo creationInfo) {
        this.gameCreationInfo = creationInfo;
    }

    public @NotNull GameCreationInfo getGameCreationInfo() {
        return this.gameCreationInfo;
    }

    public @NotNull Set<@NotNull Player> getPlayers() {
        return this.players;
    }

    public @NotNull Audience getAudience() {
        return this.audience;
    }

    /**
     * Called when a player logs in.
     *
     * <p>This exists because having the games register their own login listener isn't
     * fast enough for running in production.</p>
     *
     * @param event the login event
     */
    public abstract void onPlayerLogin(final @NotNull PlayerLoginEvent event);

    public abstract void load();

    /**
     * Called by the {@link GameManager} when all expected players have connected
     * or when the wait time for players to join has expired and there are enough players.
     * Either this or {@link #cancel()} will be called.
     */
    public abstract void start();

    /**
     * Tells the Game to cancel itself either before or during the game.
     * This should only be called in an erroneous circumstance.
     * Also Used by the Game SDK:
     * - if a player doesn't connect in time.
     */
    // todo send players to lobby on call
    public abstract void cancel();
}
