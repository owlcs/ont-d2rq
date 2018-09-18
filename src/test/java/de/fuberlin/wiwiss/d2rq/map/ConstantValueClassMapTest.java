package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.Before;
import org.junit.Test;

public class ConstantValueClassMapTest {

    private Model model;
    private Mapping mapping;
    private Database database;

    private ClassMap collection;

    private ClassMap createClassMap(String uriPattern) {
        ClassMap result = new ClassMap(this.model.createResource());
        result.setDatabase(this.database);
        result.setURIPattern(uriPattern);
        this.mapping.addClassMap(result);
        return result;
    }

    private ClassMap createConstantClassMap(String uri) {
        ClassMap result = new ClassMap(this.model.createResource());
        result.setDatabase(this.database);
        result.setConstantValue(this.model.createResource(uri));
        this.mapping.addClassMap(result);
        return result;
    }

    private PropertyBridge createPropertyBridge(ClassMap classMap, String propertyURI) {
        PropertyBridge result = new PropertyBridge(this.model.createResource());
        result.setBelongsToClassMap(classMap);
        result.addProperty(this.model.createProperty(propertyURI));
        classMap.addPropertyBridge(result);
        return result;
    }

    @Before
    public void setUp() {
        this.model = ModelFactory.createDefaultModel();
        this.mapping = MappingFactory.createEmpty();
        this.database = new Database(this.model.createResource());
        this.mapping.addDatabase(this.database);

        ClassMap concept = createClassMap("http://example.com/concept#@@c.ID@@");
        PropertyBridge conceptTypeBridge = createPropertyBridge(concept, RDF.type.getURI());
        conceptTypeBridge.setConstantValue(model.createResource("http://www.w3.org/2004/02/skos/core#Concept"));

        collection = createConstantClassMap("http://example.com/collection#MyConceptCollection");
        PropertyBridge collectionTypeBridge = createPropertyBridge(collection, RDF.type.getURI());
        collectionTypeBridge.setConstantValue(model.createResource("http://www.w3.org/2004/02/skos/core#Collection"));

        PropertyBridge memberBridge = createPropertyBridge(collection, "http://www.w3.org/2004/02/skos/core#member");
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
