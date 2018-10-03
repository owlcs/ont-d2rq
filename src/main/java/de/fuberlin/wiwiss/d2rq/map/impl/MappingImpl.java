package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.ClassMapLister;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQLScriptLoader;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A D2RQ mapping. Consists of {@link ClassMap}s, {@link PropertyBridge}s, and several other classes.
 * <p>
 * TODO: Move TripleRelation/NodeMaker building and ConnectedDB to a separate class (MappingRunner?)
 * TODO: #add* methods should write to mapping model also.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class MappingImpl implements Mapping {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingImpl.class);

    // todo: control it using listeners
    private final Map<Resource, ConnectedDB> connections = new HashMap<>();

    private final Map<Resource, ClassMapImpl> classMaps = new HashMap<>();
    private final Map<Resource, DownloadMap> downloadMaps = new HashMap<>();

    private final Model model;

    private Configuration configuration;
    private Collection<TripleRelation> compiledPropertyBridges;
    // cache for prefixes (todo: remove - should be obtained from schema)
    private PrefixMapping prefixes;
    // cache for schema (todo: remove)
    private Model vocabularyModel;
    // cache for dataGraph (todo: remove)
    private GraphD2RQ dataGraph;
    private volatile boolean connected = false;

    /**
     * protected access: to get instance of this class please use {@link MappingFactory} or {@link MapParser}.
     *
     * @param model {@link Model} with D2RQ rules.
     */
    protected MappingImpl(Model model) {
        this.model = Objects.requireNonNull(model, "Null mapping model");
    }

    @Override
    public Model asModel() {
        return model;
    }

    public Model getVocabularyModel() {
        return vocabularyModel == null ? vocabularyModel = MappingTransform.getModelBuilder().build(this) : vocabularyModel;
    }

    @Override
    public Graph getSchema() {
        return getVocabularyModel().getGraph();
    }

    @Override
    public GraphD2RQ getData() {
        return dataGraph == null ? dataGraph = new GraphD2RQ(this) : dataGraph;
    }

    /**
     * moved from {@link de.fuberlin.wiwiss.d2rq.SystemLoader}
     * TODO: it seems we don't need it at all.
     *
     * @return {@link ClassMapLister}
     */
    public ClassMapLister getClassMapLister() {
        return new ClassMapLister(this);
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return prefixes == null ? prefixes = MappingFactory.Prefixes.createSchemaPrefixes(model) : prefixes;
    }

    /**
     * Connects all databases. This is done automatically if needed.
     * The method can be used to test the connections earlier.
     * TODO: do not see any sense in this method. Scheduled to remove
     *
     * @throws D2RQException on connection failure
     */
    @Override
    public void connect() {
        if (connected) return;
        connected = true;
        for (ConnectedDB db : connections.values()) {
            db.connection();
        }
        validate();
    }

    @Override
    public void close() {
        connections.values().forEach(ConnectedDB::close);
    }

    @Override
    public DatabaseImpl createDatabase(String uri) {
        return asDatabase(model.createResource(uri, D2RQ.Database));
    }

    protected DatabaseImpl asDatabase(Resource r) {
        return new DatabaseImpl(r.inModel(model), this);
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
        return model.listResourcesWithProperty(RDF.type, D2RQ.Database).mapWith(r -> new DatabaseImpl(r, this));
    }

    public ConnectedDB getConnectedDB(DatabaseImpl db) {
        return connections.computeIfAbsent(db.asResource(), d -> createConnectionDB(db));
    }

    public boolean hasConnection(DatabaseImpl db) {
        return connections.containsKey(db.asResource()) && connections.get(db.asResource()).isConnected();
    }

    void registerConnectedDB(DatabaseImpl db, ConnectedDB c) {
        connections.put(db.asResource(), c);
    }

    public ConnectedDB createConnectionDB(DatabaseImpl db) {
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
        return new TranslationTableImpl(r.inModel(model), this);
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
        return new AdditionalPropertyImpl(r.inModel(model), this);
    }

    @Override
    public DownloadMapImpl createDownloadMap(Resource r) {
        return new DownloadMapImpl(r.inModel(model), this);
    }

    @Override
    public Configuration getConfiguration() {
        if (configuration != null) return configuration;
        Resource r = Iter.asStream(model.listResourcesWithProperty(RDF.type, D2RQ.Configuration))
                .findFirst().orElseGet(() -> model.createResource(D2RQ.Configuration));
        return configuration = createConfiguration(r);
    }

    public ConfigurationImpl createConfiguration(Resource r) {
        return new ConfigurationImpl(r, this);
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ClassMapImpl createClassMap(Resource r) {
        return new ClassMapImpl(r.inModel(model), this);
    }

    @Override
    public PropertyBridgeImpl createPropertyBridge(Resource r) {
        return asPropertyBridge(r);
    }

    public PropertyBridgeImpl asPropertyBridge(Resource r) {
        return new PropertyBridgeImpl(r.inModel(model), this);
    }

    @Override
    public void addClassMap(ClassMap classMap) { // todo: wtf
        this.classMaps.put(classMap.asResource(), (ClassMapImpl) classMap);
    }

    @Override
    public Stream<ClassMap> listClassMaps() {
        return classMaps.values().stream().map(Function.identity());
    }

    @Override
    public ClassMapImpl findClassMap(Resource name) {
        return this.classMaps.get(name);
    }

    public void addDownloadMap(DownloadMap downloadMap) {
        downloadMaps.put(downloadMap.asResource(), downloadMap);
    }

    @Override
    public Stream<DownloadMap> listDownloadMaps() {
        return downloadMaps.values().stream();
    }

    @Override
    public DownloadMap findDownloadMap(Resource name) {
        return downloadMaps.get(name);
    }

    /**
     * @return A collection of {@link TripleRelation}s corresponding to each of the property bridges.
     */
    @Override
    public synchronized Collection<TripleRelation> compiledPropertyBridges() {
        if (this.compiledPropertyBridges == null) {
            compilePropertyBridges();
        }
        return this.compiledPropertyBridges;
    }

    private void compilePropertyBridges() {
        /*
          validate temporarily disabled, see bug
          https://github.com/d2rq/d2rq/issues/194

          Not adding tests since new development in other branch
          but this patch reduces test errors from 92 to 38

         validate();

         */
        compiledPropertyBridges = new ArrayList<>();
        for (ClassMapImpl classMap : classMaps.values()) {
            this.compiledPropertyBridges.addAll(classMap.compiledPropertyBridges());
        }
        LOGGER.info("Compiled {} property bridges", compiledPropertyBridges.size());
        if (LOGGER.isDebugEnabled()) {
            compiledPropertyBridges.stream().map(String::valueOf).forEach(LOGGER::debug);
        }
    }


    @Override
    public void validate() throws D2RQException {
        Set<Database> databases = listDatabases().collect(Collectors.toSet());
        if (databases.isEmpty()) {
            throw new D2RQException("No d2rq:Database defined in the mapping", D2RQException.MAPPING_NO_DATABASE);
        }
        for (Database db : databases) {
            db.validate();
        }
        listTranslationTables().forEach(MapObject::validate);
        listAdditionalProperties().forEach(MapObject::validate);

        List<ClassMap> classMapsWithoutProperties = new ArrayList<>(classMaps.values());
        for (ClassMapImpl classMap : classMaps.values()) {
            classMap.validate();    // Also validates attached bridges
            if (classMap.hasProperties()) {
                classMapsWithoutProperties.remove(classMap);
            }
            for (PropertyBridge bridge : classMap.getPropertyBridges()) {
                ClassMap refersToClassMap = bridge.getRefersToClassMap();
                if (refersToClassMap != null) {
                    classMapsWithoutProperties.remove(refersToClassMap);
                }
            }
        }
        if (!classMapsWithoutProperties.isEmpty()) {
            throw new D2RQException(classMapsWithoutProperties.iterator().next().toString() +
                    " has no d2rq:PropertyBridges and no d2rq:class", D2RQException.CLASSMAP_NO_PROPERTYBRIDGES);
        }
        for (DownloadMap dm : downloadMaps.values()) {
            dm.validate();
        }
        for (TripleRelation bridge : compiledPropertyBridges()) {
            new AttributeTypeValidator(bridge).validate();
        }
    }

    private class AttributeTypeValidator {
        private final Relation relation;

        AttributeTypeValidator(TripleRelation relation) {
            this.relation = Objects.requireNonNull(relation.baseRelation());
        }

        void validate() {
            for (Attribute attribute : relation.allKnownAttributes()) {
                DataType dataType = relation.database().columnType(
                        relation.aliases().originalOf(attribute));
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
    }


}
