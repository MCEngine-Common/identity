package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Reads the active alt UUID for a player's identity from identity_session (PostgreSQL).
 *
 * Contract:
 * - Returns the string in identity_session.identity_alternative_uuid
 * - Returns null if the row doesn't exist or the column is NULL
 */
public final class getActiveProfileAltUuidUtil {
    private getActiveProfileAltUuidUtil() {}

    public static String invoke(Connection conn, Plugin plugin, Player player) {
        if (player == null) return null;
        final String identityUuid = player.getUniqueId().toString();

        final String sql =
            "SELECT identity_alternative_uuid " +
            "FROM identity_session WHERE identity_uuid = $1"; // PostgreSQL also supports '?', but $1 is explicit

        // If your JDBC driver expects '?', switch to the '?' version:
        // final String sql = "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // For '$1' placeholders most apps still use '?'. If you keep '$1', some drivers require setString(1, ...) the same way.
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1); // may be null
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[IdentityPostgreSQL] getActiveProfileAltUuid failed: " + e.getMessage());
        }
        return null;
    }
}
