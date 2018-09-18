package de.fuberlin.wiwiss.d2rq.vocab;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class SD {
    private static Model model = ModelFactory.createDefaultModel();
    
    public static final String NS = "http://www.w3.org/ns/sparql-service-description#";

    public static final Resource NAMESPACE = model.createResource(NS);

    public static final Resource Service = resource("Service");

    public static final Resource Dataset = resource("Dataset");

    public static final Resource Graph = resource("Graph");

    public static final Property url = property("url");

    public static final Property defaultDatasetDescription = property("defaultDatasetDescription");

    public static final Property defaultGraph = property("defaultGraph");

    public static final Property resultFormat = property("resultFormat");

    protected static Resource resource(String localName) {
        return model.createResource(localName);
    }

    protected static Property property(String localName) {
        return model.createProperty(localName);
    }


}
