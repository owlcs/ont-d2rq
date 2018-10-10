package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.graph.*;
import org.apache.jena.graph.impl.SimpleTransactionHandler;
import org.apache.jena.shared.AccessDeniedException;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ClosableIterator;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.Objects;

/**
 * Created by @ssz on 10.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public abstract class VirtualGraph implements Graph {

    protected final Graph graph;

    public VirtualGraph(Graph g) {
        this.graph = Objects.requireNonNull(g);
    }

    @Override
    public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
        return find(Triple.createMatch(s, p, o));
    }

    @Override
    public boolean contains(Node s, Node p, Node o) {
        return contains(Triple.createMatch(s, p, o));
    }

    @Override
    public boolean contains(Triple t) {
        ClosableIterator<Triple> it = find(t);
        try {
            return it.hasNext();
        } finally {
            it.close();
        }
    }

    @Override
    public boolean dependsOn(Graph other) {
        return this == other || graph == other || graph.dependsOn(other);
    }

    @Override
    public TransactionHandler getTransactionHandler() {
        return new SimpleTransactionHandler();
    }

    @Override
    public GraphEventManager getEventManager() {
        return graph.getEventManager();
    }

    @Override
    public GraphStatisticsHandler getStatisticsHandler() {
        return null;
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return graph.getPrefixMapping();
    }

    @Override
    public boolean isIsomorphicWith(Graph g) {
        return this == g || graph.isIsomorphicWith(g);
    }

    @Override
    public Capabilities getCapabilities() {
        return new CapabilitiesImpl();
    }

    @Override
    public void close() {
        // nothing to close
    }

    @Override
    public boolean isEmpty() {
        return false; // but size = 0
    }

    @Override
    public int size() {
        return 0; // but isEmpty = false
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void add(Triple t) throws AddDeniedException {
        throw new AddDeniedException("Attempt to add: " + t);
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        GraphUtil.remove(this, s, p, o);
        getEventManager().notifyEvent(this, GraphEvents.remove(s, p, o));
    }

    @Override
    public void delete(Triple t) throws DeleteDeniedException {
        throw new DeleteDeniedException("Attempt to delete: " + t);
    }

    @Override
    public void clear() {
        throw new AccessDeniedException("Operation #clear is not allowed.");
    }

    protected static class CapabilitiesImpl implements Capabilities {
        @Override
        public boolean sizeAccurate() {
            return false;
        }

        @Override
        public boolean addAllowed() {
            return addAllowed(false);
        }

        @Override
        public boolean addAllowed(boolean every) {
            return false;
        }

        @Override
        public boolean deleteAllowed() {
            return deleteAllowed(false);
        }

        @Override
        public boolean deleteAllowed(boolean every) {
            return false;
        }

        @Override
        public boolean canBeEmpty() {
            return false;
        }

        @Override
        public boolean iteratorRemoveAllowed() {
            return false;
        }

        @Override
        public boolean findContractSafe() {
            return true;
        }

        @Override
        public boolean handlesLiteralTyping() {
            return true;
        }
    }
}
