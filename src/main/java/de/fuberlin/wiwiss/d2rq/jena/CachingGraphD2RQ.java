package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.ConnectingMapping;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * A GraphD2RQ that caches the results of the most recently performed
 * queries on an LRU basis.
 *
 * @author Holger Knublauch (holger@topquadrant.com)
 *
 *         TODO: do we need this class?
 */
public class CachingGraphD2RQ extends GraphD2RQ {

    /**
     * Cache of recently queried triple matches
     * (TripleMatch -> List<Triple>)
     */
    private Map<Triple, List<Triple>> queryCache =
            new LinkedHashMap<Triple, List<Triple>>(100, 0.75f, true) {
                private static final int MAX_ENTRIES = 10000;

                @Override
                protected boolean removeEldestEntry(Map.Entry<Triple, List<Triple>> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    public CachingGraphD2RQ(ConnectingMapping mapping) throws D2RQException {
        super(mapping);
    }

    /**
     * Clears the current cache.  This can be used in case the
     * database has been changed.
     */
    public void clearCache() {
        queryCache.clear();
    }


    /**
     * Overloaded to reuse and update the cache.
     */
    @Override
    public ExtendedIterator<Triple> graphBaseFind(Triple m) {
        List<Triple> cached = queryCache.get(m);
        if (cached != null) {
            return WrappedIterator.create(cached.iterator());
        }
        ExtendedIterator<Triple> it = super.graphBaseFind(m);
        final List<Triple> list = it.toList();
        queryCache.put(m, list);
        return WrappedIterator.create(list.iterator());
    }
}