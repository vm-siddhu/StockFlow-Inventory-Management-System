# StockFlow Interview Guide

These sample answers describe the current code and its limits. Use them as a starting point and explain the real code path in your own words.

## Questions and sample answers

1. **What problem does StockFlow solve?** It keeps product inventory in MySQL, supports searches and stock reports, and records an order only when its stock deduction succeeds. *Follow-up:* Queued orders are in memory and disappear on restart; products and processed history remain in MySQL.

2. **How do `Product` and `Order` show object-oriented modeling?** They represent the main business values and keep related fields together. Services operate on these models instead of passing unrelated values throughout the code. *Follow-up:* Order IDs are UUIDs so a process restart does not reset a counter into an existing database ID.

3. **Why use `ProductRepository` and `OrderRepository`?** They define the persistence operations a service needs. JDBC implementations can be replaced by test fakes without changing service logic. *Follow-up:* Are abstractions always necessary? No; these two contracts each isolate a real persistence responsibility and help test their callers.

4. **What is a DAO's role?** It contains SQL, binds parameters, maps rows to models, and wraps database failures. It does not choose the console flow. *Follow-up:* Why prepared statements? They separate SQL structure from values and avoid concatenating user input into SQL.

5. **How are exceptions used?** Invalid values raise `IllegalArgumentException`; absent products raise `ProductNotFoundException`; low stock raises `InsufficientStockException`; JDBC problems become `TransactionExecutionException`. The menu turns those into messages. *Follow-up:* Why keep the original cause? It helps diagnose the driver or database failure.

6. **What is the main data-structure feature?** `ProductAvlTree`, a self-balancing binary search tree indexed by price and product ID. *Follow-up:* Why not use a list? A list scans all entries for ranges; an AVL can prune subtrees and keeps logarithmic height.

7. **Why is the AVL key composite?** Products can share a price. Product ID makes a unique key while keeping price order, so insert and delete can follow a single path. *Follow-up:* What if the same key is inserted twice? Its stored `Product` value is replaced.

8. **What are the four AVL rotation cases?** Left-left uses a right rotation, right-right uses a left rotation, and left-right/right-left rotate the child first and then the unbalanced node. *Follow-up:* How does two-child deletion work? Copy the in-order successor, remove its old node, then rebalance ancestors.

9. **What is price-range lookup complexity?** The intended bound is `O(log n + k)` for a balanced tree and `k` returned products. Traversal skips subtrees that cannot contain a matching price. *Follow-up:* How is correctness checked? Tests compare it with a simple linear scan.

10. **How are duplicate prices tested?** Tests insert many products at one price, delete one product ID, and confirm the others remain. Randomized insert/delete/range tests compare sorted IDs with a list oracle. *Follow-up:* Do those tests prove database/index consistency? No. Inventory tests use a fake repository and the index is only initialized from MySQL at service startup.

11. **Why use JDBC?** JDBC makes connection, prepared statement, result set, and transaction behavior visible. *Follow-up:* How do DAOs avoid leaking resources? They use try-with-resources for connections, statements, and result sets.

12. **How does an order transaction work?** `OrderService` gets one connection, disables auto-commit, conditionally deducts stock, inserts history through a DAO on that same connection, then commits. Failure calls rollback. *Follow-up:* Why share one connection? Both writes then participate in the same database transaction.

13. **What prevents overselling?** The SQL update includes `WHERE stock >= ?` in the same statement that subtracts quantity. Insufficient requests affect zero rows. *Follow-up:* Is the earlier queue stock check enough? No, it is for feedback and can become stale before processing.

14. **What does ACID mean in this implementation?** Atomicity is the stock/history commit together; consistency comes from validation and the conditional update; isolation comes from MySQL transaction and row-lock behavior; durability follows successful commit. *Follow-up:* Does `REPEATABLE_READ` alone prevent overselling? The conditional update is the direct guard.

15. **What if history insertion fails?** The service rolls back so the stock update should not persist, then puts the order at the queue front for explicit user retry. *Follow-up:* What proves MySQL rollback? The default tests do not. They verify service orchestration with a recording connection; a live integration test remains needed.

16. **Why is restock `stock = stock + ?`?** It avoids reading stock and later writing an absolute value, which could lose a concurrent increment. *Follow-up:* Is a multi-statement transaction needed? No, it is one atomic SQL update.

17. **Why HikariCP?** It reuses JDBC connections and returns them to a pool when closed. It demonstrates a common backend technique. *Follow-up:* Is it required for a single-user CLI? No, it could be removed for the smallest deployment.

18. **What tests are present and what is missing?** The AVL has a reference oracle; inventory tests use a fake repository; order tests use fake repositories and a recording JDBC connection; queue tests cover FIFO and manual retry placement. Missing: real MySQL rollback verification and simultaneous multi-connection purchase testing. *Follow-up:* Was the benchmark run? No; it requires configured MySQL and is not a Maven test.

## Walkthrough: order processing

1. The menu calls `OrderService.placeOrder(customer, productId, quantity)`. It rejects blank names, invalid IDs, and non-positive quantities.
2. The service reads current stock. If stock is already low, it reports the problem without queueing the order.
3. The service assigns a UUID and adds the order to the in-memory FIFO queue.
4. When the user chooses **Process Next Order**, the service removes the head and gets one connection from `ConnectionProvider`.
5. It disables auto-commit and runs the DAO's conditional stock update.
6. If zero rows were updated, stock is no longer sufficient; the service rolls back and reports the rejection.
7. If stock was deducted, `OrderHistoryDAO` inserts the order using the same connection.
8. The service commits and try-with-resources closes the pooled connection. If a transaction step fails, it rolls back and returns the order to the queue front so the user may retry it deliberately.
