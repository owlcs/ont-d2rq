package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.system.IRIResolver;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static void appendBase(Model m, String baseURI) {
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

    public static void fixLegacy(MappingImpl mapping) {
        parseClassMaps(mapping);
        parsePropertyBridges(mapping);
    }

    private static void parseClassMaps(MappingImpl mapping) {
        Iterator<Resource> it = mapping.asModel().listSubjectsWithProperty(RDF.type, D2RQ.ClassMap);
        while (it.hasNext()) {
            Resource r = it.next();
            ClassMapImpl classMap = mapping.asClassMap(r);
            parseClassMap(classMap, r);
        }
    }

    private static void parseClassMap(ClassMapImpl classMap, Resource r) {
        MappingImpl mapping = classMap.getMapping();
        StmtIterator stmts;
        // todo: legacy:
        stmts = mapping.asModel().listStatements(null, D2RQ.classMap, r);
        while (stmts.hasNext()) {
            classMap.addClass(stmts.nextStatement().getSubject());
        }
        // todo: legacy:
        stmts = r.listProperties(D2RQ.additionalProperty);
        while (stmts.hasNext()) {
            Resource additionalProperty = stmts.nextStatement().getResource();
            PropertyBridgeImpl bridge = mapping.createPropertyBridge(r.getURI());
            bridge.setBelongsToClassMap(classMap);
            bridge.addProperty(additionalProperty.getProperty(D2RQ.propertyName).getResource().getURI());
            bridge.setConstantValue(additionalProperty.getProperty(D2RQ.propertyValue).getObject());
            classMap.addPropertyBridge(bridge);
        }
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
            if (D2RQ.ObjectPropertyBridge.equals(r.getProperty(RDF.type))) {
                // todo: Legacy
                bridge.setURIColumn(column);
            }
        }
        stmts = r.listProperties(D2RQ.pattern);
        while (stmts.hasNext()) {
            String pattern = stmts.nextStatement().getString();
            //noinspection EqualsBetweenInconvertibleTypes
            if (D2RQ.ObjectPropertyBridge.equals(r.getProperty(RDF.type))) {
                // todo: Legacy
                bridge.setURIPattern(pattern);
            }
        }
        // todo: legacy:
        stmts = model.listStatements(null, D2RQ.propertyBridge, r);
        while (stmts.hasNext()) {
            bridge.addProperty(stmts.nextStatement().getSubject().getURI());
        }
    }

    public static void checkVocabulary(Model model) throws D2RQException {
        new VocabularySummarizer(D2RQ.class).assertNoUndefinedTerms(model,
                D2RQException.MAPPING_UNKNOWN_D2RQ_PROPERTY,
                D2RQException.MAPPING_UNKNOWN_D2RQ_CLASS);
    }

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
        appendBase(model, uri);
        MappingImpl res = new MappingImpl(model);
        fixLegacy(res);
        LOGGER.info("Done reading D2RQ map with {} databases and {} class maps",
                res.listDatabases().count(), res.listClassMaps().count());
        return res;
    }
}