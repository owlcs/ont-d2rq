package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.map.ConnectingMapping;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.mem.GraphMem;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.Objects;

/**
 * Created by @ssz on 03.11.2018.
 */
public abstract class MappingGraph extends GraphBase {
    protected final ConnectingMapping mapping;

    /**
     * Creates an instance.
     *
     * @param mapping {@link ConnectingMapping}, not {@code null}
     */
    protected MappingGraph(ConnectingMapping mapping) {
        this.mapping = Objects.requireNonNull(mapping, "Null mapping.");
    }

    @Override
    public void close() {
        mapping.close();
    }

    @Override
    protected void checkOpen() {
        mapping.connect();
    }

    /**
     * @return the {@link ConnectingMapping} this graph is based on
     */
    public ConnectingMapping getMapping() {
        return mapping;
    }

    @Override
    public boolean isEmpty() {
        return !Iter.findFirst(find()).isPresent();
    }

    /**
     * Represents this Graph as in-memory snapshot.
     * For convenience and debugging.
     *
     * @return {@link Graph}, in-memory graph
     */
    public Graph toMemory() {
        GraphMem res = new GraphMem();
        GraphUtil.addInto(res, this);
        return res;
    }

    @Override
    public String toString() {
        return String.format("[%s][%s]", getClass().getSimpleName(), mapping);
    }

}
