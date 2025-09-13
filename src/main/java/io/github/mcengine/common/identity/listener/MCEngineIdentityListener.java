package io.github.mcengine.common.identity.listener;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.Persisten­tDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listens for player lifecycle and inventory events:
 * <ul>
 *     <li>On join: ensures identity + primary alt + session via {@code api.ensureExist(player)},
 *     and auto-loads the active alt inventory.</li>
 *     <li>On quit: auto-save active alt inventory.</li>
 *     <li>On inventory open (player crafting view): auto-load active alt inventory.</li>
 *     <li>On inventory close (player crafting view): auto-save active alt inventory.</li>
 *     <li>On right-click with Identity Limit Voucher: redeem and consume one item.</li>
 * </ul>
 * This removes the need for players to run manual save/load commands and avoids direct SQL here.
 */
public class MCEngineIdentityListener implements Listener {

    /** Shared Identity common API used for DB access and logging. */
    private final MCEngineIdentityCommon api;

    /** Owning plugin (for scheduling). */
    private final Plugin plugin;

    /** Voucher PDC keys (keep in sync with ItemUtil). */
    private static final NamespacedKey KEY_MARKER =
            NamespacedKey.fromString("mcengine_identity:alt_limit_add");
    private static final NamespacedKey KEY_AMOUNT =
            NamespacedKey.fromString("mcengine_identity:alt_limit_amount");

    /**
     * Constructs the listener with the Identity common API.
     *
     * @param api the identity common API
     */
    public MCEngineIdentityListener(MCEngineIdentityCommon api) {
        this.api = api;
        this.plugin = api.getPlugin();
    }

    // -----------------------------
    // Lifecycle + inventory sync
    // -----------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            // Consolidated ensure logic (identity, {uuid}-0 alt, session) implemented in the DB layer.
            api.ensureExist(player);
            // Auto-load inventory for the active alt (if any)
            api.loadActiveAltInventoryAsync(player);
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
        api.saveActiveAltInventoryAsync(player);
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        api.saveActiveAltInventoryAsync(player);
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
            api.loadActiveAltInventoryAsync(player);
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
            api.saveActiveAltInventoryAsync(player);
        }
    }

    // -----------------------------
    // Voucher redemption (right-click)
    // -----------------------------

    /**
     * Detects right-click with MAIN hand holding an "Identity Limit Voucher" item.
     * The item must carry:
     *   mcengine_identity:alt_limit_add    = 1 (INTEGER)
     *   mcengine_identity:alt_limit_amount = N (INTEGER > 0)
     * On redeem:
     *   - Cancels the interaction (prevents placing heads, etc.)
     *   - Increments the player's identity alt limit by N (async DB)
     *   - Consumes exactly one voucher from MAIN hand
     *   - Shows the new limit to the player
     */
    @EventHandler(ignoreCancelled = false)
    public void onRightClickVoucher(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return; // only main hand to avoid double-fire

        Player player = e.getPlayer();
        if (player == null) return;

        ItemStack hand = e.getItem(); // event’s actual used item
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) return;

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer marker = KEY_MARKER == null ? null : pdc.get(KEY_MARKER, PersistentDataType.INTEGER);
        if (marker == null || marker != 1) return;

        Integer amt = KEY_AMOUNT == null ? null : pdc.get(KEY_AMOUNT, PersistentDataType.INTEGER);
        if (amt == null || amt <= 0) return;

        // Stop default behavior immediately (placing, etc.)
        e.setCancelled(true);

        final int addAmount = amt;

        // Do DB work async
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean ok;
                int newLimit;
                try {
                    ok = api.addProfileAltLimit(player, addAmount);
                    newLimit = api.getProfileAltLimit(player);
                } catch (Exception ex) {
                    ok = false;
                    newLimit = -1;
                    api.getPlugin().getLogger().warning("Failed to redeem identity limit voucher: " + ex.getMessage());
                }

                final boolean success = ok;
                final int finalNewLimit = newLimit;

                // Back to main thread to mutate inventory + message
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Player p = Bukkit.getPlayer(player.getUniqueId());
                        if (p == null || !p.isOnline()) return;

                        if (!success) {
                            p.sendMessage("§cCould not apply identity limit. Try again later.");
                            return;
                        }

                        // Consume exactly one if the item still matches voucher shape
                        ItemStack current = p.getInventory().getItemInMainHand();
                        if (current != null && current.hasItemMeta()) {
                            ItemMeta cm = current.getItemMeta();
                            PersistentDataContainer cpdc = cm.getPersistentDataContainer();
                            Integer cmMarker = KEY_MARKER == null ? null : cpdc.get(KEY_MARKER, PersistentDataType.INTEGER);
                            Integer cmAmt = KEY_AMOUNT == null ? null : cpdc.get(KEY_AMOUNT, PersistentDataType.INTEGER);

                            if (cmMarker != null && cmMarker == 1 && cmAmt != null && cmAmt == addAmount) {
                                int amount = current.getAmount();
                                if (amount <= 1) {
                                    p.getInventory().setItemInMainHand(null);
                                } else {
                                    current.setAmount(amount - 1);
                                    p.getInventory().setItemInMainHand(current);
                                }
                            }
                        }

                        if (finalNewLimit >= 0) {
                            p.sendMessage("§aIdentity limit increased by §e+" + addAmount +
                                    "§a. New limit: §b" + finalNewLimit + "§a.");
                        } else {
                            p.sendMessage("§aIdentity limit increased by §e+" + addAmount + "§a.");
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }
}
