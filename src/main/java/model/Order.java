package model;

public class Order {

    private String orderId;
    private String customerName;
    private int    productId;
    private int    quantity;

    public Order() {
    }

    public Order(String orderId, String customerName, int productId, int quantity) {
        this.orderId      = orderId;
        this.customerName = customerName;
        this.productId    = productId;
        this.quantity     = quantity;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
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

    @Override
    public String toString() {
        return "Order{"
                + "id="          + orderId
                + ", customer='" + customerName + '\''
                + ", productId=" + productId
                + ", qty="       + quantity
                + "}";
    }
}
