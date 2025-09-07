package io.github.mcengine.common.identity.database.sqlite;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Adds or refreshes a permission for a specific alt (composite PK prevents duplicates).
 */
public final class addProfileAltPermissionUtil {
    private addProfileAltPermissionUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty() || permName == null || permName.isEmpty()) return false;

        final String identityUuid = player.getUniqueId().toString();
        final String now = java.time.Instant.now().toString();

        try {
            // Validate alt belongs to player's identity
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) { if (!rs.next()) return false; }
            }

            // Try insert; if duplicate (PK), refresh updated_at
            int inserted;
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT OR IGNORE INTO identity_permission (" +
                    "identity_uuid, identity_alternative_uuid, identity_permission_name, " +
                    "identity_permission_created_at, identity_permission_updated_at) " +
                    "VALUES (?,?,?,?,?)")) {
                ins.setString(1, identityUuid);
                ins.setString(2, altUuid);
                ins.setString(3, permName);
                ins.setString(4, now);
                ins.setString(5, now);
                inserted = ins.executeUpdate();
            }
            if (inserted > 0) return true;

            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE identity_permission SET identity_permission_updated_at=? " +
                    "WHERE identity_uuid=? AND identity_alternative_uuid=? AND identity_permission_name=?")) {
                up.setString(1, now);
                up.setString(2, identityUuid);
                up.setString(3, altUuid);
                up.setString(4, permName);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltPermissionUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
