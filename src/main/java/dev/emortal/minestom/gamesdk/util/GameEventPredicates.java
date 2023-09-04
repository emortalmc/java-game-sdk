package dev.emortal.minestom.gamesdk.util;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.emortal.minestom.gamesdk.game.Game;
import net.minestom.server.event.Event;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

public final class GameEventPredicates {

    public static @NotNull Predicate<Event> inCreationInfo(@NotNull GameCreationInfo creationInfo) {
        return event -> {
            if (event instanceof PlayerEvent playerEvent) {
                return creationInfo.playerIds().contains(playerEvent.getPlayer().getUuid());
            }
            return true;
        };
    }

    /**
     * This is for use in game servers, not really in here, which is why it isn't used.
     *
     * This is a very common condition for game servers' event nodes.
     */
    public static @NotNull Predicate<Event> inGame(@NotNull Game game) {
        return event -> {
            if (event instanceof PlayerEvent playerEvent) {
                return game.getPlayers().contains(playerEvent.getPlayer());
            }
            if (event instanceof InstanceEvent instanceEvent) {
                return instanceEvent.getInstance() == game.getSpawningInstance();
            }
            return true;
        };
    }

    private GameEventPredicates() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
