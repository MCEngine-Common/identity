package io.github.mcengine.common.identity.database.sqlite;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;

/**
 * SQLite implementation for the Identity module database.
 * <p>
 * This implementation establishes a persistent connection and ensures
 * tables exist for identities, alternatives, sessions, and permissions.
 * Enforces {@code identity.identity_limit} when creating new alternatives.
 */
public class MCEngineIdentitySQLite implements IMCEngineIdentityDB {

    /** The Bukkit plugin instance providing config, paths, and logging. */
    private final Plugin plugin;

    /** JDBC SQLite database URL (file-based). */
    private final String databaseUrl;

    /** Persistent SQLite connection shared by the module. */
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
            ensureSchema(tmp);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to open SQLite connection: " + e.getMessage());
            e.printStackTrace();
        }
        this.conn = tmp;
    }

    private void ensureSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity (" +
                    "identity_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "identity_uuid TEXT NOT NULL UNIQUE," +
                    "identity_limit INTEGER NOT NULL DEFAULT 1," +
                    "identity_created_at TEXT NULL," +
                    "identity_updated_at TEXT NULL" +
                    ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity_alternative (" +
                    "identity_alternative_uuid TEXT PRIMARY KEY," +
                    "identity_uuid TEXT NOT NULL," +
                    "identity_alternative_name TEXT NULL," +
                    "identity_alternative_storage BLOB NULL," +
                    "identity_alternative_created_at TEXT NULL," +
                    "identity_alternative_updated_at TEXT NULL," +
                    "FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE" +
                    ")");
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_name ON identity_alternative(identity_uuid, identity_alternative_name)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity_session (" +
                    "identity_session_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "identity_uuid TEXT NOT NULL," +
                    "identity_alternative_uuid TEXT NULL," +
                    "FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                    "FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL" +
                    ")");
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_session ON identity_session(identity_uuid)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity_permission (" +
                    "identity_permission_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "identity_uuid TEXT NOT NULL," +
                    "identity_alternative_uuid TEXT NULL," +
                    "identity_permission_name TEXT NOT NULL," +
                    "identity_permission_created_at TEXT NULL," +
                    "identity_permission_updated_at TEXT NULL," +
                    "FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                    "FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE" +
                    ")");
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_perm ON identity_permission(identity_uuid, identity_alternative_uuid, identity_permission_name)");
        }
    }

    @Override
    public Connection getDBConnection() {
        return conn;
    }

    /**
     * Creates a new alternative for the player's identity, enforcing {@code identity_limit}.
     * If current alternative count is already at or above the limit, returns {@code null}.
     */
    @Override
    public String createProfileAlt(Player player) {
        if (conn == null) return null;
        String identityUuid = player.getUniqueId().toString();
        String now = java.time.Instant.now().toString();
        try {
            // upsert identity (ensures a row with default limit=1 exists)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) VALUES (?, 1, ?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=excluded.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setString(2, now);
                ps.setString(3, now);
                ps.executeUpdate();
            }

            // fetch limit
            int limit = 1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_limit FROM identity WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) limit = rs.getInt(1);
                }
            }

            // current count
            int count = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM identity_alternative WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) count = rs.getInt(1);
                }
            }

            // enforce limit
            if (count >= limit) {
                plugin.getLogger().info("Alt creation blocked for " + identityUuid + " (limit " + limit + ").");
                return null;
            }

            // next alt index = count
            String altUuid = identityUuid + "-" + count;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity_alternative (identity_alternative_uuid, identity_uuid, identity_alternative_name, identity_alternative_storage, identity_alternative_created_at, identity_alternative_updated_at) " +
                            "VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, altUuid);
                ps.setString(2, identityUuid);
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.BLOB);
                ps.setString(5, now);
                ps.setString(6, now);
                ps.executeUpdate();
            }
            return altUuid;
        } catch (SQLException e) {
            plugin.getLogger().warning("createProfileAlt failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean changeProfileAlt(Player player, String altUuid) {
        if (conn == null) return false;
        String identityUuid = player.getUniqueId().toString();
        try {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) return false;
                }
            }
            try (PreparedStatement up = conn.prepareStatement(
                    "INSERT INTO identity_session (identity_uuid, identity_alternative_uuid) VALUES (?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_alternative_uuid=excluded.identity_alternative_uuid")) {
                up.setString(1, identityUuid);
                up.setString(2, altUuid);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("changeProfileAlt failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean setProfileAltname(Player player, String altUuid, String altName) {
        if (conn == null) return false;
        String identityUuid = player.getUniqueId().toString();
        String now = Instant.now().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE identity_alternative SET identity_alternative_name = ?, identity_alternative_updated_at=? " +
                        "WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
            if (altName == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, altName);
            ps.setString(2, now);
            ps.setString(3, altUuid);
            ps.setString(4, identityUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("setProfileAltname failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
