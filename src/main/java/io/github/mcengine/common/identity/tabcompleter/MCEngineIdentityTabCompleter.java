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
 *     <li><b>/identity alt switch</b> → suggests player's alts (name if set, otherwise {@code {uuid}-N})</li>
 *     <li><b>/identity alt name &lt;altUuid&gt;</b> → suggests player's alts (name if set, otherwise {@code {uuid}-N}), then {@code &lt;name|null&gt;}</li>
 * </ul>
 * <p>
 * Note: For alt display names this class now consults the DB abstraction via
 * {@code getProfileAltName(...)} instead of reading the column directly, which
 * centralizes name retrieval logic in the DB layer.
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

            // /identity alt switch <alt>
            if (args.length == 3 && "switch".equalsIgnoreCase(args[1])) {
                return filterPrefix(args[2], fetchPlayerAlts(player));
            }

            // /identity alt name <alt> <name|null>
            if ("name".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    return filterPrefix(args[2], fetchPlayerAlts(player));
                } else if (args.length == 4) {
                    return filterPrefix(args[3], List.of("null"));
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Fetches all alternatives belonging to the player's identity.
     * For each alt UUID, resolves the display name via the DB abstraction
     * ({@code getProfileAltName}); if the name is unset/empty, falls back
     * to the alt UUID string.
     *
     * @param player the Bukkit player
     * @return list of alt identifiers or names
     */
    private List<String> fetchPlayerAlts(Player player) {
        Connection c = api.getDB().getDBConnection();
        if (c == null) return Collections.emptyList();

        List<String> alts = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT identity_alternative_uuid " +
                "FROM identity_alternative WHERE identity_uuid = ? ORDER BY identity_alternative_uuid ASC")) {
            ps.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String altUuid = rs.getString(1);
                    String altName = api.getDB().getProfileAltName(player, altUuid);
                    alts.add((altName != null && !altName.isEmpty()) ? altName : altUuid);
                }
            }
        } catch (Exception e) {
            api.getPlugin().getLogger().warning("TabComplete failed to fetch alts for " + player.getUniqueId() + ": " + e.getMessage());
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
