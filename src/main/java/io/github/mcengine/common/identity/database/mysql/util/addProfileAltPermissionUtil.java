package io.github.mcengine.common.identity.database.mysql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Utility for adding or refreshing a permission for an alternative (MySQL dialect).
 * <p>
 * Table: {@code identity_permission}
 * Columns: {@code identity_alternative_uuid, identity_permission_name, created_at, updated_at}
 */
public final class addProfileAltPermissionUtil {

    private addProfileAltPermissionUtil() {}

    /**
     * Adds or refreshes a permission row for the given alt.
     *
     * @param conn    MySQL connection
     * @param plugin  Bukkit plugin for logging
     * @param player  owner of the alt (not used directly in INSERT)
     * @param altUuid alternative UUID to attach the permission to
     * @param permName permission node
     * @return true if inserted/updated, false otherwise
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty()) return false;
        if (permName == null || permName.isEmpty()) return false;

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO identity_permission (" +
                        "identity_alternative_uuid, identity_permission_name, created_at, updated_at" +
                        ") VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE updated_at=CURRENT_TIMESTAMP")) {
            ps.setString(1, altUuid);
            ps.setString(2, permName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltPermissionUtil (mysql) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
