package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.engine.QueryEngineD2RQ;
import de.fuberlin.wiwiss.d2rq.find.FindQuery;
import de.fuberlin.wiwiss.d2rq.find.TripleQueryIter;
import de.fuberlin.wiwiss.d2rq.map.ConnectingMapping;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.graph.Capabilities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * A D2RQ virtual read-only Jena graph backed by a non-RDF database.
 *
 * @author Chris Bizer chris@bizer.de
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class GraphD2RQ extends GraphBase implements Graph {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphD2RQ.class);

    @SuppressWarnings("deprecated")
    private static final Capabilities READ_ONLY_CAPABILITIES = new Capabilities() {
        @Override
        public boolean sizeAccurate() {
            return true;
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
            return true;
        }

        @Override
        public boolean iteratorRemoveAllowed() {
            return false;
        }

        @Override
        public boolean findContractSafe() {
            return false;
        }

        @Override
        public boolean handlesLiteralTyping() {
            return true;
        }
    };

    static {
        QueryEngineD2RQ.register();
    }

    private final ConnectingMapping mapping;

    /**
     * Creates a new D2RQ graph from a previously prepared {@link ConnectingMapping} instance.
     *
     * @param mapping A D2RQ mapping
     * @throws D2RQException If the mapping is invalid
     */
    public GraphD2RQ(ConnectingMapping mapping) throws D2RQException {
        this.mapping = mapping;
        // todo: currently it is a snapshot:
        getPrefixMapping().setNsPrefixes(mapping.getSchema().getPrefixMapping());
    }

    @Override
    public void close() {
        mapping.close();
    }

    @Override
    public Capabilities getCapabilities() {
        return READ_ONLY_CAPABILITIES;
    }

    @Override
    public ExtendedIterator<Triple> graphBaseFind(Triple triplePattern) {
        checkOpen();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Find pattern: {}", PrettyPrinter.toString(triplePattern, getPrefixMapping()));
        }
        Collection<TripleRelation> relations = mapping.compiledPropertyBridges();
        ExtendedIterator<Triple> schema = null;
        if (mapping.withSchema()) {
            schema = mapping.getSchema().find(triplePattern);
        }
        FindQuery query = new FindQuery(triplePattern, relations, null);
        ExtendedIterator<Triple> data = TripleQueryIter.create(query.iterator());
        if (schema != null) {
            return schema.andThen(data);
        }
        return data;
    }

    @Override
    protected void checkOpen() {
        mapping.connect();
    }

    /**
     * @return The {@link ConnectingMapping} this graph is based on
     */
    public ConnectingMapping getMapping() {
        return mapping;
    }

    /**
     * Do not allow database excursion
     *
     * @return String
     */
    @Override
    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }
}