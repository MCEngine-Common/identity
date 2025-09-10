package io.github.mcengine.common.identity.database.mysql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Switches the active session alt for the player.
 */
public final class changeProfileAltUtil {
    private changeProfileAltUtil() {}

    /**
     * Verifies the alt belongs to the player, then upserts {@code identity_session}.
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid) {
        if (conn == null || altUuid == null || altUuid.isEmpty()) return false;
        final String identityUuid = player.getUniqueId().toString();

        try {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) { if (!rs.next()) return false; }
            }

            try (PreparedStatement up = conn.prepareStatement(
                    "INSERT INTO identity_session (identity_uuid, identity_alternative_uuid) VALUES (?,?) " +
                    "ON DUPLICATE KEY UPDATE identity_alternative_uuid=VALUES(identity_alternative_uuid)")) {
                up.setString(1, identityUuid);
                up.setString(2, altUuid);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("changeProfileAltUtil failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
