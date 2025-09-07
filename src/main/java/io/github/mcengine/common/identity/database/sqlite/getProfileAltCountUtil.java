package io.github.mcengine.common.identity.database.sqlite;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Counts the number of alts for the player's identity.
 */
public final class getProfileAltCountUtil {
    private getProfileAltCountUtil() {}

    public static int invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return 0;
        final String identityUuid = player.getUniqueId().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM identity_alternative WHERE identity_uuid=?")) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltCountUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
}
