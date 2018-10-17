package de.fuberlin.wiwiss.d2rq.examples;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.Mappings;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

/**
 * Shows how to use the {@link SystemLoader} to initialize various
 * D2RQ components.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class SystemLoaderExample {

    public static void main(String[] args) {
        // First, let's set up an in-memory HSQLDB database,
        // load a simple SQL database into it, and generate
        // a default D2RQ mapping for this database using the
        // W3C Direct Mapping
        SystemLoader loader = new SystemLoader().setJdbcURL("jdbc:hsqldb:mem:test");
        // don't know why but _sometimes_ it doesn't work without user specified.
        // Perhaps it is due to the impact of other tests.
        loader.setUsername("d2rq")
                .setStartupSQLScript("doc/example/simple.sql")
                .setGenerateW3CDirectMapping(true);
        Mapping mapping = loader.build();

        // Print some internal stuff that shows how D2RQ maps the
        // database to RDF triples
        for (TripleRelation internal : Mappings.asConnectingMapping(mapping).compiledPropertyBridges()) {
            System.out.println(internal);
        }

        // Write the contents of the virtual RDF model as N-Triples
        Model model = mapping.getDataModel();
        model.write(System.out, "N-TRIPLES");

        // Important -- close the model!
        model.close();

        // Now let's load an example mapping file that connects to
        // a MySQL database
        loader = new SystemLoader()
                .setMappingFileOrJdbcURL("doc/example/mapping-iswc.mysql.ttl")
                .setFastMode(true)
                .setSystemBaseURI("http://example.com/");

        // Get the virtual model and run a SPARQL query
        model = loader.build().getDataModel();
        ResultSet rs = QueryExecutionFactory.create("SELECT * {?s ?p ?o} LIMIT 10", model).execSelect();
        while (rs.hasNext()) {
            System.out.println(rs.next());
        }

        // Important -- close the model!
        model.close();
    }
}
