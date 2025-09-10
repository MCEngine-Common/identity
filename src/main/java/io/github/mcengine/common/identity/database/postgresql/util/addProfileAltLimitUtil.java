package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Utility for incrementing the allowed alternative (alt) limit for a player's identity (PostgreSQL dialect).
 * <p>
 * Provides a single static {@link #invoke(Connection, Plugin, Player, int)} entrypoint.
 */
public final class addProfileAltLimitUtil {

    /** Prevents instantiation of this utility class. */
    private addProfileAltLimitUtil() {}

    /**
     * Increments {@code identity.identity_limit} for the given player's identity by {@code amount}.
     * <ul>
     *   <li>Upserts the {@code identity} row with default limit=1 if missing.</li>
     *   <li>Performs an atomic {@code UPDATE} to add {@code amount} to the limit.</li>
     * </ul>
     *
     * @param conn   active PostgreSQL {@link Connection}; if {@code null}, returns {@code false}
     * @param plugin Bukkit {@link Plugin} used for logging warnings
     * @param player owner {@link Player} of the identity
     * @param amount non-negative increment to add
     * @return {@code true} if the limit was updated; {@code false} on validation failure or SQL error
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, int amount) {
        if (conn == null || amount < 0) return false;
        final String identityUuid = player.getUniqueId().toString();
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            // Ensure identity row exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) " +
                    "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }

            // Increment limit
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE identity SET identity_limit = identity_limit + ? WHERE identity_uuid=?")) {
                ps.setInt(1, amount);
                ps.setString(2, identityUuid);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltLimitUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
