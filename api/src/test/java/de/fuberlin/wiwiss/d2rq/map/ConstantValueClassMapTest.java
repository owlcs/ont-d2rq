package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.vocabulary.RDF;
import org.junit.Before;
import org.junit.Test;

public class ConstantValueClassMapTest {

    private ClassMap collection;

    @SuppressWarnings("SameParameterValue")
    private static ClassMap createClassMap(Mapping m, Database d, String uriPattern) {
        return m.createClassMap(null).setDatabase(d).setURIPattern(uriPattern);
    }

    @SuppressWarnings("SameParameterValue")
    private static ClassMap createConstantClassMap(Mapping m, Database d, String uri) {
        return m.createClassMap(null).setDatabase(d).setConstantValue(uri);
    }

    private PropertyBridge createPropertyBridge(Mapping mapping, ClassMap classMap, String propertyURI) {
        PropertyBridge result = mapping.createPropertyBridge(null);
        result.setBelongsToClassMap(classMap);
        result.addProperty(propertyURI);
        classMap.addPropertyBridge(result);
        return result;
    }

    @Before
    public void setUp() {
        Mapping mapping = MappingFactory.create();
        Database database = mapping.createDatabase(null);

        ClassMap concept = createClassMap(mapping, database, "http://example.com/concept#@@c.ID@@");
        createPropertyBridge(mapping, concept, RDF.type.getURI())
                .setConstantValue("http://www.w3.org/2004/02/skos/core#Concept");

        collection = createConstantClassMap(mapping, database, "http://example.com/collection#MyConceptCollection");
        createPropertyBridge(mapping, collection, RDF.type.getURI())
                .setConstantValue("http://www.w3.org/2004/02/skos/core#Collection");

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
