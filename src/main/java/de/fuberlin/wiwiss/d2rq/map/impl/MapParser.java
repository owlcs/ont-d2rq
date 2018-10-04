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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

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
                D2RQ.TranslationTable, D2RQ.Translation});
        this.mapping = new MappingImpl(this.model);
        try {
            parseConfiguration();
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

    private void ensureAllDistinct(Resource[] distinctClasses) {
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
                            + PrettyPrinter.toString(matchingType) + " and a " + PrettyPrinter.toString(type),
                            D2RQException.MAPPING_TYPECONFLICT);
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

    private void parseClassMaps() {
        Iterator<Resource> it = this.model.listSubjectsWithProperty(RDF.type, D2RQ.ClassMap);
        while (it.hasNext()) {
            Resource r = it.next();
            ClassMapImpl classMap = this.mapping.asClassMap(r);
            parseClassMap(classMap, r);
            parseResourceMap(classMap, r);
        }
    }

    private void parseResourceMap(ResourceMap resourceMap, Resource r) {
        r.listProperties(D2RQ.uriPattern)
                .toSet()
                .forEach(s -> resourceMap.setURIPattern(ensureIsAbsolute(s.getString())));
    }

    private void parseClassMap(ClassMapImpl classMap, Resource r) {
        StmtIterator stmts;
        // todo: legacy:
        stmts = this.model.listStatements(null, D2RQ.classMap, r);
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

    private void parsePropertyBridges() {
        StmtIterator stmts = this.model.listStatements(null, D2RQ.belongsToClassMap, (RDFNode) null);
        while (stmts.hasNext()) {
            Resource r = stmts.nextStatement().getSubject();
            PropertyBridgeImpl bridge = mapping.asPropertyBridge(r);
            parseResourceMap(bridge, r);
            parsePropertyBridge(bridge, r);
        }
    }

    private void parsePropertyBridge(PropertyBridgeImpl bridge, Resource r) {
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
        stmts = this.model.listStatements(null, D2RQ.propertyBridge, r);
        while (stmts.hasNext()) {
            bridge.addProperty(stmts.nextStatement().getSubject().getURI());
        }
    }

    private void parseDownloadMaps() {
        Iterator<Resource> it = this.model.listResourcesWithProperty(RDF.type, D2RQ.DownloadMap);
        while (it.hasNext()) {
            Resource r = it.next();
            DownloadMapImpl dm = mapping.asDownloadMap(r);
            parseResourceMap(dm, r);
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