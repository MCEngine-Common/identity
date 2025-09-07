package io.github.mcengine.common.identity.tabcompleter;

import io.github.mcengine.common.identity.MCEngineIdentityCommon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Utility helpers for the Identity tab-completion flow.
 * <p>
 * Centralizes common filtering and suggestion logic to keep
 * {@link MCEngineIdentityTabCompleter} concise and readable.
 * This class performs <b>no direct SQL</b>; it delegates to
 * {@link MCEngineIdentityCommon} for data access.
 */
public final class MCEngineIdentityTabCompleterUtil {

    /**
     * Hidden constructor to enforce static-only usage.
     */
    private MCEngineIdentityTabCompleterUtil() {}

    /**
     * Builds suggestions for an alt argument token.
     * <ol>
     *   <li>If the token matches an existing display name, returns the corresponding UUID (normalization).</li>
     *   <li>Otherwise, returns the filtered list of the player's alts (display names if set, otherwise UUIDs).</li>
     * </ol>
     *
     * @param api    Identity common API
     * @param player owner of the alts
     * @param token  current (possibly partial) token
     * @return suggestions that keep the executed command working with UUIDs
     */
    public static List<String> suggestionsForAltToken(MCEngineIdentityCommon api, Player player, String token) {
        if (token != null && !token.isEmpty()) {
            String resolvedUuid = api.getProfileAltUuidByName(player, token);
            if (resolvedUuid != null && !resolvedUuid.isEmpty()) {
                return List.of(resolvedUuid);
            }
        }
        return filterPrefix(token, api.getProfileAllAlt(player));
    }

    /**
     * Returns only alts that do not currently have a display name
     * (i.e., those returned as UUIDs).
     *
     * @param api    Identity common API
     * @param player owner of the alts
     * @return list of alt UUIDs
     */
    public static List<String> altsWithoutDisplayName(MCEngineIdentityCommon api, Player player) {
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
     * Returns only alts that have a display name (i.e., those returned as non-UUIDs).
     *
     * @param api    Identity common API
     * @param player owner of the alts
     * @return list of display-name alts
     */
    public static List<String> altsWithDisplayNameOnly(MCEngineIdentityCommon api, Player player) {
        List<String> all = api.getProfileAllAlt(player);
        List<String> names = new ArrayList<>();
        for (String s : all) {
            if (!looksLikeAltUuid(s)) {
                names.add(s);
            }
        }
        return names;
    }

    /**
     * Returns <em>all</em> alt UUIDs for the given player, converting any display-name alts
     * to their UUIDs via {@link MCEngineIdentityCommon#getProfileAltUuidByName(Player, String)}.
     * <p>
     * This is useful for commands that explicitly require an {@code altUuid} argument but
     * the underlying API may return a mix of names and UUIDs for suggestions.
     *
     * @param api    Identity common API
     * @param player owner of the alts
     * @return list of UUID strings for all alts belonging to the player
     */
    public static List<String> altsAllUuids(MCEngineIdentityCommon api, Player player) {
        List<String> all = api.getProfileAllAlt(player);
        List<String> uuids = new ArrayList<>();
        for (String s : all) {
            if (looksLikeAltUuid(s)) {
                uuids.add(s);
            } else {
                String resolved = api.getProfileAltUuidByName(player, s);
                if (resolved != null && !resolved.isEmpty()) {
                    uuids.add(resolved);
                }
            }
        }
        return uuids;
    }

    /**
     * Heuristic check for an alt UUID string: {@code <uuid-v4>-<number>}.
     *
     * @param s candidate string
     * @return {@code true} if the string matches the expected alt UUID format
     */
    public static boolean looksLikeAltUuid(String s) {
        return s != null && s.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-\\d+$");
    }

    /**
     * Filters a list of suggestions by a case-insensitive prefix.
     *
     * @param prefix      current typed token (may be {@code null} or empty)
     * @param suggestions full candidate list
     * @return filtered list preserving original order
     */
    public static List<String> filterPrefix(String prefix, List<String> suggestions) {
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

    /**
     * Returns a snapshot list of online player names.
     *
     * @return online player names, or an empty list if none
     */
    public static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names.isEmpty() ? Collections.emptyList() : names;
    }
}
