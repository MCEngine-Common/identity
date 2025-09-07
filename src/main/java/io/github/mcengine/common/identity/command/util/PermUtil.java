package io.github.mcengine.common.identity.command.util;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utilities for {@code /identity perm ...} subcommands.
 * <p>
 * This class centralizes the logic for:
 * <ul>
 *     <li>{@code perm add &lt;altUuid|name&gt; &lt;permission&gt;}</li>
 * </ul>
 * No direct SQL is performed here; all calls are delegated to {@link MCEngineIdentityCommon}.
 */
public final class PermUtil {

    /**
     * Permission node required to offer the {@code perm} command path.
     */
    private static final String PERM_PERMISSION_ADD = "mcengine.identity.permission.add";

    /**
     * Player-facing message when attempting to add a duplicate permission.
     */
    private static final String MSG_PERMISSION_ALREADY_ADDED = "This permission is already added.";

    /**
     * Hidden constructor to enforce static-only usage.
     */
    private PermUtil() {}

    /**
     * Handles {@code /identity perm ...} invocations.
     *
     * @param api    Identity common API
     * @param sender command sender (must be a {@link Player} for perm ops)
     * @param args   raw command arguments (starting from index 0 == {@code perm})
     * @return {@code true} if handled, otherwise {@code true} with help text
     */
    public static boolean handlePerm(MCEngineIdentityCommon api, CommandSender sender, String[] args) {
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
        if (!"add".equals(op)) {
            sender.sendMessage("/identity perm add <altUuid|name> <permission>");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage("Usage: /identity perm add <altUuid|name> <permission>");
            return true;
        }

        String altToken = args[2];
        String permName = args[3];

        // Normalize alt token: if it's a display name, resolve it to UUID
        String resolved = api.getProfileAltUuidByName(p, altToken);
        String altUuid = (resolved != null && !resolved.isEmpty()) ? resolved : altToken;

        // Check if permission already exists for this alt
        boolean exists = MCEngineIdentityCommon.getApi().hasProfileAltCount(p, altUuid, permName);
        if (exists) {
            sender.sendMessage(MSG_PERMISSION_ALREADY_ADDED);
            return true;
        }

        boolean ok = MCEngineIdentityCommon.getApi().addProfileAltPermission(p, altUuid, permName);
        sender.sendMessage(ok
                ? ("Added permission '" + permName + "' to alt '" + altUuid + "'.")
                : "Failed to add permission (invalid alt or database error).");
        return true;
    }
}
