package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.impl.ClassMapImpl;
import de.fuberlin.wiwiss.d2rq.map.impl.MappingImpl;
import de.fuberlin.wiwiss.d2rq.map.impl.PropertyBridgeImpl;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.system.IRIResolver;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Creates a {@link MappingImpl} from a Jena model representation of a D2RQ mapping file.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class MapParser implements BiFunction<Model, String, Mapping> {
    private final static Logger LOGGER = LoggerFactory.getLogger(MapParser.class);

    /**
     * Turns a relative URI into an absolute one, by using the current directory's <tt>file:</tt> URI as a base.
     * This uses the same algorithm as Jena's Model class when reading a file.
     *
     * @param uri Any URI
     * @return An absolute URI corresponding to the input
     */
    public static String absolutizeURI(String uri) {
        if (uri == null) {
            return null;
        }
        //new org.apache.jena.n3.IRIResolver().resolve(uri);
        return IRIResolver.create().resolveToStringSilent(uri);
    }

    /**
     * Inserts the given {@code baseURI} at the beginning to every {@code d2rq:uriPattern}.
     * It is supposed that this operation will switch every {@code d2rq:uriPattern} to generate absolute IRIs.
     *
     * @param m       {@link Model}
     * @param baseURI String
     */
    public static void insertBase(Model m, String baseURI) {
        if (baseURI == null || baseURI.isEmpty()) return;
        m.listStatements(null, D2RQ.uriPattern, (RDFNode) null)
                .filterKeep(s -> s.getObject().isLiteral()
                        && !s.getString().contains(":")
                        && !s.getString().startsWith(baseURI))
                .toSet()
                .forEach(s -> {
                    String uri = baseURI + s.getString();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("{}: set uri patter: {}", s.getSubject(), uri);
                    }
                    m.add(s.getSubject(), s.getPredicate(), uri).remove(s);
                });
    }

    /**
     * Verifies that the given model satisfies the following two conditions:
     * <ul>
     * <li>it does not contain any resource with namespace {@link D2RQ#NS},
     * that is not described in the {@link D2RQ vocabulary}.</li>
     * <li>There are no intersections in {@link de.fuberlin.wiwiss.d2rq.map.MapObject MapObject}s declarations</li>
     * </ul>
     * Both requirements look very strange, but it is the original logic, that I do not dare to remove yet.
     * Useless requirements actually.
     *
     * @param model {@link Model}
     */
    public void validate(Model model) {
        checkVocabulary(model);
        checkDistinctMapObjects(model);
    }

    public static void fixLegacy(MappingImpl mapping) {
        parseClassMaps(mapping);
        parsePropertyBridges(mapping);
    }

    /**
     * Eliminates any legacy D2RQ entries.
     *
     * @param m {@link Model}
     * @see #fixLegacyAdditionalProperty(Model)
     * @see #fixLegacyReferences(Model)
     * @see #fixLegacyPropertyBridges(Model)
     */
    public static void fixLegacy(Model m) {
        fixLegacyReferences(m);
        fixLegacyAdditionalProperty(m);
        fixLegacyPropertyBridges(m);
    }

    private static void parseClassMaps(MappingImpl mapping) {
        Iterator<Resource> it = mapping.asModel().listSubjectsWithProperty(RDF.type, D2RQ.ClassMap);
        while (it.hasNext()) {
            Resource r = it.next();
            ClassMapImpl classMap = mapping.asClassMap(r);
            parseClassMap(classMap, r);
        }
    }

    /**
     * Fixes {@code d2rq:classMap} and {@code d2rq:propertyBridge}.
     * The method does not touch deprecated statements, instead it only adds equivalent new ones.
     *
     * @param m {@link Model}
     * @see <a href='http://d2rq.org/d2rq-language#deprecated-class-map-property-bridge'>12.3 d2rq:classMap and d2rq:propertyBridge properties</a>
     */
    public static void fixLegacyReferences(Model m) {
        inverse(m, LegacyD2RQ.classMap, D2RQ.clazz);
        inverse(m, LegacyD2RQ.propertyBridge, D2RQ.property);
    }

    private static void inverse(Model m, Property legacy, Property replace) {
        m.listStatements(null, legacy, (RDFNode) null)
                .filterKeep(s -> s.getObject().isResource())
                .toSet()
                .forEach(s -> m.add(s.getResource(), replace, s.getSubject()).remove(s));
    }

    /**
     * Fixes {@link LegacyD2RQ#additionalProperty d2rq:additionalProperty}
     * by turning it to {@link D2RQ#PropertyBridge d2rq:PropertyBridge}.
     *
     * @param m {@link Model}
     * @see <a href='http://d2rq.org/d2rq-language#additionalproperty_deprecated'>12.2 d2rq:additionalProperty</a>
     */
    public static void fixLegacyAdditionalProperty(Model m) {
        m.listStatements(null, LegacyD2RQ.additionalProperty, (RDFNode) null)
                .filterKeep(s -> s.getObject().isResource())
                .toSet()
                .forEach(s -> {
                    Resource classMap = s.getSubject();
                    Resource additional = s.getResource();
                    Resource p = additional.getProperty(D2RQ.propertyName).getResource();
                    RDFNode v = additional.getProperty(D2RQ.propertyValue).getObject();
                    m.createResource(null, D2RQ.PropertyBridge)
                            .addProperty(D2RQ.belongsToClassMap, classMap)
                            .addProperty(D2RQ.property, p)
                            .addProperty(D2RQ.constantValue, v);
                    classMap.removeAll(LegacyD2RQ.additionalProperty);
                });
    }

    private static void parseClassMap(ClassMapImpl classMap, Resource r) {
        MappingImpl mapping = classMap.getMapping();
        StmtIterator stmts;
        // todo: legacy:
        stmts = mapping.asModel().listStatements(null, LegacyD2RQ.classMap, r);
        while (stmts.hasNext()) {
            classMap.addClass(stmts.nextStatement().getSubject());
        }
        // todo: legacy:
        stmts = r.listProperties(LegacyD2RQ.additionalProperty);
        while (stmts.hasNext()) {
            Resource additionalProperty = stmts.nextStatement().getResource();
            PropertyBridgeImpl bridge = mapping.createPropertyBridge(r.getURI());
            bridge.setBelongsToClassMap(classMap);
            bridge.addProperty(additionalProperty.getProperty(D2RQ.propertyName).getResource().getURI());
            bridge.setConstantValue(additionalProperty.getProperty(D2RQ.propertyValue).getObject());
            classMap.addPropertyBridge(bridge);
        }
    }

    /**
     * Fixes {@link LegacyD2RQ#ObjectPropertyBridge d2rq:ObjectPropertyBridge} and
     * {@link LegacyD2RQ#DataPropertyBridge d2rq:DataPropertyBridge}.
     * Also fixes {@link D2RQ#pattern d2rq:pattern} and {@link D2RQ#column d2rq:column} for object properties
     * according to preserved logic.
     *
     * @param m {@link Model}
     * @see <a href='http://d2rq.org/d2rq-language#datatype-object'>12.1 d2rq:DatatypePropertyBridge and d2rq:ObjectPropertyBridge</a>
     */
    public static void fixLegacyPropertyBridges(Model m) {
        // pattern -> uriPattern
        // column -> uriColumn
        Iter.peek(m.listResourcesWithProperty(RDF.type, LegacyD2RQ.ObjectPropertyBridge), r -> {
            replace(r, D2RQ.column, D2RQ.uriColumn);
            replace(r, D2RQ.pattern, D2RQ.uriPattern);
        }).andThen(m.listResourcesWithProperty(RDF.type, LegacyD2RQ.DataPropertyBridge))
                .toSet()
                .forEach(r -> m.add(r, RDF.type, D2RQ.PropertyBridge)
                        .remove(r, RDF.type, LegacyD2RQ.DataPropertyBridge)
                        .remove(r, RDF.type, LegacyD2RQ.ObjectPropertyBridge));
    }

    private static void replace(Resource resource, Property legacy, Property replace) {
        Model m = resource.getModel();
        resource.listProperties(legacy)
                .toSet()
                .forEach(s -> m.add(s.getSubject(), replace, s.getObject()).remove(s));
    }

    private static void parsePropertyBridges(MappingImpl mapping) {
        StmtIterator stmts = mapping.asModel().listStatements(null, D2RQ.belongsToClassMap, (RDFNode) null);
        while (stmts.hasNext()) {
            Resource r = stmts.nextStatement().getSubject();
            PropertyBridgeImpl bridge = mapping.asPropertyBridge(r);
            parsePropertyBridge(bridge, r);
        }
    }

    private static void parsePropertyBridge(PropertyBridgeImpl bridge, Resource r) {
        Model model = bridge.getModel();
        StmtIterator stmts;
        stmts = r.listProperties(D2RQ.column);
        while (stmts.hasNext()) {
            String column = stmts.nextStatement().getString();
            //noinspection EqualsBetweenInconvertibleTypes
            if (LegacyD2RQ.ObjectPropertyBridge.equals(r.getProperty(RDF.type))) {
                // todo: Legacy
                bridge.setURIColumn(column);
            }
        }
        stmts = r.listProperties(D2RQ.pattern);
        while (stmts.hasNext()) {
            String pattern = stmts.nextStatement().getString();
            //noinspection EqualsBetweenInconvertibleTypes
            if (LegacyD2RQ.ObjectPropertyBridge.equals(r.getProperty(RDF.type))) {
                // todo: Legacy
                bridge.setURIPattern(pattern);
            }
        }
        // todo: legacy:
        stmts = model.listStatements(null, LegacyD2RQ.propertyBridge, r);
        while (stmts.hasNext()) {
            bridge.addProperty(stmts.nextStatement().getSubject().getURI());
        }
    }

    /**
     * @param model {@link Model}
     * @throws D2RQException in case some external resources with D2RQ namespace found
     */
    public static void checkVocabulary(Model model) throws D2RQException {
        new VocabularySummarizer(D2RQ.class).assertNoUndefinedTerms(model,
                D2RQException.MAPPING_UNKNOWN_D2RQ_PROPERTY,
                D2RQException.MAPPING_UNKNOWN_D2RQ_CLASS);
    }

    /**
     * @param model {@link Model}
     * @throws D2RQException in case some MapObjects have different roles
     */
    public static void checkDistinctMapObjects(Model model) throws D2RQException {
        ensureAllDistinct(model, D2RQ.Database, D2RQ.ClassMap, D2RQ.PropertyBridge, D2RQ.TranslationTable, D2RQ.Translation);
    }

    private static void ensureAllDistinct(Model model, Resource... distinctClasses) {
        Collection<Resource> classes = Arrays.asList(distinctClasses);
        ResIterator it = model.listSubjects();
        while (it.hasNext()) {
            Resource resource = it.nextResource();
            Resource matchingType = null;
            StmtIterator typeIt = resource.listProperties(RDF.type);
            while (typeIt.hasNext()) {
                Resource type = typeIt.nextStatement().getResource();
                if (!classes.contains(type)) continue;
                if (matchingType == null) {
                    matchingType = type;
                } else {
                    throw new D2RQException("Name " + PrettyPrinter.toString(resource) + " cannot be both a "
                            + PrettyPrinter.toString(matchingType) + " and a " + PrettyPrinter.toString(type),
                            D2RQException.MAPPING_TYPECONFLICT);
                }
            }
        }
    }

    @Override
    public Mapping apply(Model model, String base) {
        String uri = absolutizeURI(base);
        checkVocabulary(model);
        checkDistinctMapObjects(model);
        insertBase(model, uri);
        MappingImpl res = new MappingImpl(model);
        fixLegacy(res);
        LOGGER.info("Done reading D2RQ map with {} databases and {} class maps",
                res.listDatabases().count(), res.listClassMaps().count());
        return res;
    }

    /**
     * Vocabulary for deprecated things
     */
    public static class LegacyD2RQ extends D2RQ {
        public static final Property ObjectPropertyBridge = property("ObjectPropertyBridge");
        public static final Property DataPropertyBridge = property("DataPropertyBridge");
        public static final Property additionalProperty = D2RQ.property("additionalProperty");
        public static final Property classMap = D2RQ.property("classMap");
        public static final Property propertyBridge = D2RQ.property("propertyBridge");
    }
}