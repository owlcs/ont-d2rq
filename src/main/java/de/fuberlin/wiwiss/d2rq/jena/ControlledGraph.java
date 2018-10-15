package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.graph.*;
import org.apache.jena.graph.impl.WrappedGraph;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A simple graph wrapper with controller, that is invoked before any modification on the graph is performed.
 * Alternative way to achieve the same behaviour is using graph listeners,
 * but for that way any checking will be performed after a change is made.
 * Also, it is difficult to find a concrete listener attached to the graph, due to jena interface limitations.
 * <p>
 * Created by @ssz on 07.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class ControlledGraph extends WrappedGraph {

    private final BiConsumer<Triple, Event> control;

    public ControlledGraph(Graph base, BiConsumer<Triple, Event> control) {
        super(Objects.requireNonNull(base, "Null base graph"));
        this.control = Objects.requireNonNull(control, "Null control");
    }

    /**
     * Wraps the given graph as {@link ControlledGraph}.
     * If the input is already {@link WrappedGraph Graph Wrapper}, then unwraps it first.
     *
     * @param graph   {@link Graph}, not {@code null}
     * @param control {@link BiConsumer}, not {@code null}, to control modification
     * @return {@link ControlledGraph}
     */
    public static ControlledGraph wrap(Graph graph, BiConsumer<Triple, Event> control) {
        if (graph instanceof WrappedGraph) {
            graph = ((WrappedGraph) graph).getWrapped();
        }
        return new ControlledGraph(graph, control);
    }

    @Override
    public void add(Triple t) {
        control.accept(t, Event.ADD);
        super.add(t);
    }

    @Override
    public void delete(Triple t) {
        control.accept(t, Event.DELETE);
        super.delete(t);
    }

    @Override
    public void clear() {
        control.accept(Triple.ANY, Event.CLEAR);
        super.clear();
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        // redirect to #delete method
        GraphUtil.remove(this, s, p, o);
        getEventManager().notifyEvent(this, GraphEvents.remove(s, p, o));
    }

    @Override
    public void performAdd(Triple t) {
        control.accept(t, Event.ADD);
        super.performAdd(t);
    }

    @Override
    public void performDelete(Triple t) {
        control.accept(t, Event.DELETE);
        super.performDelete(t);
    }

    public enum Event {
        ADD,
        DELETE,
        CLEAR,
    }
}
