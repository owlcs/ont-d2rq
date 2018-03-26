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
 * Currently multi-sources are not allowed:
 * to deal with several db-connections please just concat result using owl:import or by some other graph-union operation.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
public class D2RQGraphDocumentSource extends OntGraphDocumentSource implements AutoCloseable {
    public static final IRI DEFAULT_BASE_IRI = IRI.create("http://d2rq.avc.ru/");

    private final Mapping mapping;

    /**
     * The main constructor.
     *
     * @param mapping {@link Mapping}
     */
    protected D2RQGraphDocumentSource(Mapping mapping) {
        this.mapping = mapping;
    }

    /**
     * Creates an {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource} from a {@link Mapping}.
     * A mapping with multi connection strings is not allowed:
     * different databases could contain intersection in schema/table names which requires special treatment.
     * If you want a single ontology made from different sources just import one ontology to another.
     *
     * @param mapping {@link Mapping}
     * @return {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource}
     * @throws OntApiException in case argument is wrong
     */
    public static D2RQGraphDocumentSource create(Mapping mapping) {
        Set<String> dbs = OntApiException.notNull(mapping, "Null mapping").databases()
                .stream()
                .map(Database::getJDBCDSN).collect(Collectors.toSet());
        if (dbs.isEmpty()) {
            throw new OntApiException("No jdbc connection string in the mapping");
        }
        if (dbs.size() != 1) {
            throw new IllegalArgumentException("Should be only single d2rq:jdbcDSN: " + dbs);
        }
        return new D2RQGraphDocumentSource(mapping);
    }

    public static D2RQGraphDocumentSource create(IRI jdbcURI, String user, String pwd) {
        return create(DEFAULT_BASE_IRI, jdbcURI, user, pwd);
    }

    /**
     * Creates an {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource} from parametrs.
     *
     * @param baseIRI {@link IRI} the base iri to construct owl-entity iris
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
     *
     * @return {@link Graph}
     */
    @Override
    public Graph getGraph() {
        return new HybridImpl(mapping.getVocabularyModel().getGraph(), mapping.getDataGraph());
    }

    /**
     * Returns the JDBC connection string as IRI.
     * It should be the same as specified in the constructor.
     *
     * @return {@link IRI}. There should be always the only single JDBC-connection uri in the mapping.
     * @throws OntApiException in case there is no jdbc uri in mapping.
     */
    @Override
    public IRI getDocumentIRI() {
        return mapping.databases()
                .stream()
                .map(Database::getJDBCDSN)
                .map(IRI::create)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No d2rq:jdbcDSN"));
    }

    /**
     * Closes mapping.
     * note: it will be reopened if we continue work with result ontology.
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
