package test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dao.ProductDAO;
import database.DBConnection;
import dsa.ProductBST;
import exception.InsufficientStockException;
import exception.TransactionExecutionException;
import model.Product;

/**
 * Concurrent Benchmark & Correctness Harness for StockFlow.
 *
 * Validates two critical claims:
 *
 *  PHASE 1 — Batch SKU Throughput:
 *   Insert 10,000 synthetic products using batch-prepared statements.
 *   Measures: total time (ms), TPS (Transactions Per Second), and
 *   simultaneously verifies AVL tree insertion correctness.
 *
 *  PHASE 2 — Concurrent Purchase Transactions:
 *   Seed a limited-stock product (stock = N_ORDERS).
 *   Submit N_ORDERS purchase tasks across THREAD_COUNT threads simultaneously.
 *   Each task attempts to deduct 1 unit atomically via the ACID transaction path
 *   (conditional UPDATE: stock = stock - 1 WHERE stock >= 1).
 *
 *   ASSERTION: fulfilled_count + final_stock == initial_stock
 *   If this holds → 0% overselling, 0% data drift under race conditions.
 *
 * Run:
 *   javac -cp "lib/*;src" src/test/BenchmarkRunner.java
 *   java  -cp "lib/*;src" test.BenchmarkRunner
 */
public class BenchmarkRunner {

    // ── Benchmark Configuration ────────────────────────────────────────────────
    private static final int THREAD_COUNT   = 20;
    private static final int N_SKUS         = 10_000;  // batch insert volume
    private static final int N_ORDERS       = 1_000;   // concurrent purchase count
    private static final int INITIAL_STOCK  = N_ORDERS; // exactly enough for all orders

    // Test product name prefix (used for cleanup)
    private static final String TEST_PREFIX = "BENCH_SKU_";
    private static final String BENCH_PRODUCT_NAME = "BENCH_HIGH_DEMAND_ITEM";

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     StockFlow Concurrent Benchmark Harness       ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        ProductDAO productDAO = new ProductDAO();

