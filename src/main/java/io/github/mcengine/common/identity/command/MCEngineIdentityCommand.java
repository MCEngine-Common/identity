package io.github.mcengine.common.identity.command;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import io.github.mcengine.common.identity.command.util.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Command executor for the {@code /identity} command.
 * <p>
 * Delegates subcommand handling to utility classes for clarity and reuse:
 * <ul>
 *     <li>{@code /identity alt ...} → {@link AltUtil}</li>
 *     <li>{@code /identity limit ...} → {@link LimitUtil}</li>
 *     <li>{@code /identity perm ...} → {@link PermUtil}</li>
 * </ul>
 * <p>
 * Manual save/load subcommands are not present; inventory synchronization is automatic
 * on join/quit and inventory open/close.
 */
public class MCEngineIdentityCommand implements CommandExecutor {

    /**
     * Identity common API used to perform all identity and alt operations.
     */
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
            sender.sendMessage("/identity alt create | alt switch <altUuid|name> | alt name set <altUuid> <newName> | alt name change <oldName> <newName> | limit add <player> <amount> | limit get [player] | perm add <player> <altUuid|name> <permission>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "alt":
                return AltUtil.handleAlt(api, sender, args);

            case "limit":
                return LimitUtil.handleLimit(api, sender, args);

            case "perm":
                return PermUtil.handlePerm(api, sender, args);

            case "item":
                return ItemUtil.handleItem(api, sender, args);

            default:
                sender.sendMessage("/identity alt create | alt switch <altUuid|name> | alt name set <altUuid> <newName> | alt name change <oldName> <newName> | limit add <player> <amount> | limit get [player] | perm add <player> <altUuid|name> <permission>");
                return true;
        }
    }
}
