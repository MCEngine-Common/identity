package io.github.mcengine.common.identity.database.postgresql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for persisting serialized inventory bytes for the player's active alt (PostgreSQL dialect).
 * <p>
 * The active alt is determined from {@code identity_session}; the bytes are written to
 * {@code identity_alternative.identity_alternative_storage}.
 */
public final class saveProfileAltInventoryUtil {

    /** Prevents instantiation of this utility class. */
    private saveProfileAltInventoryUtil() {}

    /**
     * Persists the serialized inventory payload to the active alt for {@code player}.
     *
     * @param conn    active PostgreSQL {@link Connection}; if {@code null}, returns {@code false}
     * @param plugin  Bukkit {@link Plugin} used for logging warnings
     * @param player  target {@link Player} whose active alt is resolved via {@code identity_session}
     * @param payload opaque serialized inventory bytes to store
     * @return {@code true} if the storage column was updated; {@code false} if no active alt, validation fails, or SQL error
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, byte[] payload) {
        if (conn == null || payload == null) return false;
        final String identityUuid = player.getUniqueId().toString();

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
                    "SET identity_alternative_storage=?, identity_alternative_updated_at=NOW() " +
                    "WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                up.setBytes(1, payload);
                up.setString(2, altUuid);
                up.setString(3, identityUuid);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("saveProfileAltInventoryUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
