package io.github.mcengine.common.identity.command.util;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utilities for {@code /identity limit ...} subcommands.
 * <p>
 * This class centralizes the logic for:
 * <ul>
 *     <li>{@code limit add &lt;player&gt; &lt;amount&gt;}</li>
 *     <li>{@code limit get [player]}</li>
 * </ul>
 * No direct SQL is performed here; all calls are delegated to {@link MCEngineIdentityCommon}.
 */
public final class LimitUtil {

    /**
     * Permission required to add to a player's identity limit.
     */
    private static final String PERM_LIMIT_ADD = "mcengine.identity.limit.add";

    /**
     * Permission required to query own limit.
     */
    private static final String PERM_LIMIT_GET_SELF = "mcengine.identity.limit.get";

    /**
     * Permission required to query others' limits.
     */
    private static final String PERM_LIMIT_GET_PLAYERS = "mcengine.identity.limit.get.players";

    /**
     * Hidden constructor to enforce static-only usage.
     */
    private LimitUtil() {}

    /**
     * Handles {@code /identity limit ...} invocations.
     *
     * @param api    Identity common API
     * @param sender command sender
     * @param args   raw command arguments (starting from index 0 == {@code limit})
     * @return {@code true} if handled, otherwise {@code true} with help text
     */
    public static boolean handleLimit(MCEngineIdentityCommon api, CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/identity limit add <player> <amount> | limit get [player]");
            return true;
        }

        String op = args[1].toLowerCase();
        switch (op) {
            case "add":
                return handleLimitAdd(api, sender, args);

            case "get":
                return handleLimitGet(api, sender, args);

            default:
                sender.sendMessage("/identity limit add <player> <amount> | limit get [player]");
                return true;
        }
    }

    /**
     * Executes {@code /identity limit add &lt;player&gt; &lt;amount&gt;}.
     */
    private static boolean handleLimitAdd(MCEngineIdentityCommon api, CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_LIMIT_ADD)) {
            sender.sendMessage("You don't have permission (" + PERM_LIMIT_ADD + ").");
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

        boolean ok = api.addProfileAltLimit(target, amount);
        sender.sendMessage(ok ? ("Added " + amount + " to " + target.getName() + "'s identity limit.") : "Failed to update limit.");
        return true;
    }

    /**
     * Executes {@code /identity limit get [player]}.
     * <ul>
     *     <li>Self: requires {@link #PERM_LIMIT_GET_SELF}.</li>
     *     <li>Others: requires {@link #PERM_LIMIT_GET_PLAYERS}.</li>
     * </ul>
     */
    private static boolean handleLimitGet(MCEngineIdentityCommon api, CommandSender sender, String[] args) {
        // Self
        if (args.length == 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Usage (console): /identity limit get <player>");
                return true;
            }
            if (!sender.hasPermission(PERM_LIMIT_GET_SELF)) {
                sender.sendMessage("You don't have permission (" + PERM_LIMIT_GET_SELF + ").");
                return true;
            }
            Player self = (Player) sender;
            int limit = api.getProfileAltLimit(self);
            int altCount = api.getProfileAltCount(self);
            sender.sendMessage("Your identity count: " + altCount + " (limit " + limit + ")");
            return true;
        }

        // Others
        if (!sender.hasPermission(PERM_LIMIT_GET_PLAYERS)) {
            sender.sendMessage("You don't have permission (" + PERM_LIMIT_GET_PLAYERS + ").");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player must be online.");
            return true;
        }
        int limit = api.getProfileAltLimit(target);
        int altCount = api.getProfileAltCount(target);
        sender.sendMessage(target.getName() + "'s identity count: " + altCount + " (limit " + limit + ")");
        return true;
    }
}
