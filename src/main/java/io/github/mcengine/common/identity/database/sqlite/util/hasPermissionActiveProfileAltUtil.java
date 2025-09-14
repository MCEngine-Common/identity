package io.github.mcengine.common.identity.database.sqlite.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;

public final class hasPermissionActiveProfileAltUtil {
    private hasPermissionActiveProfileAltUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String permName, String activeAltUuid, String primaryAltUuid) {
        String targetAlt = (activeAltUuid != null ? activeAltUuid : primaryAltUuid);
        return hasPermissionProfileAltUtil.invoke(conn, plugin, player, targetAlt, permName);
    }
}
