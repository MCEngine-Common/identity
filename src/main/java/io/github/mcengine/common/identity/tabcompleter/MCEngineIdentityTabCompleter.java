package io.github.mcengine.common.identity.tabcompleter;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tab completer for the {@code /identity} command.
 * <p>
 * Completion paths:
 * <ul>
 *     <li><b>/identity</b> → {@code alt}</li>
 *     <li><b>/identity alt</b> → {@code create}, {@code switch}, {@code name}</li>
 *     <li><b>/identity alt switch</b> → suggests player's alts by <b>UUID</b></li>
 *     <li><b>/identity alt name &lt;altUuid&gt;</b> → suggests player's alts by <b>UUID</b>, then {@code &lt;name|null&gt;}</li>
 * </ul>
 * <p>
 * Rationale: The {@code /identity alt switch <arg>} command expects an <em>alt UUID</em>.
 * To avoid user confusion (e.g., selecting a display name that would fail to switch),
 * the tab completer intentionally suggests UUIDs only. If an alt has a display name,
 * users may still type it manually if the command later supports name→UUID resolution.
 */
public class MCEngineIdentityTabCompleter implements TabCompleter {

    /** Identity common API used for DB access and logging. */
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
            return filterPrefix(args[0], List.of("alt"));
        }

        // /identity alt ...
        if (args.length >= 2 && "alt".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filterPrefix(args[1], List.of("create", "switch", "name"));
            }

            // /identity alt switch <altUuid>
            if (args.length == 3 && "switch".equalsIgnoreCase(args[1])) {
                return filterPrefix(args[2], fetchPlayerAltUuids(player));
            }

            // /identity alt name <altUuid> <name|null>
            if ("name".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    return filterPrefix(args[2], fetchPlayerAltUuids(player));
                } else if (args.length == 4) {
                    return filterPrefix(args[3], List.of("null"));
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Returns all alternative UUIDs belonging to the player's identity in a deterministic order.
     *
     * @param player the Bukkit player
     * @return list of alt UUIDs
     */
    private List<String> fetchPlayerAltUuids(Player player) {
        Connection c = api.getDB().getDBConnection();
        if (c == null) return Collections.emptyList();

        List<String> alts = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT identity_alternative_uuid " +
                        "FROM identity_alternative WHERE identity_uuid = ? ORDER BY identity_alternative_uuid ASC")) {
            ps.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    alts.add(rs.getString(1));
                }
            }
        } catch (Exception e) {
            api.getPlugin().getLogger().warning("TabComplete failed to fetch alt UUIDs for " + player.getUniqueId() + ": " + e.getMessage());
        }
        return alts;
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
