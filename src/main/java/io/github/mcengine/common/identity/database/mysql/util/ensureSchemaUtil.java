package io.github.mcengine.common.identity.database.mysql.util;

import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Idempotently ensures the MySQL schema for Identity, Alternatives, Session, and Permission.
 * Notes:
 *  - Adds a UNIQUE(identity_uuid, identity_alternative_uuid) on identity_alternative so we can
 *    enforce that a session's selected alt actually belongs to the same identity.
 *  - identity_permission is scoped to the alternative only to avoid cross-identity mismatches.
 */
public final class ensureSchemaUtil {
    private ensureSchemaUtil() {}

    public static void invoke(Connection c, Plugin plugin) throws SQLException {
        try (Statement st = c.createStatement()) {

            // identity
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity (" +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_limit INT NOT NULL DEFAULT 1," +
                "  identity_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  identity_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (identity_uuid)," +
                "  CHECK (identity_limit >= 0)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // identity_alternative
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_alternative (" +
                "  identity_alternative_uuid VARCHAR(64) NOT NULL," +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_name VARCHAR(64) NULL," +
                "  identity_alternative_storage MEDIUMBLOB NULL," + // MEDIUMBLOB is usually sufficient
                "  identity_alternative_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  identity_alternative_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (identity_alternative_uuid)," +
                "  CONSTRAINT fk_alt_identity FOREIGN KEY (identity_uuid) " +
                "    REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                // allow multiple NULL names by MySQL behavior; still enforces uniqueness when non-NULL
                "  UNIQUE KEY uniq_identity_name (identity_uuid, identity_alternative_name)," +
                // needed to support composite FK from session to ensure alt belongs to identity
                "  UNIQUE KEY uq_alt_identity_uuid (identity_uuid, identity_alternative_uuid)," +
                "  KEY idx_alt_identity (identity_uuid)," +
                "  KEY idx_alt_name (identity_alternative_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // identity_session
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_session (" +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_uuid VARCHAR(64) NULL," +
                "  PRIMARY KEY (identity_uuid)," +
                "  CONSTRAINT fk_session_identity FOREIGN KEY (identity_uuid) " +
                "    REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  CONSTRAINT fk_session_alt FOREIGN KEY (identity_alternative_uuid) " +
                "    REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL," +
                // ensure that if an alt is chosen, it actually belongs to this identity
                "  CONSTRAINT fk_session_alt_matches_identity FOREIGN KEY (identity_uuid, identity_alternative_uuid) " +
                "    REFERENCES identity_alternative(identity_uuid, identity_alternative_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // identity_permission (scope to alternative only)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_permission (" +
                "  identity_alternative_uuid VARCHAR(64) NOT NULL," +
                "  identity_permission_name VARCHAR(64) NOT NULL," +
                "  identity_permission_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  identity_permission_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  CONSTRAINT fk_perm_alt FOREIGN KEY (identity_alternative_uuid) " +
                "    REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE," +
                "  PRIMARY KEY (identity_alternative_uuid, identity_permission_name)," +
                "  KEY idx_perm_name (identity_permission_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }
}
