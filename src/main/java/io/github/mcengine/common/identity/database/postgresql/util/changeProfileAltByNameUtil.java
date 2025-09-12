package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;

public final class changeProfileAltByNameUtil {
    private changeProfileAltByNameUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String altName) {
        String altUuid = getProfileAltUuidByNameUtil.invoke(conn, plugin, player, altName);
        if (altUuid == null) return false;
        return changeProfileAltUtil.invoke(conn, plugin, player, altUuid);
    }
}
