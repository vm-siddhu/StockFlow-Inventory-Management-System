package dsa;

import java.util.ArrayList;
import java.util.List;

import model.Product;

/**
 * Self-Balancing AVL Tree keyed on product price.
 *
 * Design Rationale:
 *  - Replaces the original unbalanced BST which degenerates to O(n) on
 *    sorted-price inserts (a common real-world scenario).
 *  - AVL invariant: |height(left) - height(right)| <= 1 at every node.
 *  - Guarantees strict O(log n) worst-case for insert, delete, and point lookup.
 *  - searchByPriceRange prunes entire subtrees → O(k + log n) where k = matches.
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
     * We must traverse by price to find the node, then apply BST deletion + rebalance.
     * O(log n) on a balanced tree.
     */
    public void delete(int productId) {
        root = deleteRec(root, productId);
    }

    /**
     * Efficient range query — returns all products whose price ∈ [minPrice, maxPrice].
     *
     * Branch pruning:
     *  - If node.price < minPrice  → entire left subtree is below range, skip it.
     *  - If node.price > maxPrice  → entire right subtree is above range, skip it.
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
        // Standard BST insert
        if (node == null) return new Node(product);

        double price     = product.getPrice();
        double nodePrice = node.product.getPrice();

        if (price < nodePrice) {
            node.left  = insertRec(node.left, product);
        } else {
            // Equal prices go right — deterministic, no duplicates lost
            node.right = insertRec(node.right, product);
        }

        // Update height and rebalance on the way back up the recursion stack
        updateHeight(node);
        return rebalance(node);
    }

    // ─── AVL Deletion ──────────────────────────────────────────────────────────

    private Node deleteRec(Node node, int productId) {
        if (node == null) return null;

        if (node.product.getProductId() == productId) {
            // Found the target node
            if (node.left == null)  return node.right;
            if (node.right == null) return node.left;

            // Node has two children: replace with in-order successor (leftmost of right subtree)
            Node successor = findMin(node.right);
            node.product   = successor.product;
            node.right     = deleteRec(node.right, successor.product.getProductId());
        } else {
            // Search both subtrees because products with equal prices
            // may be in either direction
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

    private void rangeSearchRec(Node node, double minPrice, double maxPrice, List<Product> results) {
        if (node == null) return;

        double price = node.product.getPrice();

        // Prune left subtree: all prices there are < node.price.
        // If node.price > minPrice, the left subtree might still have qualifying nodes.
        if (price > minPrice) {
            rangeSearchRec(node.left, minPrice, maxPrice, results);
        }

        // Visit current node
        if (price >= minPrice && price <= maxPrice) {
            results.add(node.product);
        }

        // Prune right subtree: all prices there are >= node.price.
        // If node.price < maxPrice, the right subtree might still have qualifying nodes.
        if (price < maxPrice) {
            rangeSearchRec(node.right, minPrice, maxPrice, results);
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

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
