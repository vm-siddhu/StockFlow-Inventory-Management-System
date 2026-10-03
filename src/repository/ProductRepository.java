package repository;

import java.sql.Connection;
import java.util.List;

import model.Product;

/**
 * Repository Pattern: defines the data-access contract for Product entities.
 *
 * The service layer depends only on this interface, never on the concrete DAO.
 * This decoupling enables swapping persistence implementations (e.g., from
 * MySQL to PostgreSQL, or to an in-memory stub for unit tests) without touching
 * any business logic.
 */
public interface ProductRepository {

    /** Persist a new product. Throws TransactionExecutionException on failure. */
    void save(Product product);

    /** Update metadata fields (name, category, price, minimumStock). */
    void update(Product product);

    /** Hard-delete a product row by ID. */
    void delete(int productId);

    /**
     * Fetch a single product by primary key.
     * Throws ProductNotFoundException if no row exists.
     */
    Product findById(int productId);

    /** Return all products ordered by product_id ascending. */
    List<Product> findAll();

    /** Full-text LIKE search on product_name. */
    List<Product> findByNameLike(String keyword);

    /** Return products where current stock is at or below minimum_stock threshold. */
    List<Product> findLowStock();

    /**
     * Atomic stock deduction using a conditional UPDATE.
     *
     * SQL used:  UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?
     *
     * This single statement acts as a compare-and-swap at the DB level —
     * it only updates if sufficient stock exists, preventing oversell even
     * under concurrent transactions. Returns true if a row was affected.
     *
     * @param conn     The caller-managed transactional connection (not closed here).
     * @param productId Target product.
     * @param quantity  Units to deduct.
     */
    boolean deductStock(Connection conn, int productId, int quantity);

    /**
     * Simple absolute stock assignment (used by restock / admin operations).
     * Opens and closes its own connection from the pool.
     */
    void updateStock(int productId, int newStock);
}
