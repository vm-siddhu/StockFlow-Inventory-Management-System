package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import database.DBConnection;
import exception.ProductNotFoundException;
import exception.TransactionExecutionException;
import model.Product;
import repository.ProductRepository;

/**
 * JDBC implementation of ProductRepository.
 *
 * Design Principles:
 *  1. Repository Pattern: This class implements ProductRepository so the service
 *     layer depends only on the interface, not on JDBC or MySQL specifics.
 *
 *  2. Connection Ownership:
 *     - Standalone methods (no Connection param) acquire + close their own
 *       pooled connection via try-with-resources.
 *     - Transactional methods (with Connection param) are called by OrderService
 *       mid-transaction and must NOT close or commit the connection.
 *
 *  3. Exception Strategy:
 *     - SQLExceptions are wrapped in TransactionExecutionException (unchecked)
 *       so the service layer gets typed domain failures, not raw SQL errors.
 *     - findById() throws ProductNotFoundException instead of returning null.
 *
 *  4. deductStock(): Uses an atomic conditional UPDATE —
 *       UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?
 *     This is equivalent to a Compare-And-Swap at the database level. Even under
 *     20 concurrent threads, at most ONE thread will successfully deduct stock
 *     when only 1 unit remains — the rest get 0 affected rows → rollback.
 */
public class ProductDAO implements ProductRepository {

    // ─── Row Mapper ─────────────────────────────────────────────────────────────

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setProductName(rs.getString("product_name"));
        p.setCategory(rs.getString("category"));
        p.setPrice(rs.getDouble("price"));
        p.setStock(rs.getInt("stock"));
        p.setMinimumStock(rs.getInt("minimum_stock"));
        return p;
    }

    // ─── ProductRepository Implementation ───────────────────────────────────────

    @Override
    public void save(Product product) {
        String sql = "INSERT INTO products(product_name, category, price, stock, minimum_stock) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, product.getProductName());
            ps.setString(2, product.getCategory());
            ps.setDouble(3, product.getPrice());
            ps.setInt(4, product.getStock());
            ps.setInt(5, product.getMinimumStock());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new TransactionExecutionException("INSERT into products affected 0 rows.");
            }
            // Populate the auto-generated primary key so callers can use it immediately
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    product.setProductId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to save product: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Product product) {
        String sql = "UPDATE products SET product_name=?, category=?, price=?, minimum_stock=? "
                   + "WHERE product_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, product.getProductName());
            ps.setString(2, product.getCategory());
            ps.setDouble(3, product.getPrice());
            ps.setInt(4, product.getMinimumStock());
            ps.setInt(5, product.getProductId());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new ProductNotFoundException(product.getProductId());
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to update product: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(int productId) {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new ProductNotFoundException(productId);
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to delete product #" + productId, e);
        }
    }

    @Override
    public Product findById(int productId) {
        String sql = "SELECT * FROM products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to fetch product #" + productId, e);
        }
        throw new ProductNotFoundException(productId);
    }

    @Override
    public List<Product> findAll() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY product_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                products.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to fetch all products: " + e.getMessage(), e);
        }
        return products;
    }

    @Override
    public List<Product> findByNameLike(String keyword) {
        List<Product> results = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE product_name LIKE ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to search by name: " + e.getMessage(), e);
        }
        return results;
    }

    @Override
    public List<Product> findLowStock() {
        List<Product> results = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE stock <= minimum_stock ORDER BY stock ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to fetch low-stock products: " + e.getMessage(), e);
        }
        return results;
    }

    /**
     * Atomic conditional stock deduction — TRANSACTIONAL, caller owns Connection.
     *
     * SQL: UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?
     *
     * The WHERE stock >= ? clause is the critical guard:
     *  - Under REPEATABLE_READ isolation, row-level write locks prevent two
     *    concurrent transactions from both seeing stock=1 and both deducting.
     *  - Only the first committing transaction passes; the second sees 0 rows
     *    updated → returns false → OrderService rolls back and throws InsufficientStockException.
     *
     * IMPORTANT: Does NOT close conn — caller is mid-transaction.
     */
    @Override
    public boolean deductStock(Connection conn, int productId, int quantity) {
        String sql = "UPDATE products SET stock = stock - ? "
                   + "WHERE product_id = ? AND stock >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new TransactionExecutionException(
                "Stock deduction failed for product #" + productId, e);
        }
    }

    /**
     * Absolute stock assignment (used by restock operations — not transactional).
     * Acquires and releases its own connection from the pool.
     */
    @Override
    public void updateStock(int productId, int newStock) {
        String sql = "UPDATE products SET stock = ? WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newStock);
            ps.setInt(2, productId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new ProductNotFoundException(productId);
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException("Failed to update stock for #" + productId, e);
        }
    }

    // ─── Package-private helper for BenchmarkRunner batch insert ───────────────

    /**
     * Batch-insert multiple products in a single prepared statement execution.
     * Used exclusively by BenchmarkRunner for seeding test data efficiently.
     */
    public void batchInsert(List<Product> products) {
        String sql = "INSERT INTO products(product_name, category, price, stock, minimum_stock) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            for (Product p : products) {
                ps.setString(1, p.getProductName());
                ps.setString(2, p.getCategory());
                ps.setDouble(3, p.getPrice());
                ps.setInt(4, p.getStock());
                ps.setInt(5, p.getMinimumStock());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new TransactionExecutionException("Batch insert failed: " + e.getMessage(), e);
        }
    }
}
