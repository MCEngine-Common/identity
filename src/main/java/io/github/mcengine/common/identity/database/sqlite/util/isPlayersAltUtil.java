package io.github.mcengine.common.identity.database.sqlite.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class isPlayersAltUtil {
    private isPlayersAltUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid) {
        final String sql = "SELECT 1 FROM identity_alternative WHERE identity_uuid = ? AND identity_alternative_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, altUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[IdentitySQLite] isPlayersAlt failed: " + e.getMessage());
            return false;
        }
    }
}
