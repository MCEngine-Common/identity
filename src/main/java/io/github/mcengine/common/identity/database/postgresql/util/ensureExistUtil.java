package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;

/**
 * Ensures the player's identity base rows exist (PostgreSQL).
 * <p>Creates/updates {@code identity}, primary alt {@code {uuid}-0}, and {@code identity_session}.</p>
 */
public final class ensureExistUtil {
    private ensureExistUtil() {}

    /**
     * Upserts identity (limit=1), ensures primary alt, and ensures session pointing to primary alt.
     *
     * @param conn   active connection
     * @param plugin plugin for logging
     * @param player target player
     * @return true if all steps succeed, false otherwise
     */
    public static boolean invoke(Connection conn, Plugin plugin, Player player) {
        if (conn == null) return false;
        final String identityUuid = player.getUniqueId().toString();
        final String primaryAltUuid = identityUuid + "-0";
        final Timestamp now = Timestamp.from(Instant.now());

        try {
            // 1) Upsert identity (default limit=1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) " +
                    "VALUES (?, 1, ?, ?) " +
                    "ON CONFLICT(identity_uuid) DO UPDATE SET identity_updated_at=EXCLUDED.identity_updated_at")) {
                ps.setString(1, identityUuid);
                ps.setTimestamp(2, now);
                ps.setTimestamp(3, now);
                ps.executeUpdate();
            }

            // 2) Ensure {uuid}-0 alt exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO identity_alternative (" +
                    "identity_alternative_uuid, identity_uuid, identity_alternative_name, " +
                    "identity_alternative_storage, identity_alternative_created_at, identity_alternative_updated_at" +
                    ") VALUES (?,?,?,?,?,?) " +
                    "ON CONFLICT(identity_alternative_uuid) DO NOTHING")) {
                ps.setString(1, primaryAltUuid);
                ps.setString(2, identityUuid);
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.BINARY);
                ps.setTimestamp(5, now);
                ps.setTimestamp(6, now);
                ps.executeUpdate();
            }

            // 3) Ensure session row exists (points to {uuid}-0 if absent)
            boolean hasSession;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM identity_session WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) { hasSession = rs.next(); }
            }
            if (!hasSession) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO identity_session (identity_uuid, identity_alternative_uuid) VALUES (?, ?)")) {
                    ps.setString(1, identityUuid);
                    ps.setString(2, primaryAltUuid);
                    ps.executeUpdate();
                }
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("ensureExistUtil (pg) failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
