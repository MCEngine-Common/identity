package io.github.mcengine.common.identity.tabcompleter;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab completer for the {@code /identity} command.
 * <p>
 * Completion paths:
 * <ul>
 *   <li><b>/identity</b> → {@code alt} (always), {@code limit} (if sender has any related permission),
 *       {@code perm} (if sender can add permissions), {@code item} (if sender can get items)</li>
 *   <li><b>/identity alt</b> → {@code create}, {@code switch}, {@code name}</li>
 *   <li><b>/identity alt switch &lt;alt&gt;</b> → suggests player's alts; if a <em>name</em> is typed, it converts to the corresponding <em>UUID</em>.</li>
 *   <li><b>/identity alt name</b> → {@code set}, {@code change}</li>
 *   <li><b>/identity alt name set &lt;altUuid&gt; &lt;newName&gt;</b> → suggests only alts that do <b>not</b> already have a display name for the UUID argument</li>
 *   <li><b>/identity alt name change &lt;oldName&gt; &lt;newName&gt;</b> → suggests only display-name alts for the first argument</li>
 *   <li><b>/identity limit</b> → {@code add} (if {@code mcengine.identity.limit.add}) and/or {@code get} (if {@code mcengine.identity.limit.get} or {@code mcengine.identity.limit.get.players})</li>
 *   <li><b>/identity limit add &lt;player&gt; &lt;amount&gt;</b> → suggests online player names and common amounts</li>
 *   <li><b>/identity limit get &lt;player&gt;</b> → suggests online player names (only if {@code mcengine.identity.limit.get.players})</li>
 *   <li><b>/identity perm</b> → {@code add} (if {@code mcengine.identity.permission.add})</li>
 *   <li><b>/identity perm add &lt;player&gt; &lt;altUuid&gt; &lt;permission&gt;</b> → suggests online players, then that player’s alts as
 *       <em>display name if set, otherwise the alt UUID</em></li>
 *   <li><b>/identity item</b> → {@code get} (if {@code mcengine.identity.item.get})</li>
 *   <li><b>/identity item get</b> → {@code limit}</li>
 *   <li><b>/identity item get limit &lt;amount&gt;</b> → suggests common amounts when the 4th token is numeric or empty</li>
 *   <li><b>/identity item get limit &lt;hdb id&gt; &lt;amount&gt;</b> → free-text HDB id, then suggests common amounts</li>
 * </ul>
 * <p>
 * This implementation performs <b>no direct SQL</b>. It resolves alt data via {@link MCEngineIdentityCommon}.
 */
public class MCEngineIdentityTabCompleter implements TabCompleter {

    /**
     * Shared Identity common API used for DB access and logging.
     */
    private final MCEngineIdentityCommon api;

    /**
     * Permission node required to offer the {@code perm} command path.
     */
    private static final String PERM_PERMISSION_ADD = "mcengine.identity.permission.add";

    /**
     * Permission node required to offer the {@code item} command path.
     */
    private static final String PERM_ITEM_GET = "mcengine.identity.item.get";

    /**
     * Constructs the tab completer with the Identity common API.
     *
     * @param api identity common API
     */
    public MCEngineIdentityTabCompleter(MCEngineIdentityCommon api) {
        this.api = api;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("identity")) return Collections.emptyList();
        if (!(sender instanceof Player player)) return Collections.emptyList();

        // /identity
        if (args.length == 1) {
            List<String> roots = new ArrayList<>();
            roots.add("alt");

            if (player.hasPermission("mcengine.identity.limit.add")
                    || player.hasPermission("mcengine.identity.limit.get")
                    || player.hasPermission("mcengine.identity.limit.get.players")) {
                roots.add("limit");
            }
            if (player.hasPermission(PERM_PERMISSION_ADD)) {
                roots.add("perm");
            }
            if (player.hasPermission(PERM_ITEM_GET)) {
                roots.add("item");
            }
            return MCEngineIdentityTabCompleterUtil.filterPrefix(args[0], roots);
        }

        // /identity alt ...
        if ("alt".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return MCEngineIdentityTabCompleterUtil.filterPrefix(args[1], List.of("create", "switch", "name"));
            }

            // /identity alt switch <altNameOrUuid>
            if (args.length == 3 && "switch".equalsIgnoreCase(args[1])) {
                return MCEngineIdentityTabCompleterUtil.suggestionsForAltToken(api, player, args[2]);
            }

