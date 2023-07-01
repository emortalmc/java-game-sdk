package dev.emortal.minestom.gamesdk.internal;

import net.minestom.server.event.Event;

/**
 * Called when the amount of running games changes, which happens when a game is added or removed.
 *
 * This exists to allow the Agones game handler to update the allocation status of the game,
 * without the game manager needing to know about its existence, which allows the
 * game manager to work entirely independent of it.
 */
public record GameCountUpdatedEvent(int newCount) implements Event {
}
