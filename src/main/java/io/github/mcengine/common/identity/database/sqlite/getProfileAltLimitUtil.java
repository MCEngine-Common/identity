package io.github.mcengine.common.identity.database.sqlite;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Reads the alt limit for the player's identity (upserts identity if missing).
 */
public final class getProfileAltLimitUtil {
    private getProfileAltLimitUtil() {}

    public static int invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return 1;
        final String identityUuid = player.getUniqueId().toString();
        final String now = java.time.Instant.now().toString();

        try {
            // Upsert identity if missing (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) " +
                    "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=excluded.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setString(2, now);
                ps.setString(3, now);
                ps.executeUpdate();
            }

            // Read limit
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_limit FROM identity WHERE identity_uuid=?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 1; }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltLimitUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
