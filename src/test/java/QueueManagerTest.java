import dsa.QueueManager;
import model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueManager (dual-priority VIP/Normal queue).
 *
 * Tests:
 *  1. VIP orders are always dequeued before normal orders.
 *  2. Within the same tier, FIFO order is preserved.
 *  3. removeOrder cancels a queued order.
 *  4. getNextOrder returns null on an empty queue.
 */
class QueueManagerTest {

    private QueueManager queue;

    private static Order order(int id, int priority) {
        return new Order(id, "Customer" + id, 1, 1, priority);
    }

    @BeforeEach
    void fresh() { queue = new QueueManager(); }

    @Test
    @DisplayName("Empty queue returns null")
    void emptyQueueReturnsNull() {
        assertNull(queue.getNextOrder());
    }

    @Test
    @DisplayName("VIP order dequeued before normal order")
    void vipBeforeNormal() {
        queue.addOrder(order(1, 0));   // normal first
        queue.addOrder(order(2, 1));   // VIP added second

        Order first = queue.getNextOrder();
        assertNotNull(first);
        assertEquals(2, first.getOrderId(), "VIP (id=2) must come out before normal (id=1)");

        Order second = queue.getNextOrder();
        assertNotNull(second);
        assertEquals(1, second.getOrderId());
    }

    @Test
    @DisplayName("Multiple VIPs are FIFO among themselves")
    void vipFifo() {
        queue.addOrder(order(10, 1));
        queue.addOrder(order(20, 1));
        queue.addOrder(order(30, 1));

        assertEquals(10, queue.getNextOrder().getOrderId());
        assertEquals(20, queue.getNextOrder().getOrderId());
        assertEquals(30, queue.getNextOrder().getOrderId());
    }

    @Test
    @DisplayName("Multiple normals are FIFO among themselves")
    void normalFifo() {
        queue.addOrder(order(1, 0));
        queue.addOrder(order(2, 0));
        queue.addOrder(order(3, 0));

        assertEquals(1, queue.getNextOrder().getOrderId());
        assertEquals(2, queue.getNextOrder().getOrderId());
        assertEquals(3, queue.getNextOrder().getOrderId());
    }

    @Test
    @DisplayName("Mixed: all VIPs drain before any normal")
    void allVipsBeforeNormals() {
        queue.addOrder(order(1, 0));  // normal
        queue.addOrder(order(2, 1));  // VIP
        queue.addOrder(order(3, 0));  // normal
        queue.addOrder(order(4, 1));  // VIP

        assertEquals(2, queue.getNextOrder().getOrderId());
        assertEquals(4, queue.getNextOrder().getOrderId());
        assertEquals(1, queue.getNextOrder().getOrderId());
        assertEquals(3, queue.getNextOrder().getOrderId());
        assertNull(queue.getNextOrder());
    }

    @Test
    @DisplayName("removeOrder cancels a queued VIP order")
    void removeVipOrder() {
        Order vip = order(99, 1);
        queue.addOrder(vip);
        queue.addOrder(order(1, 0));

        boolean removed = queue.removeOrder(vip);
        assertTrue(removed);

        Order next = queue.getNextOrder();
        assertNotNull(next);
        assertEquals(1, next.getOrderId(), "Normal order must come out after VIP is removed");
        assertNull(queue.getNextOrder());
    }

    @Test
    @DisplayName("removeOrder on non-existent order returns false")
    void removeNonExistent() {
        queue.addOrder(order(1, 0));
        assertFalse(queue.removeOrder(order(999, 0)));
        assertEquals(1, queue.normalCount());
    }

    @Test
    @DisplayName("vipCount and normalCount are accurate")
    void counts() {
        queue.addOrder(order(1, 1));
        queue.addOrder(order(2, 1));
        queue.addOrder(order(3, 0));
        assertEquals(2, queue.vipCount());
        assertEquals(1, queue.normalCount());

        queue.getNextOrder();  // dequeue one VIP
        assertEquals(1, queue.vipCount());
    }
}