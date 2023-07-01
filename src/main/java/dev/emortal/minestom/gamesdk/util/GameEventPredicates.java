package dev.emortal.minestom.gamesdk.util;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import java.util.function.Predicate;
import net.minestom.server.event.Event;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

public final class GameEventPredicates {

    public static @NotNull Predicate<Event> inGame(@NotNull GameCreationInfo creationInfo) {
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
    public static @NotNull Predicate<Event> inGameAndInstance(@NotNull GameCreationInfo creationInfo, @NotNull Instance instance) {
        return event -> {
            if (event instanceof PlayerEvent playerEvent) {
                return creationInfo.playerIds().contains(playerEvent.getPlayer().getUuid());
            }
            if (event instanceof InstanceEvent instanceEvent) {
                return instanceEvent.getInstance() == instance;
            }
            return true;
        };
    }

    private GameEventPredicates() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
