package dsa;

import java.util.ArrayList;
import java.util.List;

import model.Product;

/**
 * Self-Balancing AVL Tree keyed on (price, productId) composite key.
 *
 * Complexity:
 *  insert            O(log n)
 *  delete            O(log n) -- navigates the composite key directly; no off-path nodes visited
 *  searchByPriceRange O(k + log n), k = results returned
 *
 * Composite key rationale:
 *  Price alone is ambiguous when products share the same price: delete would
 *  have to search both subtrees (O(n) worst case) to find the right node.
 *  Appending productId makes every key globally unique, so every operation
 *  follows a single root-to-leaf path.
 *
 * Thread-Safety: NOT internally synchronized.
 * InventoryService wraps all access with a ReentrantReadWriteLock.
 */
public class ProductBST {

    private static class Node {
        Product product;
        Node left, right;
        int height;
        Node(Product p) { product = p; height = 1; }
    }

    private Node root;

    /** Insert a product. O(log n). */
    public void insert(Product product) {
        if (product == null) return;
        root = insertRec(root, product);
    }

    /**
     * Delete a product by its composite key (price, productId). O(log n).
     *
     * Both values are required so the tree can navigate directly to the target.
     * Callers that only hold the ID must first retrieve the product to get its
     * price (e.g. productRepository.findById(id)).
     */
    public void delete(double price, int productId) {
        root = deleteRec(root, price, productId);
    }

    /**
     * Range query -- all products with price in [minPrice, maxPrice]. O(k + log n).
     *
     * Pruning rules:
     *  price &lt; minPrice -> skip node + left subtree, recurse right only.
     *  price &gt; maxPrice -> skip node + right subtree, recurse left only.
     *  otherwise       -> collect node, recurse both children.
     */
    public List<Product> searchByPriceRange(double minPrice, double maxPrice) {
        List<Product> results = new ArrayList<>();
        rangeSearchRec(root, minPrice, maxPrice, results);
        return results;
    }

    public boolean isEmpty() { return root == null; }

    // -------------------------------------------------------------------------
    // Insertion
    // -------------------------------------------------------------------------

    private Node insertRec(Node node, Product p) {
        if (node == null) return new Node(p);
        int cmp = compareKeys(p.getPrice(), p.getProductId(), node.product);
        if      (cmp < 0) node.left  = insertRec(node.left,  p);
        else if (cmp > 0) node.right = insertRec(node.right, p);
        else              node.product = p;  // same key -> update in place
        updateHeight(node);
        return rebalance(node);
    }

    // -------------------------------------------------------------------------
    // Deletion
    // -------------------------------------------------------------------------

    private Node deleteRec(Node node, double price, int productId) {
        if (node == null) return null;

        int cmp = compareKeys(price, productId, node.product);

        if (cmp < 0) {
            node.left  = deleteRec(node.left,  price, productId);
        } else if (cmp > 0) {
            node.right = deleteRec(node.right, price, productId);
        } else {
            // Found the node to remove
            if (node.left  == null) return node.right;
            if (node.right == null) return node.left;
            // Two children: replace with in-order successor, then delete it
            Node s = findMin(node.right);
            node.product = s.product;
            node.right   = deleteRec(node.right,
                                     s.product.getPrice(),
                                     s.product.getProductId());
        }
        updateHeight(node);
        return rebalance(node);
    }

    // -------------------------------------------------------------------------
    // AVL Rebalancing -- four rotation cases
    // -------------------------------------------------------------------------

    private Node rebalance(Node n) {
        int bf = balanceFactor(n);
        if (bf > 1) {
            if (balanceFactor(n.left) < 0) n.left = leftRotate(n.left);   // LR
            return rightRotate(n);
        }
        if (bf < -1) {
            if (balanceFactor(n.right) > 0) n.right = rightRotate(n.right); // RL
            return leftRotate(n);
        }
        return n;
    }

    //     x                 y
    //      \               / \
    //       y    ->       x   C
    //      / \             \
    //     B   C             B
    private Node leftRotate(Node x) {
        Node y = x.right, B = y.left;
        y.left = x; x.right = B;
        updateHeight(x); updateHeight(y);
        return y;
    }

    //       y            x
    //      /            / \
    //     x    ->      A   y
    //    / \              /
    //   A   B            B
    private Node rightRotate(Node y) {
        Node x = y.left, B = x.right;
        x.right = y; y.left = B;
        updateHeight(y); updateHeight(x);
        return x;
    }

    // -------------------------------------------------------------------------
    // Range search
    // -------------------------------------------------------------------------

    private void rangeSearchRec(Node n, double min, double max, List<Product> out) {
        if (n == null) return;
        double p = n.product.getPrice();
        if      (p < min) rangeSearchRec(n.right, min, max, out);
        else if (p > max) rangeSearchRec(n.left,  min, max, out);
        else {
            rangeSearchRec(n.left,  min, max, out);
            out.add(n.product);
            rangeSearchRec(n.right, min, max, out);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Compare search key (price, productId) against the key stored in nodeProduct.
     * Returns negative / zero / positive (Comparator convention).
     */
    private int compareKeys(double price, int productId, Product nodeProduct) {
        int cmp = Double.compare(price, nodeProduct.getPrice());
        if (cmp != 0) return cmp;
        return Integer.compare(productId, nodeProduct.getProductId());
    }

    private int  height(Node n)        { return n == null ? 0 : n.height; }
    private void updateHeight(Node n)  { n.height = 1 + Math.max(height(n.left), height(n.right)); }
    private int  balanceFactor(Node n) { return n == null ? 0 : height(n.left) - height(n.right); }
    private Node findMin(Node n)       { while (n.left != null) n = n.left; return n; }
}
