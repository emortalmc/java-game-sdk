package dev.emortal.minestom.gamesdk.game;

import com.google.common.collect.Sets;
import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.EmptyStreamObserver;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.GameSdkModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GameManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameManager.class);

    private final @NotNull Set<Game> games = Collections.synchronizedSet(new HashSet<>());
    private final AtomicBoolean shouldAllocate = new AtomicBoolean(false); // If true, it won't be updated

    private final @NotNull GameSdkConfig config;
    private final @NotNull ModuleEnvironment environment;
    private final @Nullable SDKGrpc.SDKStub agonesSdk;

    public GameManager(@NotNull ModuleEnvironment environment, @NotNull GameSdkConfig config) {
        this.config = config;
        this.environment = environment;

        KubernetesModule kubernetesModule = environment.moduleManager().getModule(KubernetesModule.class);
        this.agonesSdk = kubernetesModule.getSdk();

        if (GameSdkModule.TEST_MODE) {
            environment.eventNode().addListener(PlayerLoginEvent.class, event -> {
                System.out.println("A");
                Player player = event.getPlayer();
                player.sendMessage(Component.text("The server is in test mode. Use /gamesdk start to start a game."));

                if (this.games.isEmpty()) {
                    Game game = this.config.gameCreator().apply(new GameCreationInfo(Sets.newHashSet(player.getUuid()), Instant.now()));
                    GameWrapper wrapper = this.registerGame(game);
                    game.load();

                    wrapper.getPreGameNode().call(event);
                } else {
                    Game game = this.games.iterator().next();
                    game.getPlayers().add(player);
                    game.getGameCreationInfo().playerIds().add(player.getUuid());
                }
            });
        }
    }

    public GameWrapper registerGame(@NotNull Game game) {
        boolean added = this.games.add(game);
        if (added) this.updateShouldAllocate();

        return new GameWrapper(this, this.environment.eventNode(), this.config, game);
    }

    /**
     * Tells the GameManager that a game has ended and can be removed from the manager.
     * At this point, the Game and all its Instances should be completely cleaned up.
     *
     * @param game The game that has ended
     */
    public void removeGame(@NotNull Game game) {
        boolean removed = this.games.remove(game);
        if (removed) this.updateShouldAllocate();
    }

    public Optional<Game> findGame(@NotNull Player player) {
        return this.games.stream().filter(game -> game.getPlayers().contains(player)).findFirst();
    }

    private void updateShouldAllocate() {
        if (this.agonesSdk == null) return;

        boolean original = this.shouldAllocate.getAndSet(this.games.size() < this.config.maxGames());
        boolean newValue = this.shouldAllocate.get();
        LOGGER.info("Updating should allocate from {} to {}", original, newValue);

        if (original != newValue) {
            this.agonesSdk.setLabel(AgonesSDKProto.KeyValue.newBuilder()
                    .setKey("should-allocate")
                    .setValue(String.valueOf(!original)).build(), new EmptyStreamObserver<>());
        }
    }
}
