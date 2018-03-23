package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.ontapi.jena.HybridImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * The document source ({@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource}) for loading graph from database.
 * The graph is provided in the hybrid form (see {@link ru.avicomp.ontapi.jena.Hybrid})
 * and includes DB-schema as primary {@link org.apache.jena.mem.GraphMem} and
 * DB-data (with schema inside also) as virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ} graphs.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
public class D2RQGraphDocumentSource extends OntGraphDocumentSource implements AutoCloseable {
    public static final IRI DEFAULT_BASE_IRI = IRI.create("http://d2rq.avc.ru/");
    public static IRI base = DEFAULT_BASE_IRI;

    private final Mapping mapping;

    public D2RQGraphDocumentSource(IRI jdbcURI) {
        this(jdbcURI, null, null);
    }

    public D2RQGraphDocumentSource(IRI jdbcURI, String user, String pwd) {
        this(loadMapping(jdbcURI, base, user, pwd));
    }

    public D2RQGraphDocumentSource(Mapping mapping) {
        this.mapping = mapping;
    }

    public static Mapping loadMapping(IRI jdbcURI, IRI base, String user, String pwd) {
        SystemLoader loader = new SystemLoader();
        loader.setJdbcURL(OntApiException.notNull(jdbcURI, "Null JDBC uri.").getIRIString());
        if (base != null) {
            loader.setSystemBaseURI(base.getIRIString());
        }
        if (user != null) {
            loader.setUsername(user);
        }
        if (pwd != null) {
            loader.setPassword(pwd);
        }
        return loader.getMapping();
    }

    /**
     * sets base iri.
     * todo: better make it not static.
     *
     * @param iri new base IRI. Could be null.
     */
    public static void setBase(IRI iri) {
        base = iri;
    }

    /**
     * Makes new {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource} with restrictions from {@link MappingFilter}
     *
     * @param filter {@link MappingFilter}, could be null.
     * @return {@link D2RQGraphDocumentSource}
     */
    public D2RQGraphDocumentSource filter(MappingFilter filter) {
        if (filter == null) {
            return this;
        }
        Model map = filter.build(mapping);
        return new D2RQGraphDocumentSource(MappingFactory.create(map));
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
     * - the default (primary) {@link org.apache.jena.mem.GraphMem} with the schema inside (editable).
     * - the virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ} with the schema and data inside (immutable).
     *
     * @return {@link Graph}
     */
    @Override
    public Graph getGraph() {
        List<Graph> graphs = new ArrayList<>();
        graphs.add(mapping.getVocabularyModel().getGraph());
        graphs.add(mapping.getDataGraph());
        return new HybridImpl(graphs);
    }

    /**
     * Returns JDBC connection string as IRI.
     * It should be the same as specified in the constructor.
     *
     * @return {@link IRI}. There should be always the only single JDBC-connection uri in the mapping.
     * @throws OntApiException in case there is no jdbc uri in mapping.
     */
    @Override
    public IRI getDocumentIRI() {
        return mapping.databases().stream()
                .map(Database::getJDBCDSN).map(IRI::create)
                .findFirst().orElseThrow(OntApiException.supplier("No database inside mapping."));
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
        return String.format("DocumentSource[%s]", getDocumentIRI().toString());
    }
}
