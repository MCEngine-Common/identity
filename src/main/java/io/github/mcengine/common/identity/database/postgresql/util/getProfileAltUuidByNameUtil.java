package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for resolving an alt UUID from its display name for a given player (PostgreSQL dialect).
 */
public final class getProfileAltUuidByNameUtil {

    /** Prevents instantiation of this utility class. */
    private getProfileAltUuidByNameUtil() {}

    /**
     * Resolves the alternative UUID by display name for {@code player}.
     *
     * @param conn    active PostgreSQL {@link Connection}; if {@code null}, returns {@code null}
     * @param plugin  Bukkit {@link Plugin} used for logging warnings
     * @param player  owner {@link Player} of the identity
     * @param altName display name of the alt to resolve
     * @return the alt UUID if found; otherwise {@code null} (including on SQL error)
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
            plugin.getLogger().warning("getProfileAltUuidByNameUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
