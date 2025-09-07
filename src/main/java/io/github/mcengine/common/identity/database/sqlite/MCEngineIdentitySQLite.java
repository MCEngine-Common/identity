package io.github.mcengine.common.identity.database.sqlite;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
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
            ensureSchema(tmp);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to open SQLite connection: " + e.getMessage());
            e.printStackTrace();
        }
        this.conn = tmp;
    }

    private void ensureSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // identity: identity_uuid is PK
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity (" +
                "  identity_uuid TEXT PRIMARY KEY," +
                "  identity_limit INTEGER NOT NULL DEFAULT 1," +
                "  identity_created_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  identity_updated_at TEXT NOT NULL DEFAULT (datetime('now'))" +
                ")"
            );

            // identity_alternative: PK on alt uuid, FK to identity, unique name per identity
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_alternative (" +
                "  identity_alternative_uuid TEXT PRIMARY KEY," +
                "  identity_uuid TEXT NOT NULL," +
                "  identity_alternative_name TEXT NULL," +
                "  identity_alternative_storage BLOB NULL," +
                "  identity_alternative_created_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  identity_alternative_updated_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE" +
                ")"
            );
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_name ON identity_alternative(identity_uuid, identity_alternative_name)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_identity ON identity_alternative(identity_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_name ON identity_alternative(identity_alternative_name)");

            // identity_session: exactly one row per identity (PK = identity_uuid)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_session (" +
                "  identity_uuid TEXT PRIMARY KEY," +
                "  identity_alternative_uuid TEXT NULL," +
                "  FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL" +
                ")"
            );

            // identity_permission: composite PK to prevent duplicates per (identity_uuid, identity_alternative_uuid, name)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_permission (" +
                "  identity_uuid TEXT NOT NULL," +
                "  identity_alternative_uuid TEXT NOT NULL," +
                "  identity_permission_name TEXT NOT NULL," +
                "  identity_permission_created_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  identity_permission_updated_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE," +
                "  PRIMARY KEY (identity_uuid, identity_alternative_uuid, identity_permission_name)" +
                ")"
            );
            // (Keep the helpful secondary indexes)
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_identity ON identity_permission(identity_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_alt ON identity_permission(identity_alternative_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_name ON identity_permission(identity_permission_name)");
        }
    }

    @Override
    public Connection getDBConnection() {
        return conn;
    }

    // ---------- Delegations to per-method util classes ----------

    @Override
    public boolean ensureExist(Player player) {
        return ensureExistUtil.invoke(conn, plugin, player);
    }

    @Override
    public int getProfileAltCount(Player player) {
        return getProfileAltCountUtil.invoke(conn, plugin, player);
    }

    /**
     * Creates a new alternative for the player's identity, enforcing {@code identity_limit}.
     * If current alternative count is already at or above the limit, returns {@code null}.
     */
    @Override
    public String createProfileAlt(Player player) {
        return createProfileAltUtil.invoke(conn, plugin, player);
    }

    @Override
    public boolean changeProfileAlt(Player player, String altUuid) {
        return changeProfileAltUtil.invoke(conn, plugin, player, altUuid);
    }

    @Override
    public boolean setProfileAltname(Player player, String altUuid, String altName) {
        return setProfileAltnameUtil.invoke(conn, plugin, player, altUuid, altName);
    }

    @Override
    public String getProfileAltName(Player player, String altUuid) {
        return getProfileAltNameUtil.invoke(conn, plugin, player, altUuid);
    }

    @Override
    public List<String> getProfileAllAlt(Player player) {
        return getProfileAllAltUtil.invoke(conn, plugin, player);
    }

    @Override
    public boolean addProfileAltLimit(Player player, int amount) {
        return addProfileAltLimitUtil.invoke(conn, plugin, player, amount);
    }

    @Override
    public int getProfileAltLimit(Player player) {
        return getProfileAltLimitUtil.invoke(conn, plugin, player);
    }

    @Override
    public boolean addProfileAltPermission(Player player, String altUuid, String permName) {
        return addProfileAltPermissionUtil.invoke(conn, plugin, player, altUuid, permName);
    }

    @Override
    public boolean hasProfileAltCount(Player player, String altUuid, String permName) {
        return hasProfileAltCountUtil.invoke(conn, plugin, player, altUuid, permName);
    }

    @Override
    public boolean saveProfileAltInventory(Player player, byte[] payload) {
        return saveProfileAltInventoryUtil.invoke(conn, plugin, player, payload);
    }

    @Override
    public byte[] loadProfileAltInventory(Player player) {
        return loadProfileAltInventoryUtil.invoke(conn, plugin, player);
    }

    @Override
    public String getProfileAltUuidByName(Player player, String altName) {
        return getProfileAltUuidByNameUtil.invoke(conn, plugin, player, altName);
    }
}
