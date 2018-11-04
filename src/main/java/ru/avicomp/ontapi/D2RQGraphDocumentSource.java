package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Configuration;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.vocab.AVC;
import org.apache.jena.rdf.model.Model;
import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.ontapi.jena.HybridGraph;

import java.util.Objects;
import java.util.Properties;

/**
 * This is an extended {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource OWLAPI Document Source}
 * for loading a graph from a database in the form of {@link OntologyModel OWL2 ontology}.
 * A graph is provided in the hybrid form (see {@link HybridGraph})
 * and includes DB-schema as primary graph and
 * DB-data (which may also be supplied with the schema)
 * as a virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ D2RQ Data Graph}.
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
     * @param mapping {@link Mapping}, not {@code null}
     */
    protected D2RQGraphDocumentSource(Mapping mapping) {
        this.mapping = Objects.requireNonNull(mapping, "Null mapping");
    }

    /**
     * Creates an OWL Document Source using the given connection settings.
     *
     * @param jdbcURI {@link IRI} jdbc-connection string, not {@code null}
     * @param user    the connection user login
     * @param pwd     the connection user password
     * @return {@link D2RQGraphDocumentSource D2RQ OGDS}
     * @see #create(IRI, IRI, String, String, Properties)
     */
    public static D2RQGraphDocumentSource create(IRI jdbcURI, String user, String pwd) {
        return create(DEFAULT_BASE_IRI, jdbcURI, user, pwd, null);
    }

    /**
     * Creates an {@link org.semanticweb.owlapi.io.OWLOntologyDocumentSource OWL Document Source}
     * using the specified connection parameters.
     * This is a generic method to create a mapping and document source.
     * <p>
     * The return graph document source (i.e. OGDS) encapsulates the {@link Mapping D2RQ Mapping},
     * which is slightly different from what can be generated with help of the
     * {@link de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator Default Mapping Generator} or
     * {@link de.fuberlin.wiwiss.d2rq.mapgen.W3CMappingGenerator Direct Mapping Generator}.
     * Like a {@code W3CMappingGenerator} it produces anonymous individuals
     * for each tuple from the table without primary key and named individuals for all other (good) tuples.
     * Each of the anonymous individuals will be equipped with the same label.
     * All other {@link de.fuberlin.wiwiss.d2rq.map.PropertyBridge PropertyBridge}s will be the same
     * as {@code MappingGenerator} would created.
     * Also notice, that the encapsulated {@link Mapping} has
     * the parameter {@link Configuration#getControlOWL()} set to {@code true} and
     * the parameter {@link Configuration#getServeVocabulary()} set to {@code false}.
     * The first setting means that each retrieved individual has always class-type and,
     * if it is named, a {@code owl:NamedIndividual} declaration.
     * The second setting means that a data part from the {@link #getGraph() Hybrid Graph} does not served with a schema,
     * it comes as primary (wrapped) part.
     *
     * @param baseIRI    {@link IRI} the base iri to build owl-entity iris (see {@code d2rq:uriPattern})
     * @param jdbcIRI    {@link IRI} jdbc-connection string, not {@code null}
     * @param user       String, the connection user login or {@code null}
     * @param pwd        String, the connection user password or {@code null}
     * @param props {@link Properties}, the JDBC connection properties or {@code null}
     * @return {@link D2RQGraphDocumentSource D2RQ OGDS}
     * @see de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator
     */
    public static D2RQGraphDocumentSource create(IRI baseIRI,
                                                 IRI jdbcIRI,
                                                 String user,
                                                 String pwd,
                                                 Properties props) {
        return create(new SystemLoader()
                .setJdbcURL(Objects.requireNonNull(jdbcIRI, "Null JDBC IRI.").getIRIString())
                .withAnonymousIndividuals(true)
                .setServeVocabulary(false)
                .setControlOWL(true)
                .setUsername(user)
                .setPassword(pwd)
                .setConnectionProperties(props)
                .setSystemBaseURI(baseIRI == null ? null : baseIRI.getIRIString())
                .build());
    }

    /**
     * Wraps the given {@link Mapping mapping} as a {@link D2RQGraphDocumentSource D2RQ OGDS}.
     *
     * @param mapping {@link Mapping}
     * @return {@link D2RQGraphDocumentSource D2RQ OGDS}
     * @throws OntApiException in case argument is wrong
     */
    public static D2RQGraphDocumentSource create(Mapping mapping) {
        return new D2RQGraphDocumentSource(mapping);
    }

    /**
     * Makes a new {@link D2RQGraphDocumentSource D2RQ OGDS} from this one using the
     * restrictions provided by the specified {@link MappingFilter}.
     * This method is useful to narrow the schema and, therefore, the data,
     * since the default auto-generated mapping (see {@link #create(IRI, IRI, String, String, Properties)})
     * reflects everything from the related database and may be huge and redundant.
     *
     * @param filter {@link MappingFilter}
     * @return {@link D2RQGraphDocumentSource D2RQ OGDS}
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
     * It is a mutable interface: any settings,
     * that have been set by the initialization method {@link #create(IRI, IRI, String, String, Properties)},
     * can be reset or changed using this reference.
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
     * This graph is virtual, but editable: any changes in it go directly to the mapping.</li>
     * <li>The virtual {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ}, that refers a database data in the RDF-form.
     * This graph is unmodifiable.
     * The D2RQ data graph is not distinct:
     * it may contain duplicate triples reflecting the duplicated tuples in a db table.
     * If the Document Source is default,
     * i.e. provided by the method {@link #create(IRI, IRI, String, String, Properties)},
     * then this {@code Graph} does not contain a schema</li>
     * </ul>
     *
     * @return {@link HybridGraph}
     * @see Mapping#getSchema()
     * @see Mapping#getData()
     */
    @Override
    public HybridGraph getGraph() {
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
     * Closes the mapping.
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
