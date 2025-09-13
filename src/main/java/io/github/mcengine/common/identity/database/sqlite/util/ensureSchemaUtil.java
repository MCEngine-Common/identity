package io.github.mcengine.common.identity.database.sqlite.util;

import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Idempotently ensures the SQLite schema for Identity, Alternatives, Session, and Permission.
 * IMPORTANT: Ensure PRAGMA foreign_keys = ON for FK enforcement in SQLite.
 */
public final class ensureSchemaUtil {
    private ensureSchemaUtil() {}

    public static void invoke(Connection c, Plugin plugin) throws SQLException {
        try (Statement st = c.createStatement()) {
            // Ensure foreign keys are enforced for this connection
            st.execute("PRAGMA foreign_keys = ON");

            // identity
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity (" +
                "  identity_uuid TEXT PRIMARY KEY," +
                "  identity_limit INTEGER NOT NULL DEFAULT 1," +
                "  identity_created_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  identity_updated_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  CHECK (identity_limit >= 0)" +
                ")"
            );

            // identity_alternative
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_alternative (" +
                "  identity_alternative_uuid TEXT PRIMARY KEY," +
                "  identity_uuid TEXT NOT NULL," +
                "  identity_alternative_name TEXT NULL," +
                "  identity_alternative_storage BLOB NULL," +
                "  identity_alternative_created_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  identity_alternative_updated_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE" +
                ")"
            );
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_name ON identity_alternative(identity_uuid, identity_alternative_name)");
            // supports composite FK from session
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_alt_identity_uuid ON identity_alternative(identity_uuid, identity_alternative_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_identity ON identity_alternative(identity_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_name ON identity_alternative(identity_alternative_name)");

            // identity_session
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_session (" +
                "  identity_uuid TEXT PRIMARY KEY," +
                "  identity_alternative_uuid TEXT NULL," +
                "  FOREIGN KEY (identity_uuid) REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL," +
                "  FOREIGN KEY (identity_uuid, identity_alternative_uuid) REFERENCES identity_alternative(identity_uuid, identity_alternative_uuid)" +
                ")"
            );

            // identity_permission (scope to alternative only)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_permission (" +
                "  identity_alternative_uuid TEXT NOT NULL," +
                "  identity_permission_name TEXT NOT NULL," +
                "  identity_permission_created_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  identity_permission_updated_at TEXT NOT NULL DEFAULT (datetime('now'))," +
                "  FOREIGN KEY (identity_alternative_uuid) REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE," +
                "  PRIMARY KEY (identity_alternative_uuid, identity_permission_name)" +
                ")"
            );
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_name ON identity_permission(identity_permission_name)");
        }
    }
}
