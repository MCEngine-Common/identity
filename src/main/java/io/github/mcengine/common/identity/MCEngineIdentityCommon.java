package io.github.mcengine.common.identity;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
import io.github.mcengine.common.identity.database.mysql.MCEngineIdentityMySQL;
import io.github.mcengine.common.identity.database.postgresql.MCEngineIdentityPostgreSQL;
import io.github.mcengine.common.identity.database.sqlite.MCEngineIdentitySQLite;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

/**
 * The {@code MCEngineIdentityCommon} class wires a Bukkit {@link Plugin} to an
 * {@link IMCEngineIdentityDB} backend and exposes convenience methods to manage
 * player identity alternatives as well as store/load the active alt's inventory.
 */
public class MCEngineIdentityCommon {

    /** Singleton instance of the Identity common API. */
    private static MCEngineIdentityCommon instance;

    /** The Bukkit plugin instance that owns this API. */
    private final Plugin plugin;

    /** Database interface used by the Identity module. */
    private final IMCEngineIdentityDB db;

    /**
     * Constructs the Identity API and selects the database implementation from config
     * ({@code database.type}: sqlite | mysql | postgresql).
     *
     * @param plugin Bukkit plugin instance
     */
    public MCEngineIdentityCommon(Plugin plugin) {
        instance = this;
        this.plugin = plugin;

        String dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        switch (dbType) {
            case "sqlite" -> this.db = new MCEngineIdentitySQLite(plugin);
            case "mysql" -> this.db = new MCEngineIdentityMySQL(plugin);
            case "postgresql" -> this.db = new MCEngineIdentityPostgreSQL(plugin);
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }

    /** Returns the global API singleton instance. */
    public static MCEngineIdentityCommon getApi() { return instance; }

    /** Returns the Bukkit plugin instance. */
    public Plugin getPlugin() { return plugin; }

    /** Returns the database interface used by this module. */
    public IMCEngineIdentityDB getDB() { return db; }

    // ------------------------------
    // Business operations (delegated)
    // ------------------------------

    /**
     * Ensures the player's identity + primary alt + session exist in the DB.
     *
     * @param player Bukkit player
     * @return {@code true} if ensure completed successfully
     */
    public boolean ensureExist(Player player) {
        return db.ensureExist(player);
    }

    /**
     * Resolves an alt UUID by its display name for the given player.
     *
     * @param player  the Bukkit player
     * @param altName the display name of the alt
     * @return the alt UUID string, or {@code null} if not found
     */
    public String getProfileAltUuidByName(Player player, String altName) {
        return db.getProfileAltUuidByName(player, altName);
    }

    public String createProfileAlt(Player player) {
        return db.createProfileAlt(player);
    }

    public boolean changeProfileAlt(Player player, String alt_uuid) {
        return db.changeProfileAlt(player, alt_uuid);
    }

    public boolean setProfileAltname(Player player, String altUuid, String alt_name) {
        return db.setProfileAltname(player, altUuid, alt_name);
    }

    /**
     * Returns the number of alternatives currently stored for the player's identity
     * (i.e., rows in {@code identity_alternative} for the player's {@code identity_uuid}).
     *
     * @param player Bukkit player
     * @return total alt count (including the primary {@code {uuid}-0})
     */
    public int getProfileCount(Player player) {
        return db.getProfileCount(player);
    }

    /**
     * Fetches the display name of an alt belonging to the player, or {@code null} if unset/not found.
     *
     * @param player  owner of the identity
     * @param altUuid alt UUID (e.g., {@code {uuid}-N})
     * @return display name, or {@code null}
     */
    public String getProfileAltName(Player player, String altUuid) {
        return db.getProfileAltName(player, altUuid);
    }

    /** Returns all alt identifiers/names for the player's identity. */
    public java.util.List<String> getProfileAllAlt(Player player) {
        return db.getProfileAllAlt(player);
    }

    /**
     * Increases the identity alt limit for the target player by {@code amount}.
     *
     * @param target the player whose limit to increase
     * @param amount non-negative increment
     * @return {@code true} if updated
     */
    public boolean addLimit(Player target, int amount) {
        return db.addLimit(target, amount);
    }

    /** 
     * Returns the current alt limit for the player's identity. 
     *
     * @param player Bukkit player
     * @return the maximum number of alts allowed for this identity
     */
    public int getLimit(Player player) {
        return db.getLimit(player);
    }

    // -------------------------------------------------
    // Inventory serialization helpers (updated)
    // -------------------------------------------------

    /**
     * Serializes the given inventory to a compact byte[] using Bukkit's object
     * streams. This avoids {@link java.io.NotSerializableException} for
     * {@code CraftItemStack}.
     */
    private byte[] serializeInventory(ItemStack[] contents) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            out.flush();
            return bos.toByteArray();
        }
    }

    /**
     * Deserializes an inventory previously serialized by
     * {@link #serializeInventory(ItemStack[])} using Bukkit's object streams.
     */
    private ItemStack[] deserializeInventory(byte[] data) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            int len = in.readInt();
            ItemStack[] out = new ItemStack[len];
            for (int i = 0; i < len; i++) {
                out[i] = (ItemStack) in.readObject();
            }
            return out;
        }
    }

    /**
     * Saves the player's current inventory into {@code identity_alternative.identity_alternative_storage}
     * for the active alt recorded in {@code identity_session}. Returns {@code true} if persisted.
     */
    public boolean saveActiveAltInventory(Player player) {
        try {
            byte[] payload = serializeInventory(player.getInventory().getContents());
            return db.saveAltInventory(player, payload);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save inventory: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads the active alt's stored inventory (if present) and applies it to the player.
     * If no stored inventory exists, the player's inventory is cleared so the alt starts empty.
     * Returns {@code true} if an inventory payload was loaded, {@code false} if cleared.
     */
    public boolean loadActiveAltInventory(Player player) {
        try {
            byte[] payload = db.loadAltInventory(player);
            if (payload == null) {
                // No stored inventory for this alt -> start empty
                player.getInventory().clear();
                player.updateInventory();
                return false; // keep caller message: "no stored inventory"
            }
            ItemStack[] restored = deserializeInventory(payload);
            player.getInventory().setContents(restored);
            player.updateInventory();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load inventory: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
