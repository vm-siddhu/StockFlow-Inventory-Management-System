package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import database.DBConnection;
import model.Product;

public class ProductDAO {

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

    public boolean addProduct(Product product) {
        String sql = "INSERT INTO products(product_name, category, price, stock, minimum_stock) VALUES (?, ?, ?, ?, ?)";
        try {
            Connection conn        = DBConnection.getConnection();
            PreparedStatement ps   = conn.prepareStatement(sql);
            ps.setString(1, product.getProductName());
            ps.setString(2, product.getCategory());
            ps.setDouble(3, product.getPrice());
            ps.setInt(4, product.getStock());
            ps.setInt(5, product.getMinimumStock());
            int rows = ps.executeUpdate();
            ps.close();
            conn.close();
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("ProductDAO.addProduct error: " + e.getMessage());
        }
        return false;
    }

    public boolean updateProduct(Product product) {
        String sql = "UPDATE products SET product_name=?, category=?, price=?, minimum_stock=? WHERE product_id=?";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, product.getProductName());
            ps.setString(2, product.getCategory());
            ps.setDouble(3, product.getPrice());
            ps.setInt(4, product.getMinimumStock());
            ps.setInt(5, product.getProductId());
            int rows = ps.executeUpdate();
            ps.close();
            conn.close();
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("ProductDAO.updateProduct error: " + e.getMessage());
        }
        return false;
    }

    public boolean deleteProduct(int productId) {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, productId);
            int rows = ps.executeUpdate();
            ps.close();
            conn.close();
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("ProductDAO.deleteProduct error: " + e.getMessage());
        }
        return false;
    }

    public ArrayList<Product> getAllProducts() {
        ArrayList<Product> products = new ArrayList<Product>();
        String sql = "SELECT * FROM products ORDER BY product_id";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs         = ps.executeQuery();
            while (rs.next()) {
                products.add(mapRow(rs));
            }
            rs.close();
            ps.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println("ProductDAO.getAllProducts error: " + e.getMessage());
        }
        return products;
    }

    public Product getProductById(int productId) {
        String sql = "SELECT * FROM products WHERE product_id = ?";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Product p = mapRow(rs);
                rs.close();
                ps.close();
                conn.close();
                return p;
            }
            rs.close();
            ps.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println("ProductDAO.getProductById error: " + e.getMessage());
        }
        return null;
    }

    public ArrayList<Product> searchByName(String keyword) {
        ArrayList<Product> results = new ArrayList<Product>();
        String sql = "SELECT * FROM products WHERE product_name LIKE ?";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            rs.close();
            ps.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println("ProductDAO.searchByName error: " + e.getMessage());
        }
        return results;
    }

    public ArrayList<Product> getLowStockProducts() {
        ArrayList<Product> results = new ArrayList<Product>();
        String sql = "SELECT * FROM products WHERE stock <= minimum_stock ORDER BY stock ASC";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs         = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            rs.close();
            ps.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println("ProductDAO.getLowStockProducts error: " + e.getMessage());
        }
        return results;
    }

    public boolean updateStock(int productId, int newStock) {
        String sql = "UPDATE products SET stock = ? WHERE product_id = ?";
        try {
            Connection conn      = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, newStock);
            ps.setInt(2, productId);
            int rows = ps.executeUpdate();
            ps.close();
            conn.close();
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("ProductDAO.updateStock error: " + e.getMessage());
        }
        return false;
    }
}
