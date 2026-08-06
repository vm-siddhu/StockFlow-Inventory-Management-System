package menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import dsa.QueueManager;
import model.Order;
import model.Product;
import service.InventoryService;
import service.OrderService;

public class Menu {

    private Scanner          sc               = new Scanner(System.in);
    private InventoryService inventoryService = new InventoryService();
    private QueueManager     queueManager     = new QueueManager();
    private OrderService     orderService     = new OrderService(queueManager);

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("  Invalid input. Please enter a whole number.");
            }
        }
    }

    private int readPositiveInt(String prompt) {
        while (true) {
            int value = readInt(prompt);
            if (value > 0) {
                return value;
            }
            System.out.println("  Value must be greater than zero.");
        }
    }

    private double readPositiveDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(sc.nextLine().trim());
                if (value > 0) {
                    return value;
                }
                System.out.println("  Value must be greater than zero.");
            } catch (NumberFormatException e) {
                System.out.println("  Invalid input. Please enter a valid number.");
            }
        }
    }

    private String readString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("  Input cannot be empty. Try again.");
        }
    }

    public void start() {
        System.out.println("\n  Welcome to StockFlow – Inventory Management System");

        while (true) {
            printMenu();
            int choice = readInt("  Choose: ");

            switch (choice) {
                case 1:  handleAddProduct();             break;
                case 2:  handleViewInventory();          break;
                case 3:  handleSearchById();             break;
                case 4:  handleSearchByName();           break;
                case 5:  handleUpdateProduct();          break;
                case 6:  handleDeleteProduct();          break;
                case 7:  handleRestockProduct();         break;
                case 8:  handleLowStockReport();         break;
                case 9:  handlePlaceOrder(0);            break;
                case 10: handlePlaceOrder(1);            break;
                case 11: orderService.processNextOrder(); break;
                case 12: orderService.viewPendingOrders(); break;
                case 13: handleViewOrderHistory();       break;
                case 14: handlePriceRangeSearch();        break;
                case 15: handleUndoLastAction();          break;
                case 0:
                    System.out.println("\n  StockFlow shut down. Goodbye.");
                    return;
                default:
                    System.out.println("  Invalid choice. Try again.");
            }
        }
    }

    private void printMenu() {
        System.out.println("\n╔══════════════════════════════╗");
        System.out.println("║       S T O C K F L O W      ║");
        System.out.println("╠══════════════════════════════╣");
        System.out.println("║  INVENTORY                   ║");
        System.out.println("║   1. Add Product             ║");
        System.out.println("║   2. View Inventory          ║");
        System.out.println("║   3. Search by ID            ║");
        System.out.println("║   4. Search by Name          ║");
        System.out.println("║   5. Update Product          ║");
        System.out.println("║   6. Delete Product          ║");
        System.out.println("║   7. Restock Product         ║");
        System.out.println("║   8. Low Stock Report        ║");
        System.out.println("╠══════════════════════════════╣");
        System.out.println("║  ORDERS                      ║");
        System.out.println("║   9. Place Normal Order      ║");
        System.out.println("║  10. Place VIP Order         ║");
        System.out.println("║  11. Process Next Order      ║");
        System.out.println("║  12. View Pending Orders     ║");
        System.out.println("║  13. View Order History      ║");
        System.out.println("╠══════════════════════════════╣");
        System.out.println("║  OTHERS                      ║");
        System.out.println("║  14. Price Range Filter      ║");
        System.out.println("║  15. Undo Last Action        ║");
        System.out.println("╠══════════════════════════════╣");
        System.out.println("║   0. Exit                    ║");
        System.out.println("╚══════════════════════════════╝");
    }

    private void handleAddProduct() {
        System.out.println("\n  -- Add Product --");
        String name         = readString("  Name: ");
        String category     = readString("  Category: ");
        double price        = readPositiveDouble("  Price: ");
        int    stock        = readInt("  Initial Stock: ");
        int    minimumStock = readPositiveInt("  Minimum Stock Alert Level: ");

        Product product = new Product(0, name, category, price, stock, minimumStock);

        if (inventoryService.addProduct(product)) {
            System.out.println("  Product added successfully.");
        } else {
            System.out.println("  Failed to add product.");
        }
    }

    private void handleViewInventory() {
        ArrayList<Product> products = inventoryService.viewInventory();
        if (products.isEmpty()) {
            System.out.println("  No products in inventory.");
            return;
        }
        System.out.println("\n  -- Inventory (" + products.size() + " products) --");
        printProductTableHeader();
        for (int i = 0; i < products.size(); i++) {
            printProductRow(products.get(i));
        }
    }

    private void handleSearchById() {
        int id        = readInt("  Product ID: ");
        Product product = inventoryService.searchById(id);

        if (product != null) {
            System.out.println("  Found: " + product);
        } else {
            System.out.println("  Product not found.");
        }
    }

    private void handleSearchByName() {
        String keyword          = readString("  Search keyword: ");
        ArrayList<Product> results = inventoryService.searchByName(keyword);

        if (results.isEmpty()) {
            System.out.println("  No products matched '" + keyword + "'.");
            return;
        }
        System.out.println("\n  -- Search Results (" + results.size() + " found) --");
        printProductTableHeader();
        for (int i = 0; i < results.size(); i++) {
            printProductRow(results.get(i));
        }
    }

    private void handleUpdateProduct() {
        int id          = readInt("  Product ID to update: ");
        Product existing = inventoryService.searchById(id);

        if (existing == null) {
            System.out.println("  Product not found.");
            return;
        }

        System.out.println("  Current: " + existing);
        System.out.println("  (Press Enter to keep existing value)");

        String name     = readString("  New Name [" + existing.getProductName() + "]: ");
        String category = readString("  New Category [" + existing.getCategory() + "]: ");

        double price = existing.getPrice();
        System.out.print("  New Price [" + existing.getPrice() + "]: ");
        String priceInput = sc.nextLine().trim();
        if (!priceInput.isEmpty()) {
            try {
                price = Double.parseDouble(priceInput);
            } catch (NumberFormatException e) {
                System.out.println("  Invalid price, keeping existing.");
            }
        }

        int minStock = existing.getMinimumStock();
        System.out.print("  New Min Stock [" + existing.getMinimumStock() + "]: ");
        String minInput = sc.nextLine().trim();
        if (!minInput.isEmpty()) {
            try {
                minStock = Integer.parseInt(minInput);
            } catch (NumberFormatException e) {
                System.out.println("  Invalid value, keeping existing.");
            }
        }

        Product updated = new Product(id, name, category, price, existing.getStock(), minStock);

        if (inventoryService.updateProduct(updated)) {
            System.out.println("  Product updated successfully.");
        } else {
            System.out.println("  Update failed.");
        }
    }

    private void handleDeleteProduct() {
        int id = readInt("  Product ID to delete: ");
        System.out.print("  Confirm delete product #" + id + "? (yes/no): ");
        String confirm = sc.nextLine().trim();

        if (!confirm.equalsIgnoreCase("yes")) {
            System.out.println("  Delete cancelled.");
            return;
        }

        if (inventoryService.deleteProduct(id)) {
            System.out.println("  Product #" + id + " deleted.");
        } else {
            System.out.println("  Product not found or delete failed.");
        }
    }

    private void handleRestockProduct() {
        int id       = readInt("  Product ID: ");
        int quantity = readPositiveInt("  Quantity to add: ");

        if (inventoryService.restockProduct(id, quantity)) {
            System.out.println("  Stock updated. Added " + quantity + " units.");
        } else {
            System.out.println("  Product not found.");
        }
    }

    private void handleLowStockReport() {
        ArrayList<Product> lowStock = inventoryService.getLowStockProducts();

        if (lowStock.isEmpty()) {
            System.out.println("  All products are sufficiently stocked.");
            return;
        }
        System.out.println("\n  -- Low Stock Report (" + lowStock.size() + " products need restocking) --");
        printProductTableHeader();
        for (int i = 0; i < lowStock.size(); i++) {
            printProductRow(lowStock.get(i));
        }
    }

    private void handlePlaceOrder(int priority) {
        String type     = (priority == 1) ? "VIP" : "Normal";
        System.out.println("\n  -- Place " + type + " Order --");
        String name     = readString("  Customer Name: ");
        int    productId = readInt("  Product ID: ");
        int    quantity  = readPositiveInt("  Quantity: ");

        orderService.placeOrder(name, productId, quantity, priority);
    }

    private void handleViewOrderHistory() {
        ArrayList<Order> history = orderService.getOrderHistory();

        if (history.isEmpty()) {
            System.out.println("  No order history yet.");
            return;
        }
        System.out.println("\n  -- Order History (Latest First) --");
        System.out.printf("  %-8s %-18s %-10s %-6s %-8s%n", "OrderID", "Customer", "ProductID", "Qty", "Type");
        System.out.println("  " + "-".repeat(56));
        for (int i = 0; i < history.size(); i++) {
            Order o    = history.get(i);
            String type = (o.getPriority() == 1) ? "VIP" : "Normal";
            System.out.printf("  %-8d %-18s %-10d %-6d %-8s%n",
                    o.getOrderId(), o.getCustomerName(), o.getProductId(), o.getQuantity(), type);
        }
    }

    private void printProductTableHeader() {
        System.out.printf("  %-5s %-20s %-15s %-8s %-7s %-8s%n",
                "ID", "Name", "Category", "Price", "Stock", "MinStock");
        System.out.println("  " + "-".repeat(68));
    }

    private void printProductRow(Product p) {
        System.out.printf("  %-5d %-20s %-15s %-8.2f %-7d %-8d%n",
                p.getProductId(), p.getProductName(), p.getCategory(),
                p.getPrice(), p.getStock(), p.getMinimumStock());
    }

    private void handlePriceRangeSearch() {
        System.out.println("\n  -- Search Products by Price Range  --");

        double min = readNonNegativeDouble("  Min price ($): ");

        double max;
        while (true) {
            max = readNonNegativeDouble("  Max price ($): ");
            if (max >= min) {
                break;
            }
            System.out.println("  Max price must be >= min price ($" + min + "). Try again.");
        }

        List<Product> results = inventoryService.filterByPrice(min, max);

        if (results.isEmpty()) {
            System.out.println("  No products found in price range [$" + min + " – $" + max + "].");
            return;
        }

        System.out.println("\n  -- Price Range Results: $" + min + " to $" + max
                + " (" + results.size() + " product(s), sorted by price) --");
        printProductTableHeader();
        for (int i = 0; i < results.size(); i++) {
            printProductRow(results.get(i));
        }
    }

    private void handleUndoLastAction() {
        System.out.println("\n  -- Undo Last Action  --");
        orderService.undoLastAction();
    }

    private double readNonNegativeDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(sc.nextLine().trim());
                if (value >= 0) {
                    return value;
                }
                System.out.println("  Value must be zero or greater.");
            } catch (NumberFormatException e) {
                System.out.println("  Invalid input. Please enter a valid number.");
            }
        }
    }
}