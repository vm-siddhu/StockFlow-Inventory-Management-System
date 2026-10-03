package service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dao.OrderHistoryDAO;
import dao.ProductDAO;
import database.DBConnection;
import dsa.QueueManager;
import dsa.UndoManager;
import dsa.UndoManager.Action;
import exception.InsufficientStockException;
import exception.ProductNotFoundException;
import exception.TransactionExecutionException;
import model.Order;
import model.Product;
import repository.OrderRepository;
import repository.ProductRepository;

/**
 * Order processing service — ACID-compliant, thread-safe.
 *
 * Transaction Design (processNextOrder):
 * ┌────────────────────────────────────────────────────────────────┐
 * │  acquire conn from HikariCP pool                              │
 * │  conn.setAutoCommit(false)   ← begin explicit transaction     │
 * │                                                               │
 * │  ① productRepository.deductStock(conn, id, qty)              │
 * │      → UPDATE ... WHERE stock >= qty   (atomic CAS at DB)    │
 * │      → returns false if stock < qty   → rollback             │
 * │                                                               │
 * │  ② orderRepository.save(order, conn)                         │
 * │      → INSERT into order_history   (same connection/txn)     │
 * │                                                               │
 * │  conn.commit()    ← both writes committed atomically         │
 * │                                                               │
 * │  catch any failure → conn.rollback()   ← neither persists    │
 * │  finally → conn.setAutoCommit(true) → return to pool         │
 * └────────────────────────────────────────────────────────────────┘
 *
 * Thread-Safety:
 *  - orderIdCounter is AtomicInteger — thread-safe increment without synchronized.
 *  - QueueManager uses ConcurrentLinkedQueue — lock-free.
 *  - UndoManager uses LinkedBlockingDeque — intrinsically thread-safe.
 *  - The DB-level conditional UPDATE prevents overselling even when multiple
 *    threads pass the queue-level stock pre-check simultaneously.
 */
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository   orderRepository;
    private final QueueManager      queueManager;
    private final UndoManager       undoManager;

    /**
     * Optional reference to InventoryService.
     * When set, successful stock deductions invalidate the LRU cache entry so
     * that searchById() never returns a stale (pre-deduction) stock count.
     * May be null when OrderService is constructed without an InventoryService
     * reference (e.g., in unit tests that don't need cache coordination).
     */
    private InventoryService inventoryService;

    // AtomicInteger replaces plain int — thread-safe increment under concurrent order placement
    private final AtomicInteger orderIdCounter = new AtomicInteger(1);

    // Max times a temporarily-failing order is put back before it is discarded
    private static final int MAX_ORDER_RETRIES = 3;

    public OrderService(QueueManager queueManager) {
        ProductDAO      productDAO      = new ProductDAO();
        OrderHistoryDAO orderHistoryDAO = new OrderHistoryDAO();
        this.productRepository = productDAO;
        this.orderRepository   = orderHistoryDAO;
        this.queueManager      = queueManager;
        this.undoManager       = new UndoManager();
    }

    /**
     * Wire an InventoryService so that post-commit cache invalidation is active.
     * Called by Menu after both services are constructed.
     */
    public void setInventoryService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Constructor-injection overload — for unit tests that supply fake repositories.
     */
    public OrderService(ProductRepository productRepository,
                        OrderRepository   orderRepository,
                        QueueManager      queueManager) {
        this.productRepository = productRepository;
        this.orderRepository   = orderRepository;
        this.queueManager      = queueManager;
        this.undoManager       = new UndoManager();
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
    public void placeOrder(String customerName, int productId, int quantity, int priority) {
        // findById throws ProductNotFoundException if not found
        Product product = productRepository.findById(productId);

        if (product.getStock() < quantity) {
            throw new InsufficientStockException(productId, quantity, product.getStock());
        }

        Order order = new Order(orderIdCounter.getAndIncrement(), customerName, productId, quantity, priority);
        queueManager.addOrder(order);
        undoManager.pushAction(Action.Type.PLACE_ORDER, order);
    }

    // ─── Process Next Order (transactional phase) ───────────────────────────────

    /**
     * Dequeue and atomically process the next highest-priority order.
     *
     * ACID guarantees:
     *  - Atomicity  : stock deduction and history insert commit together or not at all.
     *  - Consistency: conditional UPDATE ensures stock never goes negative.
     *  - Isolation  : REPEATABLE_READ isolation on the connection prevents dirty reads.
     *  - Durability : conn.commit() flushes both writes to durable storage.
     *
     * Throws:
     *   InsufficientStockException    — DB-level stock check failed (CAS returned 0 rows)
     *   ProductNotFoundException      — product deleted between queuing and processing
     *   TransactionExecutionException — any SQL failure; transaction is rolled back
     */
    public void processNextOrder() {
        Order order = queueManager.getNextOrder();
        if (order == null) {
            System.out.println("  No pending orders in queue.");
            return;
        }

        Connection conn = null;
        try {
            // ── Acquire connection and begin transaction ──────────────────────
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);  // BEGIN TRANSACTION

            // ── Step 1: Atomic stock deduction (CAS at DB level) ──────────────
            // SQL: UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?
            // Returns false if stock was insufficient — rolled back below
            boolean deducted = productRepository.deductStock(conn, order.getProductId(), order.getQuantity());
            if (!deducted) {
                conn.rollback();
                // Stock was genuinely insufficient — do NOT re-enqueue; discard.
                throw new InsufficientStockException(
                    order.getProductId(), order.getQuantity(), 0);
            }

            // ── Step 2: Insert history record (same connection/transaction) ───
            orderRepository.save(order, conn);

            // ── Commit: both operations succeed atomically ────────────────────
            conn.commit();

            // ── Invalidate LRU cache so next searchById sees updated stock ────
            // The conditional UPDATE changed stock in the DB; the cached entry
            // now holds a stale count. Evicting it forces the next read to hit DB.
            if (inventoryService != null) {
                inventoryService.invalidateProductCache(order.getProductId());
            }

            System.out.println("  [COMMIT] Order #" + order.getOrderId()
                + " processed for " + order.getCustomerName()
                + " | Product #" + order.getProductId()
                + " | Qty: " + order.getQuantity());

        } catch (InsufficientStockException | ProductNotFoundException e) {
            // Business-logic failures — permanent; do NOT re-enqueue the order.
            safeRollback(conn);
            throw e;  // re-throw for Menu to catch and display

        } catch (TransactionExecutionException e) {
            // Transient DB failure — roll back and put the order back in queue.
            safeRollback(conn);
            requeueIfRetriable(order);
            throw e;

        } catch (SQLException e) {
            safeRollback(conn);
            requeueIfRetriable(order);
            throw new TransactionExecutionException(
                "Transaction failed for order #" + order.getOrderId(), e);

        } finally {
            // ── Always reset autoCommit and return connection to pool ─────────
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);  // reset state before returning to pool
                    conn.close();              // returns to HikariCP pool (no TCP close)
                } catch (SQLException ignored) {}
            }
        }
    }

    // ─── View & History ────────────────────────────────────────────────────────

    public void viewPendingOrders() {
        System.out.println("  Pending — VIP: " + queueManager.vipCount()
            + " | Normal: " + queueManager.normalCount());
        queueManager.viewPendingOrders();
    }

    public List<Order> getOrderHistory() {
        return orderRepository.findAll();
    }

    // ─── Undo ──────────────────────────────────────────────────────────────────

    /**
     * Undo the last queued order by removing it from the pending queue.
     * Undo is only possible for orders that have NOT yet been processed.
     * Once processNextOrder() commits, the action is permanent.
     */
    public void undoLastAction() {
        Action action = undoManager.popAction();
        if (action == null) {
            System.out.println("  Nothing to undo — undo stack is empty.");
            return;
        }
        if (action.getType() == Action.Type.PLACE_ORDER) {
            Order order   = action.getOrder();
            boolean removed = queueManager.removeOrder(order);
            if (removed) {
                System.out.println("  Undo successful. Removed order from queue: " + order);
            } else {
                System.out.println("  Undo attempted but order #" + order.getOrderId()
                    + " is no longer in the queue (already processed — cannot undo).");
            }
        }
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    private void safeRollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
                System.err.println("  [ROLLBACK] Transaction rolled back.");
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Re-enqueue a failed order if it has retry budget remaining.
     *
     * This prevents silent order loss on transient DB errors (e.g. lock timeout,
     * network blip). Business-logic failures (insufficient stock, unknown product)
     * must NOT call this — those orders are discarded intentionally.
     *
     * Retry count is tracked on the Order itself. After MAX_ORDER_RETRIES attempts
     * the order is dropped and a warning is printed.
     */
    private void requeueIfRetriable(Order order) {
        int retries = order.incrementRetries();
        if (retries <= MAX_ORDER_RETRIES) {
            queueManager.addOrder(order);
            System.err.println("  [REQUEUE] Order #" + order.getOrderId()
                + " re-enqueued (attempt " + retries + "/" + MAX_ORDER_RETRIES + ")");
        } else {
            System.err.println("  [DROP] Order #" + order.getOrderId()
                + " exceeded max retries (" + MAX_ORDER_RETRIES + ") — discarded.");
        }
    }
}
