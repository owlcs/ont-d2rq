package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Configuration;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.vocab.AVC;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.ontapi.jena.HybridGraph;

import java.util.Objects;

/**
 * This is an extended {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource OWLAPI Document Source}
 * for loading a graph from a database in the form of {@link OntologyModel OWL2 ontology}.
 * A graph is provided in the hybrid form (see {@link ru.avicomp.ontapi.jena.HybridGraph})
 * and includes DB-schema as primary graph and
 * DB-data (also with the schema inside) as virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ D2RQ graph}.
 * <p>
 * Created by @szuev on 24.02.2017.
 *
 * @see <a href='https://www.w3.org/TR/rdb-direct-mapping/'>Direct Mapping</a>
 */
@SuppressWarnings("WeakerAccess")
public class D2RQGraphDocumentSource extends OntGraphDocumentSource implements AutoCloseable {
    public static final IRI DEFAULT_BASE_IRI = IRI.create(AVC.getURI());

    protected final Mapping mapping;

    /**
     * The main constructor.
     *
     * @param mapping {@link Mapping}
     * @throws OntApiException if the mapping is not suitable
     */
    protected D2RQGraphDocumentSource(Mapping mapping) throws OntApiException {
        this.mapping = Objects.requireNonNull(mapping, "Null mapping");
    }

    /**
     * Creates an OWL Document Source using the given connection settings.
     *
     * @param jdbcURI {@link IRI} jdbc-connection string, not {@code null}
     * @param user    the connection user login
     * @param pwd     the connection user password
     * @return {@link D2RQGraphDocumentSource}
     * @see #create(IRI, IRI, String, String)
     */
    public static D2RQGraphDocumentSource create(IRI jdbcURI, String user, String pwd) {
        return create(DEFAULT_BASE_IRI, jdbcURI, user, pwd);
    }

    /**
     * Creates an {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource OWL Document Source}
     * using the specified connection parameters.
     * <p>
     * The resulting graph document source encapsulates the {@link Mapping D2RQ Mapping},
     * which is slightly different from what can be generated with help of the
     * {@link de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator Default Mapping Generator} or
     * {@link de.fuberlin.wiwiss.d2rq.mapgen.W3CMappingGenerator Direct Mapping Generator}.
     * Like a {@code W3CMappingGenerator} it produces anonymous individuals
     * for each tuple from the table without primary key.
     * Each of these individuals will be equipped with the same label.
     * All other {@link de.fuberlin.wiwiss.d2rq.map.PropertyBridge PropertyBridge}s will be the same
     * as {@code MappingGenerator} would created.
     * Also note that the parameter {@link Configuration#getControlOWL()} is set to {@code true}
     * in the resulting {@link Mapping},
     * and therefore each retrieved individual will have class-type and,
     * if it is named, a {@code owl:NamedIndividual} declaration,
     *
     * @param baseIRI {@link IRI} the base iri to build owl-entity iris (see {@code d2rq:uriPattern})
     * @param jdbcIRI {@link IRI} jdbc-connection string, not {@code null}
     * @param user    the connection user login
     * @param pwd     the connection user password
     * @return {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource}
     * @see de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator
     */
    public static D2RQGraphDocumentSource create(IRI baseIRI, IRI jdbcIRI, String user, String pwd) {
        return create(new SystemLoader()
                .setJdbcURL(Objects.requireNonNull(jdbcIRI, "Null JDBC IRI.").getIRIString())
                .withAnonymousIndividuals(true)
                .setControlOWL(true)
                .setUsername(user)
                .setPassword(pwd)
                .setSystemBaseURI(baseIRI == null ? null : baseIRI.getIRIString())
                .build());
    }

    /**
     * Creates a {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource OWLAPI document source} from
     * the given {@link Mapping mapping}.
     *
     * @param mapping {@link Mapping}
     * @return {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource}
     * @throws OntApiException in case argument is wrong
     */
    public static D2RQGraphDocumentSource create(Mapping mapping) {
        return new D2RQGraphDocumentSource(mapping);
    }

    /**
     * Makes a new {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource} from this one using
     * restrictions provided by the specified {@link MappingFilter}.
     * It is useful to narrow the schema and data,
     * since the default auto-generated direct mapping contains everything for the specified database.
     *
     * @param filter {@link MappingFilter}
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
     * Returns the mapping, that is the main D2RQ interface to control RDF representation of database.
     *
     * @return {@link Mapping}
     */
    public Mapping getMapping() {
        return mapping;
    }

    /**
     * Returns a hybrid graph which consists of two graphs:
     * <ul>
     * <li>The primary {@code Graph}, that contains OWL2 declarations and reflects database schema.
     * This graph is editable</li>
     * <li>The virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ}, that contains both database schema and data.
     * This graph is unmodifiable.</li>
     * </ul>
     * Notes:
     * <ul>
     * <li>Any changes in the primary graph affects schema-part of D2RQ graph</li>
     * <li>The D2RQ virtual graph is not distinct:
     * it can contain duplicate triples reflecting duplicated tuples from a db table</li>
     * </ul>
     *
     * @return {@link Graph}
     * @see Mapping#getSchema()
     * @see Mapping#getData()
     */
    @Override
    public Graph getGraph() {
        return new HybridGraph(mapping.getSchema(), mapping.getData());
    }

    /**
     * {@inheritDoc}
     * Transformations are disabled.
     *
     * @return {@code false}
     */
    @Override
    public boolean withTransforms() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link IRI}
     * @throws OntApiException in case there is no jdbc uri in mapping.
     */
    @Override
    public IRI getDocumentIRI() {
        return IRI.create("Mapping:" + OntGraphUtils.toString(getMapping()));
    }

    /**
     * Closes mapping.
     * Note: it will be reopened on demand if continue to use the resulting ontology data graph.
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
