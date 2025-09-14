package io.github.mcengine.common.identity.database.sqlite;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
import io.github.mcengine.common.identity.database.sqlite.util.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation for the Identity module database.
 * <p>
 * This implementation establishes a persistent connection and ensures
 * tables exist for identities, alternatives, sessions, and permissions.
 * Enforces {@code identity.identity_limit} when creating new alternatives.
 *
 * <h3>Schema highlights (SQLite)</h3>
 * <ul>
 *   <li>{@code PRAGMA foreign_keys = ON} is enabled to enforce FKs.</li>
 *   <li>{@code identity_uuid} is the <b>primary key</b> for {@code identity}.</li>
 *   <li>{@code identity_session} uses {@code PRIMARY KEY (identity_uuid)} for a 1:1 row with identity.</li>
 *   <li>Timestamps are stored as ISO-8601 strings; application code sets updates explicitly.</li>
 * </ul>
 */
public class MCEngineIdentitySQLite implements IMCEngineIdentityDB {

    /**
     * The Bukkit plugin instance providing configuration access, data folder paths, and structured logging.
     */
    private final Plugin plugin;

    /** JDBC SQLite database URL (file-based). */
    private final String databaseUrl;

    /**
     * Persistent SQLite JDBC connection shared by this implementation.
     * <p>
     * @implNote Contract methods delegate their SQL to small, focused utility classes
     * (one util per method) that expose a static {@code invoke(...)} entrypoint.
     */
    private final Connection conn;

