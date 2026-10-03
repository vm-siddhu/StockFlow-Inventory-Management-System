package model;

public class Order {

    private int    orderId;
    private String customerName;
    private int    productId;
    private int    quantity;
    private int    priority;
    /** Tracks how many times this order has been re-enqueued after a transient failure. */
    private int    retryCount;

    public Order() {
    }

    public Order(int orderId, String customerName, int productId, int quantity, int priority) {
        this.orderId      = orderId;
        this.customerName = customerName;
        this.productId    = productId;
        this.quantity     = quantity;
        this.priority     = priority;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Increment and return the retry count.
     * Called by OrderService.requeueIfRetriable() before re-enqueueing a failed order.
     */
    public int incrementRetries() {
        return ++retryCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public String toString() {
        String type = (priority == 1) ? "VIP" : "Normal";
        return "Order{"
                + "id="          + orderId
                + ", customer='" + customerName + '\''
                + ", productId=" + productId
                + ", qty="       + quantity
                + ", type="      + type
                + "}";
    }
}