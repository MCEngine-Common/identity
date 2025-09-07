package io.github.mcengine.common.identity.database.postgresql;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Creates a new alternative for the player's identity, enforcing {@code identity_limit}.
 */
public final class createProfileAltUtil {
    private createProfileAltUtil() {}

    /**
     * Creates a new alt using the next index pattern {@code {uuid}-{n}} if under the limit.
     *
     * @param conn   active connection
     * @param plugin plugin logger
     * @param player target player
     * @return created alt UUID or null when blocked/failure
     */
    public static String invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return null;
        final String identityUuid = player.getUniqueId().toString();
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            // Ensure identity exists (limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) " +
                    "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }

            int limit = 1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT identity_limit FROM identity WHERE identity_uuid=?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) limit = rs.getInt(1); }
            }

            int count = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM identity_alternative WHERE identity_uuid=?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) count = rs.getInt(1); }
            }

            if (count >= limit) return null; // limit reached

            final String altUuid = identityUuid + "-" + count;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity_alternative (" +
                    "identity_alternative_uuid, identity_uuid, identity_alternative_name, " +
                    "identity_alternative_storage, identity_alternative_created_at, identity_alternative_updated_at) " +
                    "VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, altUuid);
                ps.setString(2, identityUuid);
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.BINARY);
                ps.setTimestamp(5, now);
                ps.setTimestamp(6, now);
                ps.executeUpdate();
            }
            return altUuid;
        } catch (SQLException e) {
            plugin.getLogger().warning("createProfileAltUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
