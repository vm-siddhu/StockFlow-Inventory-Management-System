package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    public static Connection getConnection() {
        String url      = "jdbc:mysql://localhost:3306/stockflow";
        String username = "root";
        String password = "root123";

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
        }
        return conn;
    }
}
