package service;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

import dao.ProductDAO;
import dsa.ProductAvlTree;
import model.Product;
import repository.ProductRepository;

/** Coordinates product rules and the in-memory price index. */
public class InventoryService {

    private final ProductRepository products;
    private final ProductAvlTree priceIndex = new ProductAvlTree();

    /** The startup wiring constructor uses MySQL persistence. */
    public InventoryService() {
        this(new ProductDAO());
    }

    /** Repository injection keeps inventory behavior independently testable. */
    public InventoryService(ProductRepository products) {
        this.products = products;
        // Load once at construction so every range query starts from database state.
        for (Product product : products.findAll()) {
            priceIndex.insert(product);
        }
    }

    public synchronized void addProduct(Product product) {
        requireValidProduct(product);
        product.setPrice(toDatabasePrice(product.getPrice()));
        products.save(product);
        priceIndex.insert(product);
    }

    public synchronized void updateProduct(Product product) {
        requireValidProduct(product);
        product.setPrice(toDatabasePrice(product.getPrice()));
        Product old = products.findById(product.getProductId());
        products.update(product);
        priceIndex.delete(old.getPrice(), old.getProductId());
        priceIndex.insert(product);
    }

    public synchronized void deleteProduct(int productId) {
        Product old = products.findById(productId);
        products.delete(productId);
        priceIndex.delete(old.getPrice(), old.getProductId());
    }

    public List<Product> viewInventory() {
        return products.findAll();
    }

    public Product searchById(int productId) {
        return products.findById(productId);
    }

    public List<Product> searchByName(String keyword) {
        return products.findByNameLike(keyword);
    }

    public void restockProduct(int productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be greater than zero");
        }
        products.increaseStock(productId, quantity);
    }

    public List<Product> getLowStockProducts() {
        return products.findLowStock();
    }

    public synchronized List<Product> filterByPrice(double min, double max) {
        return priceIndex.searchByPriceRange(min, max);
    }

    private void requireValidProduct(Product product) {
        if (product == null) throw new IllegalArgumentException("Product cannot be null");
        if (product.getProductName() == null || product.getProductName().isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank");
        }
        if (product.getCategory() == null || product.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category cannot be blank");
        }
        if (!Double.isFinite(product.getPrice()) || product.getPrice() <= 0) {
            throw new IllegalArgumentException("Product price must be finite and greater than zero");
        }
        if (product.getStock() < 0 || product.getMinimumStock() < 0) {
            throw new IllegalArgumentException("Stock values cannot be negative");
        }
    }

    private double toDatabasePrice(double price) {
        double rounded = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (rounded <= 0) {
            throw new IllegalArgumentException("Price must round to at least 0.01");
        }
        return rounded;
    }
}
