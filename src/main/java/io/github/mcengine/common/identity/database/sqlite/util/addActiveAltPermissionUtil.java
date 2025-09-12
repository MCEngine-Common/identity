package io.github.mcengine.common.identity.database.sqlite.util;

import io.github.mcengine.common.identity.database.sqlite.util.addProfileAltPermissionUtil;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;

public final class addActiveAltPermissionUtil {
    private addActiveAltPermissionUtil() {}

    public static boolean invoke(Connection conn, Plugin plugin, Player player, String permName, String activeAltUuid) {
        return addProfileAltPermissionUtil.invoke(conn, plugin, player, activeAltUuid, permName);
    }
}
