package dev.emortal.minestom.gamesdk.game;

import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface GameCreator {

    @NotNull Game createGame(@NotNull GameCreationInfo info);
}
