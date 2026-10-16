# Refactor Changelog

## Stage A — audited baseline

- Traced startup, inventory and order flows, cache/index coordination, undo, queue behavior, and JDBC transaction handling before editing.
- Confirmed there was no checked-in SQL schema, README, or database integration test setup.
- Baseline `mvn test`: **41 tests passed, 0 failures, 0 errors, 0 skipped**.
- Added the audited starting behavior to `docs/ARCHITECTURE.md` before code changes.

## Stage B — startup, input, and schema

- `Main` checks database connectivity and reports startup configuration/database failures.
- Menu numeric readers reject negative stock and non-finite prices; end-of-input exits cleanly. Update fields allow Enter to keep their current values.
- Added `database/schema.sql` with product and order-history tables and constraints.
- `mvn test`: **41 passed**.

## Stage C — AVL tree

- Renamed `ProductBST` to `ProductAvlTree` and updated code, tests, and benchmark references.
- Reject null products, non-finite prices/bounds, and reversed ranges. Added a duplicate composite-key replacement test.
- Preserved rotation and deletion algorithms and the list-oracle randomized tests.
- `mvn test`: **43 passed**.

## Stage D — inventory coordination

- Removed `LRUProductCache` and read/write lock. Product-by-ID reads now use MySQL; AVL access is synchronized at the service boundary.
- Load the AVL from repository data at inventory-service construction.
- Round price values to match SQL `DECIMAL(12,2)` before persistence and indexing.
- Restock with one atomic `stock = stock + ?` update. Added service tests for index initialization/updates, rounding, delete, restock, and validation.
- `mvn test`: **40 passed**; cache-only tests were removed and service coverage was added.

## Stage E — transaction seam and tests

- Added `ConnectionProvider` and constructor injection to test order coordination without a live MySQL server.
- Reject non-positive quantities before queueing.
- Scope the transaction connection with try-with-resources. Both writes use the same connection; failures call rollback.
- Added tests for commit, stale stock at processing time, rollback after simulated history failure, and invalid quantities.
- `mvn test`: **44 passed** after correcting a test assertion that had referred to the rollback callback list instead of its counter.

## Stage F — optional mechanics

- Replaced dual concurrent VIP/normal queues with FIFO `ArrayDeque` for the single-threaded console.
- Removed VIP priority from the model/schema, undo stack, atomic counter, and automatic retry limit.
- Use UUID order IDs to avoid history-key collisions after restarting the process.
- Simplified HikariCP configuration to connection settings, pool size/name, and shutdown; align SLF4J binding with Hikari's 1.7 API.
- On transaction failure the order returns to the queue front for an explicit user retry; no automatic retry loop runs.
- `mvn test`: **39 passed** after simplification and final console validation changes.

## Stage G — learning and setup documentation

- Added `README.md`, final architecture and transaction walkthrough, an 18-question interview guide, and this changelog.
- Added a separate v1-to-v2 migration to preserve existing integer order IDs and priority values as legacy fields while assigning UUID keys.
- Final checks: `mvn test` — **39 passed, 0 failures, 0 errors, 0 skipped**; `git diff --check` — clean.

## Remaining work and limitations

- Live MySQL integration tests were not configured or run. Transaction tests verify service orchestration with a recording connection, not persisted MySQL state.
- `BenchmarkRunner` requires configured MySQL and was not run.
- Pending orders live in memory and are lost on exit. The price AVL does not observe direct database writes or other application instances.
- The console and queue are single-threaded. The SQL stock predicate remains the cross-client safety mechanism, but simultaneous purchases were not tested against MySQL.
- The schema is for a fresh database. Existing v1 installations need a backup and the migration script; the migration was not applied to the connected local database.
