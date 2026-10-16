# StockFlow Inventory Management System

StockFlow is a Java console application for managing products and processing customer orders. It demonstrates object-oriented design, JDBC/MySQL persistence, a custom AVL tree for price ranges, conditional SQL stock updates, transactions, and JUnit tests.

## Features

- Add, update, delete, find, and list products.
- Search by name, show low-stock items, and query a price range.
- Queue and process orders in FIFO order.
- Prevent negative stock with a conditional SQL update.
- Record successful orders in the same transaction as their stock deduction.
- Show validation, missing-product, and database errors in the console.

## Requirements

- JDK 17 or later.
- Maven 3.8 or later.
- MySQL 8.0.16 or later (for enforced `CHECK` constraints in `database/schema.sql`).

## Database setup

1. Start MySQL and run `database/schema.sql` with an account allowed to create databases and tables. It creates the `stockflow` database and the `products` and `order_history` tables.
2. Set the connection environment variables. `DB_PASSWORD` is required; `DB_URL` and `DB_USER` have local defaults.

PowerShell:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/stockflow?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USER = "root"
$env:DB_PASSWORD = "your_mysql_password"
```

Linux/macOS:

```sh
export DB_URL='jdbc:mysql://localhost:3306/stockflow?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER='root'
export DB_PASSWORD='your_mysql_password'
```

Do not commit real credentials. `database/schema.sql` describes a fresh install. For an existing v1 database, back it up and run `database/migrate-v1-to-v2.sql` once. It preserves the old integer IDs and priority values in legacy columns and assigns UUID IDs to saved history rows.

## Run

From the repository root:

```sh
mvn clean compile
mvn exec:java
```

The app checks database connectivity, loads products to build its in-memory AVL index, then opens the console menu. Enter `0` to exit.

## Test

```sh
mvn test
```

The default suite needs no live database. It compares AVL results to a list oracle, tests inventory behavior with a fake repository, checks FIFO behavior, and tests order transaction coordination with a recording JDBC connection. There is no MySQL integration test or real concurrent-purchase test in the default suite.

## Console workflow

1. Choose **Add Product** and enter a name, category, positive price, stock, and minimum-stock alert.
2. Use **Search by ID**, **Low Stock Report**, or **Price Range Filter** to inspect inventory.
3. Choose **Place Order** and enter a customer, product ID, and positive quantity. The order is queued after an early stock check.
4. Choose **Process Next Order**. MySQL conditionally deducts stock and stores order history in one transaction.

Representative successful output:

```text
  Order queued for Asha.
  [COMMIT] Order #9ce9f7c5-22b3-49a7-b0e5-2ca2fd38c4be processed for Asha | Product #4 | Qty: 2
```

## Architecture

```text
Main -> Menu -> InventoryService -> ProductRepository -> ProductDAO -> MySQL
            -> OrderService -> QueueManager
                 |-> ProductRepository / OrderRepository -> JDBC DAOs -> MySQL
InventoryService -> ProductAvlTree
OrderService -> ConnectionProvider -> HikariCP pooled JDBC connection
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for class responsibilities and execution paths, [docs/INTERVIEW_GUIDE.md](docs/INTERVIEW_GUIDE.md) for interview questions, and [docs/CHANGELOG-REFACTOR.md](docs/CHANGELOG-REFACTOR.md) for staged changes and test evidence.

## Limitations

- Pending orders live only in memory and disappear when the process exits.
- The price index is loaded once and does not observe direct SQL edits or other app instances.
- The console and queue run on one thread. Conditional SQL protects stock when separate clients race, but concurrent purchases were not tested against MySQL here.
- HikariCP is retained to demonstrate connection pooling; it is more than a very small single-user console needs.
- `src/test/java/BenchmarkRunner.java` is a manual utility that requires a configured MySQL database; Maven does not run it as a test.
