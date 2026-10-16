import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import database.ConnectionProvider;
import dsa.QueueManager;
import exception.InsufficientStockException;
import exception.TransactionExecutionException;
import model.Order;
import model.Product;
import repository.OrderRepository;
import repository.ProductRepository;
import service.OrderService;

class OrderServiceTest {
    private RecordingConnection tx;
    private FakeProductRepository products;
    private FakeOrderRepository history;
    private QueueManager queue;
    private OrderService service;

    @BeforeEach
    void setUp() {
        tx = new RecordingConnection();
        products = new FakeProductRepository(tx);
        history = new FakeOrderRepository(tx);
        queue = new QueueManager();
        ConnectionProvider connections = () -> tx.connection;
        service = new OrderService(products, history, queue, connections);
    }

    @Test
    void commitsStockAndHistoryUsingTheSameConnection() {
        service.placeOrder("Asha", 1, 2);
        service.processNextOrder();

        assertEquals(3, products.product.getStock());
        assertEquals(1, history.saved.size());
        assertSame(tx.connection, history.connectionUsed);
        assertEquals(1, tx.commits);
        assertEquals(0, tx.rollbacksCount);
        assertTrue(tx.closed);
    }

    @Test
    void authoritativeStockCheckRejectsAStaleQueuedOrder() {
        service.placeOrder("Asha", 1, 3);
        products.product.setStock(1); // inventory changed after the queue-level check

        assertThrows(InsufficientStockException.class, service::processNextOrder);
        assertEquals(1, products.product.getStock());
        assertTrue(history.saved.isEmpty());
        assertEquals(0, tx.commits);
        assertEquals(1, tx.rollbacksCount);
    }

    @Test
    void historyFailureRollsBackTheStockWriteAndRequeuesForRetry() {
        service.placeOrder("Asha", 1, 2);
        history.failOnSave = true;

        assertThrows(TransactionExecutionException.class, service::processNextOrder);
        assertEquals(5, products.product.getStock());
        assertTrue(history.saved.isEmpty());
        assertEquals(0, tx.commits);
        assertEquals(1, tx.rollbacksCount);
        assertEquals(1, queue.size());
        assertTrue(tx.closed);
    }

    @Test
    void rejectsNonPositiveQuantityBeforeQueueing() {
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder("Asha", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder("Asha", 1, -2));
        assertEquals(0, queue.size());
        assertEquals(0, products.findCalls);
    }

    private static final class FakeProductRepository implements ProductRepository {
        private final RecordingConnection tx;
        private Product product = new Product(1, "Tea", "Grocery", 10, 5, 1);
        private int findCalls;
        private FakeProductRepository(RecordingConnection tx) { this.tx = tx; }
        public Product findById(int id) { findCalls++; return product; }
        public boolean deductStock(Connection connection, int id, int quantity) {
            assertSame(tx.connection, connection);
            if (product.getStock() < quantity) return false;
            int before = product.getStock();
            product.setStock(before - quantity);
            tx.onRollback(() -> product.setStock(before));
            return true;
        }
        public void save(Product p) { throw new UnsupportedOperationException(); }
        public void update(Product p) { throw new UnsupportedOperationException(); }
        public void delete(int id) { throw new UnsupportedOperationException(); }
        public List<Product> findAll() { throw new UnsupportedOperationException(); }
        public List<Product> findByNameLike(String name) { throw new UnsupportedOperationException(); }
        public List<Product> findLowStock() { throw new UnsupportedOperationException(); }
        public void increaseStock(int id, int quantity) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeOrderRepository implements OrderRepository {
        private final RecordingConnection tx;
        private final List<Order> saved = new ArrayList<>();
        private Connection connectionUsed;
        private boolean failOnSave;
        private FakeOrderRepository(RecordingConnection tx) { this.tx = tx; }
        public void save(Order order, Connection connection) {
            assertSame(tx.connection, connection);
            connectionUsed = connection;
            if (failOnSave) throw new TransactionExecutionException("simulated history insert failure");
            saved.add(order);
        }
        public List<Order> findAll() { return List.copyOf(saved); }
    }

    private static final class RecordingConnection implements InvocationHandler {
        private final List<Runnable> rollbacks = new ArrayList<>();
        private final Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, this);
        private int commits;
        private int rollbacksCount;
        private boolean autoCommit = true;
        private boolean closed;

        private void onRollback(Runnable action) { rollbacks.add(action); }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "setAutoCommit" -> { autoCommit = (boolean) args[0]; yield null; }
                case "getAutoCommit" -> autoCommit;
                case "commit" -> { commits++; rollbacks.clear(); yield null; }
                case "rollback" -> {
                    rollbacksCount++;
                    for (Runnable rollback : rollbacks) rollback.run();
                    rollbacks.clear();
                    yield null;
                }
                case "close" -> { closed = true; yield null; }
                case "isClosed" -> closed;
                case "isValid" -> !closed;
                case "toString" -> "RecordingConnection";
                default -> throw new SQLException("Unexpected Connection method: " + method.getName());
            };
        }
    }
}
