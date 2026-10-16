package service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import dao.OrderHistoryDAO;
import dao.ProductDAO;
import database.DBConnection;
import database.ConnectionProvider;
import dsa.QueueManager;
import exception.InsufficientStockException;
import exception.ProductNotFoundException;
import exception.TransactionExecutionException;
import model.Order;
import model.Product;
import repository.OrderRepository;
import repository.ProductRepository;

/**
 * Coordinates FIFO order placement and transactional processing for the console.
 * The SQL conditional update protects stock even if a second app instance places
 * an order at the same time.
 */
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository   orderRepository;
    private final QueueManager      queueManager;
    private final ConnectionProvider connectionProvider;


    public OrderService(QueueManager queueManager) {
        this(new ProductDAO(), new OrderHistoryDAO(), queueManager, DBConnection::getConnection);
    }

    /**
     * Constructor-injection overload — for unit tests that supply fake repositories.
     */
    public OrderService(ProductRepository productRepository,
                        OrderRepository   orderRepository,
                        QueueManager      queueManager) {
        this(productRepository, orderRepository, queueManager, DBConnection::getConnection);
    }

    /** Constructor used by tests to supply a recording or in-memory connection. */
    public OrderService(ProductRepository productRepository,
                        OrderRepository orderRepository,
                        QueueManager queueManager,
                        ConnectionProvider connectionProvider) {
        this.productRepository = productRepository;
        this.orderRepository   = orderRepository;
        this.queueManager      = queueManager;
        this.connectionProvider = connectionProvider;
    }

    // ─── Place Order (queuing phase) ────────────────────────────────────────────

    /**
     * Validate stock and enqueue an order for later processing.
     *
     * Note: The stock check here is a SOFT check for user feedback only.
     * The authoritative hard check happens inside processNextOrder() via the
     * conditional UPDATE — preventing oversell even if stock drops between
     * this check and processing.
     *
     * Throws:
     *   ProductNotFoundException    — if product does not exist
     *   InsufficientStockException  — if stock is clearly insufficient at enqueueing time
     */
    public void placeOrder(String customerName, int productId, int quantity) {
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be blank");
        }
        if (productId <= 0) throw new IllegalArgumentException("Product ID must be greater than zero");
        if (quantity <= 0) throw new IllegalArgumentException("Order quantity must be greater than zero");
        // findById throws ProductNotFoundException if not found
        Product product = productRepository.findById(productId);

        if (product.getStock() < quantity) {
            throw new InsufficientStockException(productId, quantity, product.getStock());
        }

        Order order = new Order(UUID.randomUUID().toString(), customerName, productId, quantity);
        queueManager.addOrder(order);
    }

    // ─── Process Next Order (transactional phase) ───────────────────────────────

    /**
     * Dequeue and atomically process the next FIFO order.
     *
     * ACID guarantees:
     *  - Atomicity  : stock deduction and history insert commit together or not at all.
     *  - Consistency: conditional UPDATE ensures stock never goes negative.
     *  - Isolation  : MySQL applies the configured transaction isolation level.
     *  - Durability : conn.commit() flushes both writes to durable storage.
     *
     * Throws:
     *   InsufficientStockException    — DB-level stock check failed (CAS returned 0 rows)
     *   TransactionExecutionException — any SQL failure; transaction is rolled back
     */
    public void processNextOrder() {
        Order order = queueManager.getNextOrder();
        if (order == null) {
            System.out.println("  No pending orders in queue.");
            return;
        }

        boolean committed = false;
        // Closing a Hikari-backed connection returns it to the pool, which resets
        // connection state before another caller borrows it.
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean deducted = productRepository.deductStock(
                    conn, order.getProductId(), order.getQuantity());
                if (!deducted) {
                    throw new InsufficientStockException(
                        order.getProductId(), order.getQuantity(), 0);
                }

                // Both writes use this same connection and transaction.
                orderRepository.save(order, conn);
                conn.commit();
                committed = true;
                System.out.println("  [COMMIT] Order #" + order.getOrderId()
                    + " processed for " + order.getCustomerName()
                    + " | Product #" + order.getProductId()
                    + " | Qty: " + order.getQuantity());
            } catch (InsufficientStockException | ProductNotFoundException e) {
                safeRollback(conn, e);
                throw e;
            } catch (TransactionExecutionException e) {
                safeRollback(conn, e);
                queueManager.requeueFirst(order);
                throw e;
            } catch (SQLException e) {
                safeRollback(conn, e);
                queueManager.requeueFirst(order);
                throw new TransactionExecutionException(
                    "Transaction failed for order #" + order.getOrderId(), e);
            }
        } catch (SQLException e) {
            // A close error after commit must not put an already-committed order back in the queue.
            if (!committed) queueManager.requeueFirst(order);
            throw new TransactionExecutionException(
                committed
                    ? "Order #" + order.getOrderId() + " committed, but connection cleanup failed"
                    : "Transaction failed for order #" + order.getOrderId(), e);
        }
    }

    // ─── View & History ────────────────────────────────────────────────────────

    public void viewPendingOrders() {
        System.out.println("  Pending orders: " + queueManager.size());
        queueManager.viewPendingOrders();
    }

    public List<Order> getOrderHistory() {
        return orderRepository.findAll();
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    private void safeRollback(Connection conn, Throwable failure) {
        if (conn != null) {
            try {
                conn.rollback();
                System.err.println("  [ROLLBACK] Transaction rolled back.");
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

}
