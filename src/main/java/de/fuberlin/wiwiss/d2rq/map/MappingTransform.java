package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

/**
 * To convert {@link Mapping D2RQ mapping} -&gt; {@link Model Jena model}.
 * Currently there is only one model builder ({@link OWLModelBuilder}), which makes simple OWL2 DL ontology.
 * Created by @szuev on 19.02.2017.
 */
public class MappingTransform {
    private static ModelBuilder owlBuilder = new OWLModelBuilder();

    public static ModelBuilder getModelBuilder() {
        return owlBuilder;
    }

    public static ModelBuilder setModelBuilder(ModelBuilder b) {
        ModelBuilder prev = MappingTransform.owlBuilder;
        MappingTransform.owlBuilder = b;
        return prev;
    }

    public interface ModelBuilder {
        Model build(Mapping mapping);
    }

    public static class OWLModelBuilder implements ModelBuilder {

        @Override
        public Model build(Mapping mapping) {
            Model model = ModelFactory.createDefaultModel();
            model.setNsPrefixes(mapping.getPrefixMapping());

            Resource anon = model.createResource();
            anon.addProperty(RDF.type, OWL.Ontology);
            mapping.databases().forEach(d -> anon.addLiteral(RDFS.comment, "Database: <" + d.getJDBCDSN() + ">"));

            mapping.classMaps().forEach(classMap -> {
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

        protected void addDefinitions(Model model, ResourceMap map, Resource targetResource) {
            if (ClassMap.class.isInstance(map)) {
                model.add(targetResource, RDF.type, OWL.Class);
            } else if (PropertyBridge.class.isInstance(map)) {
                PropertyBridge prop = (PropertyBridge) map;
                ClassMap range = prop.getRefersToClassMap();
                ClassMap domain = prop.getBelongsToClassMap();
                String column = prop.getColumn();
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
