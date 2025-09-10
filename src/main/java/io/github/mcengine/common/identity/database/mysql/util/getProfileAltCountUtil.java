package io.github.mcengine.common.identity.database.mysql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for counting the number of alts for a player's identity (MySQL dialect).
 */
public final class getProfileAltCountUtil {

    /** Prevents instantiation of this utility class. */
    private getProfileAltCountUtil() {}

    /**
     * Counts rows in {@code identity_alternative} for the player's {@code identity_uuid}.
     *
     * @param conn   active MySQL {@link Connection}; must not be {@code null}
     * @param plugin Bukkit {@link Plugin} used for logging warnings
     * @param player owner {@link Player} of the identity
     * @return number of alternatives; {@code 0} on error or when none exist
     */
    public static int invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return 0;
        final String identityUuid = player.getUniqueId().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM identity_alternative WHERE identity_uuid=?")) {
            ps.setString(1, identityUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) {
            plugin.getLogger().warning("getProfileAltCountUtil failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
}
