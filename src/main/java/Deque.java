import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Created by roberto on 2/12/14.
 */
public class Deque<Item> implements Iterable<Item> {

    private int size; // size of deque
    private Node first; // track head
    private Node last; // track tail

    /* Inner class for Node objects */
    private class Node {
        private Item item;
        private Node next;
        private Node prev;
    }

    /* Inner class for Iterator */
    private class DequeIterator implements Iterator<Item> {
        private Node current = first;

        public boolean hasNext() {
            return current != null;
        }

        public Item next() {
            if (!hasNext())
                throw new NoSuchElementException("No more items to return.");
            Item item = current.item;
            current = current.next;
            return item;
        }

        public void remove() {
            throw new UnsupportedOperationException("This iterator cannot be used to modify the Deque.");
        }
    }

    /**
     * Construct an empty deque
     */
    public Deque() {
        this.size = 0;
    }

    /**
     * Checks if the deque is empty or not
     *
     * @return boolean indicating whether empty or not
     */
    public boolean isEmpty() {
        return first == null && last == null;
    }

    /**
     * Return the number of items on the deque
     *
     * @return size
     */
    public int size() {
        return size;
    }

    /**
     * Insert the item at the beginning
     *
     * @param item Item to be added to the head of the stack
     */
    public void addFirst(Item item) {
        if (item == null)
            throw new NullPointerException("This Deque does not accept nulls.");
        Node oldFirst = first;
        Node newFirst = new Node();
        newFirst.item = item;
        newFirst.next = oldFirst;
        first = newFirst;
        /* Enforce invariants */
        last = ++size == 1 ? first : last;
        if (oldFirst != null)
            oldFirst.prev = newFirst;
    }

    /**
     * Insert the item at the end
     *
     * @param item Item to be added to the tail of the stack
     */
    public void addLast(Item item) {
        if (item == null)
            throw new NullPointerException("This Deque does not accept nulls.");
        Node oldLast = last;
        Node newLast = new Node();
        newLast.item = item;
        newLast.prev = oldLast;
        last = newLast;
        /* Enforce invariants */
        first = ++size == 1 ? last : first;
        if (oldLast != null)
            oldLast.next = newLast;
    }

    /**
     * Delete and return the item at the front
     *
     * @return item at the front
     */
    public Item removeFirst() {
        if (isEmpty())
            throw new NoSuchElementException("The Deque is empty.");
        Item item = first.item;
        first = first.next;
        /* Enforce invariants */
        if (--size >= 1)
            first.prev = null;
        if (size == 0 || size == 1)
            last = first;
        return item;
    }

    /**
     * Delete and return the item at the end
     *
     * @return item at the end
     */
    public Item removeLast() {
        if (isEmpty())
            throw new NoSuchElementException("The Deque is empty.");
        Item item = last.item;
        last = last.prev;
        /* Enforce invariants */
        if (--size >= 1)
            last.next = null;
        if (size == 0 || size == 1)
            first = last;
        return item;
    }

    /**
     * Return an iterator over items in order from front to end
     *
     * @return a new {@link Deque.DequeIterator}
     */
    public Iterator<Item> iterator() {
        return new DequeIterator();
    }

    /**
     * Main
     *
     * @param args CLI args
     */
    public static void main(String[] args) {
        Deque<String> stringDeque = new Deque<String>();
        stringDeque.addFirst("First String");
        stringDeque.addLast("Last String");
        stringDeque.addFirst("Push First back one");
        stringDeque.addLast("Push Last back one");
        stringDeque.removeLast();
        for (String s : stringDeque) {
            System.out.println(s);
        }
    }

}
