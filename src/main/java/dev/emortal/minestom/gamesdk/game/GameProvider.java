package dev.emortal.minestom.gamesdk.game;

import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GameProvider {

    @Nullable Game findGame(@NotNull Player player);
}
