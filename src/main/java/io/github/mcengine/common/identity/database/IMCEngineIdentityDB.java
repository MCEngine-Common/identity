package io.github.mcengine.common.identity.database;

import org.bukkit.entity.Player;
import java.sql.Connection;

/**
 * Minimal persistence contract for the Identity module.
 * <p>
 * Exposes a JDBC {@link Connection} and higher-level operations for
 * creating/switching alts and setting an alt's display name.
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
}
