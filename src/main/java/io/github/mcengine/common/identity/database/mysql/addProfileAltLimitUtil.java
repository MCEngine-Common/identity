package io.github.mcengine.common.identity.database.mysql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Utility for incrementing the allowed alternative (alt) limit for a player's identity (MySQL dialect).
 * <p>
 * This class is a static holder for the {@link #invoke(Connection, Plugin, Player, int)} operation and
 * is not meant to be instantiated.
 */
public final class addProfileAltLimitUtil {

    /** Prevents instantiation of this utility class. */
    private addProfileAltLimitUtil() {}

    /**
     * Increments the {@code identity.identity_limit} value for the given player's identity by {@code amount}.
     * <ul>
     *   <li>Upserts the {@code identity} row (default limit=1) if missing.</li>
     *   <li>Executes an atomic {@code UPDATE} to add {@code amount} to the current limit.</li>
     * </ul>
     *
     * @param conn    active MySQL {@link Connection}; must not be {@code null}
     * @param plugin  Bukkit {@link Plugin} used for logging warnings
     * @param player  target {@link Player} whose identity limit will be updated
     * @param amount  non-negative increment to add to the current limit
     * @return {@code true} if the limit row was updated successfully; {@code false} on validation failure or SQL error
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, int amount) {
        if (conn == null || amount < 0) return false;
        final String identityUuid = player.getUniqueId().toString();
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            // Ensure identity row exists (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) ON DUPLICATE KEY UPDATE identity_updated_at=VALUES(identity_updated_at)")) {
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
            plugin.getLogger().warning("addProfileAltLimitUtil failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
