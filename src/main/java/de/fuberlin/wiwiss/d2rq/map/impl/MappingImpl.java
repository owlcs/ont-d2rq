package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.ClassMapLister;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import de.fuberlin.wiwiss.d2rq.jena.ControlledGraph;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.jena.MappingGraph;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.map.impl.schema.SchemaGenerator;
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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
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

    protected static SchemaGenerator schemaGenerator = SchemaGenerator.getInstance();

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
        return new MappingGraph(MappingImpl.this) {

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

            @Override
            public String toString() {
                return String.format("Schema[%s]", mapping);
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
        ConfigurationImpl conf = getConfiguration();
        Graph schema = getSchema();
        Graph res = new GraphD2RQ(this, schema.getPrefixMapping(), conf.getServeVocabulary() ? schema : null);
        if (conf.getWithCache()) {
            res = new CachingGraph(res, conf.getCacheMaxSize(), conf.getCacheLengthLimit());
        }
        return res;
    }

    /**
     * Creates a Schema-{@code Graph} instance that is backed by the mapping graph.
     *
     * @return {@link Graph}
     */
    public Graph createSchemaGraph() {
        return schemaGenerator.createDynamicGraph(this);
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
        return getConfiguration().getUseAllOptimizations();
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
            // note: the following instruction also clears connections and compiled property bridges (CacheController)
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
    public Stream<Database> listDatabases() {
        return Iter.asStream(databases()).map(Function.identity());
    }

    public ExtendedIterator<DatabaseImpl> databases() {
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
    public Stream<TranslationTable> listTranslationTables() {
        return Iter.asStream(translationTables()).map(Function.identity());
    }

    public ExtendedIterator<TranslationTableImpl> translationTables() {
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
    public Stream<AdditionalProperty> listAdditionalProperties() {
        return Iter.asStream(additionalProperties()).map(Function.identity());
    }

    public ExtendedIterator<AdditionalPropertyImpl> additionalProperties() {
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
    public Stream<DownloadMap> listDownloadMaps() {
        return Iter.asStream(downloadMaps()).map(Function.identity());
    }

    public ExtendedIterator<DownloadMapImpl> downloadMaps() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.DownloadMap).mapWith(this::asDownloadMap);
    }

    public DownloadMapImpl asDownloadMap(Resource r) {
        return new DownloadMapImpl(r, this);
    }

    @Override
    public ConfigurationImpl getConfiguration() {
        Resource r = Iter.findFirst(listConfigurations())
                .orElseGet(() -> model.createResource(D2RQ.Configuration));
        return new ConfigurationImpl(r, this);
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
    public Stream<PropertyBridge> listPropertyBridges() {
        return Iter.asStream(propertyBridges()).map(Function.identity());
    }

    public ExtendedIterator<PropertyBridgeImpl> propertyBridges() {
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
    public Stream<ClassMap> listClassMaps() {
        return Iter.asStream(classMaps()).map(Function.identity());
    }

    public ExtendedIterator<ClassMapImpl> classMaps() {
        return model.listResourcesWithProperty(RDF.type, D2RQ.ClassMap).mapWith(this::asClassMap);
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
            if (getConfiguration().getControlOWL()) {
                compileOWL();
            }
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

    public ExtendedIterator<TripleRelation> tripleRelations() throws D2RQException {
        return Iter.flatMap(classMaps(), c -> c.toTripleRelations().iterator());
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
        if (listDatabases()
                .peek(MapObject::validate)
                .peek(d -> {
                    if (jdbcURIs.add(d.getJDBCDSN())) return;
                    throw new D2RQException("Duplicate d2rq:Database jdbcURI: " + d.getJDBCDSN(),
                            D2RQException.UNSPECIFIED);
                })
                .count() == 0) {
            throw new D2RQException("No d2rq:Database defined in the mapping", D2RQException.MAPPING_NO_DATABASE);
        }
        translationTables().forEachRemaining(MapObject::validate);
        additionalProperties().forEachRemaining(MapObject::validate);
        downloadMaps().forEachRemaining(MapObject::validate);
        propertyBridges().forEachRemaining(MapObject::validate);

        classMaps().forEachRemaining(MapObject::validate);
        List<ClassMapImpl> incomplete = classMaps().filterDrop(ClassMapImpl::hasContent).toList();
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
     * Generates different {@link ClassMap}s and {@link PropertyBridge}s in order to make data satisfy OWL2 requirements.
     * Currently it handles the following cases:
     * <ul>
     * <li>a class type for ClassMaps where it is missed</li>
     * <li>{@code owl:NamedIndividual} declaration for ClassMaps, if the desired individual is named (has IRI)</li>
     * <li>{@code owl:sameAs} and {@code owl:differentFrom} for PropertyBridges
     * (the right parts of these statements should also have it is own declaration)</li>
     * <li>dynamic class-types for PropertyBridges</li>
     * </ul>
     *
     * @see <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL 2 Quick Reference Guide</a>
     */
    public void compileOWL() {
        // owl:NamedIndividual declaration + class type for anonymous individuals:
        classMaps()
                .filterDrop(ResourceMap::isAutoGenerated)
                .forEachRemaining(c -> {
                    Set<Resource> classes = schemaGenerator.listClasses(model, c.asResource()).toSet();
                    if (classes.size() == 0) {
                        Resource clazz = c.asResource();
                        if (clazz.isAnon()) {
                            // require all classes to be named:
                            clazz = AVC.UnknownClass(clazz.getId().toString());
                        }
                        generatePropertyBridgeWithConstantType(c, clazz);
                    }
                    if (c.getBNodeIdColumns() == null) {
                        // explicit declaration for named individuals
                        generatePropertyBridgeWithConstantType(c, OWL.NamedIndividual);
                    }
                });
        // owl:sameAs, owl:differentFrom individual assertions:
        Set<Property> symmetricIndividualPredicates = Stream.of(OWL.sameAs, OWL.differentFrom)
                .collect(Collectors.toSet());
        propertyBridges()
                .filterDrop(ResourceMap::isAutoGenerated)
                .filterKeep(p -> p.listProperties().anyMatch(symmetricIndividualPredicates::contains))
                .filterDrop(p -> p.getURIColumn() == null)
                .forEachRemaining(p -> generateClassMapWithTypeAndPredicate(p, OWL.NamedIndividual, D2RQ.uriColumn));
        // rdf:type
        propertyBridges()
                .filterDrop(ResourceMap::isAutoGenerated)
                .filterKeep(p -> p.listProperties().anyMatch(RDF.type::equals))
                .filterDrop(p -> p.getURIPattern() == null)
                .forEachRemaining(p -> generateClassMapWithTypeAndPredicate(p, OWL.Class, D2RQ.uriPattern)
                        .setContainsDuplicates(true));
        // TODO: handle dynamic properties
    }

    /**
     * Finds or creates a PropertyBridge for the given ClassMap and {@code rdf:type}.
     *
     * @param c    {@link ClassMapImpl}, not {@code null}
     * @param type {@link Resource}, type, not {@code null}
     * @return {@link PropertyBridgeImpl}, fresh or found
     */
    protected static PropertyBridgeImpl generatePropertyBridgeWithConstantType(ClassMapImpl c, Resource type) {
        MappingImpl m = c.getMapping();
        Optional<PropertyBridgeImpl> res = Iter.findFirst(m.asModel()
                .listResourcesWithProperty(D2RQ.constantValue, type)
                .filterKeep(r -> r.hasProperty(D2RQ.belongsToClassMap, c.resource)
                        && r.hasProperty(D2RQ.property, RDF.type))
                .mapWith(m::asPropertyBridge));
        if (res.isPresent()) return res.get();
        PropertyBridgeImpl p = m.createPropertyBridge(null);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generate {} for {}", p, c);
        }
        return p.setBelongsToClassMap(c)
                .setAutoGenerated()
                .addProperty(RDF.type)
                .setConstantValue(type);
    }

    /**
     * Finds or creates a ClassMap for the given PropertyBridge, {@code rdf:type} and predicate {@code p}.
     *
     * @param p         {@link PropertyBridgeImpl}, not {@code null}
     * @param type      {@link Resource}, type, not {@code null}
     * @param predicate {@link Property} predicate that belongs to the {@code p}
     * @return {@link ClassMapImpl}, fresh or found
     * @throws IllegalArgumentException no predicate found
     * @throws IllegalStateException    no database found
     */
    protected static ClassMapImpl generateClassMapWithTypeAndPredicate(PropertyBridgeImpl p,
                                                                       Resource type,
                                                                       Property predicate) throws IllegalArgumentException, IllegalStateException {
        String value = p.findString(predicate)
                .orElseThrow(() -> new IllegalArgumentException("Can't find " + predicate + " for " + p));
        DatabaseImpl d = p.getDatabase();
        if (d == null) throw new IllegalStateException("Can't find database for " + p);
        MappingImpl m = p.getMapping();
        Optional<ClassMapImpl> res = Iter.findFirst(m.asModel()
                .listResourcesWithProperty(D2RQ.clazz, type)
                .filterKeep(r -> r.hasProperty(predicate, value) && r.hasProperty(D2RQ.dataStorage, d.resource))
                .mapWith(m::asClassMap));
        if (res.isPresent()) return res.get();
        ClassMapImpl c = m.createClassMap(null);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generate {} for {}", c, p);
        }
        return c.setDatabase(d)
                .setAutoGenerated()
                .addClass(type)
                .setLiteral(predicate, value);
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
        return Iter.asStream(databases().mapWith(DatabaseImpl::getJDBCDSN))
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
