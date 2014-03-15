import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Created by roberto on 2/12/14.
 */
public class Stack<T> implements Iterable<T> {

    /**
     * Private {@link Stack.Node} class for linked list backing structure
     */
    private class Node {
        private T item;
        private Node next;
    }

    /**
     * Private {@link Iterator} to support {@link java.lang.Iterable}
     */
    private class StackIterator implements Iterator<T> {
        private Node current = first;

        public boolean hasNext() {
            return current != null;
        }

        public void remove() { /* Nope */ }

        public T next() {
            T t = current.item;
            current = current.next;
            return t;
        }
    }

    /* Head of the linked list */
    private Node first;

    /**
     * Returns an iterator over a set of elements of type T.
     *
     * @return an {@link java.util.Iterator} to support {@link java.lang.Iterable}.
     */
    public Iterator<T> iterator() {
        return new StackIterator();
    }


    /**
     * Returns the item most recently pushed onto the stack.
     * Throws {@link java.util.NoSuchElementException} if the head is null.
     * Takes constant time O(1).
     *
     * @return The head of the list.
     */
    public T pop() {
        checkState();
        T item = first.item;
        first = first.next;
        return item;
    }

    /**
     * Pushes an item onto the stack. Takes constant time O(1);
     *
     * @param item
     */
    public void push(T item) {
        Node oldFirst = first;
        Node node = new Node();
        node.item = item;
        node.next = oldFirst;
        first = node;
    }

    private void checkState() throws NoSuchElementException {
        if (first == null)
            throw new NoSuchElementException("Stack is empty.");
    }

    public static void main(String[] args) {
        Stack<String> stringStack = new Stack<String>();
        stringStack.push("Hey we made a stack!");
        stringStack.push("Here's string 2!");
        stringStack.push("And 3.");
        for (String s : stringStack) {
            System.out.println(s);
        }
        stringStack.pop();
    }

}