import dsa.LRUProductCache;
import model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LRUProductCache.
 *
 * Tests:
 *  1. Basic get/put/invalidate.
 *  2. LRU eviction: the 501st entry evicts the least-recently-used entry.
 *  3. A get() promotes an entry so it survives the next eviction cycle.
 *  4. invalidateAll() clears the cache completely.
 */
class LRUProductCacheTest {

    private LRUProductCache cache;

    private static Product p(int id) {
        return new Product(id, "P" + id, "CAT", id * 1.0, 10, 1);
    }

    @BeforeEach
    void fresh() { cache = new LRUProductCache(); }

    @Test
    @DisplayName("get() returns null on cache miss")
    void missByDefault() {
        assertNull(cache.get(42));
    }

    @Test
    @DisplayName("put then get returns same product")
    void putAndGet() {
        Product prod = p(1);
        cache.put(1, prod);
        assertSame(prod, cache.get(1));
    }

    @Test
    @DisplayName("invalidate removes the entry")
    void invalidate() {
        cache.put(1, p(1));
        cache.invalidate(1);
        assertNull(cache.get(1));
    }

    @Test
    @DisplayName("LRU eviction: 501st insert evicts the oldest entry")
    void evictsLRUAt501() {
        int cap = LRUProductCache.MAX_CACHE_SIZE;  // 500

        // Fill to capacity; id=1 is LRU
        for (int id = 1; id <= cap; id++) cache.put(id, p(id));
        assertEquals(cap, cache.size());

        // One more entry — id=1 (inserted first, never accessed) must be evicted
        cache.put(cap + 1, p(cap + 1));
        assertEquals(cap, cache.size(), "Size must stay at MAX_CACHE_SIZE after eviction");
        assertNull(cache.get(1),   "LRU entry (id=1) must have been evicted");
        assertNotNull(cache.get(2), "id=2 must still be present");
        assertNotNull(cache.get(cap + 1), "newly inserted entry must be present");
    }

    @Test
    @DisplayName("get() promotes entry — it survives the next eviction")
    void getPromotesToMRU() {
        int cap = LRUProductCache.MAX_CACHE_SIZE;

        // Fill to capacity
        for (int id = 1; id <= cap; id++) cache.put(id, p(id));

        // Access id=1, making it MRU. Now id=2 is LRU.
        cache.get(1);

        // Trigger eviction
        cache.put(cap + 1, p(cap + 1));

        assertNotNull(cache.get(1), "id=1 was accessed after fill, must NOT be evicted");
        assertNull(cache.get(2),    "id=2 was never accessed, must be the evicted LRU");
    }

    @Test
    @DisplayName("invalidateAll clears all entries")
    void invalidateAll() {
        for (int id = 1; id <= 10; id++) cache.put(id, p(id));
        cache.invalidateAll();
        assertEquals(0, cache.size());
        assertNull(cache.get(1));
    }

    @Test
    @DisplayName("containsKey returns true only when entry is present")
    void containsKey() {
        assertFalse(cache.containsKey(99));
        cache.put(99, p(99));
        assertTrue(cache.containsKey(99));
        cache.invalidate(99);
        assertFalse(cache.containsKey(99));
    }
}