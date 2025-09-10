package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Utility for loading serialized inventory bytes for the player's active alt (PostgreSQL dialect).
 * <p>
 * The active alt is determined from {@code identity_session}.
 */
public final class loadProfileAltInventoryUtil {

    /** Prevents instantiation of this utility class. */
    private loadProfileAltInventoryUtil() {}

    /**
     * Loads the serialized inventory payload for the player's currently active alt, if present.
     *
     * @param conn   active PostgreSQL {@link Connection}; if {@code null}, returns {@code null}
     * @param plugin Bukkit {@link Plugin} used for logging warnings
     * @param player target {@link Player} whose active alt is resolved via {@code identity_session}
     * @return inventory bytes or {@code null} when not found / not set / SQL error
     */
    public static byte[] invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return null;
        final String identityUuid = player.getUniqueId().toString();

        try {
            String altUuid = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid=?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) altUuid = rs.getString(1); }
            }
            if (altUuid == null) return null;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_alternative_storage FROM identity_alternative " +
                    "WHERE identity_alternative_uuid=? AND identity_uuid=?")) {
                ps.setString(1, altUuid);
                ps.setString(2, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getBytes(1) : null; }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadProfileAltInventoryUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
