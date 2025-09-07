package io.github.mcengine.common.identity.database.mysql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Fetches the display name of a specific alt.
 */
public final class getProfileAltNameUtil {
    private getProfileAltNameUtil() {}

    public static String invoke(Connection conn, Plugin plugin, Player player, String altUuid) {
        if (conn == null || altUuid == null || altUuid.isEmpty()) return null;
        final String identityUuid = player.getUniqueId().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_alternative_name " +
                "FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
            ps.setString(1, altUuid);
            ps.setString(2, identityUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltNameUtil failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
