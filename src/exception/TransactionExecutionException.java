package exception;

/**
 * Domain exception: wraps a low-level SQLException that caused a DB transaction
 * to be rolled back.
 *
 * Preserves the original cause chain for debugging while surfacing a
 * business-readable message to the service / menu layer.
 */
public class TransactionExecutionException extends RuntimeException {

    public TransactionExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransactionExecutionException(String message) {
        super(message);
    }
}
