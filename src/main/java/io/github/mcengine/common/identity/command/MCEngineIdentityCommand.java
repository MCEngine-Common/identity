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
 *     <li>{@code alt switch &lt;altUuid|name&gt;} (auto-saves current and auto-loads target)</li>
 *     <li>{@code alt name set &lt;altUuid&gt; &lt;newName&gt;} (sets display name explicitly)</li>
 *     <li>{@code alt name change &lt;oldName&gt; &lt;newName&gt;} (renames an existing alt by display name)</li>
 *     <li>{@code limit add &lt;player&gt; &lt;amount&gt;} (requires {@code mcengine.identity.limit.add})</li>
 *     <li>{@code limit get} (self; requires {@code mcengine.identity.limit.get})</li>
 *     <li>{@code limit get &lt;player&gt;} (others; requires {@code mcengine.identity.limit.get.players})</li>
 *     <li>{@code perm add &lt;altUuid|name&gt; &lt;permission&gt;} (requires {@code mcengine.identity.permission.add})</li>
 * </ul>
 *
 * <p>Note: manual save/load commands have been removed since inventory
 * synchronization is now automatic on join, quit, and inventory open/close.</p>
 */
public class MCEngineIdentityCommand implements CommandExecutor {

    /** Identity common API used to perform all identity and alt operations. */
    private final MCEngineIdentityCommon api;

