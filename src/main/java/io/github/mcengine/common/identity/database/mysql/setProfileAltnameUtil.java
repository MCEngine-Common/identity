package io.github.mcengine.common.identity.database.mysql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Sets (or clears) the display name for an alt, enforcing uniqueness per identity.
 */
public final class setProfileAltnameUtil {
    private setProfileAltnameUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String altName) {
        if (conn == null || altUuid == null || altUuid.isEmpty()) return false;
        final String identityUuid = player.getUniqueId().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE identity_alternative " +
                "SET identity_alternative_name=?, identity_alternative_updated_at=CURRENT_TIMESTAMP " +
                "WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
            if (altName == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, altName);
            ps.setString(2, altUuid);
            ps.setString(3, identityUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("setProfileAltnameUtil failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
