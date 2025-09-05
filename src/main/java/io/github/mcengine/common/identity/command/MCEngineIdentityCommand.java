package io.github.mcengine.common.identity.command;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command executor for the {@code /identity} command.
 * <p>
 * Provides subcommands:
 * <ul>
 *     <li>{@code alt create}</li>
 *     <li>{@code alt switch &lt;altUuid&gt;} (auto-saves current and auto-loads target)</li>
 *     <li>{@code alt name &lt;altUuid&gt; &lt;name|null&gt;}</li>
 *     <li>{@code limit add &lt;player&gt; &lt;amount&gt;} (requires {@code mcengine.identity.alt.add})</li>
 * </ul>
 *
 * <p>Note: manual save/load commands have been removed since inventory
 * synchronization is now automatic on join, quit, and inventory open/close.</p>
 */
public class MCEngineIdentityCommand implements CommandExecutor {

    /** Identity common API used to perform operations. */
    private final MCEngineIdentityCommon api;

    /**
     * Constructs the command executor with the Identity common API.
     *
     * @param api identity common API
     */
    public MCEngineIdentityCommand(MCEngineIdentityCommon api) {
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("identity")) return false;

        if (args.length == 0) {
            sender.sendMessage("/identity alt create | alt switch <altUuid> | alt name <altUuid> <name|null> | limit add <player> <amount>");
            return true;
        }
        String sub = args[0].toLowerCase();

        // -------- /identity alt ... --------
        if ("alt".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            Player p = (Player) sender;

            if (args.length < 2) {
                sender.sendMessage("/identity alt create | switch <altUuid> | name <altUuid> <name|null>");
                return true;
            }
            String op = args[1].toLowerCase();
            if (op.equals("create")) {
                // Read limit (for better messaging) and rely on DB to enforce.
                int limit = api.getDB().getLimit(p);
                String alt = MCEngineIdentityCommon.getApi().createProfileAlt(p);
                if (alt != null) {
                    sender.sendMessage("Created alt: " + alt);
                } else {
                    sender.sendMessage("Failed to create alt. You may have reached your limit (" + limit + ").");
                }
                return true;
            } else if (op.equals("switch")) {
                if (args.length < 3) { sender.sendMessage("Usage: /identity alt switch <altUuid>"); return true; }

                // Auto-save current
                MCEngineIdentityCommon.getApi().saveActiveAltInventory(p);
                // Switch session
                boolean switched = MCEngineIdentityCommon.getApi().changeProfileAlt(p, args[2]);
                if (!switched) {
                    sender.sendMessage("Failed to switch alt.");
                    return true;
                }
                // Auto-load target
                boolean loaded = MCEngineIdentityCommon.getApi().loadActiveAltInventory(p);
                sender.sendMessage(loaded ? "Switched alt and loaded inventory." : "Switched alt (no stored inventory).");
                return true;
            } else if (op.equals("name")) {
                if (args.length < 4) { sender.sendMessage("Usage: /identity alt name <altUuid> <name|null>"); return true; }
                String name = args[3].equalsIgnoreCase("null") ? null : args[3];
                boolean ok = MCEngineIdentityCommon.getApi().setProfileAltname(p, args[2], name);
                sender.sendMessage(ok ? "Alt name updated." : "Failed to update alt name.");
                return true;
            } else {
                sender.sendMessage("/identity alt create | switch <altUuid> | name <altUuid> <name|null>");
                return true;
            }
        }

        // -------- /identity limit ... --------
        if ("limit".equals(sub)) {
            if (args.length < 2 || !"add".equalsIgnoreCase(args[1])) {
                sender.sendMessage("/identity limit add <player> <amount>");
                return true;
            }
            if (!sender.hasPermission("mcengine.identity.alt.add")) {
                sender.sendMessage("You don't have permission (mcengine.identity.alt.add).");
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage("Usage: /identity limit add <player> <amount>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player must be online.");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("Amount must be an integer.");
                return true;
            }
            if (amount < 0) {
                sender.sendMessage("Amount must not be negative.");
                return true;
            }
            boolean ok = api.addLimit(target, amount);
            sender.sendMessage(ok ? ("Added " + amount + " to " + target.getName() + "'s identity limit.") : "Failed to update limit.");
            return true;
        }

        sender.sendMessage("/identity alt create | alt switch <altUuid> | alt name <altUuid> <name|null> | limit add <player> <amount>");
        return true;
    }
}
