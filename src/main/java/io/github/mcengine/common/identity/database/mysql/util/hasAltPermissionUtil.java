package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for checking whether a permission entry exists for a given alt belonging to a player (PostgreSQL dialect).
 * <p>
 * The name mirrors the interface method: {@code hasAltPermission}.
 */
public final class hasAltPermissionUtil {

    /** Prevents instantiation of this utility class. */
    private hasAltPermissionUtil() {}

    /**
     * Verifies {@code altUuid} belongs to {@code player} and checks for an existing permission row.
     *
     * @param conn     active PostgreSQL {@link Connection}; if {@code null}, returns {@code false}
     * @param plugin   Bukkit {@link Plugin} used for logging warnings
     * @param player   owner {@link Player} of the identity
     * @param altUuid  alternative UUID to check
     * @param permName permission name to check
     * @return {@code true} if a matching permission row exists; {@code false} otherwise (including validation/SQL error)
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

            // Existence check (permission scoped to alternative only)
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM identity_permission " +
                    "WHERE identity_alternative_uuid=? AND identity_permission_name=? LIMIT 1")) {
                ps.setString(1, altUuid);
                ps.setString(2, permName);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("hasAltPermissionUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
