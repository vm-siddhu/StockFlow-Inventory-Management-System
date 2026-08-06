package model;

public class Product {

    private int    productId;
    private String productName;
    private String category;
    private double price;
    private int    stock;
    private int    minimumStock;

    public Product() {
    }

    public Product(int productId, String productName, String category, double price, int stock, int minimumStock) {
        this.productId    = productId;
        this.productName  = productName;
        this.category     = category;
        this.price        = price;
        this.stock        = stock;
        this.minimumStock = minimumStock;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(int minimumStock) {
        this.minimumStock = minimumStock;
    }

    @Override
    public String toString() {
        return "Product{"
                + "id="          + productId
                + ", name='"     + productName  + '\''
                + ", category='" + category     + '\''
                + ", price=$"    + price
                + ", stock="     + stock
                + ", minStock="  + minimumStock
                + "}";
    }
}
