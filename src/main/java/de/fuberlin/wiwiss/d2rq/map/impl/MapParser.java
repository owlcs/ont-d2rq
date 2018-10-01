package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.system.IRIResolver;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Creates a {@link MappingImpl} from a Jena model representation of a D2RQ mapping file.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@Deprecated //TODO: scheduled to remove
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

    private Model model;
    private String baseURI;
    private MappingImpl mapping;

    /**
     * Constructs a new MapParser from a Jena model containing the RDF statements from a D2RQ mapping file.
     *
     * @param mapModel a Jena model containing the RDF statements from a D2RQ mapping file
     * @param baseURI  used for relative URI patterns. Could be null.
     */
    public MapParser(Model mapModel, String baseURI) {
        this.model = mapModel;
        this.baseURI = absolutizeURI(baseURI);
    }

    /**
     * TODO: better to rewrite this initialization.
     * Starts the parsing process. Must be called before results can be retrieved
     * from the getter methods.
     *
     * @return {@link MappingImpl}
     */
    public MappingImpl parse() {
        if (this.mapping != null) {
            return mapping;
        }
        new VocabularySummarizer(D2RQ.class).assertNoUndefinedTerms(model,
                D2RQException.MAPPING_UNKNOWN_D2RQ_PROPERTY,
                D2RQException.MAPPING_UNKNOWN_D2RQ_CLASS);
        ensureAllDistinct(new Resource[]{D2RQ.Database, D2RQ.ClassMap, D2RQ.PropertyBridge,
                D2RQ.TranslationTable, D2RQ.Translation}, D2RQException.MAPPING_TYPECONFLICT);
        this.mapping = new MappingImpl(this.model);
        try {
            parseConfiguration();
            parseTranslationTables();
            parseClassMaps();
            parsePropertyBridges();
            parseDownloadMaps();
            LOGGER.info("Done reading D2RQ map with {} databases and {} class maps",
                    mapping.listDatabases().count(), mapping.listClassMaps().count());
            return this.mapping;
        } catch (LiteralRequiredException ex) {
            throw new D2RQException("Expected literal, found URI resource instead: " + ex.getMessage(),
                    D2RQException.MAPPING_RESOURCE_INSTEADOF_LITERAL);
        } catch (ResourceRequiredException ex) {
            throw new D2RQException("Expected URI, found literal instead: " + ex.getMessage(),
                    D2RQException.MAPPING_LITERAL_INSTEADOF_RESOURCE);
        }
    }

    private void ensureAllDistinct(Resource[] distinctClasses, int errorCode) {
        Collection<Resource> classes = Arrays.asList(distinctClasses);
        ResIterator it = this.model.listSubjects();
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
                            + PrettyPrinter.toString(matchingType) + " and a " + PrettyPrinter.toString(type), errorCode);
                }
            }
        }
    }

    private void parseConfiguration() {
        Iterator<Resource> it = this.model.listSubjectsWithProperty(RDF.type, D2RQ.Configuration);
        if (it.hasNext()) {
            Resource configResource = it.next();
            ConfigurationImpl configuration = this.mapping.createConfiguration(configResource);
            StmtIterator stmts = configResource.listProperties(D2RQ.serveVocabulary);
            while (stmts.hasNext()) {
                configuration.setServeVocabulary(stmts.nextStatement().getBoolean());
            }
            stmts = configResource.listProperties(D2RQ.useAllOptimizations);
            while (stmts.hasNext()) {
                configuration.setUseAllOptimizations(stmts.nextStatement().getBoolean());
            }
            this.mapping.setConfiguration(configuration);

            if (it.hasNext())
                throw new D2RQException("Only one configuration block is allowed");
        }
    }

    private void parseTranslationTables() {
        Set<Resource> translationTableResources = new HashSet<>();
        Iterator<? extends Resource> it = this.model.listSubjectsWithProperty(RDF.type, D2RQ.TranslationTable);
        while (it.hasNext()) {
            translationTableResources.add(it.next());
        }
        StmtIterator stmts;
        stmts = this.model.listStatements(null, D2RQ.translateWith, (Resource) null);
        while (stmts.hasNext()) {
            translationTableResources.add(stmts.nextStatement().getResource());
        }
        stmts = this.model.listStatements(null, D2RQ.translation, (RDFNode) null);
        while (stmts.hasNext()) {
            translationTableResources.add(stmts.nextStatement().getSubject());
        }
        stmts = this.model.listStatements(null, D2RQ.javaClass, (RDFNode) null);
        while (stmts.hasNext()) {
            translationTableResources.add(stmts.nextStatement().getSubject());
        }
        stmts = this.model.listStatements(null, D2RQ.href, (RDFNode) null);
        while (stmts.hasNext()) {
            translationTableResources.add(stmts.nextStatement().getSubject());
        }
        it = translationTableResources.iterator();
        while (it.hasNext()) {
            Resource r = it.next();
            TranslationTableImpl table = this.mapping.asTranslationTable(r);
            parseTranslationTable(table, r);
            this.mapping.addTranslationTable(table);
        }
    }

    private void parseTranslationTable(TranslationTableImpl table, Resource r) {
        StmtIterator stmts;
        stmts = r.listProperties(D2RQ.href);
        while (stmts.hasNext()) {
            table.setHref(stmts.nextStatement().getResource().getURI());
        }
        stmts = r.listProperties(D2RQ.javaClass);
        while (stmts.hasNext()) {
            table.setJavaClass(stmts.nextStatement().getString());
        }
        stmts = r.listProperties(D2RQ.translation);
        while (stmts.hasNext()) {
            Resource translation = stmts.nextStatement().getResource();
            String db = translation.getProperty(D2RQ.databaseValue).getString();
            Statement stmt = translation.getProperty(D2RQ.rdfValue);
            String rdf = stmt.getObject().isLiteral() ? stmt.getString() : stmt.getResource().getURI();
            table.addTranslation(db, rdf);
        }
    }

    private void parseClassMaps() {
        Iterator<Resource> it = this.model.listSubjectsWithProperty(RDF.type, D2RQ.ClassMap);
        while (it.hasNext()) {
            Resource r = it.next();
            ClassMapImpl classMap = this.mapping.createClassMap(r);
            parseClassMap(classMap, r);
            parseResourceMap(classMap, r);
            this.mapping.addClassMap(classMap);
        }
    }

    private void parseResourceMap(ResourceMap resourceMap, Resource r) {
        StmtIterator stmts;
        r.listProperties(D2RQ.uriPattern).toSet().forEach(s -> resourceMap.setURIPattern(ensureIsAbsolute(s.getString())));
        stmts = r.listProperties(D2RQ.translateWith);
        while (stmts.hasNext()) {
            resourceMap.setTranslateWith(this.mapping.findTranslationTable(stmts.nextStatement().getResource()));
        }
    }

    private void parseClassMap(ClassMapImpl classMap, Resource r) {
        StmtIterator stmts;
        stmts = r.listProperties(D2RQ.clazz);
        while (stmts.hasNext()) {
            classMap.addClass(stmts.nextStatement().getResource());
        }
        stmts = this.model.listStatements(null, D2RQ.classMap, r);
        while (stmts.hasNext()) {
            classMap.addClass(stmts.nextStatement().getSubject());
        }
        stmts = r.listProperties(D2RQ.additionalProperty);
        while (stmts.hasNext()) {
            Resource additionalProperty = stmts.nextStatement().getResource();
            PropertyBridgeImpl bridge = mapping.createPropertyBridge(r);
            bridge.setBelongsToClassMap(classMap);
            bridge.addProperty(additionalProperty.getProperty(D2RQ.propertyName).getResource());
            bridge.setConstantValue(additionalProperty.getProperty(D2RQ.propertyValue).getObject());
            classMap.addPropertyBridge(bridge);
        }
        stmts = r.listProperties(D2RQ.classDefinitionLabel);
        while (stmts.hasNext()) {
            classMap.addDefinitionLabel(stmts.nextStatement().getLiteral());
        }
        stmts = r.listProperties(D2RQ.classDefinitionComment);
        while (stmts.hasNext()) {
            classMap.addDefinitionComment(stmts.nextStatement().getLiteral());
        }
        stmts = r.listProperties(D2RQ.additionalClassDefinitionProperty);
        while (stmts.hasNext()) {
            Resource additionalProperty = stmts.nextStatement().getResource();
            classMap.addDefinitionProperty(additionalProperty);
        }
    }

    private void parsePropertyBridges() {
        StmtIterator stmts = this.model.listStatements(null, D2RQ.belongsToClassMap, (RDFNode) null);
        while (stmts.hasNext()) {
            Statement stmt = stmts.nextStatement();
            ClassMapImpl classMap = this.mapping.findClassMap(stmt.getResource());
            Resource r = stmt.getSubject();
            PropertyBridgeImpl bridge = mapping.createPropertyBridge(r);
            bridge.setBelongsToClassMap(classMap);
            parseResourceMap(bridge, r);
            parsePropertyBridge(bridge, r);
            classMap.addPropertyBridge(bridge);
        }
    }

    private void parsePropertyBridge(PropertyBridgeImpl bridge, Resource r) {
        StmtIterator stmts;
        stmts = r.listProperties(D2RQ.column);
        while (stmts.hasNext()) {
            //noinspection EqualsBetweenInconvertibleTypes
            if (D2RQ.ObjectPropertyBridge.equals(r.getProperty(RDF.type))) {
                // Legacy
                bridge.setURIColumn(stmts.nextStatement().getString());
            } else {
                bridge.setColumn(stmts.nextStatement().getString());
            }
        }
        stmts = r.listProperties(D2RQ.pattern);
        while (stmts.hasNext()) {
            //noinspection EqualsBetweenInconvertibleTypes
            if (D2RQ.ObjectPropertyBridge.equals(r.getProperty(RDF.type))) {
                // Legacy
                bridge.setURIPattern(stmts.nextStatement().getString());
            } else {
                bridge.setPattern(stmts.nextStatement().getString());
            }
        }
        stmts = r.listProperties(D2RQ.sqlExpression);
        while (stmts.hasNext()) {
            bridge.setSQLExpression(stmts.nextStatement().getString());
        }
        stmts = r.listProperties(D2RQ.lang);
        while (stmts.hasNext()) {
            bridge.setLang(stmts.nextStatement().getString());
        }
        stmts = r.listProperties(D2RQ.datatype);
        while (stmts.hasNext()) {
            bridge.setDatatype(stmts.nextStatement().getResource().getURI());
        }
        stmts = r.listProperties(D2RQ.refersToClassMap);
        while (stmts.hasNext()) {
            Resource classMapResource = stmts.nextStatement().getResource();
            bridge.setRefersToClassMap(this.mapping.findClassMap(classMapResource));
        }
        stmts = r.listProperties(D2RQ.dynamicProperty);
        while (stmts.hasNext()) {
            bridge.addDynamicProperty(stmts.next().getString());
        }
        stmts = r.listProperties(D2RQ.property);
        while (stmts.hasNext()) {
            bridge.addProperty(stmts.nextStatement().getResource());
        }
        stmts = this.model.listStatements(null, D2RQ.propertyBridge, r);
        while (stmts.hasNext()) {
            bridge.addProperty(stmts.nextStatement().getSubject());
        }
        stmts = r.listProperties(D2RQ.propertyDefinitionLabel);
        while (stmts.hasNext()) {
            bridge.addDefinitionLabel(stmts.nextStatement().getLiteral());
        }
        stmts = r.listProperties(D2RQ.propertyDefinitionComment);
        while (stmts.hasNext()) {
            bridge.addDefinitionComment(stmts.nextStatement().getLiteral());
        }
        stmts = r.listProperties(D2RQ.additionalPropertyDefinitionProperty);
        while (stmts.hasNext()) {
            Resource additionalProperty = stmts.nextStatement().getResource();
            bridge.addDefinitionProperty(additionalProperty);
        }
        stmts = r.listProperties(D2RQ.limit);
        while (stmts.hasNext()) {
            bridge.setLimit(stmts.nextStatement().getInt());
        }
        stmts = r.listProperties(D2RQ.limitInverse);
        while (stmts.hasNext()) {
            bridge.setLimitInverse(stmts.nextStatement().getInt());
        }
        stmts = r.listProperties(D2RQ.orderDesc);
        while (stmts.hasNext()) {
            bridge.setOrder(stmts.nextStatement().getString(), true);
        }
        stmts = r.listProperties(D2RQ.orderAsc);
        while (stmts.hasNext()) {
            bridge.setOrder(stmts.nextStatement().getString(), false);
        }
    }

    private void parseDownloadMaps() {
        Iterator<Resource> it = this.model.listSubjectsWithProperty(RDF.type, D2RQ.DownloadMap);
        while (it.hasNext()) {
            Resource downloadMapResource = it.next();
            DownloadMapImpl downloadMap = mapping.createDownloadMap(downloadMapResource);
            parseResourceMap(downloadMap, downloadMapResource);
            parseDownloadMap(downloadMap, downloadMapResource);
            mapping.addDownloadMap(downloadMap);
        }
    }

    private void parseDownloadMap(DownloadMapImpl dm, Resource r) {
        StmtIterator stmts;
        stmts = r.listProperties(D2RQ.belongsToClassMap);
        while (stmts.hasNext()) {
            dm.setBelongsToClassMap(mapping.findClassMap(stmts.nextStatement().getResource()));
        }
        stmts = r.listProperties(D2RQ.contentDownloadColumn);
        while (stmts.hasNext()) {
            dm.setContentDownloadColumn(stmts.nextStatement().getString());
        }
        stmts = r.listProperties(D2RQ.mediaType);
        while (stmts.hasNext()) {
            dm.setMediaType(stmts.nextStatement().getString());
        }
    }

    // TODO: I guess this should be done at map compile time
    private String ensureIsAbsolute(String uriPattern) {
        if (baseURI != null && !uriPattern.contains(":")) {
            return baseURI + uriPattern;
        }
        return uriPattern;
    }
}