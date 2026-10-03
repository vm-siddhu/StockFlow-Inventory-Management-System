package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import database.DBConnection;
import exception.TransactionExecutionException;
import model.Order;
import repository.OrderRepository;

/**
 * JDBC implementation of OrderRepository.
 *
 * Key architectural point — two variants of save():
 *
 *  save(order, conn) — TRANSACTIONAL:
 *   Called by OrderService.processNextOrder() mid-transaction.
 *   Uses the SAME connection (and therefore the same transaction) that deducted
 *   stock in ProductDAO.deductStock(). This is what makes the two-table
 *   modification (stock update + order history insert) truly atomic:
 *     - If the history insert fails → SQLException propagates → OrderService
 *       catches it and calls conn.rollback() → stock deduction is also rolled back.
 *     - Both operations either commit together or neither commits. ACID guarantee.
 *
 *  findAll() — READ-ONLY:
 *   Acquires its own pooled connection. No transaction coordination needed.
 */
public class OrderHistoryDAO implements OrderRepository {

    // ─── OrderRepository Implementation ─────────────────────────────────────────

    /**
     * Insert an order history record within an existing transaction.
     *
     * CRITICAL: Does NOT close conn — the caller (OrderService) owns the
     * Connection lifecycle and will commit or rollback after this call.
     */
    @Override
    public void save(Order order, Connection conn) {
        String sql = "INSERT INTO order_history"
                   + "(order_id, customer_name, product_id, quantity, priority, status, processed_time) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, order.getOrderId());
            ps.setString(2, order.getCustomerName());
            ps.setInt(3, order.getProductId());
            ps.setInt(4, order.getQuantity());
            ps.setInt(5, order.getPriority());
            ps.setString(6, "PROCESSED");
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // Throw — OrderService catch block will rollback the entire transaction
            throw new TransactionExecutionException(
                "Failed to insert order history for order #" + order.getOrderId(), e);
        }
    }

    /**
     * Retrieve full order history, newest first.
     * Acquires and closes its own pooled connection independently.
     */
    @Override
    public List<Order> findAll() {
        List<Order> history = new ArrayList<>();
        String sql = "SELECT * FROM order_history ORDER BY processed_time DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Order o = new Order();
                o.setOrderId(rs.getInt("order_id"));
                o.setCustomerName(rs.getString("customer_name"));
                o.setProductId(rs.getInt("product_id"));
                o.setQuantity(rs.getInt("quantity"));
                o.setPriority(rs.getInt("priority"));
                history.add(o);
            }
        } catch (SQLException e) {
            throw new TransactionExecutionException(
                "Failed to retrieve order history: " + e.getMessage(), e);
        }
        return history;
    }
}
