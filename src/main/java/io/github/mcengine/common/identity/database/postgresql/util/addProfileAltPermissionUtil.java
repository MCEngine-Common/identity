package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Adds or refreshes a permission for an alternative (PostgreSQL dialect).
 */
public final class addProfileAltPermissionUtil {

    private addProfileAltPermissionUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player,
                                 String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty()) return false;
        if (permName == null || permName.isEmpty()) return false;

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO identity_permission (" +
                        "identity_alternative_uuid, identity_permission_name" +
                        ") VALUES (?, ?) " +
                        "ON CONFLICT (identity_alternative_uuid, identity_permission_name) DO NOTHING")) {
            ps.setString(1, altUuid);
            ps.setString(2, permName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltPermissionUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
