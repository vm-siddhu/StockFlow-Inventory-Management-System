package service;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import dao.ProductDAO;
import dsa.LRUProductCache;
import dsa.ProductBST;
import exception.InsufficientStockException;
import exception.ProductNotFoundException;
import model.Product;
import repository.ProductRepository;

/**
 * Inventory management service — persistent AVL index + LRU cache.
 *
 * Two key performance improvements over the original:
 *
 * ① Persistent AVL Tree Index:
 *   - The original code rebuilt a brand-new ProductBST from a full DB SELECT
 *     on EVERY filterByPrice() call → O(n log n) cold rebuild per query.
 *   - Now: a single ProductBST is maintained as a persistent field.
 *     add/update/delete operations update the tree incrementally.
 *     filterByPrice() queries the in-memory tree directly → O(k + log n), zero DB I/O.
 *
 * ② LRU Product Cache:
 *   - The original HashMap had no eviction and called cache.clear() on every
 *     addProduct() — destroying all cached entries on every write.
 *   - Now: bounded LRU cache (500 entries). Eviction is per-entry and LRU-based.
 *     Writes only invalidate the specific affected entry.
 *
 * Thread-Safety (ReentrantReadWriteLock):
 *   - Multiple concurrent reads (filterByPrice, searchById) acquire the READ lock
 *     simultaneously — they never block each other.
 *   - Writes (add, update, delete, restock) acquire the WRITE lock exclusively.
 *   - This is superior to a single synchronized block which serializes reads.
 */
public class InventoryService {

    private final ProductRepository productRepository;

    // LRU Cache: bounded, thread-safe, O(1) get/put/evict
    private final LRUProductCache productCache = new LRUProductCache();

    // Persistent AVL Tree: maintained incrementally across all mutations
    private final ProductBST       priceIndex   = new ProductBST();

    // ReentrantReadWriteLock: multiple concurrent readers, exclusive writer
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock  readLock  = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    // Flag to track whether the BST has been initialized from DB on first use
    private volatile boolean bstInitialized = false;

    public InventoryService() {
        this.productRepository = new ProductDAO();
    }

    /**
     * Constructor-injection overload — for unit tests that supply a fake repository.
     */
    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ─── BST Lazy Initialization ────────────────────────────────────────────────

    /**
     * Load all products from DB into the AVL tree exactly once.
     * Uses double-checked locking to avoid redundant DB queries under concurrency.
     * Called lazily on first filterByPrice() invocation.
     */
    private void initBSTIfNeeded() {
        if (!bstInitialized) {
            writeLock.lock();
            try {
                if (!bstInitialized) {  // double-check inside write lock
                    List<Product> all = productRepository.findAll();
                    for (Product p : all) {
                        priceIndex.insert(p);
                    }
                    bstInitialized = true;
                }
            } finally {
                writeLock.unlock();
            }
        }
    }

    // ─── CRUD Operations ────────────────────────────────────────────────────────

    /**
     * Add a new product — persists to DB, then updates in-memory structures.
     * Throws TransactionExecutionException on DB failure.
     */
    public void addProduct(Product product) {
        productRepository.save(product);  // throws on failure; also populates product.productId
        writeLock.lock();
        try {
            // ProductDAO.save() now uses getGeneratedKeys to fill in product.productId,
            // so we can insert directly without a full table re-scan.
            priceIndex.insert(product);
            productCache.put(product.getProductId(), product);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Update product metadata — removes old BST node (old price key), inserts updated node.
     * Throws ProductNotFoundException if ID does not exist.
     */
    public void updateProduct(Product product) {
        // Fetch old version to get its price (needed for AVL deletion by composite key)
        Product existing = productRepository.findById(product.getProductId()); // throws if not found
        productRepository.update(product);

        writeLock.lock();
        try {
            // Delete using (oldPrice, productId) — the composite key that locates
            // the node in O(log n). Then re-insert at the new price.
            priceIndex.delete(existing.getPrice(), existing.getProductId());
            priceIndex.insert(product);
            productCache.put(product.getProductId(), product);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Delete a product — removes from DB, BST, and cache.
     * Throws ProductNotFoundException if the product does not exist.
     */
    public void deleteProduct(int productId) {
        // Fetch the product first to get its price for the composite AVL key.
        // findById throws ProductNotFoundException if the product is already gone.
        Product existing = productRepository.findById(productId);
        productRepository.delete(productId);
        writeLock.lock();
        try {
            priceIndex.delete(existing.getPrice(), existing.getProductId());
            productCache.invalidate(productId);
        } finally {
            writeLock.unlock();
        }
    }

    /** Fetch full product list from DB — no caching (always fresh). */
    public List<Product> viewInventory() {
        return productRepository.findAll();
    }

    /**
     * LRU cache-assisted product lookup.
     * Cache hit: O(1), no DB I/O.
     * Cache miss: fetches from DB, populates cache for future hits.
     * Throws ProductNotFoundException if the product doesn't exist.
     */
    public Product searchById(int productId) {
        readLock.lock();
        try {
            Product cached = productCache.get(productId);
            if (cached != null) return cached;
        } finally {
            readLock.unlock();
        }

        // Cache miss — fetch from DB (throws ProductNotFoundException if absent)
        Product product = productRepository.findById(productId);
        writeLock.lock();
        try {
            productCache.put(productId, product);
        } finally {
            writeLock.unlock();
        }
        return product;
    }

    public List<Product> searchByName(String keyword) {
        return productRepository.findByNameLike(keyword);
    }

    /**
     * Restock a product — fetches current stock, adds quantity, persists and updates cache.
     * Throws ProductNotFoundException if productId is invalid.
     */
    public void restockProduct(int productId, int quantity) {
        Product product = productRepository.findById(productId); // throws if not found
        int newStock    = product.getStock() + quantity;
        productRepository.updateStock(productId, newStock);

        writeLock.lock();
        try {
            product.setStock(newStock);
            productCache.put(productId, product);
            // BST is keyed by price — restock doesn't change price, no BST update needed
        } finally {
            writeLock.unlock();
        }
    }

    public List<Product> getLowStockProducts() {
        return productRepository.findLowStock();
    }

    /**
     * Price range filter — queries the persistent in-memory AVL tree.
     *
     * Zero DB round-trips after initial load. Complexity: O(k + log n).
     * Results are pruned at tree traversal time — no post-filter needed.
     */
    public List<Product> filterByPrice(double min, double max) {
        initBSTIfNeeded();  // lazy-load on first call only
        readLock.lock();
        try {
            return priceIndex.searchByPriceRange(min, max);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Evict a single entry from the LRU cache.
     *
     * Called by OrderService after a successful stock-deduction commit so that
     * the next searchById() call goes to the DB and sees the updated stock count
     * instead of a stale cached value.
     *
     * Thread-safe: LRUProductCache.invalidate() is backed by a synchronizedMap.
     */
    public void invalidateProductCache(int productId) {
        writeLock.lock();
        try {
            productCache.invalidate(productId);
        } finally {
            writeLock.unlock();
        }
    }
}
