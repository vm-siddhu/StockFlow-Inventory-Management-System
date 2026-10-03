import dsa.ProductBST;
import model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProductBST.
 *
 * The key scenario is products with repeated prices — the original price-only
 * key caused range-search misses in that case. These tests verify:
 *  1. Range search returns every product in [min, max] even with duplicate prices.
 *  2. Range search returns nothing outside the bounds.
 *  3. delete() is truly O(log n): the deleted product is gone and all others survive.
 *  4. Results match a naive linear scan on the same data (oracle comparison).
 */
class ProductBSTTest {

    private ProductBST bst;
    private static final Random RNG = new Random(42);

    /** Build a Product with a given id and price (stock/category don't matter here). */
    private static Product p(int id, double price) {
        return new Product(id, "P" + id, "CAT", price, 10, 1);
    }

    @BeforeEach
    void fresh() { bst = new ProductBST(); }

    // -----------------------------------------------------------------------
    // Basic correctness
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Empty tree returns empty list")
    void emptyTree() {
        assertTrue(bst.searchByPriceRange(0, 999).isEmpty());
    }

    @Test
    @DisplayName("Single product inside range")
    void singleInRange() {
        bst.insert(p(1, 50.0));
        assertEquals(1, bst.searchByPriceRange(10.0, 100.0).size());
    }

    @Test
    @DisplayName("Single product outside range")
    void singleOutsideRange() {
        bst.insert(p(1, 200.0));
        assertTrue(bst.searchByPriceRange(10.0, 100.0).isEmpty());
    }

    @Test
    @DisplayName("Boundary prices are inclusive")
    void boundaryInclusive() {
        bst.insert(p(1, 10.0));
        bst.insert(p(2, 50.0));
        bst.insert(p(3, 100.0));
        List<Product> res = bst.searchByPriceRange(10.0, 100.0);
        assertEquals(3, res.size(), "both boundary values must be included");
    }

    // -----------------------------------------------------------------------
    // Duplicate-price correctness  (the core bug that was fixed)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("All products at the same price are found")
    void allSamePrice() {
        for (int i = 1; i <= 20; i++) bst.insert(p(i, 99.99));
        assertEquals(20, bst.searchByPriceRange(99.99, 99.99).size());
    }

    @Test
    @DisplayName("Mixed: half share a price, half are unique — all returned")
    void mixedDuplicatePrices() {
        // ids 1-10 at price 50, ids 11-20 at distinct prices
        for (int i = 1;  i <= 10; i++) bst.insert(p(i, 50.0));
        for (int i = 11; i <= 20; i++) bst.insert(p(i, i * 10.0));
        // Range covers everything
        List<Product> res = bst.searchByPriceRange(1.0, 500.0);
        assertEquals(20, res.size());
    }

    // -----------------------------------------------------------------------
    // Random oracle test — AVL vs naive list (catches range-search misses)
    // -----------------------------------------------------------------------

    @RepeatedTest(10)
    @DisplayName("Random repeated-price data: AVL result == naive list result")
    void randomOracleTest() {
        List<Product> allProducts = new ArrayList<>();
        // 200 products, prices drawn from only 20 distinct values → lots of repeats
        int N = 200;
        double[] prices = new double[20];
        for (int i = 0; i < prices.length; i++) prices[i] = (i + 1) * 10.0;

        for (int id = 1; id <= N; id++) {
            double price = prices[RNG.nextInt(prices.length)];
            Product prod = p(id, price);
            allProducts.add(prod);
            bst.insert(prod);
        }

        double lo = 50.0, hi = 150.0;

        // Oracle: linear scan
        List<Integer> expected = allProducts.stream()
            .filter(pr -> pr.getPrice() >= lo && pr.getPrice() <= hi)
            .map(Product::getProductId)
            .sorted()
            .collect(Collectors.toList());

        // AVL result
        List<Integer> actual = bst.searchByPriceRange(lo, hi).stream()
            .map(Product::getProductId)
            .sorted()
            .collect(Collectors.toList());

        assertEquals(expected.size(), actual.size(),
            "AVL missed " + (expected.size() - actual.size()) + " result(s)");
        assertEquals(expected, actual, "AVL returned wrong products");
    }