    /** Permission node required to add an alt permission (via {@code /identity perm add ...}). */
    private static final String PERM_PERMISSION_ADD = "mcengine.identity.permission.add";

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
            sender.sendMessage("/identity alt create | alt switch <altUuid|name> | alt name set <altUuid> <newName> | alt name change <oldName> <newName> | limit add <player> <amount> | limit get [player] | perm add <altUuid|name> <permission>");
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
                sender.sendMessage("/identity alt create | switch <altUuid|name> | name set <altUuid> <newName> | name change <oldName> <newName>");
                return true;
            }
            String op = args[1].toLowerCase();

            if (op.equals("create")) {
                // Read limit (for better messaging) and rely on DB to enforce.
                int limit = api.getLimit(p);
                String alt = MCEngineIdentityCommon.getApi().createProfileAlt(p);
                if (alt != null) {
                    sender.sendMessage("Created alt: " + alt);
                } else {
                    sender.sendMessage("Failed to create alt. You may have reached your limit (" + limit + ").");
                }
                return true;
            }

            if (op.equals("switch")) {
                if (args.length < 3) { sender.sendMessage("Usage: /identity alt switch <altUuid|name>"); return true; }

                // Resolve display name -> UUID if the user provided a name instead of UUID
                String token = args[2];
                String resolved = api.getProfileAltUuidByName(p, token);
                String targetAltUuid = (resolved != null && !resolved.isEmpty()) ? resolved : token;

                // Auto-save current
                MCEngineIdentityCommon.getApi().saveActiveAltInventory(p);
                // Switch session
                boolean switched = MCEngineIdentityCommon.getApi().changeProfileAlt(p, targetAltUuid);
                if (!switched) {
                    sender.sendMessage("Failed to switch alt.");
                    return true;
                }
                // Auto-load target
                boolean loaded = MCEngineIdentityCommon.getApi().loadActiveAltInventory(p);
                sender.sendMessage(loaded ? "Switched alt and loaded inventory." : "Switched alt (no stored inventory).");
                return true;
            }

            if (op.equals("name")) {
                // /identity alt name <set|change> ...
                if (args.length < 3) {
                    sender.sendMessage("Usage: /identity alt name set <altUuid> <newName> | /identity alt name change <oldName> <newName>");
                    return true;
                }
                String nameOp = args[2].toLowerCase();

                if (nameOp.equals("set")) {
                    // /identity alt name set <altUuid> <newName>
                    if (args.length < 5) {
                        sender.sendMessage("Usage: /identity alt name set <altUuid> <newName>");
                        return true;
                    }
                    String altToken = args[3];
                    String newName  = args[4];

                    // Allow users to accidentally type a display name; resolve it to UUID if so.
                    String resolved = api.getProfileAltUuidByName(p, altToken);
                    String altUuid = (resolved != null && !resolved.isEmpty()) ? resolved : altToken;

                    // If already has a name, do nothing (per request).
                    String existing = api.getProfileAltName(p, altUuid);
                    if (existing != null && !existing.isEmpty()) {
                        sender.sendMessage("This alt already has a name: " + existing);
                        return true;
                    }

                    // Prevent UNIQUE constraint violation: ensure no other alt already uses newName
                    String conflictAlt = api.getProfileAltUuidByName(p, newName);
                    if (conflictAlt != null && !conflictAlt.isEmpty() && !conflictAlt.equals(altUuid)) {
                        sender.sendMessage("That name is already in use by another alt.");
                        return true;
                    }

                    boolean ok = MCEngineIdentityCommon.getApi().setProfileAltname(p, altUuid, newName);
                    sender.sendMessage(ok ? ("Alt name set to '" + newName + "'.") : "Failed to set alt name.");
                    return true;
                }

                if (nameOp.equals("change")) {
                    // /identity alt name change <oldName> <newName>
                    if (args.length < 5) {
                        sender.sendMessage("Usage: /identity alt name change <oldName> <newName>");
                        return true;
                    }
                    String oldName = args[3];
                    String newName = args[4];

                    // Resolve current name to alt UUID
                    String altUuid = api.getProfileAltUuidByName(p, oldName);
                    if (altUuid == null || altUuid.isEmpty()) {
                        sender.sendMessage("No alt found with name '" + oldName + "'.");
                        return true;
                    }

                    // If new name equals existing, short-circuit
                    String existing = api.getProfileAltName(p, altUuid);
                    if (existing != null && existing.equals(newName)) {
                        sender.sendMessage("Alt already has the name '" + newName + "'.");
                        return true;
                    }

                    // Prevent UNIQUE constraint violation: ensure no other alt already uses newName
                    String conflictAlt = api.getProfileAltUuidByName(p, newName);
                    if (conflictAlt != null && !conflictAlt.isEmpty() && !conflictAlt.equals(altUuid)) {
                        sender.sendMessage("That name is already in use by another alt.");
                        return true;
                    }

                    boolean ok = MCEngineIdentityCommon.getApi().setProfileAltname(p, altUuid, newName);
                    if (ok) {
                        sender.sendMessage("Renamed alt '" + oldName + "' to '" + newName + "'.");
                    } else {
                        sender.sendMessage("Failed to rename alt. The new name may already be in use.");
                    }
                    return true;
                }

                sender.sendMessage("Usage: /identity alt name set <altUuid> <newName> | /identity alt name change <oldName> <newName>");
                return true;
            }

            // Fallback help for /identity alt
            sender.sendMessage("/identity alt create | switch <altUuid|name> | name set <altUuid> <newName> | name change <oldName> <newName>");
            return true;
        }

        // -------- /identity limit ... --------
        if ("limit".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage("/identity limit add <player> <amount> | limit get [player]");
                return true;
            }
            String op = args[1].toLowerCase();

            // /identity limit add <player> <amount>
            if ("add".equals(op)) {
                if (!sender.hasPermission("mcengine.identity.limit.add")) {
                    sender.sendMessage("You don't have permission (mcengine.identity.limit.add).");
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

            // /identity limit get [player]
            if ("get".equals(op)) {
                // self
                if (args.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Usage (console): /identity limit get <player>");
                        return true;
                    }
                    if (!sender.hasPermission("mcengine.identity.limit.get")) {
                        sender.sendMessage("You don't have permission (mcengine.identity.limit.get).");
                        return true;
                    }
                    Player self = (Player) sender;
                    int limit = api.getLimit(self);
                    int altCount = api.getProfileCount(self);
                    sender.sendMessage("Your identity count: " + altCount + " (limit " + limit + ")");
                    return true;
                }

                // others
                if (args.length >= 3) {
                    if (!sender.hasPermission("mcengine.identity.limit.get.players")) {
                        sender.sendMessage("You don't have permission (mcengine.identity.limit.get.players).");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        sender.sendMessage("Player must be online.");
                        return true;
                    }
                    int limit = api.getLimit(target);
                    int altCount = api.getProfileCount(target);
                    sender.sendMessage(target.getName() + "'s identity count: " + altCount + " (limit " + limit + ")");
                    return true;
                }
            }

            sender.sendMessage("/identity limit add <player> <amount> | limit get [player]");
            return true;
        }

        // -------- /identity perm ... --------
        if ("perm".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            Player p = (Player) sender;

            if (!p.hasPermission(PERM_PERMISSION_ADD)) {
                sender.sendMessage("You don't have permission (" + PERM_PERMISSION_ADD + ").");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("/identity perm add <altUuid|name> <permission>");
                return true;
            }

            String op = args[1].toLowerCase();
            if ("add".equals(op)) {
                if (args.length < 4) {
                    sender.sendMessage("Usage: /identity perm add <altUuid|name> <permission>");
                    return true;
                }

                String altToken = args[2];
                String permName = args[3];

                // Normalize alt token: if it's a display name, resolve it to UUID
                String resolved = api.getProfileAltUuidByName(p, altToken);
                String altUuid = (resolved != null && !resolved.isEmpty()) ? resolved : altToken;

                boolean ok = MCEngineIdentityCommon.getApi().addProfileAltPermission(p, altUuid, permName);
                sender.sendMessage(ok ? ("Added permission '" + permName + "' to alt '" + altUuid + "'.")
                                      : "Failed to add permission (invalid alt or database error).");
                return true;
            }

            sender.sendMessage("/identity perm add <altUuid|name> <permission>");
            return true;
        }

        sender.sendMessage("/identity alt create | alt switch <altUuid|name> | alt name set <altUuid> <newName> | alt name change <oldName> <newName> | limit add <player> <amount> | limit get [player] | perm add <altUuid|name> <permission>");
        return true;
    }
}
