package app;

import java.sql.SQLException;

import database.DBConnection;
import exception.TransactionExecutionException;
import menu.Menu;

public class Main {

    public static void main(String[] args) {
        try {
            // Fail before presenting an interactive menu if MySQL is unavailable.
            try (var ignored = DBConnection.getConnection()) {
                System.out.println("[StockFlow] Connected to MySQL.");
            }
            new Menu().start();
            DBConnection.shutdown();
        } catch (SQLException | ExceptionInInitializerError e) {
            System.err.println("[StockFlow] Could not start: " + e.getMessage());
            System.err.println("Check MySQL, the stockflow schema, and DB_URL / DB_USER / DB_PASSWORD.");
        } catch (TransactionExecutionException e) {
            System.err.println("[StockFlow] Could not load inventory: " + e.getMessage());
            System.err.println("Check the database schema and table permissions.");
        }
    }
}
