package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.Before;
import org.junit.Test;

public class ConstantValueClassMapTest {

    private ClassMap collection;

    private static ClassMap createClassMap(Mapping m, Database d, String uriPattern) {
        ClassMap result = m.createClassMap(m.asModel().createResource());
        result.setDatabase(d);
        result.setURIPattern(uriPattern);
        m.addClassMap(result);
        return result;
    }

    private static ClassMap createConstantClassMap(Mapping m, Database d, String uri) {
        ClassMap result = m.createClassMap(m.asModel().createResource());
        result.setDatabase(d);
        result.setConstantValue(m.asModel().createResource(uri));
        m.addClassMap(result);
        return result;
    }

    private PropertyBridge createPropertyBridge(Mapping mapping, ClassMap classMap, String propertyURI) {
        Model m = classMap.asResource().getModel();
        PropertyBridge result = mapping.createPropertyBridge(m.createResource());
        result.setBelongsToClassMap(classMap);
        result.addProperty(m.createProperty(propertyURI));
        classMap.addPropertyBridge(result);
        return result;
    }

    @Before
    public void setUp() {
        Model model = ModelFactory.createDefaultModel();
        Mapping mapping = MappingFactory.createEmpty();
        Database database = mapping.createDatabase(null);

        ClassMap concept = createClassMap(mapping, database, "http://example.com/concept#@@c.ID@@");
        PropertyBridge conceptTypeBridge = createPropertyBridge(mapping, concept, RDF.type.getURI());
        conceptTypeBridge.setConstantValue(model.createResource("http://www.w3.org/2004/02/skos/core#Concept"));

        collection = createConstantClassMap(mapping, database, "http://example.com/collection#MyConceptCollection");
        PropertyBridge collectionTypeBridge = createPropertyBridge(mapping, collection, RDF.type.getURI());
        collectionTypeBridge.setConstantValue(model.createResource("http://www.w3.org/2004/02/skos/core#Collection"));

        PropertyBridge memberBridge = createPropertyBridge(mapping, collection, "http://www.w3.org/2004/02/skos/core#member");
        memberBridge.setRefersToClassMap(concept);
        memberBridge.addCondition("c.foo = 1");
    }

    @Test
    public void testValidate() {
        try {
            collection.validate();
        } catch (D2RQException e) {
            throw new AssertionError("Should validate without exceptions", e);
        }
    }
}
