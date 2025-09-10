package io.github.mcengine.common.identity.database.sqlite.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists all alts for the player's identity (name if set, else UUID), ordered by UUID asc.
 */
public final class getProfileAllAltUtil {

    /**
     * Utility class; not instantiable.
     */
    private getProfileAllAltUtil() {}

    /**
     * Returns all alternative entries for the player's identity.
     * Each list element is the display name if present; otherwise the alt UUID.
     *
     * @param conn   active SQLite {@link Connection}
     * @param plugin Bukkit {@link Plugin} for logging
     * @param player owner {@link Player}
     * @return ordered list of alternative identifiers or names (never {@code null})
     */
    public static List<String> invoke(Connection conn, Plugin plugin, Player player) {
        final List<String> out = new ArrayList<>();
        if (conn == null) return out;

        final String identityUuid = player.getUniqueId().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_alternative_uuid, identity_alternative_name " +
                "FROM identity_alternative WHERE identity_uuid=? ORDER BY identity_alternative_uuid ASC")) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final String uuid = rs.getString(1);
                    final String name = rs.getString(2);
                    out.add((name != null && !name.isEmpty()) ? name : uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAllAltUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
        }
        return out;
    }
}
