package io.github.mcengine.common.identity.database.mysql;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
import io.github.mcengine.common.identity.database.mysql.util.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * MySQL implementation for the Identity module database.
 * <p>
 * Establishes a persistent connection and ensures tables exist according to the provided schema.
 * Enforces {@code identity.identity_limit} when creating new alternatives.
 *
 * <h3>Schema highlights (MySQL/InnoDB)</h3>
 * <ul>
 *   <li>{@code identity_uuid} is the <b>primary key</b> for {@code identity}.</li>
 *   <li>{@code identity_session} uses {@code PRIMARY KEY (identity_uuid)} to enforce one row per identity.</li>
 *   <li>Timestamps default to {@code CURRENT_TIMESTAMP} and update via {@code ON UPDATE CURRENT_TIMESTAMP} where supported.</li>
 *   <li>Engine/charset set to {@code InnoDB/utf8mb4}.</li>
 * </ul>
 */
public class MCEngineIdentityMySQL implements IMCEngineIdentityDB {

    /**
     * The Bukkit plugin instance providing configuration access and structured logging.
     */
    private final Plugin plugin;

    /**
     * Persistent MySQL JDBC connection used by all operations in this implementation.
     * <p>
     * @implNote All database contract methods delegate their SQL to small, focused
     * utility classes (one util per method) that expose a static {@code invoke(...)}
     * entrypoint.
     */
    private final Connection conn;

    /**
     * Builds the MySQL database connection from config keys:
     * <ul>
     *     <li>{@code database.mysql.host} (default: {@code localhost})</li>
     *     <li>{@code database.mysql.port} (default: {@code 3306})</li>
     *     <li>{@code database.mysql.name} (default: {@code mcengine_identity})</li>
     *     <li>{@code database.mysql.user} (default: {@code root})</li>
     *     <li>{@code database.mysql.password} (default: empty)</li>
     * </ul>
     *
     * @param plugin Bukkit plugin instance
     */
    public MCEngineIdentityMySQL(Plugin plugin) {
        this.plugin = plugin;

        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        String port = plugin.getConfig().getString("database.mysql.port", "3306");
        String dbName = plugin.getConfig().getString("database.mysql.name", "mcengine_identity");
        String user = plugin.getConfig().getString("database.mysql.user", "root");
        String pass = plugin.getConfig().getString("database.mysql.password", "");

        // Use utf8mb4 for full Unicode coverage
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&autoReconnect=true&characterEncoding=utf8mb4";

        Connection tmp = null;
        try {
            tmp = DriverManager.getConnection(jdbcUrl, user, pass);
            ensureSchemaUtil.invoke(tmp, plugin);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to connect to MySQL: " + e.getMessage());
            e.printStackTrace();
        }
        this.conn = tmp;
    }

