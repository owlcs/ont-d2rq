package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.IRIResolver;
import org.apache.jena.util.iterator.WrappedIterator;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A helper to perform various utility operations on a model that contains D2RQ instructions.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class MapParser {
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
     * @param model   {@link Model}
     * @param baseURI String
     */
    public static void insertBase(Model model, String baseURI) {
        if (baseURI == null || baseURI.isEmpty()) return;
        model.listStatements(null, D2RQ.uriPattern, (RDFNode) null)
                .filterKeep(s -> s.getObject().isLiteral()
                        && !s.getString().contains(":")
                        && !s.getString().startsWith(baseURI))
                .toSet()
                .forEach(s -> {
                    String uri = baseURI + s.getString();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("{}: set uri patter: {}", s.getSubject(), uri);
                    }
                    model.add(s.getSubject(), s.getPredicate(), uri).remove(s);
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
    public static void validate(Model model) {
        checkVocabulary(model);
        checkDistinctMapObjects(model);
    }

    /**
     * Eliminates any legacy D2RQ entries.
     *
     * @param model {@link Model}
     * @see #fixLegacyAdditionalProperty(Model)
     * @see #fixLegacyReferences(Model)
     * @see #fixLegacyPropertyBridges(Model)
     */
    public static void fixLegacy(Model model) {
        fixLegacyReferences(model);
        fixLegacyAdditionalProperty(model);
        fixLegacyPropertyBridges(model);
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

    private static void inverse(Model m, Property legacy, Property replacement) {
        m.listStatements(null, legacy, (RDFNode) null)
                .filterKeep(s -> s.getObject().isResource())
                .toSet()
                .forEach(s -> m.add(s.getResource(), replacement, s.getSubject()).remove(s));
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

    private static void replace(Resource resource, Property legacy, Property replacement) {
        Model m = resource.getModel();
        resource.listProperties(legacy)
                .toSet()
                .forEach(s -> m.add(s.getSubject(), replacement, s.getObject()).remove(s));
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

    private static void ensureAllDistinct(Model model, Resource... types) {
        Set<Resource> set = new HashSet<>(Arrays.asList(types));
        Set<Resource> res = Iter.flatMap(WrappedIterator.create(set.iterator()),
                t -> model.listResourcesWithProperty(RDF.type, t))
                .filterKeep(r -> r.listProperties(RDF.type)
                        .filterKeep(s -> s.getObject().isResource()
                                && set.contains(s.getResource())).toSet().size() > 1)
                .toSet();
        if (res.isEmpty()) return;
        String prefix;
        if (res.size() == 1) {
            prefix = String.format("Resource %s has ", PrettyPrinter.toString(res.iterator().next()));
        } else {
            prefix = String.format("Resources %s have ", PrettyPrinter.toString(res));
        }
        throw new D2RQException(prefix + " intersection in the allowed types (" + PrettyPrinter.toString(set) + ")",
                D2RQException.MAPPING_TYPECONFLICT);
    }

    /**
     * Vocabulary for deprecated D2RQ things.
     */
    public static class LegacyD2RQ extends D2RQ {
        public static final Resource ObjectPropertyBridge = resource("ObjectPropertyBridge");
        public static final Resource DataPropertyBridge = resource("DataPropertyBridge");
        public static final Property additionalProperty = D2RQ.property("additionalProperty");
        public static final Property classMap = D2RQ.property("classMap");
        public static final Property propertyBridge = D2RQ.property("propertyBridge");
    }
}