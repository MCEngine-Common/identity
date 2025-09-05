package io.github.mcengine.common.identity.database.postgresql;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * PostgreSQL implementation for the Identity module database.
 * <p>
 * Establishes a persistent connection and ensures a schema exists for identity data.
 * Enforces {@code identity.identity_limit} when creating new alternatives.
 */
public class MCEngineIdentityPostgreSQL implements IMCEngineIdentityDB {

    /** The Bukkit plugin instance providing config and logging. */
    private final Plugin plugin;

    /** Persistent PostgreSQL connection. */
    private final Connection conn;

    /**
     * Builds the PostgreSQL connection from config keys:
     * <ul>
     *     <li>{@code database.postgresql.host} (default: {@code localhost})</li>
     *     <li>{@code database.postgresql.port} (default: {@code 5432})</li>
     *     <li>{@code database.postgresql.name} (default: {@code mcengine_identity})</li>
     *     <li>{@code database.postgresql.user} (default: {@code postgres})</li>
     *     <li>{@code database.postgresql.password} (default: empty)</li>
     * </ul>
     *
     * @param plugin Bukkit plugin instance
     */
    public MCEngineIdentityPostgreSQL(Plugin plugin) {
        this.plugin = plugin;

        String host = plugin.getConfig().getString("database.postgresql.host", "localhost");
        String port = plugin.getConfig().getString("database.postgresql.port", "5432");
        String dbName = plugin.getConfig().getString("database.postgresql.name", "mcengine_identity");
        String user = plugin.getConfig().getString("database.postgresql.user", "postgres");
        String pass = plugin.getConfig().getString("database.postgresql.password", "");

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;

        Connection tmp = null;
        try {
            tmp = DriverManager.getConnection(jdbcUrl, user, pass);
            ensureSchema(tmp);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to connect to PostgreSQL: " + e.getMessage());
            e.printStackTrace();
        }
        this.conn = tmp;
    }

    private void ensureSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity (" +
                    "identity_id SERIAL PRIMARY KEY," +
                    "identity_uuid VARCHAR(36) UNIQUE NOT NULL," +
                    "identity_limit INT NOT NULL DEFAULT 1," +
                    "identity_created_at TIMESTAMP NULL," +
                    "identity_updated_at TIMESTAMP NULL" +
                    ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity_alternative (" +
                    "identity_alternative_uuid VARCHAR(64) PRIMARY KEY," +
                    "identity_uuid VARCHAR(36) NOT NULL," +
                    "identity_alternative_name VARCHAR(64) NULL," +
                    "identity_alternative_storage BYTEA NULL," +
                    "identity_alternative_created_at TIMESTAMP NULL," +
                    "identity_alternative_updated_at TIMESTAMP NULL," +
                    "FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE" +
                    ")");
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uniq_identity_name ON identity_alternative(identity_uuid, identity_alternative_name)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity_session (" +
                    "identity_session_id SERIAL PRIMARY KEY," +
                    "identity_uuid VARCHAR(36) NOT NULL," +
                    "identity_alternative_uuid VARCHAR(64) NULL," +
                    "FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                    "FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL," +
                    "UNIQUE (identity_uuid)" +
                    ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS identity_permission (" +
                    "identity_permission_id SERIAL PRIMARY KEY," +
                    "identity_uuid VARCHAR(36) NOT NULL," +
                    "identity_alternative_uuid VARCHAR(64) NULL," +
                    "identity_permission_name VARCHAR(64) NOT NULL," +
                    "identity_permission_created_at TIMESTAMP NULL," +
                    "identity_permission_updated_at TIMESTAMP NULL," +
                    "FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                    "FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE," +
                    "UNIQUE (identity_uuid, identity_alternative_uuid, identity_permission_name)" +
                    ")");
        }
    }

    @Override
    public Connection getDBConnection() {
        return conn;
    }

    @Override
    public boolean ensureExist(Player player) {
        if (conn == null) return false;
        final String identityUuid = player.getUniqueId().toString();
        final String primaryAltUuid = identityUuid + "-0";
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // 1) Upsert identity (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                            "VALUES (?, 1, ?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }

            // 2) Ensure {uuid}-0 alt exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity_alternative (" +
                            "identity_alternative_uuid, identity_uuid, identity_alternative_name, " +
                            "identity_alternative_storage, identity_alternative_created_at, identity_alternative_updated_at" +
                            ") VALUES (?,?,?,?,?,?) " +
                            "ON CONFLICT(identity_alternative_uuid) DO NOTHING")) {
                ps.setString(1, primaryAltUuid);
                ps.setString(2, identityUuid);
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.BINARY);
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
    public String createProfileAlt(Player player) {
        if (conn == null) return null;
        String identityUuid = player.getUniqueId().toString();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // upsert identity (ensures a row with default limit=1 exists)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) VALUES (?, 1, ?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
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
                ps.setNull(4, Types.BINARY);
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
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_alternative_uuid=EXCLUDED.identity_alternative_uuid")) {
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
                "UPDATE identity_alternative SET identity_alternative_name = ?, identity_alternative_updated_at=NOW() " +
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
    public boolean addLimit(Player player, int amount) {
        if (conn == null || amount < 0) return false;
        String identityUuid = player.getUniqueId().toString();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // ensure identity row
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) VALUES (?, 1, ?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
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
    public int getLimit(Player player) {
        if (conn == null) return 1;
        String identityUuid = player.getUniqueId().toString();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // upsert identity if missing (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) VALUES (?, 1, ?, ?) " +
                            "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
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
    public boolean saveAltInventory(Player player, byte[] payload) {
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

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE identity_alternative SET identity_alternative_storage = ?, identity_alternative_updated_at=NOW() " +
                            "WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
                ps.setBytes(1, payload);
                ps.setString(2, altUuid);
                ps.setString(3, identityUuid);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("saveAltInventory failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public byte[] loadAltInventory(Player player) {
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
