package io.github.mcengine.common.identity.database.sqlite;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;
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

    @Override
    public int getProfileCount(Player player) {
        if (conn == null) return 0;
        String identityUuid = player.getUniqueId().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM identity_alternative WHERE identity_uuid = ?")) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileCount failed: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
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

    @Override
    public String getProfileAltName(Player player, String altUuid) {
        if (conn == null) return null;
        String identityUuid = player.getUniqueId().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_alternative_name FROM identity_alternative WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
            ps.setString(1, altUuid);
            ps.setString(2, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltName failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean addProfileAltLimit(Player player, int amount) {
        if (conn == null || amount < 0) return false;
        String identityUuid = player.getUniqueId().toString();
        String now = Instant.now().toString();
        try {
            // ensure identity row
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) VALUES (?, 1, ?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=excluded.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setString(2, now);
                ps.setString(3, now);
                ps.executeUpdate();
            }
            // increment limit
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE identity SET identity_limit = identity_limit + ? WHERE identity_uuid = ?")) {
                ps.setInt(1, amount);
                ps.setString(2, identityUuid);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("addLimit failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int getProfileAltLimit(Player player) {
        if (conn == null) return 1;
        String identityUuid = player.getUniqueId().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_limit FROM identity WHERE identity_uuid = ?")) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getLimit failed: " + e.getMessage());
            e.printStackTrace();
        }
        return 1;
    }

    @Override
    public boolean addProfileAltPermission(Player player, String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty() || permName == null || permName.isEmpty()) return false;

        final String identityUuid = player.getUniqueId().toString();
        final String now = Instant.now().toString();

        try {
            // Validate alt belongs to player's identity
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) return false;
                }
            }

            // Try insert; if duplicate, refresh updated_at
            int ins;
            try (PreparedStatement insStmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO identity_permission (" +
                            "identity_uuid, identity_alternative_uuid, identity_permission_name, " +
                            "identity_permission_created_at, identity_permission_updated_at) " +
                            "VALUES (?,?,?,?,?)")) {
                insStmt.setString(1, identityUuid);
                insStmt.setString(2, altUuid);
                insStmt.setString(3, permName);
                insStmt.setString(4, now);
                insStmt.setString(5, now);
                ins = insStmt.executeUpdate();
            }
            if (ins > 0) return true;

            // Duplicate → bump updated_at
            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE identity_permission SET identity_permission_updated_at=? " +
                            "WHERE identity_uuid=? AND identity_alternative_uuid=? AND identity_permission_name=?")) {
                up.setString(1, now);
                up.setString(2, identityUuid);
                up.setString(3, altUuid);
                up.setString(4, permName);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltPermission failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Saves the active alt's inventory payload.
     * <p><b>SQLite fix:</b> uses a parameterized timestamp string instead of SQL {@code NOW()}.</p>
     */
    @Override
    public boolean saveProfileAltInventory(Player player, byte[] payload) {
        if (conn == null) return false;
        String identityUuid = player.getUniqueId().toString();
        String now = Instant.now().toString();
        try {
            String altUuid = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) altUuid = rs.getString(1);
                }
            }
            if (altUuid == null) return false;

            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE identity_alternative SET identity_alternative_storage = ?, identity_alternative_updated_at = ? " +
                            "WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
                up.setBytes(1, payload);
                up.setString(2, now); // explicit timestamp string
                up.setString(3, altUuid);
                up.setString(4, identityUuid);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("saveAltInventory failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public byte[] loadProfileAltInventory(Player player) {
        if (conn == null) return null;
        String identityUuid = player.getUniqueId().toString();
        try {
            String altUuid = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) altUuid = rs.getString(1);
                }
            }
            if (altUuid == null) return null;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_alternative_storage FROM identity_alternative WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
                ps.setString(1, altUuid);
                ps.setString(2, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBytes(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadAltInventory failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<String> getProfileAllAlt(Player player) {
        List<String> alts = new ArrayList<>();
        if (conn == null) return alts;
        String identityUuid = player.getUniqueId().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_alternative_uuid, identity_alternative_name " +
                        "FROM identity_alternative WHERE identity_uuid = ? ORDER BY identity_alternative_uuid ASC")) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuid = rs.getString(1);
                    String name = rs.getString(2);
                    alts.add((name != null && !name.isEmpty()) ? name : uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAllAlt failed: " + e.getMessage());
            e.printStackTrace();
        }
        return alts;
    }

    @Override
    public boolean ensureExist(Player player) {
        if (conn == null) return false;
        final String identityUuid = player.getUniqueId().toString();
        final String primaryAltUuid = identityUuid + "-0";
        final String now = java.time.Instant.now().toString();
        try {
            // 1) Upsert identity (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                            "VALUES (?, 1, ?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=excluded.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setString(2, now);
                ps.setString(3, now);
                ps.executeUpdate();
            }

            // 2) Ensure {uuid}-0 alt exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO identity_alternative (" +
                            "identity_alternative_uuid, identity_uuid, identity_alternative_name, " +
                            "identity_alternative_storage, identity_alternative_created_at, identity_alternative_updated_at" +
                            ") VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, primaryAltUuid);
                ps.setString(2, identityUuid);
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.BLOB);
                ps.setString(5, now);
                ps.setString(6, now);
                ps.executeUpdate();
            }

            // 3) Ensure session row exists (points to {uuid}-0 if absent)
            boolean hasSession = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM identity_session WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    hasSession = rs.next();
                }
            }
            if (!hasSession) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO identity_session (identity_uuid, identity_alternative_uuid) VALUES (?, ?)")) {
                    ps.setString(1, identityUuid);
                    ps.setString(2, primaryAltUuid);
                    ps.executeUpdate();
                }
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("ensureExist failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getProfileAltUuidByName(Player player, String altName) {
        if (conn == null) return null;
        String identityUuid = player.getUniqueId().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_alternative_uuid FROM identity_alternative " +
                        "WHERE identity_uuid = ? AND identity_alternative_name = ?")) {
            ps.setString(1, identityUuid);
            ps.setString(2, altName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltUuidByName failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
