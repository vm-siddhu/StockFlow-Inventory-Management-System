package exception;

/**
 * Domain exception: thrown when a purchase order quantity exceeds available stock.
 *
 * Design: Unchecked (RuntimeException) so callers in the service layer are not
 * forced to declare it, while still carrying structured diagnostic context
 * (productId, requested, available) that the Menu layer can display precisely.
 */
public class InsufficientStockException extends RuntimeException {

    private final int productId;
    private final int requested;
    private final int available;

    public InsufficientStockException(int productId, int requested, int available) {
        super(String.format(
            "Insufficient stock for product #%d — requested: %d, available: %d",
            productId, requested, available
        ));
        this.productId = productId;
        this.requested = requested;
        this.available  = available;
    }

    public int getProductId() { return productId; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
