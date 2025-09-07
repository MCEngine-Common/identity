package io.github.mcengine.common.identity.database.mysql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Reads the alt limit for the player's identity (upserts identity if missing).
 */
public final class getProfileAltLimitUtil {
    private getProfileAltLimitUtil() {}

    public static int invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return 1;
        final String identityUuid = player.getUniqueId().toString();
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) ON DUPLICATE KEY UPDATE identity_updated_at=VALUES(identity_updated_at)")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_limit FROM identity WHERE identity_uuid=?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 1; }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltLimitUtil failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
