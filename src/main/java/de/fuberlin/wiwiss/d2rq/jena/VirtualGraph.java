package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.graph.*;
import org.apache.jena.graph.impl.SimpleTransactionHandler;
import org.apache.jena.shared.AccessDeniedException;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ClosableIterator;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * A simple abstract graph wrapper, which is used as virtual graph to conduct some indirect behaviour.
 * Subtypes of this class must provide {@link #find(Triple)} method.
 * Notice that it is a read-only accessor: the methods
 * {@link #add(Triple)}, {@link #delete(Triple)}, {@link #remove(Node, Node, Node)} and {@link #clear()}
 * throw {@code JenaException}.
 * Also, the methods {@link #isEmpty()} and {@link #size()} of this implementation do not return the actual values,
 * and the method {@link #close()} has no-op behaviour.
 * <p>
 * Created by @ssz on 10.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public abstract class VirtualGraph implements Graph {

    protected final Graph graph;

    public VirtualGraph(Graph g) {
        this.graph = Objects.requireNonNull(g, "Null base graph.");
    }

    /**
     * Creates a virtual mask graph that is provided a possibility to hide some desired parts
     * of the encapsulated {@code base} graph using the specified {@link BiPredicate},
     * which accepts {@link Graph} and {@link Triple} as parameters.
     * The returning graph is read-only: any attempt to modify it causes {@link org.apache.jena.shared.JenaException}.
     *
     * @param base {@link Graph}, not {@code null}
     * @param mask {@link BiPredicate} with {@link Graph} and {@link Triple} as first and second input
     * @return {@link Graph}, not {@code null}
     */
    public static Graph createMaskGraph(Graph base, BiPredicate<Graph, Triple> mask) {
        return new VirtualGraph(base) {
            @Override
            public ExtendedIterator<Triple> find(Triple m) {
                return graph.find(m).filterDrop(t -> mask.test(graph, t));
            }
        };
    }

    /**
     * Creates a dynamic virtual graph for the given {@code base} graph,
     * using the {@link DynamicTriples} transformation mechanism.
     * This is such a sort of an inference: having a base graph,
     * it is possible to get another view of the same data but in totally different form.
     * The returning graph is read-only: any attempt to modify it causes {@link org.apache.jena.shared.JenaException}.
     *
     * @param base      {@link Graph}, not {@code null}
     * @param transform {@link DynamicTriples}
     * @return {@link Graph}, not {@code null}
     */
    public static Graph createDynamicGraph(Graph base, DynamicTriples transform) {
        return new VirtualGraph(base) {

            @Override
            public ExtendedIterator<Triple> find(Triple m) {
                return transform.find(graph, m);
            }
        };
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

    /**
     * A functional interface that is used to transform triples to another form.
     * <p>
     * Created by @ssz on 22.09.2018.
     */
    public interface DynamicTriples {
        DynamicTriples EMPTY = (g, t) -> NullIterator.instance();

        static DynamicTriples concat(DynamicTriples left, DynamicTriples right) {
            if (left == right) return left;
            if (left == EMPTY) {
                return right;
            }
            if (right == EMPTY) {
                return left;
            }
            return (g, t) -> left.find(g, t).andThen(right.find(g, t));
        }

        default boolean test(Triple m) {
            return true;
        }

        ExtendedIterator<Triple> list(Graph g, Triple m);

        default ExtendedIterator<Triple> find(Graph g, Triple m) {
            return test(m) ? list(g, m) : NullIterator.instance();
        }

        default DynamicTriples andThen(DynamicTriples right) {
            return concat(this, right);
        }
    }
}
