package io.github.mcengine.common.identity.database.postgresql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for listing all alternatives for a player's identity (PostgreSQL dialect).
 * <p>
 * Each entry is the alt's display name if set; otherwise the alt UUID (e.g., {@code {uuid}-N}).
 * Results are ordered by {@code identity_alternative_uuid} ascending.
 */
public final class getProfileAllAltUtil {

    /** Prevents instantiation of this utility class. */
    private getProfileAllAltUtil() {}

    /**
     * Returns all alternatives for the player's identity.
     *
     * @param conn   active PostgreSQL {@link Connection}; if {@code null}, returns an empty list
     * @param plugin Bukkit {@link Plugin} used for logging warnings
     * @param player owner {@link Player} of the identity
     * @return a list of display names or UUIDs (never {@code null}); empty list on error or when none exist
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
            plugin.getLogger().warning("getProfileAllAltUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
        }
        return out;
    }
}
