package io.github.mcengine.common.identity.database.sqlite;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Persists serialized inventory bytes for the player's active alt from {@code identity_session}.
 */
public final class saveProfileAltInventoryUtil {

    /**
     * Utility class; not instantiable.
     */
    private saveProfileAltInventoryUtil() {}

    /**
     * Stores {@code payload} into {@code identity_alternative.identity_alternative_storage} for the
     * player's currently active alternative and updates its timestamp.
     *
     * @param conn    active SQLite {@link Connection}
     * @param plugin  Bukkit {@link Plugin} for logging
     * @param player  owner {@link Player}
     * @param payload serialized inventory bytes (non-null)
     * @return {@code true} if the row was updated; {@code false} if no active alt or on error
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, byte[] payload) {
        if (conn == null || payload == null) return false;
        final String identityUuid = player.getUniqueId().toString();
        final String now = java.time.Instant.now().toString();

        try {
            String altUuid = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid=?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) altUuid = rs.getString(1); }
            }
            if (altUuid == null) return false;

            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE identity_alternative " +
                    "SET identity_alternative_storage=?, identity_alternative_updated_at=? " +
                    "WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                up.setBytes(1, payload);
                up.setString(2, now);
                up.setString(3, altUuid);
                up.setString(4, identityUuid);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("saveProfileAltInventoryUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