        try {
            // ── Phase 1: Batch SKU Insert ──────────────────────────────────────
            runPhase1BatchInsert(productDAO);

            // ── Phase 2: Concurrent Order Transactions ─────────────────────────
            runPhase2ConcurrentOrders(productDAO);

        } finally {
            // ── Cleanup: remove all test data ──────────────────────────────────
            cleanup();
            DBConnection.shutdown();
            System.out.println("\n[CLEANUP] Test data removed. Pool shut down.");
        }
    }

    // ─── Phase 1 ───────────────────────────────────────────────────────────────

    /**
     * Batch-insert N_SKUS products using ProductDAO.batchInsert() + verify BST.
     *
     * The batchInsert() method uses a single PreparedStatement with addBatch()
     * and a single commit — orders of magnitude faster than N individual INSERTs.
     * This tests both DB throughput and AVL tree insertion correctness.
     */
    private static void runPhase1BatchInsert(ProductDAO productDAO) {
        System.out.println("── PHASE 1: Batch SKU Insert (" + N_SKUS + " products) ──────────────");

        // Build product list
        List<Product> products = new ArrayList<>(N_SKUS);
        ProductBST bst = new ProductBST();

        for (int i = 0; i < N_SKUS; i++) {
            // Vary prices to stress the AVL tree with diverse insert patterns
            double price = 1.0 + (i % 500) * 0.99;
            Product p = new Product(0, TEST_PREFIX + i, "BENCHMARK", price, 100, 10);
            products.add(p);
            bst.insert(p);  // simultaneously build AVL index
        }

        long start = System.currentTimeMillis();
        productDAO.batchInsert(products);
        long elapsed = System.currentTimeMillis() - start;

        double tps = (N_SKUS / Math.max(elapsed, 1.0)) * 1000.0;

        System.out.printf("  Inserted  : %,d SKUs%n", N_SKUS);
        System.out.printf("  Time      : %d ms%n", elapsed);
        System.out.printf("  Throughput: %.0f inserts/sec%n", tps);

        // Verify AVL tree range query (sanity check)
        List<Product> rangeResult = bst.searchByPriceRange(50.0, 200.0);
        System.out.printf("  AVL Range [50-200]: %d products found (O(k+log n))%n%n", rangeResult.size());
    }

    // ─── Phase 2 ───────────────────────────────────────────────────────────────

    /**
     * Simulate N_ORDERS concurrent purchase transactions on a single product
     * seeded with exactly N_ORDERS units of stock.
     *
     * CountDownLatch synchronizes all threads to start simultaneously,
     * maximizing contention on the database row to stress-test the CAS UPDATE.
     *
     * ASSERTION: fulfilled + final_stock == INITIAL_STOCK
     */
    private static void runPhase2ConcurrentOrders(ProductDAO productDAO) throws InterruptedException {
        System.out.println("── PHASE 2: Concurrent Purchase Transactions (" + N_ORDERS + " threads) ───");

        // Seed a single high-demand product with exactly INITIAL_STOCK units
        int benchProductId = seedBenchmarkProduct(INITIAL_STOCK);
        System.out.println("  Seeded product #" + benchProductId
            + " with stock = " + INITIAL_STOCK);

        ExecutorService pool     = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch  ready    = new CountDownLatch(N_ORDERS);  // tasks signal ready
        CountDownLatch  startGun = new CountDownLatch(1);          // one release fires all

        AtomicInteger fulfilled = new AtomicInteger(0);  // successful deductions
        AtomicInteger rejected  = new AtomicInteger(0);  // stock-exhausted rejections
        AtomicInteger errors    = new AtomicInteger(0);  // unexpected failures

        // Pre-create all tasks (they block on startGun)
        for (int i = 0; i < N_ORDERS; i++) {
            final int orderId = i + 1;
            pool.submit(() -> {
                ready.countDown();          // signal this thread is ready
                try {
                    startGun.await();       // wait for all threads to be ready
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                // ── Execute ACID transaction ────────────────────────────────
                Connection conn = null;
                try {
                    conn = DBConnection.getConnection();
                    conn.setAutoCommit(false);

                    // Atomic conditional stock deduction — CAS at DB level
                    boolean deducted = productDAO.deductStock(conn, benchProductId, 1);
                    if (deducted) {
                        // Insert order history in same transaction
                        insertOrderHistory(conn, orderId, benchProductId);
                        conn.commit();
                        fulfilled.incrementAndGet();
                    } else {
                        conn.rollback();
                        rejected.incrementAndGet();  // stock exhausted — correct behaviour
                    }

                } catch (InsufficientStockException e) {
                    safeRollback(conn);
                    rejected.incrementAndGet();
                } catch (TransactionExecutionException | SQLException e) {
                    safeRollback(conn);
                    errors.incrementAndGet();
                    System.err.println("  [ERROR] Order #" + orderId + ": " + e.getMessage());
                } finally {
                    if (conn != null) {
                        try { conn.setAutoCommit(true); conn.close(); }
                        catch (SQLException ignored) {}
                    }
                }
            });
        }

        // Wait until all threads are spawned and blocked on startGun
        ready.await();

        long start = System.currentTimeMillis();
        startGun.countDown();  // FIRE — all N_ORDERS threads start simultaneously

        pool.shutdown();
        pool.awaitTermination(120, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        // ── Read final stock from DB ────────────────────────────────────────
        int finalStock = readFinalStock(benchProductId);
        double tps = (fulfilled.get() / Math.max(elapsed, 1.0)) * 1000.0;

        System.out.printf("  Threads       : %d%n", THREAD_COUNT);
        System.out.printf("  Total tasks   : %,d%n", N_ORDERS);
        System.out.printf("  Fulfilled     : %d%n", fulfilled.get());
        System.out.printf("  Rejected      : %d (stock exhausted — correct)%n", rejected.get());
        System.out.printf("  Errors        : %d%n", errors.get());
        System.out.printf("  Initial stock : %d%n", INITIAL_STOCK);
        System.out.printf("  Final stock   : %d%n", finalStock);
        System.out.printf("  Time          : %d ms%n", elapsed);
        System.out.printf("  Throughput    : %.0f TPS%n", tps);
        System.out.printf("  Avg latency   : %.2f ms/txn%n", (double) elapsed / Math.max(fulfilled.get(), 1));

        // ── Zero-Oversell Assertion ─────────────────────────────────────────
        System.out.println();
        boolean zeroOversell = (fulfilled.get() + finalStock == INITIAL_STOCK);
        boolean zeroErrors   = (errors.get() == 0);

        if (zeroOversell && zeroErrors) {
            System.out.println("  ✔ [PASS] ZERO OVERSELL: fulfilled(" + fulfilled.get()
                + ") + finalStock(" + finalStock + ") == initialStock(" + INITIAL_STOCK + ")");
            System.out.println("  ✔ [PASS] ZERO transaction errors under " + N_ORDERS + " concurrent requests.");
        } else {
            if (!zeroOversell) {
                System.out.println("  ✘ [FAIL] OVERSELL DETECTED: fulfilled(" + fulfilled.get()
                    + ") + finalStock(" + finalStock + ") != initialStock(" + INITIAL_STOCK + ")");
            }
            if (!zeroErrors) {
                System.out.println("  ✘ [FAIL] " + errors.get() + " unexpected errors during benchmark.");
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Insert a benchmark order history record within the provided transaction.
     * Mirrors what OrderHistoryDAO.save() does — inline here to avoid
     * dependency on the full service stack in the benchmark.
     */
    private static void insertOrderHistory(Connection conn, int orderId, int productId)
            throws SQLException {
        String sql = "INSERT INTO order_history"
                   + "(order_id, customer_name, product_id, quantity, priority, status, processed_time) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId + 900_000);  // offset to avoid collision with real orders
            ps.setString(2, "BENCHMARK_CUSTOMER_" + orderId);
            ps.setInt(3, productId);
            ps.setInt(4, 1);
            ps.setInt(5, 0);
            ps.setString(6, "PROCESSED");
            ps.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /** Seed a single product and return its auto-generated product_id. */
    private static int seedBenchmarkProduct(int stock) {
        String insert = "INSERT INTO products(product_name, category, price, stock, minimum_stock) "
                      + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(insert,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, BENCH_PRODUCT_NAME);
            ps.setString(2, "BENCHMARK");
            ps.setDouble(3, 99.99);
            ps.setInt(4, stock);
            ps.setInt(5, 1);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to seed benchmark product", e);
        }
        throw new TransactionExecutionException("Benchmark product seed returned no generated key");
    }

    /** Read the current stock of a product directly from DB. */
    private static int readFinalStock(int productId) {
        String sql = "SELECT stock FROM products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("stock");
            }
        } catch (SQLException e) {
            System.err.println("[WARN] Could not read final stock: " + e.getMessage());
        }
        return -1;
    }

    /** Remove all benchmark test data from the database after the run. */
    private static void cleanup() {
        String[] sqls = {
            "DELETE FROM order_history WHERE customer_name LIKE 'BENCHMARK_CUSTOMER_%'",
            "DELETE FROM products WHERE product_name LIKE '" + TEST_PREFIX + "%'",
            "DELETE FROM products WHERE product_name = '" + BENCH_PRODUCT_NAME + "'"
        };
        try (Connection conn = DBConnection.getConnection()) {
            for (String sql : sqls) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int rows = ps.executeUpdate();
                    System.out.println("[CLEANUP] " + sql.split(" ")[2] + " — " + rows + " rows removed.");
                }
            }
        } catch (SQLException e) {
            System.err.println("[WARN] Cleanup failed: " + e.getMessage());
        }
    }

    private static void safeRollback(Connection conn) {
        if (conn != null) {
            try { conn.rollback(); } catch (SQLException ignored) {}
        }
    }
}
