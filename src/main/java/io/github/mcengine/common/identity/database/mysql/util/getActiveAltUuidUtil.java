package io.github.mcengine.common.identity.database.mysql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Reads the active alt UUID for a player's identity from identity_session (MySQL).
 *
 * Contract:
 * - Returns the string in identity_session.identity_alternative_uuid
 * - Returns null if the row doesn't exist or the column is NULL
 */
public final class getActiveAltUuidUtil {
    private getActiveAltUuidUtil() {}

    public static String invoke(Connection conn, Plugin plugin, Player player) {
        if (player == null) return null;
        final String identityUuid = player.getUniqueId().toString();

        final String sql =
            "SELECT identity_alternative_uuid " +
            "FROM identity_session WHERE identity_uuid = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1); // may be null
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[IdentityMySQL] getActiveAltUuid failed: " + e.getMessage());
        }
        return null;
    }
}
