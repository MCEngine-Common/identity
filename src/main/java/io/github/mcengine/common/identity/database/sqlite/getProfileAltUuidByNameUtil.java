package io.github.mcengine.common.identity.database.sqlite;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Resolves an alt UUID by its display name for the given player.
 */
public final class getProfileAltUuidByNameUtil {

    /**
     * Utility class; not instantiable.
     */
    private getProfileAltUuidByNameUtil() {}

    /**
     * Looks up the alternative UUID by display name for the player's identity.
     *
     * @param conn   active SQLite {@link Connection}
     * @param plugin Bukkit {@link Plugin} for logging
     * @param player owner {@link Player}
     * @param altName display name to resolve
     * @return alt UUID string if found; otherwise {@code null}
     */
    public static String invoke(Connection conn, Plugin plugin, Player player, String altName) {
        if (conn == null || altName == null || altName.isEmpty()) return null;
        final String identityUuid = player.getUniqueId().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_alternative_uuid FROM identity_alternative " +
                "WHERE identity_uuid=? AND identity_alternative_name=?")) {
            ps.setString(1, identityUuid);
            ps.setString(2, altName);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltUuidByNameUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
