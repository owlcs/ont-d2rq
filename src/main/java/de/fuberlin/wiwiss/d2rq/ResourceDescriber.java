package de.fuberlin.wiwiss.d2rq;

import org.apache.jena.atlas.lib.Alarm;
import org.apache.jena.atlas.lib.AlarmClock;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.iterator.QueryIterConcat;

import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.find.FindQuery;
import de.fuberlin.wiwiss.d2rq.find.TripleQueryIter;
import de.fuberlin.wiwiss.d2rq.map.Mapping;

public class ResourceDescriber {
    private final Mapping mapping;
    private final Node node;
    private final boolean onlyOutgoing;
    private final int limit;
    private final long timeout;
    private final Graph result = new GraphMem();
    private final ExecutionContext context;
    private boolean executed = false;

    public ResourceDescriber(Mapping mapping, Node resource) {
        this(mapping, resource, false, Relation.NO_LIMIT, -1);
    }

    public ResourceDescriber(Mapping mapping, Node resource, boolean onlyOutgoing, int limit, long timeout) {
        this.mapping = mapping;
        this.node = resource;
        this.onlyOutgoing = onlyOutgoing;
        this.limit = limit;
        this.timeout = timeout;
        this.context = null;
    }

    public Graph description() {
        if (executed) return result;
        executed = true;

        final QueryIterConcat qIter = new QueryIterConcat(context);
        Alarm pingback = null;
        if (timeout > 0) {
            pingback = AlarmClock.get().add(qIter::cancel, timeout);
        }

        FindQuery outgoing = new FindQuery(
                Triple.create(node, Node.ANY, Node.ANY),
                mapping.compiledPropertyBridges(), limit, context);
        qIter.add(outgoing.iterator());

        if (!onlyOutgoing) {
            FindQuery incoming = new FindQuery(
                    Triple.create(Node.ANY, Node.ANY, node),
                    mapping.compiledPropertyBridges(), limit, context);
            qIter.add(incoming.iterator());

            FindQuery triples = new FindQuery(
                    Triple.create(Node.ANY, node, Node.ANY),
                    mapping.compiledPropertyBridges(), limit, context);
            qIter.add(triples.iterator());
        }
        // todo: no more com.hp.hpl.jena.graph.BulkUpdateHandler. Use org.apache.jena.graph.GraphUtil:
        //result.getBulkUpdateHandler().add(TripleQueryIter.create(qIter));
        GraphUtil.add(result, TripleQueryIter.create(qIter));

        if (pingback != null) {
            AlarmClock.get().cancel(pingback);
        }

        return result;
    }
}
