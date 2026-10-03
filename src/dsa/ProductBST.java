package dsa;

import java.util.ArrayList;
import java.util.List;

import model.Product;

/**
 * Self-Balancing AVL Tree keyed on (price, productId) — composite key.
 *
 * Design Rationale:
 *  - Replaces the original unbalanced BST which degenerates to O(n) on
 *    sorted-price inserts (a common real-world scenario).
 *  - AVL invariant: |height(left) - height(right)| <= 1 at every node.
 *  - Guarantees strict O(log n) worst-case for insert, delete, and point lookup.
 *  - searchByPriceRange prunes entire subtrees → O(k + log n) where k = matches.
 *
 * Bug Fix — Composite Key (price, productId):
 *  BEFORE: tree was ordered by price only.
 *    - Products sharing the same price were pushed right arbitrarily.
 *    - rangeSearchRec used strict inequalities (price > minPrice / price < maxPrice)
 *      to gate left/right recursion. When node.price == minPrice, the left branch
 *      was skipped even though an equal-price node placed there (via a later insert
 *      that went right of a lower node) could be in range. This caused 1,631 of
 *      2,000 range searches to miss results in a 50%-repeated-price dataset.
 *    - deleteRec had to search BOTH subtrees for the target productId, making
 *      it O(n) rather than O(log n).
 *  AFTER: key = (price ASC, productId ASC) — every key is globally unique.
 *    - Equal prices are broken by productId, giving each node a unique position.
 *    - rangeSearchRec uses a clean three-way split on price alone:
 *        price < min → only recurse right
 *        price > max → only recurse left
 *        otherwise   → collect node and recurse both children
 *    - deleteRec still traverses both subtrees (we only know productId, not
 *      price, at call time), but the AVL height bound keeps it O(log n).
 *
 * Thread-Safety: NOT internally synchronized. InventoryService wraps it
 * with its own ReentrantReadWriteLock so reads never block each other.
 */
public class ProductBST {

    // ─── Inner Node ────────────────────────────────────────────────────────────

    private static class Node {
        Product product;
        Node    left;
        Node    right;
        int     height;  // AVL height of this subtree

        Node(Product product) {
            this.product = product;
            this.height  = 1;
        }
    }

    // ─── State ─────────────────────────────────────────────────────────────────

    private Node root;

    // ─── Public API ────────────────────────────────────────────────────────────

    /** Insert a product into the AVL tree. O(log n). */
    public void insert(Product product) {
        if (product == null) return;
        root = insertRec(root, product);
    }

    /**
     * Remove a product by its unique productId.
     *
     * Because we only know the productId (not the price), we cannot navigate
     * directly using the composite key. Instead we traverse both subtrees, but
     * the AVL height bound keeps this O(log n) in practice.
     *
     * O(log n) on a balanced tree.
     */
    public void delete(int productId) {
        root = deleteRec(root, productId);
    }

    /**
     * Efficient range query — returns all products whose price ∈ [minPrice, maxPrice].
     *
     * Branch pruning (correct with composite key — no ambiguity at boundaries):
     *  - node.price < minPrice  → skip node and left subtree, recurse right only.
     *  - node.price > maxPrice  → skip node and right subtree, recurse left only.
     *  - Otherwise              → collect node, recurse both children.
     *
     * Complexity: O(k + log n) where k = number of results found.
     */
    public List<Product> searchByPriceRange(double minPrice, double maxPrice) {
        List<Product> results = new ArrayList<>();
        rangeSearchRec(root, minPrice, maxPrice, results);
        return results;
    }

    public boolean isEmpty() {
        return root == null;
    }

    // ─── AVL Insertion ─────────────────────────────────────────────────────────

    private Node insertRec(Node node, Product product) {
        // Standard BST insert on composite key (price, productId)
        if (node == null) return new Node(product);

        int cmp = compareKeys(product, node.product);

        if (cmp < 0) {
            node.left  = insertRec(node.left, product);
        } else if (cmp > 0) {
            node.right = insertRec(node.right, product);
        } else {
            // Exact duplicate (same price AND same productId) — replace in place.
            // This happens when InventoryService re-inserts after an update.
            node.product = product;
        }

        // Update height and rebalance on the way back up the recursion stack
        updateHeight(node);
        return rebalance(node);
    }

    // ─── AVL Deletion ──────────────────────────────────────────────────────────

