package io.github.mcengine.common.identity.database.postgresql.util;

import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Idempotently ensures the PostgreSQL schema for Identity, Alternatives, Session, and Permission.
 * Notes mirror the MySQL variant (composite FK for session; permission scoped to alt).
 */
public final class ensureSchemaUtil {
    private ensureSchemaUtil() {}

    public static void invoke(Connection c, Plugin plugin) throws SQLException {
        try (Statement st = c.createStatement()) {

            // identity
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity (" +
                "  identity_uuid VARCHAR(36) PRIMARY KEY," +
                "  identity_limit INT NOT NULL DEFAULT 1," +
                "  identity_created_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  identity_updated_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  CHECK (identity_limit >= 0)" +
                ")"
            );

            // identity_alternative
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_alternative (" +
                "  identity_alternative_uuid VARCHAR(64) PRIMARY KEY," +
                "  identity_uuid VARCHAR(36) NOT NULL," +
                "  identity_alternative_name VARCHAR(64) NULL," +
                "  identity_alternative_storage BYTEA NULL," +
                "  identity_alternative_created_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  identity_alternative_updated_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  CONSTRAINT fk_alt_identity FOREIGN KEY (identity_uuid) " +
                "    REFERENCES identity(identity_uuid) ON DELETE CASCADE" +
                ")"
            );
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uniq_identity_name ON identity_alternative(identity_uuid, identity_alternative_name)");
            // supports composite FK from session
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_alt_identity_uuid ON identity_alternative(identity_uuid, identity_alternative_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_identity ON identity_alternative(identity_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_alt_name ON identity_alternative(identity_alternative_name)");

            // identity_session
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_session (" +
                "  identity_uuid VARCHAR(36) PRIMARY KEY," +
                "  identity_alternative_uuid VARCHAR(64) NULL," +
                "  CONSTRAINT fk_session_identity FOREIGN KEY (identity_uuid) " +
                "    REFERENCES identity(identity_uuid) ON DELETE CASCADE," +
                "  CONSTRAINT fk_session_alt FOREIGN KEY (identity_alternative_uuid) " +
                "    REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE SET NULL," +
                "  CONSTRAINT fk_session_alt_matches_identity FOREIGN KEY (identity_uuid, identity_alternative_uuid) " +
                "    REFERENCES identity_alternative(identity_uuid, identity_alternative_uuid)" +
                ")"
            );

            // identity_permission (scope to alternative only)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS identity_permission (" +
                "  identity_alternative_uuid VARCHAR(64) NOT NULL," +
                "  identity_permission_name VARCHAR(64) NOT NULL," +
                "  identity_permission_created_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  identity_permission_updated_at TIMESTAMP NOT NULL DEFAULT NOW()," +
                "  CONSTRAINT fk_perm_alt FOREIGN KEY (identity_alternative_uuid) " +
                "    REFERENCES identity_alternative(identity_alternative_uuid) ON DELETE CASCADE," +
                "  PRIMARY KEY (identity_alternative_uuid, identity_permission_name)" +
                ")"
            );
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_perm_name ON identity_permission(identity_permission_name)");
        }
    }
}
