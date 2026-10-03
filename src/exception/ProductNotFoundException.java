package exception;

/**
 * Domain exception: thrown when a product lookup by ID yields no result.
 *
 * Replaces the original pattern of returning null / printing "Product not found."
 * to System.out. The caller (Menu) catches this and formats the user-facing message.
 */
public class ProductNotFoundException extends RuntimeException {

    private final int productId;

    public ProductNotFoundException(int productId) {
        super("Product not found with ID: " + productId);
        this.productId = productId;
    }

    public int getProductId() { return productId; }
}