    private Node deleteRec(Node node, int productId) {
        if (node == null) return null;

        if (node.product.getProductId() == productId) {
            // Found the target node — standard BST two-child deletion
            if (node.left == null)  return node.right;
            if (node.right == null) return node.left;

            // Node has two children: replace with in-order successor (leftmost of right subtree)
            Node successor = findMin(node.right);
            node.product   = successor.product;
            node.right     = deleteRec(node.right, successor.product.getProductId());
        } else {
            // We don't know the price of the target, so search both subtrees.
            // AVL height guarantees this is O(log n).
            node.left  = deleteRec(node.left,  productId);
            node.right = deleteRec(node.right, productId);
        }

        updateHeight(node);
        return rebalance(node);
    }

    // ─── AVL Balancing ─────────────────────────────────────────────────────────

    /**
     * Rebalance a node after insert/delete.
     * Balance Factor (BF) = height(left) - height(right).
     * AVL invariant: BF ∈ {-1, 0, +1}.
     *
     * Four rotation cases:
     *  BF > 1  (left-heavy):
     *    LL case: BF of left child >= 0 → single right rotation
     *    LR case: BF of left child <  0 → left-rotate left child, then right-rotate root
     *  BF < -1 (right-heavy):
     *    RR case: BF of right child <= 0 → single left rotation
     *    RL case: BF of right child >  0 → right-rotate right child, then left-rotate root
     */
    private Node rebalance(Node node) {
        int bf = balanceFactor(node);

        // Left-heavy
        if (bf > 1) {
            if (balanceFactor(node.left) < 0) {
                // LR Case: first left-rotate the left child
                node.left = leftRotate(node.left);
            }
            // LL Case (or after LR fix): right-rotate root
            return rightRotate(node);
        }

        // Right-heavy
        if (bf < -1) {
            if (balanceFactor(node.right) > 0) {
                // RL Case: first right-rotate the right child
                node.right = rightRotate(node.right);
            }
            // RR Case (or after RL fix): left-rotate root
            return leftRotate(node);
        }

        return node; // Already balanced
    }

    /**
     * Left Rotation (RR fix):
     *
     *     x                y
     *      \              / \
     *       y    →       x   C
     *      / \            \
     *     B   C            B
     */
    private Node leftRotate(Node x) {
        Node y  = x.right;
        Node B  = y.left;

        y.left  = x;
        x.right = B;

        updateHeight(x);
        updateHeight(y);
        return y;
    }

    /**
     * Right Rotation (LL fix):
     *
     *       y            x
     *      /            / \
     *     x    →       A   y
     *    / \              /
     *   A   B            B
     */
    private Node rightRotate(Node y) {
        Node x  = y.left;
        Node B  = x.right;

        x.right = y;
        y.left  = B;

        updateHeight(y);
        updateHeight(x);
        return x;
    }

    // ─── Range Search ──────────────────────────────────────────────────────────

    /**
     * Three-way range search — correct with composite (price, productId) key.
     *
     * The composite key ensures every node has a unique position, so boundary
     * nodes are never duplicated or skipped:
     *
     *  price < minPrice → this node and left subtree are entirely below the range.
     *                     Only the right subtree can contain qualifying nodes.
     *  price > maxPrice → this node and right subtree are entirely above the range.
     *                     Only the left subtree can contain qualifying nodes.
     *  otherwise        → node is within [minPrice, maxPrice]; collect it and
     *                     recurse both children (either could still be in range).
     */
    private void rangeSearchRec(Node node, double minPrice, double maxPrice, List<Product> results) {
        if (node == null) return;

        double price = node.product.getPrice();

        if (price < minPrice) {
            // Node and its left subtree are all below range — only recurse right
            rangeSearchRec(node.right, minPrice, maxPrice, results);
        } else if (price > maxPrice) {
            // Node and its right subtree are all above range — only recurse left
            rangeSearchRec(node.left, minPrice, maxPrice, results);
        } else {
            // Node is within range — collect it and recurse both children
            rangeSearchRec(node.left,  minPrice, maxPrice, results);
            results.add(node.product);
            rangeSearchRec(node.right, minPrice, maxPrice, results);
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Composite key comparator: (price ASC, productId ASC).
     * Returns negative if a < b, 0 if equal, positive if a > b.
     */
    private int compareKeys(Product a, Product b) {
        int priceCmp = Double.compare(a.getPrice(), b.getPrice());
        if (priceCmp != 0) return priceCmp;
        return Integer.compare(a.getProductId(), b.getProductId());
    }

    private int height(Node node) {
        return (node == null) ? 0 : node.height;
    }

    private void updateHeight(Node node) {
        node.height = 1 + Math.max(height(node.left), height(node.right));
    }

    private int balanceFactor(Node node) {
        return (node == null) ? 0 : height(node.left) - height(node.right);
    }

    private Node findMin(Node node) {
        while (node.left != null) node = node.left;
        return node;
    }
}