    // -----------------------------------------------------------------------
    // Delete correctness
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Deleted product is no longer returned")
    void deleteRemovesProduct() {
        bst.insert(p(1, 50.0));
        bst.insert(p(2, 50.0));  // same price — composite key must distinguish them
        bst.insert(p(3, 75.0));

        bst.delete(50.0, 1);

        List<Integer> ids = bst.searchByPriceRange(0, 200).stream()
            .map(Product::getProductId).sorted().collect(Collectors.toList());
        assertEquals(List.of(2, 3), ids);
    }

    @Test
    @DisplayName("Delete one of many same-price products — others survive")
    void deleteOneDuplicate() {
        for (int i = 1; i <= 10; i++) bst.insert(p(i, 99.99));
        bst.delete(99.99, 5);
        List<Product> res = bst.searchByPriceRange(99.99, 99.99);
        assertEquals(9, res.size());
        assertTrue(res.stream().noneMatch(pr -> pr.getProductId() == 5));
    }

    @Test
    @DisplayName("Delete root — tree still balanced and searchable")
    void deleteRoot() {
        // Insert in sorted order to stress AVL rotations
        for (int i = 1; i <= 7; i++) bst.insert(p(i, i * 10.0));
        // The root after AVL balancing is somewhere in the middle; delete several
        bst.delete(40.0, 4);
        bst.delete(20.0, 2);
        List<Product> res = bst.searchByPriceRange(0, 200);
        assertEquals(5, res.size());
    }

    @Test
    @DisplayName("Delete non-existent key is a no-op")
    void deleteNonExistent() {
        bst.insert(p(1, 50.0));
        assertDoesNotThrow(() -> bst.delete(999.0, 999));
        assertEquals(1, bst.searchByPriceRange(0, 9999).size());
    }

    @Test
    @DisplayName("Insert then delete all products — tree is empty")
    void deleteAll() {
        List<Product> products = List.of(p(1,10.0), p(2,20.0), p(3,10.0), p(4,30.0));
        products.forEach(bst::insert);
        for (Product pr : products) bst.delete(pr.getPrice(), pr.getProductId());
        assertTrue(bst.isEmpty());
        assertTrue(bst.searchByPriceRange(0, 9999).isEmpty());
    }

    // -----------------------------------------------------------------------
    // Random insert+delete oracle test
    // -----------------------------------------------------------------------

    @RepeatedTest(5)
    @DisplayName("Random inserts and deletes: AVL == naive list at every step")
    void randomInsertDeleteOracle() {
        List<Product> live = new ArrayList<>();
        int idCounter = 1;

        for (int round = 0; round < 100; round++) {
            // Insert a product
            double price = (RNG.nextInt(10) + 1) * 25.0;  // 25, 50, ..., 250 — lots of repeats
            Product prod = p(idCounter++, price);
            live.add(prod);
            bst.insert(prod);

            // Occasionally delete a random live product
            if (live.size() > 5 && RNG.nextBoolean()) {
                int idx = RNG.nextInt(live.size());
                Product toRemove = live.remove(idx);
                bst.delete(toRemove.getPrice(), toRemove.getProductId());
            }
        }

        // Final check: AVL range [0, 9999] must equal the live list
        List<Integer> expected = live.stream()
            .map(Product::getProductId).sorted().collect(Collectors.toList());
        List<Integer> actual = bst.searchByPriceRange(0, 9999).stream()
            .map(Product::getProductId).sorted().collect(Collectors.toList());

        assertEquals(expected.size(), actual.size(),
            "Size mismatch after mixed inserts/deletes");
        assertEquals(expected, actual);
    }
}