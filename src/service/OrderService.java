package service;

import java.util.ArrayList;

import dao.OrderHistoryDAO;
import dao.ProductDAO;
import dsa.QueueManager;
import dsa.UndoManager;
import dsa.UndoManager.Action;
import model.Order;
import model.Product;

public class OrderService {

    private ProductDAO       productDAO       = new ProductDAO();
    private OrderHistoryDAO  orderHistoryDAO  = new OrderHistoryDAO();
    private QueueManager     queueManager;
    private UndoManager      undoManager      = new UndoManager();
    private int              orderIdCounter   = 1;

    public OrderService(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    public boolean placeOrder(String customerName, int productId, int quantity, int priority) {
        Product product = productDAO.getProductById(productId);

        if (product == null) {
            System.out.println("  Product not found.");
            return false;
        }

        if (product.getStock() < quantity) {
            System.out.println("  Insufficient stock. Available: " + product.getStock());
            return false;
        }

        Order order = new Order(orderIdCounter++, customerName, productId, quantity, priority);
        queueManager.addOrder(order);
        undoManager.pushAction(Action.Type.PLACE_ORDER, order);

        String type = (priority == 1) ? "VIP" : "Normal";
        System.out.println("  Order queued as " + type + ": " + order);
        return true;
    }

    public void processNextOrder() {
        Order order = queueManager.getNextOrder();

        if (order == null) {
            System.out.println("  No pending orders in queue.");
            return;
        }
        Product product = productDAO.getProductById(order.getProductId());
        if (product == null) {
            System.out.println("  Product no longer exists. Order #" + order.getOrderId() + " cancelled.");
            return;
        }
        int newStock = product.getStock() - order.getQuantity();
        if (newStock < 0) {
            System.out.println("  Insufficient stock at processing time. Order #" + order.getOrderId() + " cancelled.");
            return;
        }
        boolean stockUpdated = productDAO.updateStock(product.getProductId(), newStock);
        if (!stockUpdated) {
            System.out.println("  Stock update failed. Order not processed.");
            return;
        }
        boolean saved = orderHistoryDAO.saveOrder(order);
        System.out.println("  Order #" + order.getOrderId() + " processed for " + order.getCustomerName());
        System.out.println("  Product: " + product.getProductName() + " | Qty: " + order.getQuantity() + " | Remaining stock: " + newStock);
        if (!saved) {
            System.out.println("  Warning: Order history record could not be saved.");
        }
    }

    public void viewPendingOrders() {
        System.out.println("  Pending — VIP: " + queueManager.vipCount() + " | Normal: " + queueManager.normalCount());
        queueManager.viewPendingOrders();
    }

    public ArrayList<Order> getOrderHistory() {
        return orderHistoryDAO.getOrderHistory();
    }

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
                System.out.println("  Undo successful. Removed order from pending queue: " + order);
            } else {
                System.out.println("  Undo attempted but order #" + order.getOrderId()
                        + " is no longer in the pending queue (already processed).");
            }
        }
    }
}
