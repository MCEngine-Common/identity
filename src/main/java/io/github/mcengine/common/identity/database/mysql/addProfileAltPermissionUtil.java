package io.github.mcengine.common.identity.database.mysql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Adds or refreshes a permission for a specific alt (composite PK prevents duplicates).
 */
public final class addProfileAltPermissionUtil {
    private addProfileAltPermissionUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty() || permName == null || permName.isEmpty()) return false;

        final String identityUuid = player.getUniqueId().toString();
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) { if (!rs.next()) return false; }
            }

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
