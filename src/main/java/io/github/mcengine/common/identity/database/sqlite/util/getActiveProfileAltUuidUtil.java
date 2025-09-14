package io.github.mcengine.common.identity.database.sqlite.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class getActiveProfileAltUuidUtil {
    private getActiveProfileAltUuidUtil() {}

    public static String invoke(Connection conn, Plugin plugin, Player player) {
        if (player == null) return null;
        final String identityUuid = player.getUniqueId().toString(); // your identity PK

        final String sql =
            "SELECT identity_alternative_uuid " +
            "FROM identity_session WHERE identity_uuid = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1); // may be null if cleared
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[IdentitySQLite] getActiveProfileAltUuid failed: " + e.getMessage());
        }
        return null;
    }
}
