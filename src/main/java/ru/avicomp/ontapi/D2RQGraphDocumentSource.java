package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.ontapi.jena.HybridImpl;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The document source ({@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource}) for loading graph from database in form of OWL2 ontology.
 * The graph is provided in the hybrid form (see {@link ru.avicomp.ontapi.jena.Hybrid})
 * and includes DB-schema as primary {@link org.apache.jena.mem.GraphMem} and
 * DB-data (with schema inside also) as virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ} graphs.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
@SuppressWarnings("WeakerAccess")
public class D2RQGraphDocumentSource extends OntGraphDocumentSource implements AutoCloseable {
    public static final IRI DEFAULT_BASE_IRI = IRI.create("http://d2rq.avc.ru/");

    protected final Mapping mapping;
    protected final IRI doc;

    /**
     * The main constructor.
     *
     * @param mapping {@link Mapping}
     * @throws OntApiException if the mapping is not suitable
     */
    protected D2RQGraphDocumentSource(Mapping mapping) throws OntApiException {
        Set<String> dbs = OntApiException.notNull(mapping, "Null mapping").databases()
                .stream()
                .map(Database::getJDBCDSN).collect(Collectors.toSet());
        if (dbs.isEmpty()) {
            throw new OntApiException("No jdbc connection string in the mapping");
        }
        this.doc = IRI.create("d2rq://" + dbs.stream().collect(Collectors.joining(";")));
        this.mapping = mapping;
    }

    /**
     * Creates a {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource OWLAPI document source} from a {@link Mapping mapping}.
     *
     * @param mapping {@link Mapping}
     * @return {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource}
     * @throws OntApiException in case argument is wrong
     */
    public static D2RQGraphDocumentSource create(Mapping mapping) {
        return new D2RQGraphDocumentSource(mapping);
    }

    public static D2RQGraphDocumentSource create(IRI jdbcURI, String user, String pwd) {
        return create(DEFAULT_BASE_IRI, jdbcURI, user, pwd);
    }

    /**
     * Creates an {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource} from parameters.
     *
     * @param baseIRI {@link IRI} the base iri to build owl-entity iris
     * @param jdbcIRI {@link IRI} jdbc-connection string
     * @param user    the connection user login
     * @param pwd     the connection user password
     * @return {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource}
     */
    public static D2RQGraphDocumentSource create(IRI baseIRI, IRI jdbcIRI, String user, String pwd) {
        SystemLoader loader = new SystemLoader();
        loader.setJdbcURL(OntApiException.notNull(jdbcIRI, "Null JDBC uri.").getIRIString());
        if (baseIRI != null) {
            loader.setSystemBaseURI(baseIRI.getIRIString());
        }
        if (user != null) {
            loader.setUsername(user);
        }
        if (pwd != null) {
            loader.setPassword(pwd);
        }
        return create(loader.getMapping());
    }

    /**
     * Makes a new {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource} with restrictions from {@link MappingFilter}
     *
     * @param filter {@link MappingFilter}, could be null.
     * @return {@link D2RQGraphDocumentSource}
     */
    public D2RQGraphDocumentSource filter(MappingFilter filter) {
        if (filter == null) {
            return this;
        }
        Model map = filter.build(mapping);
        return create(MappingFactory.create(map));
    }

    /**
     * Returns mapping. The main D2RQ interface to work with DB.
     *
     * @return {@link Mapping}
     */
    public Mapping getMapping() {
        return mapping;
    }

    /**
     * Returns hybrid graph which consists of two graphs:
     * <ul>
     * <li>the default (primary) {@link org.apache.jena.mem.GraphMem} with the schema inside (editable)</li>
     * <li>the virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ} with the schema and data inside (immutable)</li>
     * </ul>
     * Please note:
     * <ul>
     * <li>Any changes in primary graph affects schema-part of D2RQ graph</li>
     * <li>The D2RQ virtual graph is not distinct: it can contain duplicate triples reflecting duplicated tuples from a db table</li>
     * </ul>
     * @return {@link Graph}
     */
    @Override
    public Graph getGraph() {
        return new HybridImpl(mapping.getVocabularyModel().getGraph(), mapping.getDataGraph());
    }

    /**
     * Returns an IRI with JDBC connection details.
     *
     * @return {@link IRI}
     * @throws OntApiException in case there is no jdbc uri in mapping.
     */
    @Override
    public IRI getDocumentIRI() {
        return doc;
    }

    /**
     * Closes mapping.
     * Note: it will be reopened on demand if you continue work with the result ontology virtual graph data.
     */
    @Override
    public void close() {
        mapping.close();
    }

    @Override
    public String toString() {
        return String.format("DocumentSource[%s]", getDocumentIRI());
    }
}
