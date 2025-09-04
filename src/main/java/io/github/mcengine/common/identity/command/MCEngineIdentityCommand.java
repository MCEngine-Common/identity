package io.github.mcengine.common.identity.command;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command executor for the {@code /identity} command.
 * <p>
 * Provides subcommands:
 * <ul>
 *     <li>{@code saveinv}</li>
 *     <li>{@code loadinv}</li>
 *     <li>{@code alt create}</li>
 *     <li>{@code alt switch &lt;altUuid&gt;}</li>
 *     <li>{@code alt name &lt;altUuid&gt; &lt;name|null&gt;}</li>
 * </ul>
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
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;
        if (args.length == 0) {
            sender.sendMessage("/identity saveinv | loadinv | alt create | alt switch <altUuid> | alt name <altUuid> <name|null>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "saveinv" -> {
                boolean ok = MCEngineIdentityCommon.getApi().saveActiveAltInventory(p);
                sender.sendMessage(ok ? "Saved active alt inventory." : "Failed to save (no active alt?).");
                return true;
            }
            case "loadinv" -> {
                boolean ok = MCEngineIdentityCommon.getApi().loadActiveAltInventory(p);
                sender.sendMessage(ok ? "Loaded active alt inventory." : "Failed to load (no data?).");
                return true;
            }
            case "alt" -> {
                if (args.length < 2) {
                    sender.sendMessage("/identity alt create | switch <altUuid> | name <altUuid> <name|null>");
                    return true;
                }
                String op = args[1].toLowerCase();
                if (op.equals("create")) {
                    String alt = MCEngineIdentityCommon.getApi().createProfileAlt(p);
                    sender.sendMessage(alt != null ? ("Created alt: " + alt) : "Failed to create alt.");
                    return true;
                } else if (op.equals("switch")) {
                    if (args.length < 3) { sender.sendMessage("Usage: /identity alt switch <altUuid>"); return true; }
                    boolean ok = MCEngineIdentityCommon.getApi().changeProfileAlt(p, args[2]);
                    sender.sendMessage(ok ? "Switched active alt." : "Failed to switch alt.");
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
            default -> {
                sender.sendMessage("/identity saveinv | loadinv | alt create | alt switch <altUuid> | alt name <altUuid> <name|null>");
                return true;
            }
        }
    }
}
