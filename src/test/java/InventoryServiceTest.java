import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import exception.ProductNotFoundException;
import model.Product;
import repository.ProductRepository;
import service.InventoryService;

class InventoryServiceTest {
    private FakeProducts repository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = new FakeProducts();
        repository.rows.put(1, product(1, "first", 10.0, 5));
        repository.rows.put(2, product(2, "second", 20.0, 3));
        service = new InventoryService(repository);
    }

    @Test
    void startsPriceIndexFromRepositoryAndUpdatesOldKeyOnPriceChange() {
        assertEquals(List.of(1), ids(service.filterByPrice(9, 11)));
        service.updateProduct(product(1, "first revised", 30.0, 5));
        assertTrue(service.filterByPrice(9, 11).isEmpty());
        assertEquals(List.of(1), ids(service.filterByPrice(29, 31)));
    }

    @Test
    void addDeleteAndRestockKeepPersistentDataAndIndexAligned() {
        Product added = new Product(0, "third", "CAT", 15.0, 2, 1);
        service.addProduct(added);
        assertTrue(added.getProductId() > 0);
        assertEquals(List.of(1, added.getProductId()), ids(service.filterByPrice(10, 16)));

        service.restockProduct(2, 4);
        assertEquals(7, service.searchById(2).getStock());
        assertEquals(4, repository.lastRestockQuantity);

        service.deleteProduct(1);
        assertTrue(service.filterByPrice(9, 11).isEmpty());
    }

    @Test
    void roundsPricesToDatabasePrecisionBeforeIndexing() {
        Product added = new Product(0, "rounded", "CAT", 12.345, 1, 0);
        service.addProduct(added);
        assertEquals(12.35, added.getPrice());
        assertEquals(List.of(added.getProductId()), ids(service.filterByPrice(12.35, 12.35)));
    }

    @Test
    void rejectsInvalidProductsAndRestockQuantities() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addProduct(new Product(0, "bad", "CAT", 1, -1, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> service.addProduct(new Product(0, "too small", "CAT", 0.001, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.restockProduct(2, 0));
        assertEquals(0, repository.lastRestockQuantity);
    }

    private static Product product(int id, String name, double price, int stock) {
        return new Product(id, name, "CAT", price, stock, 1);
    }

    private static List<Integer> ids(List<Product> products) {
        return products.stream().map(Product::getProductId).sorted().toList();
    }

    private static final class FakeProducts implements ProductRepository {
        private final Map<Integer, Product> rows = new HashMap<>();
        private int nextId = 3;
        private int lastRestockQuantity;

        public void save(Product product) {
            if (product.getProductId() == 0) product.setProductId(nextId++);
            rows.put(product.getProductId(), product);
        }
        public void update(Product product) { rows.put(product.getProductId(), product); }
        public void delete(int id) {
            if (rows.remove(id) == null) throw new ProductNotFoundException(id);
        }
        public Product findById(int id) {
            Product product = rows.get(id);
            if (product == null) throw new ProductNotFoundException(id);
            return product;
        }
        public List<Product> findAll() { return new ArrayList<>(rows.values()); }
        public List<Product> findByNameLike(String keyword) {
            return rows.values().stream().filter(p -> p.getProductName().contains(keyword)).toList();
        }
        public List<Product> findLowStock() {
            return rows.values().stream().filter(p -> p.getStock() <= p.getMinimumStock()).toList();
        }
        public boolean deductStock(java.sql.Connection connection, int id, int quantity) { throw new UnsupportedOperationException(); }
        public void increaseStock(int id, int quantity) {
            lastRestockQuantity = quantity;
            Product product = findById(id);
            product.setStock(product.getStock() + quantity);
        }
    }
}
