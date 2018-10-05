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

    private final Model model;

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
     * @deprecated todo: remove
     */
    @Deprecated
    public ClassMapLister getClassMapLister() {
        return new ClassMapLister(this);
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return prefixes == null ? prefixes = MappingFactory.Prefixes.createSchemaPrefixes(model) : prefixes;
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
    public Configuration getConfiguration() {
        Resource r = Iter.asStream(listConfigurations())
                .findFirst().orElseGet(() -> model.createResource(D2RQ.Configuration));
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
     * @return A collection of {@link TripleRelation}s corresponding to each of the property bridges.
     */
    @Override
    public synchronized Collection<TripleRelation> compiledPropertyBridges() {
        if (compiledPropertyBridges == null) {
            //validateRDF();
            compiledPropertyBridges = compilePropertyBridges();
            LOGGER.info("Compiled {} property bridges", compiledPropertyBridges.size());
            if (LOGGER.isDebugEnabled()) {
                compiledPropertyBridges.forEach(x -> LOGGER.debug("{}", x));
            }
        }
        return compiledPropertyBridges;
    }

    public Collection<TripleRelation> compilePropertyBridges() throws D2RQException {
         /*
          validate temporarily disabled, see bug
          https://github.com/d2rq/d2rq/issues/194

          Not adding tests since new development in other branch
          but this patch reduces test errors from 92 to 38

         validate();

         */
        return Iter.flatMap(classMaps(), c -> c.toTripleRelations().iterator()).toList();
    }

    @Override
    public void validate() throws D2RQException {
        validateRDF();
        compiledPropertyBridges().forEach(MappingImpl::validateRelation);
    }

    public void validateRDF() throws D2RQException {
        List<Resource> conf = listConfigurations().toList();
        if (conf.size() == 1) {
            getConfiguration().validate();
        } else if (!conf.isEmpty()) {
            throw new D2RQException("Duplicate configurations : " + conf);
        }
        if (listDatabases().peek(MapObject::validate).count() == 0) {
            throw new D2RQException("No d2rq:Database defined in the mapping", D2RQException.MAPPING_NO_DATABASE);
        }
        listTranslationTables().forEach(MapObject::validate);
        listAdditionalProperties().forEach(MapObject::validate);
        listDownloadMaps().forEach(MapObject::validate);
        listPropertyBridges().forEach(MapObject::validate);

        listClassMaps().forEach(MapObject::validate);
        List<ClassMapImpl> empty = classMaps().filterDrop(ClassMapImpl::hasContent).toList();
        if (!empty.isEmpty()) {
            throw new D2RQException((empty.size() == 1 ?
                    String.format("Class map %s has", empty.get(0)) :
                    String.format("Class maps %s have", empty)) +
                    " no d2rq:PropertyBridges and no d2rq:class", D2RQException.CLASSMAP_NO_PROPERTYBRIDGES);
        }
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


}
