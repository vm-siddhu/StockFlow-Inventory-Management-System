package database;

import java.sql.Connection;
import java.sql.SQLException;

/** Supplies a JDBC connection for a use case; production uses the Hikari pool. */
@FunctionalInterface
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}
