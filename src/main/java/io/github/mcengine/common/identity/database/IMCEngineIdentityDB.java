package io.github.mcengine.common.identity.database;

import org.bukkit.entity.Player;

import java.sql.Connection;

/**
 * Minimal persistence contract for the Identity module.
 * <p>
 * Exposes a JDBC {@link Connection} and higher-level operations for
 * creating/switching alts, naming, limits, and active-alt inventory I/O.
 */
public interface IMCEngineIdentityDB {

    /**
     * Returns the active JDBC connection for the Identity database.
     *
     * @return an open {@link Connection}, or {@code null} if unavailable
     */
    Connection getDBConnection();

    /**
     * Creates (or ensures) an identity for the given player and then creates a new alternative
     * profile with the next available suffix pattern <code>{player_uuid}-{n}</code>.
     *
     * @param player Bukkit player
     * @return the created alt UUID (e.g., {@code 123e4567-e89b-12d3-a456-426614174000-0})
     */
    String createProfileAlt(Player player);

    /**
     * Switches the active session alt for the player's identity. If a session row does not
     * exist yet, it will be created.
     *
     * @param player   Bukkit player
     * @param altUuid  alternative UUID to activate (must belong to player's identity)
     * @return true if changed, false otherwise
     */
    boolean changeProfileAlt(Player player, String altUuid);

    /**
     * Sets (or clears when {@code altName} is {@code null}) an alt display name.
     * Enforces uniqueness per identity on (identity_uuid, identity_alternative_name).
     *
     * @param player  Bukkit player
     * @param altUuid alternative UUID to rename
     * @param altName new display name (nullable)
     * @return true if updated, false otherwise
     */
    boolean setProfileAltname(Player player, String altUuid, String altName);

    /**
     * Fetches the display name of an alternative belonging to the given player.
     *
     * @param player  owner of the identity
     * @param altUuid alternative UUID
     * @return display name or {@code null} if unset/not found
     */
    String getProfileAltName(Player player, String altUuid);

    /**
     * Returns all alternatives for the player's identity, each entry being the display
     * name if set, otherwise the alt UUID (e.g., {@code {uuid}-N}). Ordered by UUID asc.
     *
     * @param player Bukkit player
     * @return list of alt identifiers or names (never {@code null})
     */
    java.util.List<String> getProfileAllAlt(Player player);

    /**
     * Increases the identity alt limit for the given player by {@code amount}.
     * Implementations should upsert the identity row if it does not exist.
     *
     * @param player the player whose limit to change
     * @param amount non-negative increment
     * @return {@code true} if updated/persisted
     */
    boolean addLimit(Player player, int amount);

    /**
     * Returns the configured alt limit for the player's identity. Implementations should
     * upsert an identity row when absent and return the effective limit (default {@code 1}).
     *
     * @param player the player whose limit to read
     * @return the current limit (>= 1)
     */
    int getLimit(Player player);

    /**
     * Persists a serialized inventory payload for the active alt recorded in {@code identity_session}.
     *
     * @param player  the player whose active alt to persist
     * @param payload opaque, serialized inventory bytes
     * @return {@code true} if written
     */
    boolean saveAltInventory(Player player, byte[] payload);

    /**
     * Loads the serialized inventory payload for the player's currently active alt, if present.
     *
     * @param player the player whose active alt to load
     * @return inventory bytes, or {@code null} when no data is stored
     */
    byte[] loadAltInventory(Player player);
}