            // /identity alt name ...
            if ("name".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    return MCEngineIdentityTabCompleterUtil.filterPrefix(args[2], List.of("set", "change"));
                }
                if ("set".equalsIgnoreCase(args[2])) {
                    if (args.length == 4) {
                        return MCEngineIdentityTabCompleterUtil.filterPrefix(
                                args[3],
                                MCEngineIdentityTabCompleterUtil.altsWithoutDisplayName(api, player)
                        );
                    }
                    if (args.length == 5) return Collections.emptyList();
                }
                if ("change".equalsIgnoreCase(args[2])) {
                    if (args.length == 4) {
                        return MCEngineIdentityTabCompleterUtil.filterPrefix(
                                args[3],
                                MCEngineIdentityTabCompleterUtil.altsWithDisplayNameOnly(api, player)
                        );
                    }
                    if (args.length == 5) return Collections.emptyList();
                }
                return Collections.emptyList();
            }

            return Collections.emptyList();
        }

        // /identity limit ...
        if ("limit".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                List<String> subs = new ArrayList<>();
                if (player.hasPermission("mcengine.identity.limit.add")) subs.add("add");

                boolean canGet = player.hasPermission("mcengine.identity.limit.get")
                        || player.hasPermission("mcengine.identity.limit.get.players");
                if (canGet) subs.add("get");
                return MCEngineIdentityTabCompleterUtil.filterPrefix(args[1], subs);
            }

            if ("add".equalsIgnoreCase(args[1]) && player.hasPermission("mcengine.identity.limit.add")) {
                if (args.length == 3) {
                    return MCEngineIdentityTabCompleterUtil.filterPrefix(
                            args[2],
                            MCEngineIdentityTabCompleterUtil.onlinePlayerNames()
                    );
                } else if (args.length == 4) {
                    return MCEngineIdentityTabCompleterUtil.filterPrefix(args[3], List.of("1", "5", "10"));
                }
                return Collections.emptyList();
            }

            if ("get".equalsIgnoreCase(args[1])) {
                if (args.length == 3 && player.hasPermission("mcengine.identity.limit.get.players")) {
                    return MCEngineIdentityTabCompleterUtil.filterPrefix(
                            args[2],
                            MCEngineIdentityTabCompleterUtil.onlinePlayerNames()
                    );
                }
                return Collections.emptyList();
            }

            return Collections.emptyList();
        }

        // /identity perm ...
        if ("perm".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission(PERM_PERMISSION_ADD)) return Collections.emptyList();

            if (args.length == 2) {
                return MCEngineIdentityTabCompleterUtil.filterPrefix(args[1], List.of("add"));
            }

            if ("add".equalsIgnoreCase(args[1])) {
                // /identity perm add <player> <altUuid> <permission>
                if (args.length == 3) {
                    // Suggest online player names
                    return MCEngineIdentityTabCompleterUtil.filterPrefix(
                            args[2],
                            MCEngineIdentityTabCompleterUtil.onlinePlayerNames()
                    );
                }
                if (args.length == 4) {
                    // Show target player's alts using "name if set, otherwise altUuid"
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) return Collections.emptyList();
                    // api.getProfileAllAlt(target) already returns display names where set, else UUIDs
                    return MCEngineIdentityTabCompleterUtil.filterPrefix(
                            args[3],
                            api.getProfileAllAlt(target)
                    );
                }
                if (args.length == 5) {
                    // permission suggestions: none by default (free text)
                    return Collections.emptyList();
                }
                return Collections.emptyList();
            }

            return Collections.emptyList();
        }

        // /identity item ...
        if ("item".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission(PERM_ITEM_GET)) return Collections.emptyList();

            if (args.length == 2) {
                return MCEngineIdentityTabCompleterUtil.filterPrefix(args[1], List.of("get"));
            }

            if ("get".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    return MCEngineIdentityTabCompleterUtil.filterPrefix(args[2], List.of("limit"));
                }

                if ("limit".equalsIgnoreCase(args[2])) {
                    // /identity item get limit [amount]
                    // /identity item get limit [hdb id] [amount]
                    if (args.length == 4) {
                        String token = args[3];
                        // If numeric or empty => suggest amounts; otherwise user is typing an HDB id (free text)
                        if (token == null || token.isEmpty() || token.matches("^\\d+$")) {
                            return MCEngineIdentityTabCompleterUtil.filterPrefix(token, List.of("1", "5", "10"));
                        }
                        return Collections.emptyList();
                    }
                    if (args.length == 5) {
                        // After HDB id, suggest common amounts
                        return MCEngineIdentityTabCompleterUtil.filterPrefix(args[4], List.of("1", "5", "10"));
                    }
                }
            }

            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
