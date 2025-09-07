package io.github.mcengine.common.identity.database.postgresql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for setting (or clearing) an alt's display name while enforcing per-identity uniqueness (PostgreSQL dialect).
 * <p>
 * The uniqueness constraint should be maintained by a unique index on
 * {@code (identity_uuid, identity_alternative_name)} at the database level.
 */
public final class setProfileAltnameUtil {

    /** Prevents instantiation of this utility class. */
    private setProfileAltnameUtil() {}

    /**
     * Updates the display name for an alternative belonging to {@code player}.
     *
     * @param conn    active PostgreSQL {@link Connection}; if {@code null}, returns {@code false}
     * @param plugin  Bukkit {@link Plugin} used for logging warnings
     * @param player  owner {@link Player} of the identity
     * @param altUuid alternative UUID to rename
     * @param altName new display name, or {@code null} to clear the name
     * @return {@code true} if the row was updated; {@code false} on validation failure or SQL error
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid, String altName) {
        if (conn == null || altUuid == null || altUuid.isEmpty()) return false;
        final String identityUuid = player.getUniqueId().toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE identity_alternative " +
                "SET identity_alternative_name=?, identity_alternative_updated_at=NOW() " +
                "WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
            if (altName == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, altName);
            ps.setString(2, altUuid);
            ps.setString(3, identityUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("setProfileAltnameUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
