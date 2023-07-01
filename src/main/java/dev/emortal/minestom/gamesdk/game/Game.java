package dev.emortal.minestom.gamesdk.game;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import net.kyori.adventure.audience.Audience;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class Game {

    private final GameCreationInfo creationInfo;

    protected final Set<Player> players = Collections.synchronizedSet(new HashSet<>());
    protected final Audience audience = PacketGroupingAudience.of(players);

    protected Game(@NotNull GameCreationInfo creationInfo) {
        this.creationInfo = creationInfo;
    }

    /**
     * Called by the {@link GameManager} when all expected players have connected
     * or when the wait time for players to join has expired and there are enough players.
     */
    public abstract void start();

    /**
     * Called by the game manager to signal to the game that it should clean itself up when it's finished.
     */
    public abstract void cleanUp();

    /**
     * Called when a player logs in.
     *
     * This exists because having the games register their own login listener isn't
     * fast enough for running in production.
     */
    public abstract void onJoin(@NotNull Player player);

    /**
     * Called when a player leaves the game.
     *
     * This allows the game to clean up after a player if they decide to leave mid game.
     */
    public abstract void onLeave(@NotNull Player player);

    public abstract @NotNull Instance getInstance();

    public abstract @NotNull EventNode<Event> getEventNode();

    public final @NotNull GameCreationInfo getCreationInfo() {
        return creationInfo;
    }

    public final @NotNull Set<Player> getPlayers() {
        return players;
    }

    public final @NotNull Audience getAudience() {
        return audience;
    }

    public final void finish() {
        MinecraftServer.getGlobalEventHandler().call(new GameFinishedEvent(this));
    }
}
