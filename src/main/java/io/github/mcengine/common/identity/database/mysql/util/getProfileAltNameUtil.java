package io.github.mcengine.common.identity.database.mysql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for fetching the display name of a specific alternative (alt) associated with a player (MySQL dialect).
 * <p>
 * This is a static helper with a single {@link #invoke(Connection, Plugin, Player, String)} entrypoint.
 */
public final class getProfileAltNameUtil {

    /** Prevent instantiation of this utility class. */
    private getProfileAltNameUtil() {}

    /**
     * Looks up the display name for {@code altUuid} owned by {@code player}.
     *
     * @param conn     active MySQL {@link Connection}; if {@code null}, {@code null} is returned
     * @param plugin   Bukkit {@link Plugin} for logging warnings
     * @param player   owner {@link Player} of the identity
     * @param altUuid  target alternative UUID to resolve
     * @return the display name if present; {@code null} if unset, not found, validation fails, or on SQL error
     */
    public static String invoke(Connection conn, Plugin plugin, Player player, String altUuid) {
        if (conn == null || altUuid == null || altUuid.isEmpty()) return null;
        final String identityUuid = player.getUniqueId().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identity_alternative_name " +
                "FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
            ps.setString(1, altUuid);
            ps.setString(2, identityUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltNameUtil failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
