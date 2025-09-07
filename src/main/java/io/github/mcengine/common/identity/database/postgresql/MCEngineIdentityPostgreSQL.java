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
 *
 * <h3>Schema highlights (PostgreSQL)</h3>
 * <ul>
 *   <li>{@code identity_uuid} is the <b>primary key</b> for {@code identity}.</li>
 *   <li>{@code identity_session} uses {@code PRIMARY KEY (identity_uuid)} to enforce one row per identity.</li>
 *   <li>Timestamps default to {@code NOW()} (no {@code ON UPDATE} in Postgres; updates are handled in queries).</li>
 * </ul>
 */
public class MCEngineIdentityPostgreSQL implements IMCEngineIdentityDB {

    /**
     * The Bukkit plugin instance providing configuration access and structured logging.
     */
    private final Plugin plugin;

    /**
     * Persistent PostgreSQL JDBC connection used by all operations in this implementation.
     * <p>
     * @implNote All database contract methods delegate their SQL to small, focused
     * utility classes (one util per method) that expose a static {@code invoke(...)}
     * entrypoint.
     */
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

    /**
     * Ensures the required schema objects exist (idempotent DDL).
     *
     * @param c open {@link Connection} to the PostgreSQL database
     * @throws SQLException if any DDL statement fails
     */
    private void ensureSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // identity: identity_uuid is PK; timestamps default to now()
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity (" +
                "  identity_uuid VARCHAR(36) PRIMARY KEY," +
                "  identity_limit INT NOT NULL DEFAULT 1," +
                "  identity_created_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  identity_updated_at TIMESTAMP NOT NULL DEFAULT NOW()" +
                ")"
            );

            // identity_alternative: PK on alt uuid, FK to identity, unique name per identity (NULLs allowed in PG)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_alternative (" +
                "  identity_alternative_uuid VARCHAR(64) PRIMARY KEY," +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_name VARCHAR(64) NULL," +
                "  identity_alternative_storage BYTEA NULL," +
                "  identity_alternative_created_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  identity_alternative_updated_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  CONSTRAINT fk_alt_identity FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE" +
                ")"
            );
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uniq_identity_name ON identity_alternative(identity_uuid, identity_alternative_name)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_identity ON identity_alternative(identity_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_name ON identity_alternative(identity_alternative_name)");

            // identity_session: exactly one row per identity
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_session (" +
                "  identity_uuid VARCHAR(36) PRIMARY KEY," +
                "  identity_alternative_uuid VARCHAR(64) NULL," +
                "  CONSTRAINT fk_session_identity FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  CONSTRAINT fk_session_alt FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL" +
                ")"
            );

            // identity_permission: composite PK to prevent duplicates per (identity_uuid, identity_alternative_uuid, name)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_permission (" +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_uuid VARCHAR(64) NOT NULL," +
                "  identity_permission_name VARCHAR(64) NOT NULL," +
                "  identity_permission_created_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  identity_permission_updated_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  CONSTRAINT fk_perm_identity FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  CONSTRAINT fk_perm_alt FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE," +
                "  PRIMARY KEY (identity_uuid, identity_alternative_uuid, identity_permission_name)" +
                ")"
            );
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_identity ON identity_permission(identity_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_alt ON identity_permission(identity_alternative_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_name ON identity_permission(identity_permission_name)");
        }
    }

    /**
     * Returns the active JDBC connection for the Identity database.
     *
     * @return an open {@link Connection}, or {@code null} if unavailable
     */
    @Override
    public Connection getDBConnection() {
        return conn;
    }

    /**
     * Ensures the player's identity structures exist (identity row, primary alt, and session row).
     * Delegates to {@code ensureExistUtil.invoke(...)} for the PostgreSQL dialect.
     *
     * @param player Bukkit player whose identity structures should be present
     * @return {@code true} on success; {@code false} if the operation fails
     */
    @Override
    public boolean ensureExist(Player player) {
        return ensureExistUtil.invoke(conn, plugin, player);
    }

    /**
     * Returns the number of alternatives owned by the player's identity (including the primary).
     * Delegates to {@code getProfileAltCountUtil.invoke(...)}.
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
     * Delegates to {@code getProfileAltUuidByNameUtil.invoke(...)}.
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
     * Delegates to {@code createProfileAltUtil.invoke(...)}.
     *
     * @param player Bukkit player
     * @return the created alt UUID, or {@code null} when blocked by the limit or on error
     */
    @Override
    public String createProfileAlt(Player player) {
        return createProfileAltUtil.invoke(conn, plugin, player);
    }

    /**
     * Switches the active session alt for the player's identity.
     * Delegates to {@code changeProfileAltUtil.invoke(...)}.
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
     * Delegates to {@code setProfileAltnameUtil.invoke(...)}.
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
     * Delegates to {@code getProfileAltNameUtil.invoke(...)}.
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
     * Delegates to {@code getProfileAllAltUtil.invoke(...)}.
     *
     * @param player Bukkit player
     * @return list of alt identifiers or names (never {@code null})
     */
    @Override
    public java.util.List<String> getProfileAllAlt(Player player) {
        return getProfileAllAltUtil.invoke(conn, plugin, player);
    }

    /**
     * Increases the identity's allowed number of alternatives by {@code amount}.
     * Delegates to {@code addProfileAltLimitUtil.invoke(...)}.
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
     * Delegates to {@code getProfileAltLimitUtil.invoke(...)}.
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
     * Delegates to {@code addProfileAltPermissionUtil.invoke(...)}.
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
     * Delegates to {@code hasProfileAltCountUtil.invoke(...)}.
     *
     * @param player   owner {@link Player} of the identity
     * @param altUuid  alternative UUID to check
     * @param permName permission name to check
     * @return {@code true} if a matching permission row exists; otherwise {@code false}
     */
    @Override
    public boolean hasProfileAltCount(Player player, String altUuid, String permName) {
        return hasProfileAltCountUtil.invoke(conn, plugin, player, altUuid, permName);
    }

    /**
     * Persists a serialized inventory payload for the active alt recorded in {@code identity_session}.
     * Delegates to {@code saveProfileAltInventoryUtil.invoke(...)}.
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
     * Delegates to {@code loadProfileAltInventoryUtil.invoke(...)}.
     *
     * @param player the player whose active alt to load
     * @return inventory bytes, or {@code null} when no data is stored
     */
    @Override
    public byte[] loadProfileAltInventory(Player player) {
        return loadProfileAltInventoryUtil.invoke(conn, plugin, player);
    }
}
