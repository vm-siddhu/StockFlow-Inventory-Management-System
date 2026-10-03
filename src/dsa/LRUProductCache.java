package dsa;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import model.Product;

/**
 * Bounded, thread-safe LRU (Least Recently Used) cache for Product lookups.
 *
 * Design:
 *  - Backed by java.util.LinkedHashMap with accessOrder=true so every get()
 *    moves the accessed entry to the tail of the internal doubly-linked list.
 *  - removeEldestEntry() is overridden to evict the LRU (head) entry when
 *    the cache exceeds MAX_CACHE_SIZE, giving O(1) amortised eviction.
 *  - The map is wrapped with Collections.synchronizedMap() so all mutating
 *    operations (get, put, remove) are serialised under the map's intrinsic lock,
 *    making it safe for concurrent service-layer access.
 *
 * Replaces the original HashMap which had:
 *  1. No eviction — unbounded memory growth.
 *  2. productCache.clear() on every addProduct() — defeating the whole purpose.
 *  3. No thread-safety — concurrent reads/writes corrupt HashMap state.
 */
public class LRUProductCache {

    /** Maximum number of products to hold in memory before evicting the LRU entry. */
    public static final int MAX_CACHE_SIZE = 500;

    // synchronizedMap wraps all method calls in the map's intrinsic lock
    private final Map<Integer, Product> cache;

    public LRUProductCache() {
        // accessOrder=true: get() promotes entry to tail (most-recently-used position)
        LinkedHashMap<Integer, Product> lhm = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Product> eldest) {
                // Evict the LRU head when capacity is exceeded — O(1)
                return size() > MAX_CACHE_SIZE;
            }
        };
        this.cache = Collections.synchronizedMap(lhm);
    }

    /**
     * Retrieve a product by ID. Returns null if not cached.
     * Accessing an entry promotes it to MRU position (it won't be evicted next).
     */
    public Product get(int productId) {
        return cache.get(productId);
    }

    /**
     * Insert or refresh a product in the cache.
     * If cache is at capacity, the least-recently-used entry is silently evicted.
     */
    public void put(int productId, Product product) {
        cache.put(productId, product);
    }

    /**
     * Invalidate a single product entry (call on update/delete).
     * Forces the next read to go to the DB and re-populate the cache.
     */
    public void invalidate(int productId) {
        cache.remove(productId);
    }

    /**
     * Flush the entire cache (call sparingly — only needed for bulk operations
     * that affect many products simultaneously).
     */
    public void invalidateAll() {
        cache.clear();
    }

    /** Returns current number of cached entries. */
    public int size() {
        return cache.size();
    }

    public boolean containsKey(int productId) {
        return cache.containsKey(productId);
    }
}
