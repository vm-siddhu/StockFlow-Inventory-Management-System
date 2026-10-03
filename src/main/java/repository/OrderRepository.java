package repository;

import java.sql.Connection;
import java.util.List;

import model.Order;

/**
 * Repository Pattern: defines the data-access contract for Order history entities.
 *
 * The two-signature design of save() is intentional:
 *  - save(order, conn) : participates in an external ACID transaction (called by OrderService).
 *  - findAll()         : read-only, acquires its own pooled connection.
 */
public interface OrderRepository {

    /**
     * Persist an order record within an existing database transaction.
     *
     * The caller (OrderService) owns the Connection lifecycle — this method
     * must NOT close or commit the connection.
     *
     * @param order The fulfilled order to record.
     * @param conn  The active transactional connection.
     */
    void save(Order order, Connection conn);

    /** Retrieve all historical orders sorted by processed_time descending. */
    List<Order> findAll();
}
