package database;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Owns the application's shared Hikari connection pool. */
public final class DBConnection {
    private static final HikariDataSource dataSource = createDataSource();

    private static HikariDataSource createDataSource() {
        String url = setting("DB_URL", "jdbc:mysql://localhost:3306/stockflow"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String user = setting("DB_USER", "root");
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new ExceptionInInitializerError(
                "DB_PASSWORD is required. Set it before starting StockFlow.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setPoolName("StockFlow");
        HikariDataSource pool = new HikariDataSource(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!pool.isClosed()) pool.close();
        }, "stockflow-pool-shutdown"));
        return pool;
    }

    private static String setting(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void shutdown() {
        if (!dataSource.isClosed()) dataSource.close();
    }

    private DBConnection() {}
}
