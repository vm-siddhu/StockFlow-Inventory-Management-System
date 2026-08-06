package dsa;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Iterator;
import model.Order;

public class QueueManager {

    private Queue<Order> vipQueue = new LinkedList<>();
    private Queue<Order> normalQueue = new LinkedList<>();

    public void addOrder(Order order) {
        if (order.getPriority() == 1) {
            vipQueue.offer(order);
        } else {
            normalQueue.offer(order);
        }
    }

    public Order getNextOrder() {
        if (!vipQueue.isEmpty()) {
            return vipQueue.poll();
        }
        if (!normalQueue.isEmpty()) {
            return normalQueue.poll();
        }
        return null;
    }

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

    public int vipCount() {
        return vipQueue.size();
    }

    public int normalCount() {
        return normalQueue.size();
    }

    public boolean hasPendingOrders() {
        return !vipQueue.isEmpty() || !normalQueue.isEmpty();
    }

    public boolean removeOrder(Order order) {
        Iterator<Order> vipIter = vipQueue.iterator();
        while (vipIter.hasNext()) {
            if (vipIter.next().getOrderId() == order.getOrderId()) {
                vipIter.remove();
                return true;
            }
        }

        Iterator<Order> normalIter = normalQueue.iterator();
        while (normalIter.hasNext()) {
            if (normalIter.next().getOrderId() == order.getOrderId()) {
                normalIter.remove();
                return true;
            }
        }

        return false;
    }
}