package de.fuberlin.wiwiss.d2rq.parser;

import java.util.Collection;

import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.PropertyBridge;
import de.fuberlin.wiwiss.d2rq.map.ResourceMap;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;

/**
 * todo: move it somewhere.
 * To convert {@link Mapping} -> {@link Model}
 * <p>
 * Currently there is only one model builder ({@link OWLModelBuilder}), which makes simple OWL2 DL ontology.
 * <p>
 * Created by @szuev on 19.02.2017.
 */
public class MappingTransform {
    private static ModelBuilder builder = new OWLModelBuilder();

    public static ModelBuilder getBuilder() {
        return builder;
    }

    public static void setBuilder(ModelBuilder b) {
        builder = b;
    }

    interface ModelBuilder {
        Model build(Mapping mapping);
    }

    public static class OWLModelBuilder implements ModelBuilder {
        public static final PrefixMapping OWL2_PREFIXES = PrefixMapping.Factory.create()
                .setNsPrefix("rdfs", RDFS.getURI())
                .setNsPrefix("rdf", RDF.getURI())
                .setNsPrefix("owl", OWL.getURI())
                .setNsPrefix("xsd", XSD.getURI())
                .lock();

        @Override
        public Model build(Mapping mapping) {
            Model model = ModelFactory.createDefaultModel();

            model.setNsPrefixes(mapping.getPrefixMapping().withDefaultMappings(OWL2_PREFIXES));

            Resource anon = model.createResource();
            anon.addProperty(RDF.type, OWL.Ontology);
            mapping.databases().forEach(d -> anon.addLiteral(RDFS.comment, "Database: <" + d.getJDBCDSN() + ">"));
            Collection<Resource> classes = mapping.classMapResources();
            /*List<String> uris = classes.stream().filter(Resource::isURIResource).map(Resource::getNameSpace).distinct().collect(Collectors.toList());
            if (uris.size() == 1) {
                model.setNsPrefix(SCHEMA_PREFIX, uris.get(0));
            } else {
                for (int i = 0; i < uris.size(); i++) {
                    model.setNsPrefix(String.format(SCHEMA_PREFIX_TEMPLATE, i + 1), uris.get(i));
                }
            }*/
            classes.stream().map(mapping::classMap).forEach(classMap -> {
                for (Resource clazz : classMap.getClasses()) {
                    addDefinitions(model, classMap, clazz);
                }
                for (PropertyBridge bridge : classMap.propertyBridges()) {
                    for (Resource property : bridge.properties()) {
                        addDefinitions(model, bridge, property);
                    }
                    // TODO: What to do about dynamic properties?
                }
            });
            return model;
        }

        private void addDefinitions(Model model, ResourceMap map, Resource targetResource) {
            if (ClassMap.class.isInstance(map)) {
                model.add(targetResource, RDF.type, OWL.Class);
            } else if (PropertyBridge.class.isInstance(map)) {
                PropertyBridge prop = (PropertyBridge) map;
                ClassMap range = prop.getRefersToClassMap();
                ClassMap domain = prop.getBelongsToClassMap();
                String column = prop.getColumn();
                if (targetResource.equals(RDFS.label)) {
                    System.err.println();
                }
                if (domain != null) {
                    if (range != null) { // if range != null -> object property (foreign key)
                        model.add(targetResource, RDF.type, OWL.ObjectProperty);
                        for (Resource c : range.getClasses()) {
                            model.add(targetResource, RDFS.range, c);
                        }
                    }
                    if (column != null) { // if it has column -> data property (built-in properties has no column)
                        model.add(targetResource, RDF.type, OWL.DatatypeProperty);
                        String dt = prop.getDatatype() != null ? prop.getDatatype() : XSD.xstring.getURI();
                        model.add(targetResource, RDFS.range, model.getResource(dt));
                    }
                    if (column != null || range != null) {
                        for (Resource c : domain.getClasses()) {
                            model.add(targetResource, RDFS.domain, c);
                        }
                    }
                }
            }
           /* if (!model.contains(targetResource, RDF.type)) { // no declaration:
                LOGGER.warn("Unknown mapping " + map + " : <" + targetResource + ">");
            }*/
            for (Literal propertyLabel : map.getDefinitionLabels()) {
                model.add(targetResource, RDFS.label, propertyLabel);
            }
            for (Literal propertyComment : map.getDefinitionComments()) {
                model.add(targetResource, RDFS.comment, propertyComment);
            }
            for (Resource additionalProperty : map.getAdditionalDefinitionProperties()) {
                Property property = additionalProperty.getProperty(D2RQ.propertyName).getResource().as(Property.class);
                RDFNode object = additionalProperty.getProperty(D2RQ.propertyValue).getObject();
                model.add(targetResource, property, object);
            }
        }

    }
}
