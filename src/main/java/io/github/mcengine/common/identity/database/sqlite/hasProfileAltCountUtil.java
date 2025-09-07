package io.github.mcengine.common.identity.database.sqlite;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Checks whether a permission entry exists for the given alt.
 * <p>Method name follows the interface: {@code hasProfileAltCount} (permission existence).</p>
 */
public final class hasProfileAltCountUtil {

    /**
     * Utility class; not instantiable.
     */
    private hasProfileAltCountUtil() {}

    /**
     * Verifies that {@code altUuid} belongs to the player's identity and checks whether
     * the permission name exists for that alternative.
     *
     * @param conn     active SQLite {@link Connection}
     * @param plugin   Bukkit {@link Plugin} for logging
     * @param player   owner {@link Player}
     * @param altUuid  alternative UUID to check
     * @param permName permission name to check
     * @return {@code true} if a matching permission row exists; otherwise {@code false}
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String permName) {
        if (conn == null) return false;
        if (altUuid == null || altUuid.isEmpty() || permName == null || permName.isEmpty()) return false;

        final String identityUuid = player.getUniqueId().toString();

        try {
            // Validate alt belongs to player's identity
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) { if (!rs.next()) return false; }
            }

            // Existence check
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM identity_permission " +
                    "WHERE identity_uuid=? AND identity_alternative_uuid=? AND identity_permission_name=? LIMIT 1")) {
                ps.setString(1, identityUuid);
                ps.setString(2, altUuid);
                ps.setString(3, permName);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("hasProfileAltCountUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
