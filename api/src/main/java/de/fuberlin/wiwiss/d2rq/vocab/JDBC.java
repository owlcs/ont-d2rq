package de.fuberlin.wiwiss.d2rq.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Open namespace for JDBC connection properties. A JDBC connection
 * property named <tt>foo</tt> is modelled as an RDF property with
 * URI <tt>http://d2rq.org/terms/jdbc/foo</tt>. Values are plain string
 * literals.
 *
 * @author Richard Cyganiak
 */
@SuppressWarnings("unused")
public class JDBC {

    /**
     * The namespace of the vocabulary as a string.
     */
    public static final String NS = "http://d2rq.org/terms/jdbc/";

    /**
     * The namespace of the vocabulary as a string.
     *
     * @return String
     */
    public static String getURI() {
        return NS;
    }


    public static Property property(String k) {
        return ResourceFactory.createProperty(NS + k);
    }
}
