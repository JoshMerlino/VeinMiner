package wtf.choco.veinminer.data;

import com.google.common.base.Enums;
import com.google.common.collect.Collections2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import wtf.choco.veinminer.ActivationStrategy;
import wtf.choco.veinminer.VeinMiner;
import wtf.choco.veinminer.VeinMinerPlugin;
import wtf.choco.veinminer.network.VeinMinerPlayer;
import wtf.choco.veinminer.pattern.VeinMiningPattern;
import wtf.choco.veinminer.tool.VeinMinerToolCategory;

/**
 * An implementation of {@link PersistentDataStorage} for SQLite databases.
 */
public final class PersistentDataStorageSQLite implements PersistentDataStorage {

    // Create tables

    private static final String CREATE_TABLE_PLAYERS = """
            CREATE TABLE IF NOT EXISTS player_data (
                player_uuid              TEXT PRIMARY KEY,
                activation_strategy_id   TEXT,
                disabled_categories      TEXT,
                vein_mining_pattern_id   TEXT
            )
            """;

    private static final String INSERT_PLAYER_DATA = """
            INSERT INTO player_data VALUES (?, ?, ?, ?)
                ON CONFLICT (player_uuid) DO
                UPDATE SET
                    activation_strategy_id = excluded.activation_strategy_id,
                    disabled_categories = excluded.disabled_categories,
                    vein_mining_pattern_id = excluded.vein_mining_pattern_id
            """;

    private static final String SELECT_PLAYER_DATA = """
            SELECT * FROM player_data WHERE player_uuid = ?
            """;

    private final Thread connectionThread;
    private final Lock lock = new ReentrantLock();

    private final String connectionURL;
    private final Queue<Consumer<Connection>> connectionQueue = new ConcurrentLinkedDeque<>();

    /**
     * Construct a new {@link PersistentDataStorageSQLite}.
     *
     * @param plugin the plugin instance
     * @param fileName the name of the file at which the database is located
     *
     * @throws IOException if an exception occurs while creating the database file (if it was
     * not already created)
     */
    public PersistentDataStorageSQLite(@NotNull VeinMinerPlugin plugin, @NotNull String fileName) throws IOException {
        Path databaseFilePath = plugin.getDataFolder().toPath().resolve(fileName);
        if (Files.notExists(databaseFilePath)) {
            try {
                Files.createFile(databaseFilePath);
            } catch (IOException e) {
                throw e;
            }
        }

        this.connectionURL = "jdbc:sqlite:plugins/" + plugin.getDataFolder().getName() + "/" + fileName;

        this.connectionThread = new Thread(new SQLConnectionRunnable());
        this.connectionThread.start();
    }

    @NotNull
    @Override
    public Type getType() {
        return Type.SQLITE;
    }

    @NotNull
    @Override
    public CompletableFuture<Void> init() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        this.connectionQueue.add(connection -> {
            try {
                connection.createStatement().execute(CREATE_TABLE_PLAYERS);
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @NotNull
    @Override
    public CompletableFuture<VeinMinerPlayer> save(@NotNull VeinMinerPlugin plugin, @NotNull VeinMinerPlayer player) {
        if (!player.isDirty()) {
            return CompletableFuture.completedFuture(player);
        }

        CompletableFuture<VeinMinerPlayer> future = new CompletableFuture<>();

        this.connectionQueue.add(connection -> {
            try {
                // Insert player
                PreparedStatement statement = connection.prepareStatement(INSERT_PLAYER_DATA);
                statement.setString(1, player.getPlayerUUID().toString());
                statement.setString(2, player.getActivationStrategy().name());
                Set<VeinMinerToolCategory> disabledCategories = player.getDisabledCategories();
                statement.setString(3, disabledCategories.isEmpty() ? null : String.join(",", Collections2.transform(disabledCategories, VeinMinerToolCategory::getId)));
                statement.setString(4, player.getVeinMiningPattern().getKey().toString());
                statement.execute();

                future.complete(player);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @NotNull
    @Override
    public CompletableFuture<VeinMinerPlayer> load(@NotNull VeinMinerPlugin plugin, @NotNull VeinMinerPlayer player) {
        CompletableFuture<VeinMinerPlayer> future = new CompletableFuture<>();

        this.connectionQueue.add(connection -> {
            try {
                PreparedStatement statement = connection.prepareStatement(SELECT_PLAYER_DATA);
                statement.setString(1, player.getPlayerUUID().toString());

                ResultSet result = statement.executeQuery();

                if (result.next()) {
                    String activationStrategyId = result.getString("activation_strategy_id");
                    String disabledCategories = result.getString("disabled_categories");
                    String veinMiningPatternId = result.getString("vein_mining_pattern_id");

                    if (activationStrategyId != null) {
                        player.setActivationStrategy(Enums.getIfPresent(ActivationStrategy.class, activationStrategyId.toUpperCase()).or(VeinMiner.getInstance().getDefaultActivationStrategy()));
                    }

                    if (disabledCategories != null) {
                        player.enableVeinMiner(); // Ensure that all categories are loaded again

                        for (String categoryId : disabledCategories.split(",")) {
                            VeinMinerToolCategory category = plugin.getToolCategoryRegistry().get(categoryId.toUpperCase());

                            if (category == null) {
                                continue;
                            }

                            player.disableVeinMiner(category);
                        }
                    }

                    if (veinMiningPatternId != null) {
                        VeinMiningPattern pattern = plugin.getPatternRegistry().get(veinMiningPatternId);
                        player.setVeinMiningPattern(pattern != null ? pattern : plugin.getDefaultVeinMiningPattern());
                    }

                    player.setDirty(false); // They are no longer dirty. We just loaded them
                }

                future.complete(player);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public void close() {
        try {
            this.connectionThread.join(5000);
        } catch (InterruptedException e) {
            // If there were still queries waiting for a connection, something went wrong. Print our stack trace
            if (!connectionQueue.isEmpty()) {
                e.printStackTrace();
            }
        }
    }

    private Connection openConnection() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
        return DriverManager.getConnection(connectionURL);
    }

    private class SQLConnectionRunnable implements Runnable {

        @Override
        public void run() {
            while (true) {
                Consumer<Connection> task = connectionQueue.poll();
                if (task == null) {
                    continue;
                }

                lock.lock();
                try (Connection connection = openConnection()) {
                    task.accept(connection);
                } catch (SQLException | ClassNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }

            }
        }

    }

}