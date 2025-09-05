package io.github.mcengine.common.identity.listener;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for player lifecycle and inventory events:
 * <ul>
 *     <li>On join: ensures identity + primary alt + session via {@code api.ensureExist(player)},
 *     and auto-loads the active alt inventory.</li>
 *     <li>On quit: auto-save active alt inventory.</li>
 *     <li>On inventory open (player crafting view): auto-load active alt inventory.</li>
 *     <li>On inventory close (player crafting view): auto-save active alt inventory.</li>
 * </ul>
 * This removes the need for players to run manual save/load commands and avoids direct SQL here.
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
     * On player join, ensure their identity structures exist and load the active alt inventory.
     *
     * @param event Bukkit player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            // Consolidated ensure logic (identity, {uuid}-0 alt, session) implemented in the DB layer.
            api.ensureExist(player);
            // Auto-load inventory for the active alt (if any)
            api.loadActiveAltInventory(player);
        } catch (Exception e) {
            api.getPlugin().getLogger().warning("Failed during ensure/load for " + player.getUniqueId() + ": " + e.getMessage());
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

    /**
     * When a player opens their own crafting (player) inventory, auto-load the
     * active alt inventory so it is always in sync without manual commands.
     *
     * @param event Bukkit inventory open event
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Only react to the player's main (crafting) inventory screen
        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            api.loadActiveAltInventory(player);
        }
    }

    /**
     * When a player closes their crafting (player) inventory, auto-save the
     * active alt inventory so changes persist automatically.
     *
     * @param event Bukkit inventory close event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            api.saveActiveAltInventory(player);
        }
    }
}
