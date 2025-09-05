package io.github.mcengine.common.identity.tabcompleter;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
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
 *     <li><b>/identity</b> → {@code alt}</li>
 *     <li><b>/identity alt</b> → {@code create}, {@code switch}, {@code name}</li>
 *     <li><b>/identity alt switch &lt;alt&gt;</b> → suggests player's alts; if the user typed a <em>name</em>, it converts to the corresponding <em>UUID</em>.</li>
 *     <li><b>/identity alt name &lt;alt&gt; &lt;name|null&gt;</b> → suggests player's alts; if the user typed a <em>name</em>, it converts to the corresponding <em>UUID</em>, then suggests {@code null} for clearing.</li>
 * </ul>
 * <p>
 * This implementation performs <b>no direct SQL</b>. It resolves:
 * <ul>
 *     <li>All alt suggestions via {@link MCEngineIdentityCommon#getProfileAllAlt(Player)} (display name if set, otherwise UUID)</li>
 *     <li>Name → UUID normalization via {@link MCEngineIdentityCommon#getProfileAltUuidByName(Player, String)}</li>
 * </ul>
 * The normalization ensures that if a player types an alt's <em>display name</em>,
 * tab-completion will replace it with the <em>UUID</em> expected by the underlying command handler.
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
            return filterPrefix(args[0], List.of("alt"));
        }

        // /identity alt ...
        if (args.length >= 2 && "alt".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filterPrefix(args[1], List.of("create", "switch", "name"));
            }

            // /identity alt switch <altNameOrUuid>
            if (args.length == 3 && "switch".equalsIgnoreCase(args[1])) {
                return suggestionsForAltToken(player, args[2]);
            }

            // /identity alt name <altNameOrUuid> <name|null>
            if ("name".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    return suggestionsForAltToken(player, args[2]);
                } else if (args.length == 4) {
                    return filterPrefix(args[3], List.of("null"));
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Produces suggestions for an alt token:
     * <ol>
     *     <li>If the token matches an existing display name, return a single suggestion with the corresponding UUID (normalization).</li>
     *     <li>Otherwise, return the filtered list of the player's alts (display names if set, else UUIDs).</li>
     * </ol>
     *
     * @param player owner of the alts
     * @param token  current argument token (may be partial)
     * @return suggestions that will keep the executed command working with UUIDs
     */
    private List<String> suggestionsForAltToken(Player player, String token) {
        // Exact display-name → UUID normalization (case-sensitive DB semantics; prefix completion happens below)
        if (token != null && !token.isEmpty()) {
            String resolvedUuid = api.getProfileAltUuidByName(player, token);
            if (resolvedUuid != null && !resolvedUuid.isEmpty()) {
                // Replace the typed name with the UUID so the command will succeed when executed.
                return List.of(resolvedUuid);
            }
        }
        // Otherwise, suggest names-or-uuids filtered by the current prefix.
        return filterPrefix(token, api.getProfileAllAlt(player));
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
