package io.github.mcengine.common.identity.database.sqlite;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Sets (or clears) the display name for an alt, enforcing uniqueness per identity.
 */
public final class setProfileAltnameUtil {

    /**
     * Utility class; not instantiable.
     */
    private setProfileAltnameUtil() {}

    /**
     * Updates {@code identity_alternative.identity_alternative_name} for {@code altUuid}
     * if the alternative belongs to the player's identity. When {@code altName} is {@code null},
     * the name is cleared.
     *
     * @param conn    active SQLite {@link Connection}
     * @param plugin  Bukkit {@link Plugin} for logging
     * @param player  owner {@link Player}
     * @param altUuid alternative UUID to rename
     * @param altName new display name (nullable to clear)
     * @return {@code true} if the row was updated; otherwise {@code false}
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String altName) {
        if (conn == null || altUuid == null || altUuid.isEmpty()) return false;
        final String identityUuid = player.getUniqueId().toString();
        final String now = java.time.Instant.now().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE identity_alternative " +
                "SET identity_alternative_name=?, identity_alternative_updated_at=? " +
                "WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
            if (altName == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, altName);
            ps.setString(2, now);
            ps.setString(3, altUuid);
            ps.setString(4, identityUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("setProfileAltnameUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
