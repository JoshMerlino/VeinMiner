package wtf.choco.veinminer.data;

import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;

import wtf.choco.veinminer.VeinMinerPlugin;
import wtf.choco.veinminer.network.VeinMinerPlayer;

/**
 * A means of storing persistent {@link VeinMinerPlayer} data.
 */
public interface PersistentDataStorage {

    /**
     * Get the {@link Type} of persistent storage.
     *
     * @return the type
     */
    @NotNull
    public Type getType();

    /**
     * Initialize this persistent storage.
     *
     * @return a {@link CompletableFuture} completed when the initialization has finished
     */
    @NotNull
    public CompletableFuture<Void> init();

    /**
     * Save the data of the given {@link VeinMinerPlayer} to disk.
     * <p>
     * If the VeinMinerPlayer is not dirty (according to {@link VeinMinerPlayer#isDirty()}), this
     * method will complete immediately and not perform a save.
     *
     * @param plugin the plugin instance
     * @param player the player to save
     *
     * @return a {@link CompletableFuture} completed when saving has finished
     */
    @NotNull
    public CompletableFuture<VeinMinerPlayer> save(@NotNull VeinMinerPlugin plugin, @NotNull VeinMinerPlayer player);

    /**
     * Load the data of the given {@link VeinMinerPlayer} from disk.
     *
     * @param plugin the plugin instance
     * @param player the player whose data to load
     *
     * @return a {@link CompletableFuture} completed when loading has finished
     */
    @NotNull
    public CompletableFuture<VeinMinerPlayer> load(@NotNull VeinMinerPlugin plugin, @NotNull VeinMinerPlayer player);

    /**
     * Close this persistent data storage if necessary. Will block the thread on which this
     * method is invoked.
     */
    @Blocking
    public void close();

    /**
     * Represents a support type of persistent storage.
     */
    public enum Type {

        JSON,
        MYSQL,
        SQLITE,
        UNKNOWN;

    }

}