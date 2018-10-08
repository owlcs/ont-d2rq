package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.map.impl.ResourceMap;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Objects;

/**
 * To convert {@link Mapping D2RQ mapping} -&gt; {@link Model Jena model}.
 * Currently there is only one model builder ({@link OWLModelBuilder}), which makes simple OWL2 DL ontology.
 * <p>
 * Created by @szuev on 19.02.2017.
 */
@Deprecated // TODO: will be replaced with another solution service
@SuppressWarnings("WeakerAccess")
public class MappingTransform {
    private static ModelBuilder defaultFactory = new OWLModelBuilder();

    public static ModelBuilder getModelBuilder() {
        return defaultFactory;
    }

    /**
     * Sets new default {@link ModelBuilder}.
     *
     * @param b {@link ModelBuilder} to set, not {@code null}
     * @return {@link ModelBuilder} the previously associated factory-builder
     */
    public static ModelBuilder setModelBuilder(ModelBuilder b) {
        Objects.requireNonNull(b, "Null factory");
        ModelBuilder prev = MappingTransform.defaultFactory;
        MappingTransform.defaultFactory = b;
        return prev;
    }

    /**
     * Mapping factory-builder.
     */
    @FunctionalInterface
    public interface ModelBuilder {
        Model build(Mapping mapping);
    }

    public static class OWLModelBuilder implements ModelBuilder {

        @Override
        public Model build(Mapping mapping) {
            Model res = ModelFactory.createDefaultModel().setNsPrefixes(createSchemaPrefixes(mapping.asModel()));

            Resource anon = res.createResource();
            anon.addProperty(RDF.type, OWL.Ontology);
            mapping.listDatabases().forEach(d -> anon.addLiteral(RDFS.comment, "Database: <" + d.getJDBCDSN() + ">"));

            mapping.listClassMaps().forEach(classMap -> {
                classMap.listClasses().forEach(c -> addDefinitions(res, (ResourceMap) classMap, c));
                classMap.listPropertyBridges().forEach(b -> {
                    b.listProperties().forEach(p -> addDefinitions(res, (ResourceMap) b, p));
                    // TODO: What to do about dynamic properties?
                });
            });
            return res;
        }

        protected void addDefinitions(Model model, ResourceMap map, Resource targetResource) {
            if (map instanceof ClassMap) {
                model.add(targetResource, RDF.type, OWL.Class);
            } else if (map instanceof PropertyBridge) {
                PropertyBridge prop = (PropertyBridge) map;
                ClassMap range = prop.getRefersToClassMap();
                ClassMap domain = prop.getBelongsToClassMap();
                String column = prop.getColumn();
                if (domain != null) {
                    if (range != null) { // if range != null -> object property (foreign key)
                        model.add(targetResource, RDF.type, OWL.ObjectProperty);
                        range.listClasses().forEach(c -> model.add(targetResource, RDFS.range, c));
                    }
                    if (column != null) { // if it has column -> data property (built-in properties has no column)
                        model.add(targetResource, RDF.type, OWL.DatatypeProperty);
                        String dt = prop.getDatatype() != null ? prop.getDatatype() : XSD.xstring.getURI();
                        model.add(targetResource, RDFS.range, model.getResource(dt));
                    }
                    if (column != null || range != null) {
                        domain.listClasses().forEach(c -> model.add(targetResource, RDFS.domain, c));
                    }
                }
            }
            map.listLabels().forEach(p -> model.add(targetResource, RDFS.label, p));
            map.listComments().forEach(p -> model.add(targetResource, RDFS.comment, p));
            map.listAdditionalProperties().forEach(p -> {
                // todo: should be annotation property:
                Property property = p.getName();
                RDFNode object = p.getValue();
                model.add(targetResource, property, object);
            });
        }

        private static PrefixMapping createSchemaPrefixes(Model mapping) {
            return PrefixMapping.Factory.create()
                    .setNsPrefixes(MappingFactory.SCHEMA)
                    .setNsPrefixes(mapping.getNsPrefixMap())
                    .removeNsPrefix(MappingFactory.D2RQ_PREFIX)
                    .removeNsPrefix(MappingFactory.JDBC_PREFIX)
                    .removeNsPrefix(MappingFactory.MAP_PREFIX).lock();
        }

    }
}
