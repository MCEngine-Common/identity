package io.github.mcengine.common.identity.database.mysql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Utility for adding or refreshing a permission for a specific alt (MySQL dialect).
 * <p>
 * The composite primary key on {@code identity_permission} prevents duplicates; when a duplicate
 * is detected, the row's {@code identity_permission_updated_at} is refreshed.
 */
public final class addProfileAltPermissionUtil {

    /** Prevents instantiation of this utility class. */
    private addProfileAltPermissionUtil() {}

    /**
     * Adds a permission to the specified alt belonging to the given player, or refreshes the timestamp
     * if the permission already exists.
     * <ol>
     *   <li>Validates that {@code altUuid} belongs to the player's identity.</li>
     *   <li>Performs an upsert into {@code identity_permission}.</li>
     * </ol>
     *
     * @param conn     active MySQL {@link Connection}; must not be {@code null}
     * @param plugin   Bukkit {@link Plugin} used for logging warnings
     * @param player   owner {@link Player} of the identity
     * @param altUuid  alternative UUID that will receive the permission (must belong to player)
     * @param permName permission name to add/refresh (non-null, non-empty)
     * @return {@code true} if inserted or updated successfully; {@code false} if validation fails or on SQL error
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty() || permName == null || permName.isEmpty()) return false;

        final String identityUuid = player.getUniqueId().toString();
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            // Validate alt belongs to player's identity
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) { if (!rs.next()) return false; }
            }

            // Upsert permission (composite PK)
            try (PreparedStatement up = conn.prepareStatement(
                    "INSERT INTO identity_permission (" +
                    "identity_uuid, identity_alternative_uuid, identity_permission_name, " +
                    "identity_permission_created_at, identity_permission_updated_at) " +
                    "VALUES (?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE identity_permission_updated_at=VALUES(identity_permission_updated_at)")) {
                up.setString(1, identityUuid);
                up.setString(2, altUuid);
                up.setString(3, permName);
                up.setTimestamp(4, now);
                up.setTimestamp(5, now);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltPermissionUtil failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
