package dsa;

import java.util.ArrayDeque;

import model.Order;

/** FIFO queue for the single-threaded console order workflow. */
public class QueueManager {
    private final ArrayDeque<Order> orders = new ArrayDeque<>();

    public void addOrder(Order order) {
        if (order == null) throw new IllegalArgumentException("Order cannot be null");
        orders.add(order);
    }

    /** Put a failed transaction back at the front for a deliberate manual retry. */
    public void requeueFirst(Order order) {
        if (order == null) throw new IllegalArgumentException("Order cannot be null");
        orders.addFirst(order);
    }

    public Order getNextOrder() { return orders.poll(); }

    public void viewPendingOrders() {
        if (orders.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }
        for (Order order : orders) System.out.println("  " + order);
    }

    public int size() { return orders.size(); }
    public boolean hasPendingOrders() { return !orders.isEmpty(); }
}
