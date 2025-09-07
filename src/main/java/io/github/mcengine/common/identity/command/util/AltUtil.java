package io.github.mcengine.common.identity.command.util;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utilities for {@code /identity alt ...} subcommands.
 * <p>
 * This class centralizes the logic for:
 * <ul>
 *     <li>{@code alt create}</li>
 *     <li>{@code alt switch &lt;altUuid|name&gt;}</li>
 *     <li>{@code alt name set &lt;altUuid&gt; &lt;newName&gt;}</li>
 *     <li>{@code alt name change &lt;oldName&gt; &lt;newName&gt;}</li>
 * </ul>
 * No direct SQL is performed here; all calls are delegated to {@link MCEngineIdentityCommon}.
 */
public final class AltUtil {

    /**
     * Usage line for {@code /identity alt} root.
     */
    private static final String USAGE_ALT =
            "/identity alt create | switch <altUuid|name> | name set <altUuid> <newName> | name change <oldName> <newName>";

    /**
     * Hidden constructor to enforce static-only usage.
     */
    private AltUtil() {}

    /**
     * Handles {@code /identity alt ...} invocations.
     *
     * @param api    Identity common API
     * @param sender command sender (must be a {@link Player} for alt ops)
     * @param args   raw command arguments (starting from index 0 == {@code alt})
     * @return {@code true} if the command was handled; {@code false} to let Bukkit show usage
     */
    public static boolean handleAlt(MCEngineIdentityCommon api, CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(USAGE_ALT);
            return true;
        }

        String op = args[1].toLowerCase();
        switch (op) {
            case "create":
                return createAlt(api, sender, p);

            case "switch":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /identity alt switch <altUuid|name>");
                    return true;
                }
                return switchAlt(api, sender, p, args[2]);

            case "name":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /identity alt name set <altUuid> <newName> | /identity alt name change <oldName> <newName>");
                    return true;
                }
                return handleAltName(api, sender, p, args);

            default:
                sender.sendMessage(USAGE_ALT);
                return true;
        }
    }

    /**
     * Executes {@code /identity alt create}.
     */
    private static boolean createAlt(MCEngineIdentityCommon api, CommandSender sender, Player p) {
        int limit = api.getProfileAltLimit(p); // fetch for better feedback; DB enforces true limit
        String alt = MCEngineIdentityCommon.getApi().createProfileAlt(p);
        if (alt != null) {
            sender.sendMessage("Created alt: " + alt);
        } else {
            sender.sendMessage("Failed to create alt. You may have reached your limit (" + limit + ").");
        }
        return true;
    }

    /**
     * Executes {@code /identity alt switch &lt;altUuid|name&gt;}.
     */
    private static boolean switchAlt(MCEngineIdentityCommon api, CommandSender sender, Player p, String token) {
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

    /**
     * Dispatches {@code /identity alt name ...} subcommands.
     */
    private static boolean handleAltName(MCEngineIdentityCommon api, CommandSender sender, Player p, String[] args) {
        String nameOp = args[2].toLowerCase();
        switch (nameOp) {
            case "set":
                if (args.length < 5) {
                    sender.sendMessage("Usage: /identity alt name set <altUuid> <newName>");
                    return true;
                }
                return altNameSet(api, sender, p, args[3], args[4]);

            case "change":
                if (args.length < 5) {
                    sender.sendMessage("Usage: /identity alt name change <oldName> <newName>");
                    return true;
                }
                return altNameChange(api, sender, p, args[3], args[4]);

            default:
                sender.sendMessage("Usage: /identity alt name set <altUuid> <newName> | /identity alt name change <oldName> <newName>");
                return true;
        }
    }

    /**
     * Executes {@code /identity alt name set &lt;altUuid&gt; &lt;newName&gt;}.
     */
    private static boolean altNameSet(MCEngineIdentityCommon api, CommandSender sender, Player p, String altToken, String newName) {
        // Allow users to type a display name by mistake; resolve to UUID if so.
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

    /**
     * Executes {@code /identity alt name change &lt;oldName&gt; &lt;newName&gt;}.
     */
    private static boolean altNameChange(MCEngineIdentityCommon api, CommandSender sender, Player p, String oldName, String newName) {
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
}
