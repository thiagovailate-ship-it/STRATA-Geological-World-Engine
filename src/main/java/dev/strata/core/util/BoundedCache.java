package dev.strata.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny synchronized LRU cache with a hard entry cap.
 *
 * <p>Used for expensive regional results (hydrology tiles). The cap bounds
 * memory; eviction is least-recently-used. All methods are synchronized so
 * shared model instances are safe to query from any thread.</p>
 */
public final class BoundedCache<K, V> {

    private final LinkedHashMap<K, V> map;
    private final int maxEntries;

    public BoundedCache(final int maxEntries) {
        this.maxEntries = Math.max(16, maxEntries);
        this.map = new LinkedHashMap<K, V>(64, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedCache.this.maxEntries;
            }
        };
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized void put(K key, V value) {
        map.put(key, value);
    }

    public synchronized void clear() {
        map.clear();
    }

    public synchronized int size() {
        return map.size();
    }
}
