package io.github.mcengine.common.identity.listener;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

/**
 * Listens for player join/quit events, ensures the primary alternative
 * identifier <code>{uuid}-0</code> exists for each player, and performs
 * automatic inventory load/save on join/quit so players don't have to run
 * commands manually.
 */
public class MCEngineIdentityListener implements Listener {

    /** Shared Identity common API used for DB access and logging. */
    private final MCEngineIdentityCommon api;

    /**
     * Constructs the listener with the Identity common API.
     *
     * @param api the identity common API
     */
    public MCEngineIdentityListener(MCEngineIdentityCommon api) {
        this.api = api;
    }

    /**
     * Handles player joins:
     * <ol>
     *     <li>Ensures {@code identity(identity_uuid)} exists.</li>
     *     <li>Ensures {@code identity_alternative(identity_alternative_uuid = {uuid}-0)} exists.</li>
     *     <li>Ensures a {@code identity_session} row exists and points to {@code {uuid}-0} if none present.</li>
     *     <li>Automatically loads the active alt's inventory into the player, if stored.</li>
     * </ol>
     *
     * @param event Bukkit player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String identityUuid = player.getUniqueId().toString();
        String primaryAltUuid = identityUuid + "-0";

        Connection c = api.getDB().getDBConnection();
        if (c == null) {
            api.getPlugin().getLogger().warning("DB connection unavailable; cannot ensure primary alt for " + identityUuid);
            return;
        }

        try {
            // 1) Ensure identity row exists
            boolean hasIdentity = false;
            try (PreparedStatement chk = c.prepareStatement(
                    "SELECT 1 FROM identity WHERE identity_uuid = ?")) {
                chk.setString(1, identityUuid);
                try (ResultSet rs = chk.executeQuery()) {
                    hasIdentity = rs.next();
                }
            }
            if (!hasIdentity) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO identity (identity_uuid, identity_limit, identity_created_at, identity_updated_at) VALUES (?, 1, ?, ?)")) {
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    ins.setString(1, identityUuid);
                    ins.setTimestamp(2, now);
                    ins.setTimestamp(3, now);
                    ins.executeUpdate();
                }
            }

            // 2) Ensure {uuid}-0 alternative exists
            boolean hasPrimaryAlt = false;
            try (PreparedStatement chkAlt = c.prepareStatement(
                    "SELECT 1 FROM identity_alternative WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
                chkAlt.setString(1, primaryAltUuid);
                chkAlt.setString(2, identityUuid);
                try (ResultSet rs = chkAlt.executeQuery()) {
                    hasPrimaryAlt = rs.next();
                }
            }
            if (!hasPrimaryAlt) {
                try (PreparedStatement insAlt = c.prepareStatement(
                        "INSERT INTO identity_alternative (" +
                                "identity_alternative_uuid, identity_uuid, identity_alternative_name, " +
                                "identity_alternative_storage, identity_alternative_created_at, identity_alternative_updated_at" +
                                ") VALUES (?,?,?,?,?,?)")) {
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    insAlt.setString(1, primaryAltUuid);
                    insAlt.setString(2, identityUuid);
                    insAlt.setNull(3, java.sql.Types.VARCHAR);
                    insAlt.setNull(4, java.sql.Types.BLOB);
                    insAlt.setTimestamp(5, now);
                    insAlt.setTimestamp(6, now);
                    insAlt.executeUpdate();
                }
            }

            // 3) Ensure session row exists; if absent, point to {uuid}-0
            boolean hasSession = false;
            try (PreparedStatement chkSess = c.prepareStatement(
                    "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid = ?")) {
                chkSess.setString(1, identityUuid);
                try (ResultSet rs = chkSess.executeQuery()) {
                    hasSession = rs.next();
                }
            }
            if (!hasSession) {
                try (PreparedStatement insSess = c.prepareStatement(
                        "INSERT INTO identity_session (identity_uuid, identity_alternative_uuid) VALUES (?, ?)")) {
                    insSess.setString(1, identityUuid);
                    insSess.setString(2, primaryAltUuid);
                    insSess.executeUpdate();
                }
            }

            // 4) Auto-load inventory for the active alt (if any)
            api.loadActiveAltInventory(player);

        } catch (Exception e) {
            api.getPlugin().getLogger().warning("Failed ensuring primary alt / autoload for " + identityUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles player quits by automatically saving the active alt's inventory.
     *
     * @param event Bukkit player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean ok = api.saveActiveAltInventory(player);
        if (!ok) {
            api.getPlugin().getLogger().fine("No active alt or nothing to save for " + player.getUniqueId());
        }
    }
}
