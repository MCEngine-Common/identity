package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Utility for reading (and ensuring) the alt limit for a player's identity (PostgreSQL dialect).
 * <p>
 * If the identity row does not exist, it is upserted with a default limit of {@code 1} and the
 * effective limit is returned.
 */
public final class getProfileAltLimitUtil {

    /** Prevents instantiation of this utility class. */
    private getProfileAltLimitUtil() {}

    /**
     * Returns the configured alt limit for the player's identity.
     * <ul>
     *   <li>Upserts the {@code identity} row if missing (default limit=1).</li>
     *   <li>Reads and returns {@code identity_limit}.</li>
     * </ul>
     *
     * @param conn   active PostgreSQL {@link Connection}; if {@code null}, returns {@code 1}
     * @param plugin Bukkit {@link Plugin} used for logging warnings
     * @param player owner {@link Player} of the identity
     * @return current alt limit (≥ 1); defaults to {@code 1} on error
     */
    public static int invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return 1;
        final String identityUuid = player.getUniqueId().toString();
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            // Upsert identity if missing (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) " +
                    "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }

            // Read limit
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_limit FROM identity WHERE identity_uuid=?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 1; }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltLimitUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