    /**
     * Executes a SQL statement (DDL/DML) without returning a result value.
     */
    @Override
    public void executeQuery(String sql) {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("MySQL executeQuery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a SQL query expected to return a single scalar value.
     */
    @Override
    public <T> T getValue(String sql, Class<T> type) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) return null;
            Object v = rs.getObject(1);
            return (v == null) ? null : type.cast(v);
        } catch (SQLException e) {
            throw new RuntimeException("MySQL getValue failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures the player's identity structures exist (identity row, primary alt, and session row).
     *
     * @param player Bukkit player whose identity structures should be present
     * @return {@code true} on success; {@code false} if the operation fails
     */
    @Override
    public boolean ensureExist(org.bukkit.entity.Player player) {
        return ensureExistUtil.invoke(conn, plugin, player);
    }

    @Override
    public String getActiveProfileAltUuid(Player player) {
        return getActiveProfileAltUuidUtil.invoke(conn, plugin, player);
    }

    /**
     * Returns the number of alternatives owned by the player's identity (including the primary).
     *
     * @param player Bukkit player
     * @return count of rows in {@code identity_alternative} for the player's identity
     */
    @Override
    public int getProfileAltCount(Player player) {
        return getProfileAltCountUtil.invoke(conn, plugin, player);
    }

    /**
     * Resolves an alt UUID from its display name for the given player.
     *
     * @param player  Bukkit player
     * @param altName display name of the alt
     * @return alt UUID string if found; otherwise {@code null}
     */
    @Override
    public String getProfileAltUuidByName(Player player, String altName) {
        return getProfileAltUuidByNameUtil.invoke(conn, plugin, player, altName);
    }

    /**
     * Creates a new alternative for the player's identity, enforcing {@code identity_limit}.
     * If current alternative count is already at or above the limit, returns {@code null}.
     *
     * @param player Bukkit player
     * @return the created alt UUID, or {@code null} when blocked by the limit or on error
     */
    @Override
    public String createProfileAlt(org.bukkit.entity.Player player) {
        return createProfileAltUtil.invoke(conn, plugin, player);
    }

    /**
     * Switches the active session alt for the player's identity.
     *
     * @param player  Bukkit player
     * @param altUuid alternative UUID to activate (must belong to the player)
     * @return {@code true} if the session row was inserted/updated; else {@code false}
     */
    @Override
    public boolean changeProfileAlt(Player player, String altUuid) {
        return changeProfileAltUtil.invoke(conn, plugin, player, altUuid);
    }

    /**
     * Sets (or clears) an alt display name, enforcing per-identity uniqueness.
     *
     * @param player  Bukkit player
     * @param altUuid alternative UUID to rename
     * @param altName new display name (nullable to clear)
     * @return {@code true} if the row was updated; otherwise {@code false}
     */
    @Override
    public boolean setProfileAltname(Player player, String altUuid, String altName) {
        return setProfileAltnameUtil.invoke(conn, plugin, player, altUuid, altName);
    }

    /**
     * Fetches the display name of an alternative belonging to the given player.
     *
     * @param player  owner {@link Player}
     * @param altUuid alternative UUID
     * @return display name or {@code null} if unset/not found
     */
    @Override
    public String getProfileAltName(Player player, String altUuid) {
        return getProfileAltNameUtil.invoke(conn, plugin, player, altUuid);
    }

    /**
     * Returns all alternatives for the player's identity, each entry being the display name if set,
     * otherwise the alt UUID (e.g., {@code {uuid}-N}). Ordered by UUID ascending.
     *
     * @param player Bukkit player
     * @return list of alt identifiers or names (never {@code null})
     */
    @Override
    public java.util.List<String> getAllProfileAlt(Player player) {
        return getAllProfileAltUtil.invoke(conn, plugin, player);
    }

    @Override
    public boolean isPlayersAlt(Player player, String altUuid) {
        return isPlayersAltUtil.invoke(conn, plugin, player, altUuid);
    }

    @Override
    public boolean changeProfileAltByName(Player player, String altName) {
        return changeProfileAltByNameUtil.invoke(conn, plugin, player, altName);
    }

    @Override
    public boolean addActiveProfileAltPermission(Player player, String permName) {
        return addActiveProfileAltPermissionUtil.invoke(
            conn,
            plugin,
            player,
            permName,
            getActiveProfileAltUuid(player)
        );
    }

    @Override
    public boolean hasPermissionActiveProfileAlt(Player player, String permName) {
        return hasPermissionActiveProfileAltUtil.invoke(
            conn,
            plugin,
            player,
            permName,
            getActiveProfileAltUuid(player),
        player.getUniqueId().toString() + "-0"
        );
    }

    /**
     * Increases the identity's allowed number of alternatives by {@code amount}.
     *
     * @param player Bukkit player whose limit to change
     * @param amount non-negative increment
     * @return {@code true} if updated/persisted; {@code false} otherwise
     */
    @Override
    public boolean addProfileAltLimit(Player player, int amount) {
        return addProfileAltLimitUtil.invoke(conn, plugin, player, amount);
    }

    /**
     * Returns the configured alt limit for the player's identity.
     *
     * @param player Bukkit player
     * @return current alt limit (≥ 1)
     */
    @Override
    public int getProfileAltLimit(Player player) {
        return getProfileAltLimitUtil.invoke(conn, plugin, player);
    }

    /**
     * Adds (or refreshes) a permission for the given player's alternative.
     *
     * @param player   owner {@link Player} of the identity
     * @param altUuid  alternative UUID that will receive the permission
     * @param permName permission name (non-null, non-empty)
     * @return {@code true} if inserted or updated; {@code false} if validation fails or on error
     */
    @Override
    public boolean addProfileAltPermission(Player player, String altUuid, String permName) {
        return addProfileAltPermissionUtil.invoke(conn, plugin, player, altUuid, permName);
    }

    /**
     * Checks whether a permission entry already exists for the given player's alternative.
     *
     * @param player   owner {@link Player} of the identity
     * @param altUuid  alternative UUID to check
     * @param permName permission name to check
     * @return {@code true} if a matching permission row exists; otherwise {@code false}
     */
    @Override
    public boolean hasAltPermission(Player player, String altUuid, String permName) {
        return hasAltPermissionUtil.invoke(conn, plugin, player, altUuid, permName);
    }

    /**
     * Persists a serialized inventory payload for the active alt recorded in {@code identity_session}.
     *
     * @param player  the player whose active alt to persist
     * @param payload opaque, serialized inventory bytes
     * @return {@code true} if written; otherwise {@code false}
     */
    @Override
    public boolean saveProfileAltInventory(Player player, byte[] payload) {
        return saveProfileAltInventoryUtil.invoke(conn, plugin, player, payload);
    }

    /**
     * Loads the serialized inventory payload for the player's currently active alt, if present.
     *
     * @param player the player whose active alt to load
     * @return inventory bytes, or {@code null} when no data is stored
     */
    @Override
    public byte[] loadProfileAltInventory(Player player) {
        return loadProfileAltInventoryUtil.invoke(conn, plugin, player);
    }
}
