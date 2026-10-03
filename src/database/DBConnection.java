package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP Connection Pool — Singleton, thread-safe.
 *
 * Design Rationale (replacing raw DriverManager):
 *  - DriverManager.getConnection() opens a fresh TCP socket to MySQL on every
 *    call. Under 20 concurrent threads each making 5 SQL calls, that is 100
 *    connection establishment handshakes — typically 20-100ms each on localhost.
 *  - HikariCP maintains a warm pool of reusable connections. getConnection()
 *    returns an already-open connection in sub-millisecond time.
 *  - Pool exhaustion protection: connectionTimeout=20s means a thread waiting
 *    for a connection will throw after 20 seconds instead of hanging forever.
 *
 * Isolation Level: REPEATABLE_READ (MySQL default).
 *  - A transaction sees a consistent snapshot from its start time.
 *  - Combined with the atomic conditional UPDATE in ProductDAO.deductStock(),
 *    this prevents phantom reads and dirty reads during concurrent order processing.
 *
 * Singleton pattern: the HikariDataSource is created once at class-load time
 * (static initializer) and reused for the JVM lifetime. A shutdown hook
 * cleanly drains and closes all connections on JVM exit.
 */
public class DBConnection {

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();

        // ── JDBC settings ────────────────────────────────────────────────────
        config.setJdbcUrl("jdbc:mysql://localhost:3306/stockflow"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        config.setUsername("root");
        config.setPassword("root123");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // ── Pool sizing ───────────────────────────────────────────────────────
        // maximumPoolSize=10: caps concurrent DB connections; prevents MySQL
        // "Too many connections" errors under high load.
        config.setMaximumPoolSize(10);

        // minimumIdle=5: keeps 5 connections pre-warmed so burst traffic does
        // not pay connection setup latency.
        config.setMinimumIdle(5);

        // ── Timeouts ──────────────────────────────────────────────────────────
        // idleTimeout=30s: idle connections above minimumIdle are reaped after
        // 30 seconds to free MySQL server resources.
        config.setIdleTimeout(30_000);

        // connectionTimeout=20s: a thread waiting for a pooled connection will
        // throw SQLException after 20 seconds. Prevents indefinite blocking.
        config.setConnectionTimeout(20_000);

        // maxLifetime=1800s: connections are retired after 30 minutes regardless
        // of activity. Prevents stale connections caused by MySQL wait_timeout.
        config.setMaxLifetime(1_800_000);

        // ── Connection health ─────────────────────────────────────────────────
        // keepaliveTime sends a lightweight ping every 60s to prevent MySQL
        // from closing idle connections server-side.
        config.setKeepaliveTime(60_000);
        config.setConnectionTestQuery("SELECT 1");

        // ── Pool name (visible in thread dumps / JMX) ─────────────────────────
        config.setPoolName("StockFlow-HikariPool");

        dataSource = new HikariDataSource(config);

        // Shutdown hook: drains pool cleanly on JVM exit (Ctrl+C / System.exit)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!dataSource.isClosed()) {
                dataSource.close();
                System.out.println("[StockFlow] HikariCP pool shut down cleanly.");
            }
        }, "hikari-shutdown-hook"));
    }

    /**
     * Acquire a Connection from the Hikari pool.
     *
     * The caller is responsible for closing (returning) the connection,
     * preferably in a try-with-resources or finally block.
     *
     * @throws SQLException if no connection is available within connectionTimeout.
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Explicit pool shutdown — call this only from integration tests or
     * non-JVM-exit teardown paths. Normal app shutdown uses the ShutdownHook.
     */
    public static void shutdown() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // Prevent instantiation — this is a pure utility class
    private DBConnection() {}
}
