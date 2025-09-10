package io.github.mcengine.common.identity.database.sqlite.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Increments the allowed alt limit for the player's identity.
 */
public final class addProfileAltLimitUtil {

    /**
     * Utility class; not instantiable.
     */
    private addProfileAltLimitUtil() {}

    /**
     * Increases the identity's allowed number of alternatives by {@code amount}.
     * <p>
     * The method first upserts the {@code identity} row (default limit = 1) and then
     * increments {@code identity.identity_limit} for the given {@code identity_uuid}.
     *
     * @param conn   active SQLite {@link Connection}
     * @param plugin Bukkit {@link Plugin} for logging
     * @param player target {@link Player} whose identity limit is increased
     * @param amount non-negative increment to apply
     * @return {@code true} if the limit row was updated; {@code false} otherwise (including validation failures)
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, int amount) {
        if (conn == null || amount < 0) return false;
        final String identityUuid = player.getUniqueId().toString();
        final String now = java.time.Instant.now().toString();

        try {
            // Ensure identity row exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) " +
                    "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=excluded.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setString(2, now);
                ps.setString(3, now);
                ps.executeUpdate();
            }

            // Increment limit
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE identity SET identity_limit = identity_limit + ? WHERE identity_uuid=?")) {
                ps.setInt(1, amount);
                ps.setString(2, identityUuid);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("addProfileAltLimitUtil (sqlite) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
