package de.fuberlin.wiwiss.d2rq.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

@Deprecated // todo: it is not used by the system -> scheduled to remove
public class SD {

    public static final String NS = "http://www.w3.org/ns/sparql-service-description#";

    public static final Resource Service = resource("Service");

    public static final Resource Dataset = resource("Dataset");

    public static final Resource Graph = resource("Graph");

    public static final Property url = property("url");

    public static final Property defaultDatasetDescription = property("defaultDatasetDescription");

    public static final Property defaultGraph = property("defaultGraph");

    public static final Property resultFormat = property("resultFormat");

    protected static Resource resource(String localName) {
        return ResourceFactory.createResource(NS + localName);
    }

    protected static Property property(String localName) {
        return ResourceFactory.createProperty(NS + localName);
    }


}
