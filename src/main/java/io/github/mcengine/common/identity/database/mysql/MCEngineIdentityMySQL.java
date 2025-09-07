package io.github.mcengine.common.identity.database.mysql;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
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

    /** The Bukkit plugin instance providing config and logging. */
    private final Plugin plugin;

    /** Persistent MySQL connection. */
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
            ensureSchema(tmp);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to connect to MySQL: " + e.getMessage());
            e.printStackTrace();
        }
        this.conn = tmp;
    }

    /** Ensures schema exists with the requested constraints. */
    private void ensureSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // identity: identity_uuid is PK; timestamps auto-managed
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity (" +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_limit INT NOT NULL DEFAULT 1," +
                "  identity_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  identity_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (identity_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // identity_alternative: PK on alt uuid, FK to identity, name unique per identity (NULLs allowed), helpful indexes
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_alternative (" +
                "  identity_alternative_uuid VARCHAR(64) NOT NULL," +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_name VARCHAR(64) NULL," +
                "  identity_alternative_storage LONGBLOB NULL," +
                "  identity_alternative_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  identity_alternative_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  CONSTRAINT fk_alt_identity FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  UNIQUE KEY uniq_identity_name (identity_uuid, identity_alternative_name)," +
                "  PRIMARY KEY (identity_alternative_uuid)," +
                "  KEY idx_alt_identity (identity_uuid)," +
                "  KEY idx_alt_name (identity_alternative_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // identity_session: exactly one row per identity (PK = identity_uuid); alt is nullable and SET NULL on alt deletion
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_session (" +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_uuid VARCHAR(64) NULL," +
                "  CONSTRAINT fk_session_identity FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  CONSTRAINT fk_session_alt FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL," +
                "  PRIMARY KEY (identity_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // identity_permission: composite PK to prevent duplicates per (identity_uuid, identity_alternative_uuid, name)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_permission (" +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_uuid VARCHAR(64) NOT NULL," +
                "  identity_permission_name VARCHAR(64) NOT NULL," +
                "  identity_permission_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  identity_permission_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  CONSTRAINT fk_perm_identity FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  CONSTRAINT fk_perm_alt FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE," +
                "  PRIMARY KEY (identity_uuid, identity_alternative_uuid, identity_permission_name)," +
                "  KEY idx_perm_identity (identity_uuid)," +
                "  KEY idx_perm_alt (identity_alternative_uuid)," +
                "  KEY idx_perm_name (identity_permission_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    @Override
    public Connection getDBConnection() {
        return conn;
    }

    @Override
    public boolean ensureExist(org.bukkit.entity.Player player) {
        if (conn == null) return false;
        final String identityUuid = player.getUniqueId().toString();
        final String primaryAltUuid = identityUuid + "-0";
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // 1) Upsert identity (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                            "VALUES (?, 1, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE identity_updated_at = VALUES(identity_updated_at)")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }

            // 2) Ensure {uuid}-0 alt exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO identity_alternative (" +
                            "identity_alternative_uuid, identity_uuid, identity_alternative_name, " +
                            "identity_alternative_storage, identity_alternative_created_at, identity_alternative_updated_at" +
                            ") VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, primaryAltUuid);
                ps.setString(2, identityUuid);
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.BLOB);
                ps.setTimestamp(5, now);
                ps.setTimestamp(6, now);
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

    /**
     * Creates a new alternative for the player's identity, enforcing {@code identity_limit}.
     * If current alternative count is already at or above the limit, returns {@code null}.
     */
    @Override
    public String createProfileAlt(org.bukkit.entity.Player player) {
        if (conn == null) return null;
        String identityUuid = player.getUniqueId().toString();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // upsert identity (ensures a row with default limit=1 exists)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                            "VALUES (?, 1, ?, ?) ON DUPLICATE KEY UPDATE identity_updated_at = VALUES(identity_updated_at)")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
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
                ps.setTimestamp(5, now);
                ps.setTimestamp(6, now);
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
            // verify alt belongs to identity
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) return false;
                }
            }
            // upsert session (PK on identity_uuid ensures 1 row)
            try (PreparedStatement up = conn.prepareStatement(
                    "INSERT INTO identity_session (identity_uuid, identity_alternative_uuid) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE identity_alternative_uuid = VALUES(identity_alternative_uuid)")) {
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
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE identity_alternative SET identity_alternative_name = ?, identity_alternative_updated_at = CURRENT_TIMESTAMP " +
                        "WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
            if (altName == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, altName);
            ps.setString(2, altUuid);
            ps.setString(3, identityUuid);
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
    public java.util.List<String> getProfileAllAlt(Player player) {
        java.util.List<String> alts = new java.util.ArrayList<>();
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
    public boolean addProfileAltLimit(Player player, int amount) {
        if (conn == null || amount < 0) return false;
        String identityUuid = player.getUniqueId().toString();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // ensure identity row
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                            "VALUES (?, 1, ?, ?) ON DUPLICATE KEY UPDATE identity_updated_at = VALUES(identity_updated_at)")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
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
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // upsert identity if missing (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                            "VALUES (?, 1, ?, ?) ON DUPLICATE KEY UPDATE identity_updated_at = VALUES(identity_updated_at)")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }
            // read limit
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_limit FROM identity WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
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
        Timestamp now = Timestamp.from(Instant.now());

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

            // Upsert permission (composite PK)
            try (PreparedStatement up = conn.prepareStatement(
                    "INSERT INTO identity_permission (" +
                            "identity_uuid, identity_alternative_uuid, identity_permission_name, " +
                            "identity_permission_created_at, identity_permission_updated_at) " +
                            "VALUES (?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE identity_permission_updated_at = VALUES(identity_permission_updated_at)")) {
                up.setString(1, identityUuid);
                up.setString(2, altUuid);
                up.setString(3, permName);
                up.setTimestamp(4, now);
                up.setTimestamp(5, now);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltPermission failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean saveProfileAltInventory(Player player, byte[] payload) {
        if (conn == null) return false;
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
            if (altUuid == null) return false;

            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE identity_alternative SET identity_alternative_storage = ?, identity_alternative_updated_at = CURRENT_TIMESTAMP " +
                            "WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
                up.setBytes(1, payload);
                up.setString(2, altUuid);
                up.setString(3, identityUuid);
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
}
