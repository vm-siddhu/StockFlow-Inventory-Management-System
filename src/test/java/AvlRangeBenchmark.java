import dsa.ProductAvlTree;
import model.Product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Reproducible micro-benchmark: StockFlow's ProductAvlTree vs a linear scan of an ArrayList.
 * No database needed. Fixed random seed; same data and same queries for both structures.
 *
 * Compile (from the repo root):
 *   javac -d target/avlbench src/main/java/model/Product.java src/main/java/dsa/ProductAvlTree.java src/test/java/AvlRangeBenchmark.java
 * Run:
 *   java -cp target/avlbench AvlRangeBenchmark
 */
public class AvlRangeBenchmark {

    static final long SEED = 42L;
    static final int WARMUP_ROUNDS = 5;
    static final int MEASURED_ROUNDS = 7;
    static final int QUERIES_PER_ROUND = 2_000;
    static final double MAX_PRICE = 10_000.0;
    static final double WINDOW_FRACTION = 0.01;

    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version") + " | cores " + Runtime.getRuntime().availableProcessors());
        for (int n : new int[] {1_000, 10_000, 100_000}) {
            run(n);
        }
    }

    static void run(int n) {
        Random rnd = new Random(SEED);
        List<Product> products = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            double price = Math.round(rnd.nextDouble() * MAX_PRICE * 100.0) / 100.0;
            products.add(new Product(i, "P" + i, "C", price, 10, 1));
        }

        double window = MAX_PRICE * WINDOW_FRACTION;
        double[] lows = new double[QUERIES_PER_ROUND];
        Random qr = new Random(SEED + 1);
        for (int i = 0; i < lows.length; i++) lows[i] = qr.nextDouble() * (MAX_PRICE - window);

        long[] buildNs = new long[MEASURED_ROUNDS];
        ProductAvlTree tree = null;
        for (int r = -WARMUP_ROUNDS; r < MEASURED_ROUNDS; r++) {
            long t0 = System.nanoTime();
            ProductAvlTree t = new ProductAvlTree();
            for (Product p : products) t.insert(p);
            long dt = System.nanoTime() - t0;
            tree = t;
            if (r >= 0) buildNs[r] = dt;
        }

        int checked = 0;
        for (int i = 0; i < 200; i++) {
            double lo = lows[i], hi = lo + window;
            Set<Integer> a = new HashSet<>();
            for (Product p : tree.searchByPriceRange(lo, hi)) a.add(p.getProductId());
            Set<Integer> b = new HashSet<>();
            for (Product p : products) if (p.getPrice() >= lo && p.getPrice() <= hi) b.add(p.getProductId());
            if (!a.equals(b)) throw new IllegalStateException("AVL and linear scan disagree for [" + lo + ", " + hi + "]");
            checked++;
        }
        double avgResults = 0;
        for (int i = 0; i < 200; i++) avgResults += tree.searchByPriceRange(lows[i], lows[i] + window).size();
        avgResults /= 200;

        long[] avlNs = new long[MEASURED_ROUNDS];
        long[] listNs = new long[MEASURED_ROUNDS];
        for (int r = -WARMUP_ROUNDS; r < MEASURED_ROUNDS; r++) {
            long t0 = System.nanoTime();
            long acc = 0;
            for (double lo : lows) acc += tree.searchByPriceRange(lo, lo + window).size();
            long dAvl = System.nanoTime() - t0;
            sink += acc;

            t0 = System.nanoTime();
            acc = 0;
            for (double lo : lows) {
                double hi = lo + window;
                int c = 0;
                for (Product p : products) {
                    double pr = p.getPrice();
                    if (pr >= lo && pr <= hi) c++;
                }
                acc += c;
            }
            long dList = System.nanoTime() - t0;
            sink += acc;

            if (r >= 0) {
                avlNs[r] = dAvl;
                listNs[r] = dList;
            }
        }

        double avlUs = median(avlNs) / 1000.0 / QUERIES_PER_ROUND;
        double listUs = median(listNs) / 1000.0 / QUERIES_PER_ROUND;
        System.out.printf("N=%,7d | avg results/query=%6.1f | AVL %9.2f us/query | linear scan %9.2f us/query | speedup %6.1fx | build %6.1f ms | results verified on %d queries%n",
                n, avgResults, avlUs, listUs, listUs / avlUs, median(buildNs) / 1e6, checked);
    }

    static double median(long[] v) {
        long[] c = v.clone();
        Arrays.sort(c);
        return c.length % 2 == 1 ? c[c.length / 2] : (c[c.length / 2 - 1] + c[c.length / 2]) / 2.0;
    }
}
