package de.fuberlin.wiwiss.d2rq.map.impl;

import com.github.owlcs.ontapi.jena.utils.Iter;
import com.github.owlcs.ontapi.jena.vocabulary.RDF;
import de.fuberlin.wiwiss.d2rq.ClassMapLister;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import de.fuberlin.wiwiss.d2rq.jena.ControlledGraph;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQLScriptLoader;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType;
import de.fuberlin.wiwiss.d2rq.vocab.AVC;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.graph.*;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A D2RQ mapping. Consists of {@link ClassMap}s, {@link PropertyBridge}s, and several other classes.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class MappingImpl implements Mapping, ConnectingMapping {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingImpl.class);

    protected static final Set<Node> D2RQ_PREDICATES = Stream.of(D2RQ.class, AVC.class).map(VocabularySummarizer::new)
            .map(VocabularySummarizer::getAllProperties).flatMap(Collection::stream)
            .map(FrontsNode::asNode).collect(Iter.toUnmodifiableSet());

    protected static SchemaController schemaController = SchemaController.getInstance();

    // a lock object to conduct synchronization:
    protected final Object lockObject = new Object();
    // a graph state controller
    protected volatile boolean isGraphLocked;
    // cache-collection of connected DBs:
    protected final Map<Node, ConnectedDB> connections = new ConcurrentHashMap<>();
    // collection of compiled property bridges, if it is not null, then a physical connection is present:
    protected volatile Collection<TripleRelation> compiledPropertyBridges;
    // an in-memory schema cache to optimize dynamic schema calculations
    protected volatile Graph schemaGraph;
    // a graph-reference to conduct a possibility to share db RDF data between threads
    protected volatile Graph dataGraph;
    // the mapping graph that contains all the physical information:
    protected final Model model;

    public MappingImpl(Graph base) {
        this.model = ModelFactory.createModelForGraph(ControlledGraph.wrap(base, new CacheController()));
    }

    @Override
    public Model asModel() {
        return model;
    }

    @Override
    public Graph getSchema() {
        Graph dynamic = createSchemaGraph();
        return new SchemaGraph(MappingImpl.this) {
            @Override
            protected ExtendedIterator<Triple> graphBaseFind(Triple m) {
                return getCache().find(m);
            }

            @Override
            public PrefixMapping getPrefixMapping() {
                return dynamic.getPrefixMapping();
            }

            @Override
            public void performAdd(Triple t) {
                dynamic.add(t);
            }

            @Override
            public void performDelete(Triple t) {
                dynamic.delete(t);
            }

            @Override
            public Graph toMemory() {
                return getCache();
            }

            public Graph getCache() {
                if (schemaGraph != null) return schemaGraph;
                synchronized (lockObject) {
                    if (schemaGraph != null) return schemaGraph;
                    GraphMem res = new GraphMem();
                    GraphUtil.addInto(res, dynamic);
                    return schemaGraph = res;
                }
            }

        };
    }

    @Override
    public Graph getData() {
        if (dataGraph != null) return dataGraph;
        synchronized (lockObject) {
            if (dataGraph != null) return dataGraph;
            return dataGraph = createDataGraph();
        }
    }

    /**
     * Creates a fresh instance of D2RQ Data Graph,
     * that can be either {@link GraphD2RQ} or {@link CachingGraph} with {@code GraphD2RQ} inside.
     *
     * @return new instance of D2RQ Data Graph
     */
    public Graph createDataGraph() {
        ConfigurationImpl conf = findConfiguration().orElse(null);
        Graph schema = getSchema();
        Graph res = createDataGraph(schema.getPrefixMapping(),
                conf == null || conf.getServeVocabulary() ? schema : null);
        if (conf != null && conf.getWithCache()) {
            res = withCache(res, conf.getCacheMaxSize(), conf.getCacheLengthLimit());
        }
        return res;
    }

    /**
     * A factory method to produce virtual DB graph instance.
     *
     * @param pm     {@link PrefixMapping}, not {@code null}
     * @param schema {@link Graph}, can be {@code null}
     * @return {@link Graph}
     */
    protected Graph createDataGraph(PrefixMapping pm, Graph schema) {
        return new GraphD2RQ(this, pm, schema);
    }

    /**
     * A factory method to produce caching graph instance.
     *
     * @param g      {@link Graph}, not {@code null}
     * @param size   positive
     * @param length positive
     * @return {@link Graph}
     */
    protected Graph withCache(Graph g, int size, long length) {
        return new CachingGraph(g, size, length);
    }

    /**
     * Creates a Schema-{@code Graph} instance that is backed by the mapping graph.
     *
     * @return {@link Graph}
     */
    public Graph createSchemaGraph() {
        return schemaController.inferSchema(this);
    }

    @Override
    public Model getDataModel() {
        return new ModelCom(getData()) {

            @Override
            public String toString() {
                // overridden, since the original super method prints all data as string,
                // for the GraphD2RQ it means getting everything from database.
                return String.format("DataModel[%s]", MappingImpl.this.toString());
            }
        };
    }

    @Override
    public boolean withAllOptimizations() {
        return findConfiguration().map(ConfigurationImpl::getUseAllOptimizations).orElse(false);
    }

    /**
     * Has been moved from {@link de.fuberlin.wiwiss.d2rq.SystemLoader}
     * TODO: it seems we don't need it at all, going to delete.
     *
     * @return {@link ClassMapLister}
     * @deprecated going to delete
     */
    @Deprecated
    public ClassMapLister getClassMapLister() {
        return new ClassMapLister(this);
    }

    public ConnectedDB getConnectedDB(DatabaseImpl db) {
        return connections.computeIfAbsent(db.asResource().asNode(), n -> createConnectionDB(db));
    }

    /**
     * Registers a new pair of {@link DatabaseImpl} and {@link ConnectedDB}.
     * WARNING: it is for internal usage only!
     *
     * @param db {@link DatabaseImpl}, not {@code null}
     * @param c  {@link ConnectedDB}, not {@code null}
     */
    public void registerConnectedDB(DatabaseImpl db, ConnectedDB c) {
        connections.put(db.asResource().asNode(), c);
    }

    /**
     * Creates a fresh {@link ConnectedDB} for the given {@link DatabaseImpl}.
     * Note: if the database {@code MapObject} has a {@code d2rq:startupSQLScript},
     * the physical db data might be overwritten.
     *
     * @param db {@link DatabaseImpl}, not {@code null}
     * @return {@link ConnectedDB}, not {@code null}
     */
    protected ConnectedDB createConnectionDB(DatabaseImpl db) {
        ConnectedDB res = db.toConnectionDB();
        String script = db.getStartupSQLScript();
        if (script == null) {
            return res;
        }
        try {
            SQLScriptLoader.loadURI(URI.create(script), res.connection());
        } catch (IllegalArgumentException | IOException | SQLException ex) {
            res.close();
            throw new D2RQException("Can't process " + script, ex);
        }
        return res;
    }

    /**
     * Ensures the mapping is connected.
     *
     * @throws D2RQException on connection failure
     */
    @Override
    public void connect() {
        compiledPropertyBridges();
    }

    @Override
    public void close() {
        if (!isConnected()) return;
        synchronized (lockObject) {
            if (!isConnected()) return;
            connections.values().forEach(ConnectedDB::close);
            clearAutoGenerated();
        }
    }

    /**
     * Answers {@code true} if the mapping has a connection to the database.
     *
     * @return boolean
     */
    public boolean isConnected() {
        return compiledPropertyBridges != null
                || !connections.isEmpty() && connections.values().stream().anyMatch(ConnectedDB::isConnected);
    }

    @Override
    public void lock() {
        this.isGraphLocked = true;
    }

    @Override
    public void unlock() {
        this.isGraphLocked = false;
    }

    @Override
    public boolean isLocked() {
        return this.isGraphLocked;
    }

    @Override
    public DatabaseImpl createDatabase(String uri) {
        return asDatabase(model.createResource(uri, D2RQ.Database));
    }

    @Override
    public MappingImpl addDatabase(Database database) {
        asDatabase(database.asResource()).copy(database);
        return this;
    }

    @Override
    public Stream<Database> databases() {
        return Iter.asStream(listDatabases());
    }

    public ExtendedIterator<DatabaseImpl> listDatabases() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.Database).mapWith(this::asDatabase);
    }

    public DatabaseImpl asDatabase(Resource r) {
        return new DatabaseImpl(r, this);
    }

    @Override
    public TranslationTableImpl createTranslationTable(String uri) {
        return asTranslationTable(model.createResource(uri, D2RQ.TranslationTable));
    }

    @Override
    public MappingImpl addTranslationTable(TranslationTable table) {
        asTranslationTable(table.asResource()).copy(table);
        return this;
    }

    @Override
    public Stream<TranslationTable> translationTables() {
        return Iter.asStream(listTranslationTables());
    }

    public ExtendedIterator<TranslationTableImpl> listTranslationTables() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.TranslationTable).mapWith(this::asTranslationTable);
    }

    public TranslationTableImpl asTranslationTable(Resource r) {
        return new TranslationTableImpl(r, this);
    }

    @Override
    public AdditionalPropertyImpl createAdditionalProperty(String uri) {
        return asAdditionalProperty(model.createResource(uri, D2RQ.AdditionalProperty));
    }

    @Override
    public MappingImpl addAdditionalProperty(AdditionalProperty property) {
        asAdditionalProperty(property.asResource()).copy(property);
        return this;
    }

    @Override
    public Stream<AdditionalProperty> additionalProperties() {
        return Iter.asStream(listAdditionalProperties());
    }

    public ExtendedIterator<AdditionalPropertyImpl> listAdditionalProperties() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.AdditionalProperty).mapWith(this::asAdditionalProperty);
    }

    public AdditionalPropertyImpl asAdditionalProperty(Resource r) {
        return new AdditionalPropertyImpl(r, this);
    }

    @Override
    public DownloadMapImpl createDownloadMap(String uri) {
        return asDownloadMap(model.createResource(uri, D2RQ.DownloadMap));
    }

    @Override
    public MappingImpl addDownloadMap(DownloadMap dm) {
        asDownloadMap(dm.asResource()).copy(dm);
        return this;
    }

    @Override
    public Stream<DownloadMap> downloadMaps() {
        return Iter.asStream(listDownloadMaps());
    }

    public ExtendedIterator<DownloadMapImpl> listDownloadMaps() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.DownloadMap).mapWith(this::asDownloadMap);
    }

    public DownloadMapImpl asDownloadMap(Resource r) {
        return new DownloadMapImpl(r, this);
    }

    @Override
    public ConfigurationImpl getConfiguration() {
        return findConfiguration()
                .orElseGet(() -> new ConfigurationImpl(model.createResource(D2RQ.Configuration), this));
    }

    public Optional<ConfigurationImpl> findConfiguration() {
        return Iter.findFirst(listConfigurations()).map(r -> new ConfigurationImpl(r, this));
    }

    protected ExtendedIterator<Resource> listConfigurations() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.Configuration);
    }

    @Override
    public PropertyBridgeImpl createPropertyBridge(String uri) {
        return asPropertyBridge(model.createResource(uri, D2RQ.PropertyBridge));
    }

    @Override
    public MappingImpl addPropertyBridge(PropertyBridge p) {
        asPropertyBridge(p.asResource()).copy(p);
        return this;
    }

    @Override
    public Stream<PropertyBridge> propertyBridges() {
        return Iter.asStream(listPropertyBridges());
    }

    public ExtendedIterator<PropertyBridgeImpl> listPropertyBridges() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.PropertyBridge).mapWith(this::asPropertyBridge);
    }

    public PropertyBridgeImpl asPropertyBridge(Resource r) {
        return new PropertyBridgeImpl(r, this);
    }

    @Override
    public ClassMapImpl createClassMap(String uri) {
        return asClassMap(model.createResource(uri, D2RQ.ClassMap));
    }

    @Override
    public MappingImpl addClassMap(ClassMap c) {
        asClassMap(c.asResource()).copy(c);
        return this;
    }

    @Override
    public Stream<ClassMap> classMaps() {
        return Iter.asStream(listClassMaps());
    }

    public ExtendedIterator<ClassMapImpl> listClassMaps() {
        return classMapResources().mapWith(this::asClassMap);
    }

    public ExtendedIterator<Resource> classMapResources() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.ClassMap);
    }

    public ClassMapImpl asClassMap(Resource r) {
        return new ClassMapImpl(r, this);
    }

    /**
     * Compiles the mapping.
     * Please note: this method establishes physical connections to the databases.
     *
     * @return a {@code Collection} of {@link TripleRelation}s corresponding to each of the property bridges.
     */
    @Override
    public Collection<TripleRelation> compiledPropertyBridges() {
        if (compiledPropertyBridges != null) return compiledPropertyBridges;
        synchronized (lockObject) {
            if (compiledPropertyBridges != null) return compiledPropertyBridges;
            // validate only RDF:
            validate(false);
            // clear auto-generated resources:
            clearAutoGenerated();
            // populate OWL declarations and axioms:
            compileSchema();
            // compile and validate all bridges (note: it requires connection):
            List<TripleRelation> res = Iter.peek(tripleRelations(), tr -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("TR={}", tr);
                }
                validateRelation(tr);
            }).toList();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiled {} property bridges", res.size());
            }
            return compiledPropertyBridges = res;
        }
    }

    /**
     * Compiles the schema.
     *
     * @see SchemaController#compileSchema(MappingImpl)
     */
    public void compileSchema() {
        if (compiledPropertyBridges != null) {
            // then already compiled
            return;
        }
        if (!getConfiguration().getControlOWL()) {
            // no dynamic schema
            return;
        }
        schemaController.compileSchema(this);
    }

    public ExtendedIterator<TripleRelation> tripleRelations() throws D2RQException {
        return Iter.flatMap(listClassMaps(), c -> c.toTripleRelations().iterator());
    }

    @Override
    public void validate(boolean withDBConnectivity) throws D2RQException {
        List<Resource> conf = listConfigurations().toList();
        if (conf.size() == 1) {
            getConfiguration().validate();
        } else if (!conf.isEmpty()) {
            throw new D2RQException("Duplicate configurations : " + conf);
        }
        Set<String> jdbcURIs = new HashSet<>();
        if (databases()
                .peek(MapObject::validate)
                .peek(d -> {
                    if (jdbcURIs.add(d.getJDBCDSN())) return;
                    throw new D2RQException("Duplicate d2rq:Database jdbcURI: " + d.getJDBCDSN(),
                            D2RQException.UNSPECIFIED);
                })
                .count() == 0) {
            throw new D2RQException("No d2rq:Database defined in the mapping", D2RQException.MAPPING_NO_DATABASE);
        }
        listTranslationTables().forEachRemaining(MapObject::validate);
        listAdditionalProperties().forEachRemaining(MapObject::validate);
        listDownloadMaps().forEachRemaining(MapObject::validate);
        listPropertyBridges().forEachRemaining(MapObject::validate);

        listClassMaps().forEachRemaining(MapObject::validate);
        List<ClassMapImpl> incomplete = listClassMaps().filterDrop(ClassMapImpl::hasContent).toList();
        if (!incomplete.isEmpty()) {
            throw new D2RQException((incomplete.size() == 1 ?
                    String.format("Class map %s has", incomplete.get(0)) :
                    String.format("Class maps %s have", incomplete)) +
                    " no d2rq:PropertyBridges and no d2rq:class", D2RQException.CLASSMAP_NO_PROPERTYBRIDGES);
        }
        if (withDBConnectivity)
            tripleRelations().forEachRemaining(MappingImpl::validateRelation);
    }

    public static void validateRelation(TripleRelation tripleRelation) throws D2RQException {
        validateRelation(tripleRelation.baseRelation());
    }

    public static void validateRelation(Relation relation) throws D2RQException {
        for (Attribute attribute : relation.allKnownAttributes()) {
            DataType dataType = relation.database().columnType(relation.aliases().originalOf(attribute));
            if (dataType == null) {
                throw new D2RQException("Column " + relation.aliases().originalOf(attribute) +
                        " has a datatype that is unknown to D2RQ; override it with d2rq:xxxColumn in the mapping file",
                        D2RQException.DATATYPE_UNKNOWN);
            }
            if (dataType.isUnsupported()) {
                throw new D2RQException("Column " + relation.aliases().originalOf(attribute) +
                        " has a datatype that D2RQ cannot express in RDF: " + dataType,
                        D2RQException.DATATYPE_UNMAPPABLE);
            }
        }
    }

    /**
     * Answers {@code true} if this mapping contains no D2RQ instructions.
     * Please note: the empty mapping does not mean that the mapping graph is also empty,
     * i.e. the expression {@code #asModel().isEmpty()} may return {@code false} for such a mapping.
     * The graph may contain some OWL or RDFS definitions or whatever else.
     *
     * @return boolean
     */
    public boolean isEmpty() {
        return !Iter.findFirst(Iter.flatMap(WrappedIterator.create(Nodes.D2RQ_TYPES.iterator())
                .mapWith(model::wrapAsResource), t -> model.listResourcesWithProperty(RDF.type, t))).isPresent();
    }

    /**
     * Removes all resources that are generated by the mapping itself while compiling.
     */
    public void clearAutoGenerated() {
        model.listResourcesWithProperty(AVC.autoGenerated).forEachRemaining(r -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Delete auto-generated resource '{}'", r);
            }
            model.remove(r.listProperties());
        });
    }

    @Override
    public String toString() {
        return Iter.asStream(listDatabases().mapWith(DatabaseImpl::getJDBCDSN))
                .collect(Collectors.joining(", ", "D2RQ-Mapping[", "]"));
    }

    /**
     * A Mapping Graph Controller to manage caches.
     *
     * @see ControlledGraph
     */
    protected class CacheController implements BiConsumer<Triple, ControlledGraph.Event> {

        @Override
        public void accept(Triple triple, ControlledGraph.Event event) {
            if (isLocked()) {
                throw new D2RQException(MappingImpl.this.toString() + " is locked. " +
                        "Can't perform " + event + " operation for the triple '" +
                        PrettyPrinter.toString(triple, model) + "'");
            }
            // reset the schema cache -> any mapping triple may be related to the schema,
            // so any change in the mapping graph must invalidate that cache
            schemaGraph = null;
            Node s = triple.getSubject();
            Node p = triple.getPredicate();
            if (ControlledGraph.Event.CLEAR == event || D2RQ_PREDICATES.contains(p) || connections.containsKey(s)) {
                compiledPropertyBridges = null;
                // reset the data -> it is possible that change is in the configuration
                // (anyway if the primary graph is not locked the preserving the same reference has a little sense)
                dataGraph = null;
            }
            if (!connections.containsKey(s)) {
                return;
            }
            if (connections.get(s).isConnected()) {
                throw new D2RQException(String.format("[%s][%s]:: cannot modify Database as it is already connected",
                        event, PrettyPrinter.toString(triple, model)), D2RQException.DATABASE_ALREADY_CONNECTED);
            }
            connections.remove(s);
        }

    }

}
