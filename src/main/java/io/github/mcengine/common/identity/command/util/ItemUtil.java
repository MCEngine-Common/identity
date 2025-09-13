package io.github.mcengine.common.identity.command.util;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for creating and handing out "Identity Limit Voucher" items.
 *
 * Syntax (handled by {@link #handleItem}):
 *   /identity item get limit [amount]
 *   /identity item get limit [hdb id] [amount]
 *
 * The item carries two PDC entries:
 *   mcengine_identity:alt_limit_add     (INTEGER = 1)   // marker
 *   mcengine_identity:alt_limit_amount  (INTEGER > 0)   // how many extra alts to grant on redeem
 *
 * Redemption is not performed here; another listener should detect right-click
 * on items with the marker and then call {@link MCEngineIdentityCommon#addProfileAltLimit(Player, int)}.
 */
public final class ItemUtil {

    /** Namespace keys for voucher identification and payload. */
    public static final NamespacedKey KEY_MARKER =
            NamespacedKey.fromString("mcengine_identity:alt_limit_add");
    public static final NamespacedKey KEY_AMOUNT =
            NamespacedKey.fromString("mcengine_identity:alt_limit_amount");

    private ItemUtil() {}

    /* ---------------------------------------------------------
     * Public command entry
     * --------------------------------------------------------- */

    /**
     * Handles:
     *   /identity item get limit [amount]
     *   /identity item get limit [hdb id] [amount]
     *
     * Permissions:
     *   mcengine.identity.item.get
     *
     * Behavior:
     *   - Creates a single voucher item (stack size 1) encoding the amount.
     *   - If run by a player, the item is placed into their inventory (or dropped at feet if full).
     *   - If run from console, rejects (no player arg in syntax).
     */
    public static boolean handleItem(MCEngineIdentityCommon api, CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /identity item get limit [amount] | /identity item get limit [hdb id] [amount]");
            return true;
        }

        // /identity item ...
        String op = args[1].toLowerCase(); // "get"
        if (!"get".equals(op)) {
            sender.sendMessage("§cUsage: /identity item get limit [amount] | /identity item get limit [hdb id] [amount]");
            return true;
        }

        String kind = args[2].toLowerCase(); // "limit"
        if (!"limit".equals(kind)) {
            sender.sendMessage("§cUnknown item kind: " + kind);
            return true;
        }

        if (!sender.hasPermission("mcengine.identity.item.get")) {
            sender.sendMessage("§cYou don't have permission: §fmcengine.identity.item.get");
            return true;
        }

        // Disallow console for now (syntax doesn't include target player)
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole cannot receive the item. Run in-game as a player.");
            return true;
        }

        // Parse arguments
        ItemStack voucher;
        if (args.length == 4) {
            // /identity item get limit [amount]
            Integer amount = parsePositiveInt(args[3]);
            if (amount == null || amount <= 0) {
                sender.sendMessage("§cAmount must be a positive integer.");
                return true;
            }
            voucher = createPaperVoucher(amount);
        } else if (args.length == 5) {
            // /identity item get limit [hdb id] [amount]
            String hdbId = args[3];
            Integer amount = parsePositiveInt(args[4]);
            if (amount == null || amount <= 0) {
                sender.sendMessage("§cAmount must be a positive integer.");
                return true;
            }
            voucher = createHdbVoucher(hdbId, amount);
        } else {
            sender.sendMessage("§cUsage: /identity item get limit [amount] | /identity item get limit [hdb id] [amount]");
            return true;
        }

        // Give to player
        var inv = player.getInventory();
        var leftover = inv.addItem(voucher);
        if (!leftover.isEmpty()) {
            // Inventory full -> drop at feet
            player.getWorld().dropItemNaturally(player.getLocation(), voucher);
            player.sendMessage("§aIdentity Limit Voucher created and dropped at your location.");
        } else {
            player.sendMessage("§aIdentity Limit Voucher added to your inventory.");
        }
        return true;
    }

    /* ---------------------------------------------------------
     * Item factories
     * --------------------------------------------------------- */

    /** Creates a plain PAPER voucher with hidden metadata. */
    public static ItemStack createPaperVoucher(int amount) {
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eIdentity Limit Voucher");
            List<String> lore = new ArrayList<>();
            lore.add("§7Right-click to add extra alt slots:");
            lore.add("§b+" + Math.max(0, amount) + " §7to your identity limit");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (KEY_MARKER != null) pdc.set(KEY_MARKER, PersistentDataType.INTEGER, 1);
            if (KEY_AMOUNT != null) pdc.set(KEY_AMOUNT, PersistentDataType.INTEGER, Math.max(0, amount));

            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Creates an HDB head voucher if HeadDatabase is installed; otherwise falls back to paper.
     * Uses reflection to avoid a hard dependency.
     */
    public static ItemStack createHdbVoucher(String hdbId, int amount) {
        try {
            if (Bukkit.getPluginManager().getPlugin("HeadDatabase") == null) {
                return createPaperVoucher(amount);
            }

            // me.arcaniax.hdb.api.HeadDatabaseAPI
            Class<?> apiCls = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Constructor<?> ctor = apiCls.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object api = ctor.newInstance();
            Method getItemHead = apiCls.getMethod("getItemHead", String.class);
            Object headObj = getItemHead.invoke(api, hdbId);
            if (!(headObj instanceof ItemStack head)) {
                return createPaperVoucher(amount);
            }

            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§eIdentity Limit Voucher (Head)");
                List<String> lore = new ArrayList<>();
                lore.add("§7Right-click to add extra alt slots:");
                lore.add("§b+" + Math.max(0, amount) + " §7to your identity limit");
                lore.add("§7HDB: §f" + hdbId);
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if (KEY_MARKER != null) pdc.set(KEY_MARKER, PersistentDataType.INTEGER, 1);
                if (KEY_AMOUNT != null) pdc.set(KEY_AMOUNT, PersistentDataType.INTEGER, Math.max(0, amount));

                head.setItemMeta(meta);
            }
            return head;
        } catch (Throwable t) {
            return createPaperVoucher(amount);
        }
    }

    /** Reads the encoded +limit amount from a voucher. Returns null if the item is not a voucher. */
    public static Integer readVoucherAmount(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer marker = KEY_MARKER == null ? null : pdc.get(KEY_MARKER, PersistentDataType.INTEGER);
        if (marker == null || marker != 1) return null;

        Integer amt = KEY_AMOUNT == null ? null : pdc.get(KEY_AMOUNT, PersistentDataType.INTEGER);
        if (amt == null || amt <= 0) return null;
        return amt;
        }

    /* ---------------------------------------------------------
     * Helpers
     * --------------------------------------------------------- */

    private static Integer parsePositiveInt(String s) {
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