    /**
     * Builds the SQLite database from plugin config:
     * <ul>
     *     <li>{@code database.sqlite.path} → DB file name in plugin data folder (default: {@code identity.db})</li>
     * </ul>
     *
     * @param plugin Bukkit plugin instance
     */
    public MCEngineIdentitySQLite(Plugin plugin) {
        this.plugin = plugin;
        String fileName = plugin.getConfig().getString("database.sqlite.path", "identity.db");
        File dbFile = new File(plugin.getDataFolder(), fileName);

        if (!dbFile.exists()) {
            try {
                if (dbFile.getParentFile() != null) dbFile.getParentFile().mkdirs();
                boolean created = dbFile.createNewFile();
                if (created) plugin.getLogger().info("SQLite database file created: " + dbFile.getAbsolutePath());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create SQLite database file: " + e.getMessage());
                e.printStackTrace();
            }
        }

        this.databaseUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        Connection tmp = null;
        try {
            tmp = DriverManager.getConnection(databaseUrl);
            // Ensure FK enforcement
            try (Statement pragma = tmp.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
            ensureSchemaUtil.invoke(tmp, plugin);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to open SQLite connection: " + e.getMessage());
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
            throw new RuntimeException("SQLite executeQuery failed: " + e.getMessage(), e);
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
            throw new RuntimeException("SQLite getValue failed: " + e.getMessage(), e);
        }
    }

    // ---------- Delegations to per-method util classes ----------

    /**
     * Ensures the player's identity structures exist by delegating to {@code ensureExistUtil.invoke}.
     *
     * @param player Bukkit player
     * @return {@code true} on success; {@code false} on failure
     */
    @Override
    public boolean ensureExist(Player player) {
        return ensureExistUtil.invoke(conn, plugin, player);
    }

    @Override
    public String getActiveProfileAltUuid(Player player) {
        return getActiveProfileAltUuidUtil.invoke(conn, plugin, player);
    }

    /**
     * Returns the number of alternatives for the player's identity by delegating to {@code getProfileAltCountUtil.invoke}.
     *
     * @param player Bukkit player
     * @return count of alternatives owned by the player's identity
     */
    @Override
    public int getProfileAltCount(Player player) {
        return getProfileAltCountUtil.invoke(conn, plugin, player);
    }

    /**
     * Creates a new alternative for the player's identity (respecting {@code identity_limit})
     * by delegating to {@code createProfileAltUtil.invoke}.
     *
     * @param player Bukkit player
     * @return the created alt UUID, or {@code null} when blocked by the limit
     */
    @Override
    public String createProfileAlt(Player player) {
        return createProfileAltUtil.invoke(conn, plugin, player);
    }

    /**
     * Switches the active session alt by delegating to {@code changeProfileAltUtil.invoke}.
     *
     * @param player  Bukkit player
     * @param altUuid alternative UUID to activate
     * @return {@code true} if the active alt was changed; otherwise {@code false}
     */
    @Override
    public boolean changeProfileAlt(Player player, String altUuid) {
        return changeProfileAltUtil.invoke(conn, plugin, player, altUuid);
    }

    /**
     * Sets (or clears) an alt's display name by delegating to {@code setProfileAltnameUtil.invoke}.
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
     * Fetches an alt's display name by delegating to {@code getProfileAltNameUtil.invoke}.
     *
     * @param player  owner of the identity
     * @param altUuid alternative UUID
     * @return display name or {@code null} if unset/not found
     */
    @Override
    public String getProfileAltName(Player player, String altUuid) {
        return getProfileAltNameUtil.invoke(conn, plugin, player, altUuid);
    }

    /**
     * Returns all alternatives (names or UUIDs) by delegating to {@code getAllProfileAltUtil.invoke}.
     *
     * @param player Bukkit player
     * @return ordered list of alt identifiers or names
     */
    @Override
    public List<String> getAllProfileAlt(Player player) {
        return getAllProfileAltUtil.invoke(conn, plugin, player);
    }

    @Override
    public boolean isPlayerProfileAlt(Player player, String altUuid) {
        return isPlayerProfileAltUtil.invoke(conn, plugin, player, altUuid);
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
     * Increments the alt limit for the player's identity by delegating to {@code addProfileAltLimitUtil.invoke}.
     *
     * @param player Bukkit player
     * @param amount non-negative increment to apply
     * @return {@code true} if updated; otherwise {@code false}
     */
    @Override
    public boolean addProfileAltLimit(Player player, int amount) {
        return addProfileAltLimitUtil.invoke(conn, plugin, player, amount);
    }

    /**
     * Retrieves the alt limit for the player's identity by delegating to {@code getProfileAltLimitUtil.invoke}.
     *
     * @param player Bukkit player
     * @return the current alt limit (≥ 1)
     */
    @Override
    public int getProfileAltLimit(Player player) {
        return getProfileAltLimitUtil.invoke(conn, plugin, player);
    }

    /**
     * Adds or refreshes a permission for a given alt by delegating to {@code addProfileAltPermissionUtil.invoke}.
     *
     * @param player   owner of the identity
     * @param altUuid  alternative UUID that will receive the permission
     * @param permName permission name (non-null, non-empty)
     * @return {@code true} if inserted or updated; {@code false} otherwise
     */
    @Override
    public boolean addProfileAltPermission(Player player, String altUuid, String permName) {
        return addProfileAltPermissionUtil.invoke(conn, plugin, player, altUuid, permName);
    }

    /**
     * Checks whether a permission exists for the given alt by delegating to {@code hasPermissionProfileAltUtil.invoke}.
     *
     * @param player   owner of the identity
     * @param altUuid  alternative UUID to check
     * @param permName permission name to check
     * @return {@code true} if a matching permission row exists; otherwise {@code false}
     */
    @Override
    public boolean hasPermissionProfileAlt(Player player, String altUuid, String permName) {
        return hasPermissionProfileAltUtil.invoke(conn, plugin, player, altUuid, permName);
    }

    /**
     * Saves the active alt's inventory payload by delegating to {@code saveProfileAltInventoryUtil.invoke}.
     *
     * @param player  the player whose active alt to persist
     * @param payload serialized inventory bytes
     * @return {@code true} if written; otherwise {@code false}
     */
    @Override
    public boolean saveProfileAltInventory(Player player, byte[] payload) {
        return saveProfileAltInventoryUtil.invoke(conn, plugin, player, payload);
    }

    /**
     * Loads the active alt's inventory payload by delegating to {@code loadProfileAltInventoryUtil.invoke}.
     *
     * @param player the player whose active alt to load
     * @return serialized inventory bytes, or {@code null} if absent
     */
    @Override
    public byte[] loadProfileAltInventory(Player player) {
        return loadProfileAltInventoryUtil.invoke(conn, plugin, player);
    }

    /**
     * Resolves an alt UUID from its display name by delegating to {@code getProfileAltUuidByNameUtil.invoke}.
     *
     * @param player  owner of the identity
     * @param altName display name of the alt
     * @return the alt UUID string if found; otherwise {@code null}
     */
    @Override
    public String getProfileAltUuidByName(Player player, String altName) {
        return getProfileAltUuidByNameUtil.invoke(conn, plugin, player, altName);
    }
}
