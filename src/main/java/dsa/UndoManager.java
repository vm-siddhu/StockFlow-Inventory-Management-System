package dsa;

import java.util.concurrent.LinkedBlockingDeque;

import model.Order;

/**
 * Thread-safe undo action stack.
 *
 * Design — LinkedBlockingDeque:
 *  - A thread-safe, optionally bounded, doubly-ended blocking deque from
 *    java.util.concurrent. All head/tail operations are guarded by a single
 *    ReentrantLock internally (two condition variables for head and tail).
 *  - offerFirst() / pollFirst() are non-blocking and atomic — no explicit
 *    synchronization needed in this class.
 *  - Bounded capacity (MAX_UNDO_HISTORY) prevents unbounded memory growth from
 *    unconstrained undo-stack depth.
 *
 * Replaces: ArrayDeque which is not thread-safe — concurrent push/pop
 * from multiple threads can corrupt internal element array pointers.
 */
public class UndoManager {

    /** Maximum undo history depth. Old actions are silently dropped if full. */
    private static final int MAX_UNDO_HISTORY = 1_000;

    // ─── Action Inner Class ────────────────────────────────────────────────────

    public static class Action {

        public enum Type {
            PLACE_ORDER
        }

        private final Type  type;
        private final Order order;

        public Action(Type type, Order order) {
            this.type  = type;
            this.order = order;
        }

        public Type  getType()  { return type; }
        public Order getOrder() { return order; }

        @Override
        public String toString() {
            return "Action{type=" + type + ", order=" + order + "}";
        }
    }

    // ─── State ─────────────────────────────────────────────────────────────────

    // LinkedBlockingDeque with a fixed capacity — bounded to prevent memory leaks
    private final LinkedBlockingDeque<Action> stack = new LinkedBlockingDeque<>(MAX_UNDO_HISTORY);

    // ─── Stack Operations ──────────────────────────────────────────────────────

    /**
     * Push a new undo action onto the top of the stack.
     * offerFirst() is non-blocking and atomic. If capacity is exceeded,
     * the push silently fails (oldest actions are still recoverable until evicted).
     */
    public void pushAction(Action.Type type, Order order) {
        if (order == null) return;
        // offerFirst = push to head (stack top)
        stack.offerFirst(new Action(type, order));
    }

    /**
     * Pop and return the most recent action (LIFO).
     * pollFirst() is non-blocking — returns null if the stack is empty.
     */
    public Action popAction() {
        return stack.pollFirst();   // returns null if empty — no exception thrown
    }

    /**
     * Inspect the top action without removing it.
     * peekFirst() is non-blocking and atomic.
     */
    public Action peekAction() {
        return stack.peekFirst();
    }

    public int     size()    { return stack.size(); }
    public boolean isEmpty() { return stack.isEmpty(); }
}
