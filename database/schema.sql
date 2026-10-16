CREATE DATABASE IF NOT EXISTS stockflow;
USE stockflow;

CREATE TABLE IF NOT EXISTS products (
    product_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(12, 2) NOT NULL CHECK (price > 0),
    stock INT NOT NULL CHECK (stock >= 0),
    minimum_stock INT NOT NULL CHECK (minimum_stock >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_history (
    order_id CHAR(36) NOT NULL PRIMARY KEY,
    customer_name VARCHAR(150) NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL,
    processed_time TIMESTAMP NOT NULL
) ENGINE=InnoDB;
