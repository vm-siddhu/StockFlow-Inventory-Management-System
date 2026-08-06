package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import database.DBConnection;
import model.Order;

public class OrderHistoryDAO {

    public boolean saveOrder(Order order) {
        String sql = "INSERT INTO order_history(order_id, customer_name, product_id, quantity, priority, status, processed_time) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, order.getOrderId());
            ps.setString(2, order.getCustomerName());
            ps.setInt(3, order.getProductId());
            ps.setInt(4, order.getQuantity());
            ps.setInt(5, order.getPriority());
            ps.setString(6, "PROCESSED");
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            int rows = ps.executeUpdate();
            ps.close();
            conn.close();
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("OrderHistoryDAO.saveOrder error: " + e.getMessage());
        }
        return false;
    }

    public ArrayList<Order> getOrderHistory() {
        ArrayList<Order> history = new ArrayList<Order>();
        String sql = "SELECT * FROM order_history ORDER BY processed_time DESC";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs         = ps.executeQuery();
            while (rs.next()) {
                Order o = new Order();
                o.setOrderId(rs.getInt("order_id"));
                o.setCustomerName(rs.getString("customer_name"));
                o.setProductId(rs.getInt("product_id"));
                o.setQuantity(rs.getInt("quantity"));
                o.setPriority(rs.getInt("priority"));
                history.add(o);
            }
            rs.close();
            ps.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println("OrderHistoryDAO.getOrderHistory error: " + e.getMessage());
        }
        return history;
    }
}
