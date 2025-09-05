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
import java.util.Locale;

/**
 * Tab completer for the {@code /identity} command.
 * <p>
 * Completion paths:
 * <ul>
 *   <li><b>/identity</b> → {@code alt} (always), {@code limit} (if sender has any related permission)</li>
 *   <li><b>/identity alt</b> → {@code create}, {@code switch}, {@code name}</li>
 *   <li><b>/identity alt switch &lt;alt&gt;</b> → suggests player's alts; if a <em>name</em> is typed, it converts to the corresponding <em>UUID</em>.</li>
 *   <li><b>/identity alt name</b> → {@code set}, {@code change}</li>
 *   <li><b>/identity alt name set &lt;altUuid&gt;</b> → suggests only alts that do <b>not</b> already have a display name</li>
 *   <li><b>/identity alt name change &lt;oldName&gt; &lt;newName&gt;</b> → suggests only display-name alts for the first argument</li>
 *   <li><b>/identity limit</b> → {@code add} (if {@code mcengine.identity.limit.add}) and/or {@code get} (if {@code mcengine.identity.limit.get} or {@code mcengine.identity.limit.get.players})</li>
 *   <li><b>/identity limit add &lt;player&gt; &lt;amount&gt;</b> → suggests online player names and common amounts</li>
 *   <li><b>/identity limit get &lt;player&gt;</b> → suggests online player names (only if {@code mcengine.identity.limit.get.players})</li>
 * </ul>
 * <p>
 * This implementation performs <b>no direct SQL</b>. It resolves:
 * <ul>
 *   <li>All alt suggestions via {@link MCEngineIdentityCommon#getProfileAllAlt(Player)} (display name if set, otherwise UUID)</li>
 *   <li>Name → UUID normalization via {@link MCEngineIdentityCommon#getProfileAltUuidByName(Player, String)}</li>
 * </ul>
 */
public class MCEngineIdentityTabCompleter implements TabCompleter {

    /** Shared Identity common API used for DB access and logging. */
    private final MCEngineIdentityCommon api;

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
            return filterPrefix(args[0], roots);
        }

        // /identity alt ...
        if ("alt".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filterPrefix(args[1], List.of("create", "switch", "name"));
            }

            // /identity alt switch <altNameOrUuid>
            if (args.length == 3 && "switch".equalsIgnoreCase(args[1])) {
                return suggestionsForAltToken(player, args[2]);
            }

            // /identity alt name ...
            if ("name".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    return filterPrefix(args[2], List.of("set", "change"));
                }
                // /identity alt name set <altUuid>
                if (args.length == 4 && "set".equalsIgnoreCase(args[2])) {
                    return filterPrefix(args[3], altsWithoutDisplayName(player));
                }
                // /identity alt name change <oldName> <newName>
                if ("change".equalsIgnoreCase(args[2])) {
                    // First argument is an existing display name
                    if (args.length == 4) {
                        return filterPrefix(args[3], altsWithDisplayNameOnly(player));
                    }
                    // Second argument (new name): no suggestions by default
                    if (args.length == 5) {
                        return Collections.emptyList();
                    }
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
                if (canGet) {
                    subs.add("get"); // real token only
                }
                return filterPrefix(args[1], subs);
            }

            // /identity limit add <player> <amount>
            if ("add".equalsIgnoreCase(args[1]) && player.hasPermission("mcengine.identity.limit.add")) {
                if (args.length == 3) {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                    return filterPrefix(args[2], names);
                } else if (args.length == 4) {
                    return filterPrefix(args[3], List.of("1", "5", "10"));
                }
                return Collections.emptyList();
            }

            // /identity limit get [player]
            if ("get".equalsIgnoreCase(args[1])) {
                if (args.length == 3 && player.hasPermission("mcengine.identity.limit.get.players")) {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                    return filterPrefix(args[2], names);
                }
                return Collections.emptyList();
            }

            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    /**
     * Build suggestions for an alt token:
     * <ol>
     *   <li>If the token matches an existing display name, return the corresponding UUID (normalization).</li>
     *   <li>Otherwise, return the filtered list of the player's alts (display names if set, else UUIDs).</li>
     * </ol>
     *
     * @param player owner of the alts
     * @param token  current argument token (may be partial)
     * @return suggestions that keep the executed command working with UUIDs
     */
    private List<String> suggestionsForAltToken(Player player, String token) {
        if (token != null && !token.isEmpty()) {
            String resolvedUuid = api.getProfileAltUuidByName(player, token);
            if (resolvedUuid != null && !resolvedUuid.isEmpty()) {
                return List.of(resolvedUuid);
            }
        }
        return filterPrefix(token, api.getProfileAllAlt(player));
    }

    /**
     * Returns only alts that do not have a display name yet (i.e., those returned as UUIDs).
     * Uses a heuristic that matches our alt UUID format: <pre>{identity-uuid}-{index}</pre>.
     */
    private List<String> altsWithoutDisplayName(Player player) {
        List<String> all = api.getProfileAllAlt(player);
        List<String> onlyUuids = new ArrayList<>();
        for (String s : all) {
            if (looksLikeAltUuid(s)) {
                onlyUuids.add(s);
            }
        }
        return onlyUuids;
    }

    /**
     * Returns only alts that currently have a display name (i.e., those returned as non-UUIDs).
     */
    private List<String> altsWithDisplayNameOnly(Player player) {
        List<String> all = api.getProfileAllAlt(player);
        List<String> names = new ArrayList<>();
        for (String s : all) {
            if (!looksLikeAltUuid(s)) {
                names.add(s);
            }
        }
        return names;
    }

    /** Heuristic check for an alt UUID string: {@code <uuid-v4>-<number>}. */
    private boolean looksLikeAltUuid(String s) {
        return s != null && s.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-\\d+$");
    }

    /**
     * Filters suggestions by a case-insensitive prefix.
     *
     * @param prefix      current typed token
     * @param suggestions full candidate list
     * @return filtered list preserving original order
     */
    private List<String> filterPrefix(String prefix, List<String> suggestions) {
        if (prefix == null || prefix.isEmpty()) return new ArrayList<>(suggestions);
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : suggestions) {
            if (s != null && s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}
