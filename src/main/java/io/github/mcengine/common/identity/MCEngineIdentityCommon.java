package io.github.mcengine.common.identity;

import io.github.mcengine.common.identity.database.IMCEngineIdentityDB;
import io.github.mcengine.common.identity.database.mysql.MCEngineIdentityMySQL;
import io.github.mcengine.common.identity.database.postgresql.MCEngineIdentityPostgreSQL;
import io.github.mcengine.common.identity.database.sqlite.MCEngineIdentitySQLite;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.*;

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

    public String createProfileAlt(Player player) {
        return db.createProfileAlt(player);
    }

    public boolean changeProfileAlt(Player player, String alt_uuid) {
        return db.changeProfileAlt(player, alt_uuid);
    }

    public boolean setProfileAltname(Player player, String altUuid, String alt_name) {
        return db.setProfileAltname(player, altUuid, alt_name);
    }

    // -------------------------------------------------
    // Inventory serialization helpers (example feature)
    // -------------------------------------------------

    /**
     * Serializes the given inventory to a compact byte[] using Java serialization.
     * This is a simple example; production systems may prefer a format like NBT or JSON.
     */
    private byte[] serializeInventory(ItemStack[] contents) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(contents);
            out.flush();
            return bos.toByteArray();
        }
    }

    /** Deserializes an inventory previously serialized by {@link #serializeInventory(ItemStack[])}. */
    private ItemStack[] deserializeInventory(byte[] data) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return (ItemStack[]) in.readObject();
        }
    }

    /**
     * Saves the player's current inventory into {@code identity_alternative.identity_alternative_storage}
     * for the active alt recorded in {@code identity_session}. Returns {@code true} if persisted.
     */
    public boolean saveActiveAltInventory(Player player) {
        Connection c = db.getDBConnection();
        if (c == null) return false;
        String identityUuid = player.getUniqueId().toString();
        try {
            String altUuid = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) altUuid = rs.getString(1);
                }
            }
            if (altUuid == null) return false;

            byte[] payload = serializeInventory(player.getInventory().getContents());
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE identity_alternative SET identity_alternative_storage = ?, identity_alternative_updated_at = CURRENT_TIMESTAMP " +
                            "WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
                up.setBytes(1, payload);
                up.setString(2, altUuid);
                up.setString(3, identityUuid);
                return up.executeUpdate() > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save inventory: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads the active alt's stored inventory (if present) and applies it to the player.
     * Returns {@code true} if loaded.
     */
    public boolean loadActiveAltInventory(Player player) {
        Connection c = db.getDBConnection();
        if (c == null) return false;
        String identityUuid = player.getUniqueId().toString();
        try {
            String altUuid = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT identity_alternative_uuid FROM identity_session WHERE identity_uuid = ?")) {
                ps.setString(1, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) altUuid = rs.getString(1);
                }
            }
            if (altUuid == null) return false;

            byte[] payload = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT identity_alternative_storage FROM identity_alternative WHERE identity_alternative_uuid = ? AND identity_uuid = ?")) {
                ps.setString(1, altUuid);
                ps.setString(2, identityUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) payload = rs.getBytes(1);
                }
            }
            if (payload == null) return false;

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
