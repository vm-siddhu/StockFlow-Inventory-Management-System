# StockFlow Architecture

## Structure

```text
app/Main
  └── menu/Menu ── service/InventoryService ── repository/ProductRepository
        │                                          └── dao/ProductDAO ── DBConnection/Hikari ── MySQL
        └── service/OrderService ── repository/ProductRepository
              ├── repository/OrderRepository ── dao/OrderHistoryDAO ── MySQL
              ├── database/ConnectionProvider
              └── dsa/QueueManager (FIFO)

InventoryService ── dsa/ProductAvlTree (price index)
```

## Responsibilities

| Class | Responsibility |
|---|---|
| `Main` | Checks database connectivity, starts `Menu`, closes the pool on normal exit, and reports startup errors. |
| `Menu` | Reads and validates console input, selects the use case, and formats results. It contains no SQL or tree algorithms. |
| `Product`, `Order` | Hold values passed between the menu, services, and DAOs. Order IDs are UUID strings so IDs do not collide when the app restarts. |
| `InventoryService` | Applies product validation, coordinates repository writes, and keeps the price index in sync. It loads initial products from MySQL at construction. |
| `OrderService` | Applies order validation, queues orders, and coordinates stock deduction and history recording in one transaction. |
| `ProductRepository`, `OrderRepository` | Contracts that let services use test doubles as well as JDBC implementations. |
| `ProductDAO`, `OrderHistoryDAO` | Map product/order values to parameterized JDBC statements. Transactional methods receive and reuse the caller's connection. |
| `ConnectionProvider` | Small seam for obtaining the transaction connection; production points to Hikari, tests provide a recording connection. |
| `DBConnection` | Configures and owns the static Hikari connection pool. |
| `ProductAvlTree` | Custom AVL tree keyed by `(price, productId)` for inclusive price-range queries. |
| `QueueManager` | Single-threaded FIFO queue for pending orders. Failed database transactions go to the front for deliberate manual retry. |

## Inventory path

On startup, `InventoryService` calls `ProductRepository.findAll()` and inserts each product in the AVL. Add persists first so MySQL assigns the product ID, then indexes it. Update reads the old product, writes the new values, removes the old `(price, productId)` key, and inserts the new key. Delete reads the old price, deletes the row, then removes that key. Restock issues one atomic `stock = stock + ?` update. Product-by-ID searches always read MySQL, so order deductions cannot leave a stale in-memory product cache.

The tree is service-local. Changes made directly in MySQL or by another running StockFlow process do not refresh an already-built price index; restart the app to reload it. Multiple service instances in the same JVM are not coordinated.

## AVL key, rotations, and range lookup

The tree sorts by price and then product ID. If three products have prices `(10, 10, 20)`, their keys are `(10, idA)`, `(10, idB)`, `(20, idC)`. The second key prevents equal-price products from replacing each other. Inserting an identical `(price, productId)` pair replaces its `Product` value.

For balance factor `height(left) - height(right)`, a node is rebalanced when the factor reaches `+2` or `-2`:

- left-left: rotate right;
- right-right: rotate left;
- left-right: rotate left on the child, then right on the node;
- right-left: rotate right on the child, then left on the node.

Deleting a node with two children moves its in-order successor into its place, removes the successor's old node, then recalculates heights and rebalances on the way back up. Range lookup prunes left when the current price is below the minimum and prunes right when it is above the maximum. The stated target complexity is `O(log n + k)` for a balanced tree and `k` returned products. The JUnit suite compares results against a simple list-based oracle and exercises insertions, removals, repeated prices, and random ranges.

The tree rejects null products, non-finite prices, and invalid ranges (`min > max`). `InventoryService` rounds valid prices to two decimal places to match the SQL `DECIMAL(12,2)` column and rejects prices that round to zero.

## Order path and database transaction

1. `Menu` reads customer, product ID, and a positive quantity.
2. `OrderService.placeOrder` validates the values and reads current stock. This is an early feedback check only; another process may change stock after it.
3. The order gets a UUID and enters the FIFO queue.
4. Processing obtains one pooled connection and calls `setAutoCommit(false)`.
5. `ProductDAO.deductStock` runs `UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?`. This conditional update is the authoritative guard. If stock is no longer sufficient, the update affects zero rows and the transaction rolls back.
6. `OrderHistoryDAO.save` inserts history on that same connection.
7. `OrderService` commits. If either database write fails, it rolls back and puts the order at the queue front. The user may explicitly retry processing.
8. Try-with-resources closes the pooled connection. Hikari restores a borrowed connection's state before returning it to the pool.

The queue stock check exists for a clear immediate message; the conditional SQL update protects correctness across simultaneous requests. The JUnit transaction tests verify the service passes the same connection to both repositories and calls rollback after a simulated history failure. They do not run against a MySQL server.

## Why features were retained or removed

| Feature | Decision | Reason |
|---|---|---|
| Custom AVL | Retained | Main DSA feature; has explicit duplicate-key behavior and an independent reference test suite. |
| MySQL/JDBC and repositories | Retained | Required persistence learning goal; two contracts directly serve two persistence responsibilities. |
| HikariCP | Retained with simple wiring | The project already demonstrates pooling; normal DAO methods use try-with-resources and the order connection is scoped to one use case. |
| LRU cache | Removed | Added stale-data invalidation and mutable-reference concerns without a measured need. Reads now use MySQL. |
| Read/write service locks | Removed | The console and queue are single-threaded. AVL methods are synchronized at the service boundary; the database protects concurrent stock writes. |
| VIP/normal concurrent queues | Replaced by FIFO `ArrayDeque` | VIP priority was outside the target scope and the console does not need a concurrent queue. |
| Undo stack | Removed | It only cancelled queued orders, was not a database undo, and could consume actions after an order had already processed. |
| Automatic retry counter | Removed | Retry policy/backoff was hidden complexity. A failed transaction returns the order to the queue front for a deliberate user retry. |
| Atomic order counter | Replaced with UUID | A counter restarted at one each process and could collide with primary keys in saved history. |
| Multiple persistence abstractions | Kept to two | Product and order history each have a clear persistence contract and use case tests need fakes. |

## Current limitations

- There is no live MySQL integration test in the default Maven suite. Transaction coordination is unit-tested with a recording connection; actual driver/server behavior still needs a configured manual or integration test.
- The application uses a process-local FIFO queue. Pending orders are lost if the application exits before processing them; the database only records processed orders.
- The in-memory price index does not observe direct database edits or other app instances.
- The console is single-threaded. Concurrent stock correctness is provided by the SQL conditional update, but there is no retained service-level concurrent queue harness.
- Existing installations with the v1 integer-ID schema must run `database/migrate-v1-to-v2.sql` after taking a backup. It retains the old ID and priority in legacy columns. `schema.sql` is for a fresh database.
