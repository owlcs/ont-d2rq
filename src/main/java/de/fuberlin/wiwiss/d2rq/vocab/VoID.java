package de.fuberlin.wiwiss.d2rq.vocab;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class VoID {

    private static Model model = ModelFactory.createDefaultModel();
    
    public static final String NS = "http://rdfs.org/ns/void#";

    public static final Resource NAMESPACE = model.createResource(NS);

    public static final Resource Dataset = resource("Dataset");

    public static final Property homepage = model.createProperty("http://xmlns.com/foaf/0.1/homepage");

    public static final Property feature = property("feature");

    public static final Property rootResource = property("rootResource");

    public static final Property uriSpace = property("uriSpace");

    public static final Property class_ = property("class");

    public static final Property property = property("property");

    public static final Property vocabulary = property("vocabulary");

    public static final Property classPartition = property("classPartition");

    public static final Property propertyPartition = property("propertyPartition");

    public static final Property sparqlEndpoint = property("sparqlEndpoint");

    public static final Property inDataset = property("inDataset");

    protected static Resource resource(String localName) {
        return model.createResource(NS + localName);
    }

    protected static Property property(String localName) {
        return model.createProperty(NS + localName);
    }
}
