package de.fuberlin.wiwiss.d2rq.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class VoID {

    public static final String NS = "http://rdfs.org/ns/void#";

    public static final Resource Dataset = resource("Dataset");

    public static final Property homepage = ResourceFactory.createProperty("http://xmlns.com/foaf/0.1/homepage");

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
        return ResourceFactory.createResource(NS + localName);
    }

    protected static Property property(String localName) {
        return ResourceFactory.createProperty(NS + localName);
    }
}
