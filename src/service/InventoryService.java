package service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dao.ProductDAO;
import dsa.ProductBST;
import model.Product;

public class InventoryService {

    private ProductDAO productDAO = new ProductDAO();
    private HashMap<Integer, Product> productCache = new HashMap<Integer, Product>();

    public boolean addProduct(Product product) {
        boolean added = productDAO.addProduct(product);
        if (added) {
            productCache.clear();
        }
        return added;
    }

    public boolean updateProduct(Product product) {
        boolean updated = productDAO.updateProduct(product);
        if (updated) {
            productCache.remove(product.getProductId());
        }
        return updated;
    }

    public boolean deleteProduct(int productId) {
        boolean deleted = productDAO.deleteProduct(productId);
        if (deleted) {
            productCache.remove(productId);
        }
        return deleted;
    }

    public ArrayList<Product> viewInventory() {
        return productDAO.getAllProducts();
    }

    public Product searchById(int productId) {
        if (productCache.containsKey(productId)) {
            return productCache.get(productId);
        }
        Product product = productDAO.getProductById(productId);
        if (product != null) {
            productCache.put(productId, product);
        }
        return product;
    }

    public ArrayList<Product> searchByName(String keyword) {
        return productDAO.searchByName(keyword);
    }

    public boolean restockProduct(int productId, int quantity) {
        Product product = productDAO.getProductById(productId);
        if (product == null) {
            return false;
        }
        int newStock   = product.getStock() + quantity;
        boolean result = productDAO.updateStock(productId, newStock);
        if (result) {
            productCache.remove(productId);
        }
        return result;
    }

    public ArrayList<Product> getLowStockProducts() {
        return productDAO.getLowStockProducts();
    }

    public List<Product> filterByPrice(double min, double max) {
        ArrayList<Product> allProducts = productDAO.getAllProducts();

        ProductBST bst = new ProductBST();
        for (int i = 0; i < allProducts.size(); i++) {
            bst.insert(allProducts.get(i));
        }

        return bst.searchByPriceRange(min, max);
    }
}
