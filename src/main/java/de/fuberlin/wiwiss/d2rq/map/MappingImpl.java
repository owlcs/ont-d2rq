package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.ClassMapLister;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.JDBC;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    private final Map<Resource, Database> databases = new HashMap<>();
    private final Map<Resource, ClassMap> classMaps = new HashMap<>();
    private final Map<Resource, TranslationTable> translationTables = new HashMap<>();
    private final Map<Resource, DownloadMap> downloadMaps = new HashMap<>();
    private final Model model;

    private Configuration configuration = new Configuration();
    private Collection<TripleRelation> compiledPropertyBridges;
    private PrefixMapping prefixes;
    private Model vocabularyModel;
    // cache for dataGraph
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
    public Model getMappingModel() {
        return model;
    }

    @Override
    public Model getVocabularyModel() {
        return vocabularyModel == null ? vocabularyModel = MappingTransform.getModelBuilder().build(this) : vocabularyModel;
    }

    @Override
    public Model getDataModel() {
        return ModelFactory.createModelForGraph(getDataGraph());
    }

    @Override
    public GraphD2RQ getDataGraph() {
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
        return prefixes == null ? prefixes = Prefixes.createSchemaPrefixes(model) : prefixes;
    }

    @Override
    public void validate() throws D2RQException {
        if (this.databases.isEmpty()) {
            throw new D2RQException("No d2rq:Database defined in the mapping", D2RQException.MAPPING_NO_DATABASE);
        }
        for (Database db : databases.values()) {
            db.validate();
        }
        for (TranslationTable table : translationTables.values()) {
            table.validate();
        }
        List<ClassMap> classMapsWithoutProperties = new ArrayList<>(classMaps.values());
        for (ClassMap classMap : classMaps.values()) {
            classMap.validate();    // Also validates attached bridges
            if (classMap.hasProperties()) {
                classMapsWithoutProperties.remove(classMap);
            }
            for (PropertyBridge bridge : classMap.propertyBridges()) {
                if (bridge.refersToClassMap() != null) {
                    classMapsWithoutProperties.remove(bridge.refersToClassMap());
                }
            }
        }
        if (!classMapsWithoutProperties.isEmpty()) {
            throw new D2RQException(classMapsWithoutProperties.iterator().next().toString() +
                    " has no d2rq:PropertyBridges and no d2rq:class", D2RQException.CLASSMAP_NO_PROPERTYBRIDGES);
        }
        for (DownloadMap dlm : downloadMaps.values()) {
            dlm.validate();
        }
        for (TripleRelation bridge : compiledPropertyBridges()) {
            new AttributeTypeValidator(bridge).validate();
        }
    }

    /**
     * Connects all databases. This is done automatically if needed.
     * The method can be used to test the connections earlier.
     *
     * @throws D2RQException on connection failure
     */
    @Override
    public void connect() {
        if (connected) return;
        connected = true;
        for (Database db : databases()) {
            db.connectedDB().connection();
        }
        validate();
    }

    @Override
    public void close() {
        for (Database db : databases()) {
            db.connectedDB().close();
        }
    }

    @Override
    public void addDatabase(Database database) {
        this.databases.put(database.resource(), database);
    }

    @Override
    public Collection<Database> databases() {
        return this.databases.values();
    }

    @Override
    public Database database(Resource name) {
        return databases.get(name);
    }

    @Override
    public Configuration configuration() {
        return this.configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void addClassMap(ClassMap classMap) {
        this.classMaps.put(classMap.resource(), classMap);
    }

    @Override
    public Collection<Resource> classMapResources() {
        return this.classMaps.keySet();
    }

    @Override
    public Stream<ClassMap> classMaps() {
        return classMaps.values().stream();
    }

    @Override
    public ClassMap classMap(Resource name) {
        return this.classMaps.get(name);
    }

    public void addTranslationTable(TranslationTable table) {
        this.translationTables.put(table.resource(), table);
    }

    @Override
    public TranslationTable translationTable(Resource name) {
        return this.translationTables.get(name);
    }

    public void addDownloadMap(DownloadMap downloadMap) {
        downloadMaps.put(downloadMap.resource(), downloadMap);
    }

    @Override
    public Collection<Resource> downloadMapResources() {
        return downloadMaps.keySet();
    }

    @Override
    public DownloadMap downloadMap(Resource name) {
        return downloadMaps.get(name);
    }

    /**
     * @return A collection of {@link TripleRelation}s corresponding to each
     * of the property bridges
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
        for (ClassMap classMap : classMaps.values()) {
            this.compiledPropertyBridges.addAll(classMap.compiledPropertyBridges());
        }
        LOGGER.info("Compiled {} property bridges", compiledPropertyBridges.size());
        if (LOGGER.isDebugEnabled()) {
            compiledPropertyBridges.stream().map(String::valueOf).forEach(LOGGER::debug);
        }
    }

    private class AttributeTypeValidator {
        private final Relation relation;

        AttributeTypeValidator(TripleRelation relation) {
            this.relation = relation.baseRelation();
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

    /**
     * Just a set of constants and auxiliary methods to work with prefixes (mapping + schema)
     * It is used by {@link de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator} and {@link MappingImpl}.
     * <p>
     * Created by szuev on 22.02.2017.
     */
    public static class Prefixes {
        public static final String VOCAB_PREFIX = "vocab";
        public static final String MAP_PREFIX = "map";
        public static final String D2RQ_PREFIX = "d2rq";
        public static final String JDBC_PREFIX = "jdbc";

        private static final PrefixMapping COMMON = PrefixMapping.Factory.create()
                .setNsPrefix("rdf", RDF.getURI())
                .setNsPrefix("rdfs", RDFS.getURI())
                .setNsPrefix("xsd", XSD.getURI()).lock();

        public static final PrefixMapping MAPPING = PrefixMapping.Factory.create()
                .withDefaultMappings(COMMON)
                .setNsPrefix(D2RQ_PREFIX, D2RQ.getURI())
                .setNsPrefix(JDBC_PREFIX, JDBC.getURI()).lock();

        public static final PrefixMapping SCHEMA = PrefixMapping.Factory.create()
                .withDefaultMappings(COMMON)
                .setNsPrefix("owl", OWL.getURI()).lock();

        private static PrefixMapping createSchemaPrefixes(Model mapping) {
            PrefixMapping res = PrefixMapping.Factory.create().withDefaultMappings(Prefixes.SCHEMA);
            Map<String, String> add = mapping.getNsPrefixMap();
            Map<String, String> ignore = calcMapSpecificPrefixes(mapping);
            ignore.forEach(add::remove);
            res.setNsPrefixes(add);
            return res;
        }

        private static Map<String, String> calcMapSpecificPrefixes(Model mapping) {
            Map<String, String> res = new HashMap<>();
            Stream.of(D2RQ_PREFIX, JDBC_PREFIX).forEach(p -> {
                String ns = mapping.getNsPrefixURI(p);
                if (ns == null) return;
                res.put(p, ns);
            });
            mapping.listSubjectsWithProperty(RDF.type, D2RQ.Database).forEachRemaining(r -> {
                String ns = r.getNameSpace();
                if (ns == null) return;
                String p = mapping.getNsURIPrefix(ns);
                if (p == null) return;
                res.put(p, ns);
            });
            return res;
        }
    }
}
