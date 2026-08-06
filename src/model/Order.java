package model;

public class Order {

    private int    orderId;
    private String customerName;
    private int    productId;
    private int    quantity;
    private int    priority;

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