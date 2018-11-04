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
import org.apache.jena.shared.PrefixMapping;
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
public class GraphD2RQ extends MappingGraph implements Graph {
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

    protected final Graph schema;

    /**
     * Creates a new D2RQ graph from a previously prepared {@link ConnectingMapping} instance.
     *
     * @param mapping  {@link ConnectingMapping}, a D2RQ mapping, not {@code null}
     * @param prefixes {@link PrefixMapping} to use, can be {@code null}
     * @param schema   {@link Graph}, Vocabulary Graph, can be {@code null}
     */
    public GraphD2RQ(ConnectingMapping mapping, PrefixMapping prefixes, Graph schema) {
        super(mapping);
        this.schema = schema;
        if (prefixes != null) {
            getPrefixMapping().setNsPrefixes(prefixes);
        }
    }

    @Override
    public Capabilities getCapabilities() {
        return READ_ONLY_CAPABILITIES;
    }

    public boolean containsSchema() {
        return schema != null;
    }

    @Override
    public ExtendedIterator<Triple> graphBaseFind(Triple triplePattern) throws D2RQException {
        checkOpen();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Find pattern: {}", PrettyPrinter.toString(triplePattern, getPrefixMapping()));
        }
        Collection<TripleRelation> relations = mapping.compiledPropertyBridges();
        FindQuery query = new FindQuery(triplePattern, relations, null);
        ExtendedIterator<Triple> data = TripleQueryIter.create(query.iterator());
        if (schema != null) {
            return schema.find(triplePattern).andThen(data);
        }
        return data;
    }

    /**
     * Do not allow database excursion.
     *
     * @return String
     */
    @Override
    public String toString() {
        return String.format("Data[%s]", mapping);
    }
}