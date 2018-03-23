package de.fuberlin.wiwiss.d2rq;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Test suite for D2RQ
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class D2RQTestHelper {
    public static final String DIRECTORY = "src/test/java/de/fuberlin/wiwiss/d2rq/";
    public static final String DIRECTORY_URL = "file:" + DIRECTORY;
    public static final String ISWC_MAP = "file:doc/example/mapping-iswc.mysql.ttl";

    public static Model loadTurtle(String fileName) {
        Model m = ModelFactory.createDefaultModel();
        m.read(D2RQTestHelper.DIRECTORY_URL + fileName, "TURTLE");
        return m;
    }
}