package dev.emortal.minestom.gamesdk.game;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import dev.emortal.minestom.gamesdk.util.GameEventPredicates;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Game implements PacketGroupingAudience, TrackableGame {

    private final @NotNull GameCreationInfo creationInfo;
    private final @NotNull EventNode<Event> eventNode;

    private final Set<Player> players = Collections.synchronizedSet(new HashSet<>());

    private final @NotNull AtomicBoolean gameTrackerUpdateQueued = new AtomicBoolean(false);
    private long lastGameTrackerUpdate = 0L;

    protected Game(@NotNull GameCreationInfo creationInfo) {
        this.creationInfo = creationInfo;
        this.eventNode = this.createEventNode();
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
     * <p>
     * This is called <b>after</b> the player has been added to the players list.
     */
    public abstract void onJoin(@NotNull Player player);

    /**
     * Called when a player leaves the game.
     * <p>
     * This allows the game to clean up after a player if they decide to leave mid-game.
     *
     * <p>
     * This is called <b>after</b> the player has been removed from the players list.
     */
    public abstract void onLeave(@NotNull Player player);

    public abstract @NotNull Instance getSpawningInstance(@NotNull Player player);

    public final @NotNull EventNode<Event> getEventNode() {
        return this.eventNode;
    }

    protected @NotNull EventNode<Event> createEventNode() {
        return EventNode.event(UUID.randomUUID().toString(), EventFilter.ALL, GameEventPredicates.inGame(this));
    }

    public final @NotNull GameCreationInfo getCreationInfo() {
        return this.creationInfo;
    }

    /**
     * WARNING: This set is modifiable, but MUST NOT be modified by the game.
     */
    @Override
    public final @NotNull Set<Player> getPlayers() {
        return this.players;
    }

    public final void finish() {
        MinecraftServer.getGlobalEventHandler().call(new GameFinishedEvent(this));
    }

    public void markTrackerUpdated() {
        this.lastGameTrackerUpdate = System.currentTimeMillis();
        this.gameTrackerUpdateQueued.set(false);
    }

    /**
     * Marks the game as pending an update from the tracker - it may be in progress.
     * An update should not be triggered whilst this is false, so it should always be called before updating and
     * {@link #markTrackerUpdated()} should be called after updating.
     * <p>
     * After this operation, the flag is guaranteed to be true.
     *
     * @return the initial value of the flag
     */
    public boolean markTrackerUpdateQueued() {
        return this.gameTrackerUpdateQueued.getAndSet(true);
    }

    public long getLastGameTrackerUpdate() {
        return this.lastGameTrackerUpdate;
    }
}
