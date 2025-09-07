package io.github.mcengine.common.identity.database.mysql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Checks whether a permission entry exists for the given alt.
 * <p>Method name follows the interface: {@code hasProfileAltCount} (permission existence).</p>
 */
public final class hasProfileAltCountUtil {
    private hasProfileAltCountUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty() || permName == null || permName.isEmpty()) return false;

        final String identityUuid = player.getUniqueId().toString();

        try {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) { if (!rs.next()) return false; }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM identity_permission " +
                    "WHERE identity_uuid=? AND identity_alternative_uuid=? AND identity_permission_name=? LIMIT 1")) {
                ps.setString(1, identityUuid);
                ps.setString(2, altUuid);
                ps.setString(3, permName);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("hasProfileAltCountUtil failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
