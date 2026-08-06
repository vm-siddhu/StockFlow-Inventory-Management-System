package dsa;

import java.util.ArrayList;
import java.util.List;

import model.Product;

public class ProductBST {

    private static class Node {
        Product product;
        Node    left;
        Node    right;

        Node(Product product) {
            this.product = product;
        }
    }

    private Node root;

    public void insert(Product product) {
        if (product == null) {
            return;
        }
        root = insertRec(root, product);
    }

    public List<Product> searchByPriceRange(double minPrice, double maxPrice) {
        List<Product> results = new ArrayList<Product>();
        searchRec(root, minPrice, maxPrice, results);
        return results;
    }

    public boolean isEmpty() {
        return root == null;
    }

    private Node insertRec(Node node, Product product) {
        if (node == null) {
            return new Node(product);
        }

        if (product.getPrice() < node.product.getPrice()) {
            node.left = insertRec(node.left, product);
        } else {
            node.right = insertRec(node.right, product);
        }

        return node;
    }

    private void searchRec(Node node, double minPrice, double maxPrice, List<Product> results) {
        if (node == null) {
            return;
        }

        double price = node.product.getPrice();

        if (price > minPrice) {
            searchRec(node.left, minPrice, maxPrice, results);
        }

        if (price >= minPrice && price <= maxPrice) {
            results.add(node.product);
        }

        if (price < maxPrice) {
            searchRec(node.right, minPrice, maxPrice, results);
        }
    }
}
