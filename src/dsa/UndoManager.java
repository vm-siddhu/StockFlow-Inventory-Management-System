package dsa;

import java.util.ArrayDeque;
import java.util.Deque;

import model.Order;

public class UndoManager {

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

        public Type getType() {
            return type;
        }

        public Order getOrder() {
            return order;
        }

        @Override
        public String toString() {
            return "Action{type=" + type + ", order=" + order + "}";
        }
    }

    private final Deque<Action> stack = new ArrayDeque<Action>();

    public void pushAction(Action.Type type, Order order) {
        if (order == null) {
            return;
        }
        stack.push(new Action(type, order));
    }

    public Action popAction() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.pop();
    }

    public Action peekAction() {
        return stack.peek();
    }

    public int size() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
