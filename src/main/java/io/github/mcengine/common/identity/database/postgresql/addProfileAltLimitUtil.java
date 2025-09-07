package io.github.mcengine.common.identity.database.postgresql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Increments the allowed alt limit for the player's identity.
 */
public final class addProfileAltLimitUtil {
    private addProfileAltLimitUtil() {}

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
