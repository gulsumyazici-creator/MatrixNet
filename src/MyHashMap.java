import java.util.ArrayList;

/** Simple hash map implementation using separate chaining */
public class MyHashMap<K, V> {

    /** Key–value pair entry */
    private static class Entry<K, V> {
        final K key;
        V value;

        Entry(K k, V v) {
            this.key = k;
            this.value = v;
        }
    }

    /** Hash table buckets */
    private ArrayList<Entry<K, V>>[] table;

    /** Number of stored entries */
    private int size = 0;

    /** Default capacity */
    private static final int CAPACITY = 1024;

    /** Resize threshold */
    private static final double LOAD_FACTOR = 0.75;

    /** Creates map with given capacity */
    public MyHashMap(int capacity) {
        table = (ArrayList<Entry<K, V>>[]) new ArrayList[capacity];
    }

    /** Creates map with default capacity */
    public MyHashMap() {
        table = (ArrayList<Entry<K, V>>[]) new ArrayList[CAPACITY];
    }

    /** Returns number of entries */
    public int size() {
        return size;
    }

    /** Checks if key exists */
    public boolean containsKey(K key) {
        return get(key) != null;
    }

    /** Checks if map is empty */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Computes bucket index */
    private int index(Object k) {
        return (k.hashCode() & 0x7fffffff) % table.length;
    }

    /** Returns value for key or null */
    public V get(K key) {
        int idx = index(key);
        if (table[idx] == null) return null;

        for (Entry<K, V> e : table[idx]) {
            if (e.key.equals(key)) return e.value;
        }
        return null;
    }

    /** Inserts or updates key–value pair */
    public void put(K key, V value) {
        if ((size + 1.0) / table.length > LOAD_FACTOR) resize();

        int idx = index(key);
        if (table[idx] == null) table[idx] = new ArrayList<>(4);

        for (Entry<K, V> e : table[idx]) {
            if (e.key.equals(key)) {
                e.value = value;
                return;
            }
        }

        table[idx].add(new Entry<>(key, value));
        size++;
    }

    /** Resizes table when load factor exceeded */
    private void resize() {
        ArrayList<Entry<K, V>>[] old = table;
        table = (ArrayList<Entry<K, V>>[]) new ArrayList[old.length * 2];
        size = 0;

        for (ArrayList<Entry<K, V>> bucket : old) {
            if (bucket != null) {
                for (Entry<K, V> e : bucket) {
                    put(e.key, e.value);
                }
            }
        }
    }
}
