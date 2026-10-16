import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dsa.QueueManager;
import model.Order;

class QueueManagerTest {
    private QueueManager queue;

    @BeforeEach
    void setUp() { queue = new QueueManager(); }

    private static Order order(int id) { return new Order(String.valueOf(id), "Customer" + id, 1, 1); }

    @Test
    void processesOrdersInFifoOrder() {
        queue.addOrder(order(1));
        queue.addOrder(order(2));
        assertEquals("1", queue.getNextOrder().getOrderId());
        assertEquals("2", queue.getNextOrder().getOrderId());
        assertNull(queue.getNextOrder());
    }

    @Test
    void failedOrderCanBePlacedAtFrontForManualRetry() {
        queue.addOrder(order(1));
        queue.addOrder(order(2));
        Order failed = queue.getNextOrder();
        queue.requeueFirst(failed);
        assertEquals("1", queue.getNextOrder().getOrderId());
        assertEquals("2", queue.getNextOrder().getOrderId());
    }

    @Test
    void rejectsNullOrders() {
        assertThrows(IllegalArgumentException.class, () -> queue.addOrder(null));
        assertThrows(IllegalArgumentException.class, () -> queue.requeueFirst(null));
    }
}
