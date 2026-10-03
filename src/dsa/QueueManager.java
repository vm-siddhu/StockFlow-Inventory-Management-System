package dsa;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Iterator;

import model.Order;

/**
 * Thread-safe dual-priority order queue.
 *
 * Design — ConcurrentLinkedQueue (Michael-Scott non-blocking queue):
 *  - Lock-free, wait-free offer() and poll() using CAS (Compare-And-Swap) operations.
 *  - No thread ever blocks waiting for a lock — eliminates the deadlock risk
 *    present in the original LinkedList-based implementation.
 *  - Weakly consistent iterators: safe to iterate while other threads modify
 *    the queue (no ConcurrentModificationException).
 *
 * Priority semantics (unchanged):
 *  - VIP orders (priority=1) are always dequeued before normal orders.
 *  - Within the same priority tier, FIFO ordering is preserved by CLQ.
 *
 * Replaces: LinkedList<Order> which is not thread-safe — concurrent
 * addOrder() + getNextOrder() calls could corrupt internal list pointers.
 */
public class QueueManager {

    // ConcurrentLinkedQueue — lock-free, unbounded, thread-safe FIFO
    private final ConcurrentLinkedQueue<Order> vipQueue    = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Order> normalQueue = new ConcurrentLinkedQueue<>();

    /**
     * Enqueue an order into the appropriate priority lane.
     * Thread-safe: ConcurrentLinkedQueue.offer() uses CAS internally.
     */
    public void addOrder(Order order) {
        if (order.getPriority() == 1) {
            vipQueue.offer(order);
        } else {
            normalQueue.offer(order);
        }
    }

    /**
     * Dequeue and return the highest-priority pending order.
     * VIP lane is drained completely before normal lane is touched.
     *
     * Thread-safe: poll() is an atomic CAS operation — no explicit lock needed.
     * Returns null if both queues are empty.
     */
    public Order getNextOrder() {
        Order order = vipQueue.poll();
        if (order != null) return order;
        return normalQueue.poll();
    }

    /**
     * Remove a specific order by orderId from whichever queue holds it.
     * Used by UndoManager to cancel a queued-but-unprocessed order.
     *
     * ConcurrentLinkedQueue.remove(o) uses equals() comparison and is thread-safe.
     * We compare by orderId manually via iterator for precise control.
     *
     * Note: removeOrder is O(n) but is only called on explicit user undo —
     * not on the hot path.
     */
    public boolean removeOrder(Order order) {
        // Try VIP queue first
        Iterator<Order> vipIter = vipQueue.iterator();
        while (vipIter.hasNext()) {
            if (vipIter.next().getOrderId() == order.getOrderId()) {
                vipIter.remove();  // weakly consistent remove — safe with CLQ
                return true;
            }
        }
        // Try normal queue
        Iterator<Order> normalIter = normalQueue.iterator();
        while (normalIter.hasNext()) {
            if (normalIter.next().getOrderId() == order.getOrderId()) {
                normalIter.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Display all pending orders for both queues.
     * CLQ's weakly-consistent iterator means we may miss very recent additions
     * under heavy concurrency — acceptable for a display-only operation.
     */
    public void viewPendingOrders() {
        System.out.println("\n  --- VIP Queue ---");
        if (vipQueue.isEmpty()) {
            System.out.println("  (empty)");
        } else {
            for (Order order : vipQueue) {
                System.out.println("  " + order);
            }
        }
        System.out.println("  --- Normal Queue ---");
        if (normalQueue.isEmpty()) {
            System.out.println("  (empty)");
        } else {
            for (Order order : normalQueue) {
                System.out.println("  " + order);
            }
        }
    }

    /** O(n) size — CLQ does not maintain a constant-time size counter. */
    public int vipCount() {
        return vipQueue.size();
    }

    public int normalCount() {
        return normalQueue.size();
    }

    public boolean hasPendingOrders() {
        return !vipQueue.isEmpty() || !normalQueue.isEmpty();
    }
}