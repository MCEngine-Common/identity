package io.github.mcengine.common.identity.database.postgresql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for switching the active session alt for a player (PostgreSQL dialect).
 */
public final class changeProfileAltUtil {

    /** Prevents instantiation of this utility class. */
    private changeProfileAltUtil() {}

    /**
     * Verifies the alt belongs to the player and then upserts {@code identity_session}.
     *
     * @param conn    active PostgreSQL {@link Connection}; if {@code null}, returns {@code false}
     * @param plugin  Bukkit {@link Plugin} used for logging warnings
     * @param player  owner {@link Player} of the identity
     * @param altUuid alternative UUID to activate (must belong to {@code player})
     * @return {@code true} if the session row was inserted/updated; {@code false} on validation failure or SQL error
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altUuid) {
        if (conn == null || altUuid == null || altUuid.isEmpty()) return false;
        final String identityUuid = player.getUniqueId().toString();

        try {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                chk.setString(1, altUuid);
                chk.setString(2, identityUuid);
                try (ResultSet rs = chk.executeQuery()) { if (!rs.next()) return false; }
            }

            try (PreparedStatement up = conn.prepareStatement(
                    "INSERT INTO identity_session (identity_uuid, identity_alternative_uuid) VALUES (?, ?) " +
                    "ON CONFLICT(identity_uuid) DO UPDATE SET identity_alternative_uuid=EXCLUDED.identity_alternative_uuid")) {
                up.setString(1, identityUuid);
                up.setString(2, altUuid);
                return up.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("changeProfileAltUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
