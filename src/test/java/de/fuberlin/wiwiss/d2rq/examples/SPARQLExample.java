package de.fuberlin.wiwiss.d2rq.examples;

import org.apache.jena.query.*;

import de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;

public class SPARQLExample {

    public static void main(String[] args) {
        ModelD2RQ m = MappingFactory.load("file:doc/example/mapping-iswc.mysql.ttl").getDataModel();
        String sparql =
                "PREFIX dc: <http://purl.org/dc/elements/1.1/>" +
                        "PREFIX foaf: <http://xmlns.com/foaf/0.1/>" +
                        "SELECT ?paperTitle ?authorName WHERE {" +
                        "    ?paper dc:title ?paperTitle . " +
                        "    ?paper dc:creator ?author ." +
                        "    ?author foaf:name ?authorName ." +
                        "}";
        Query q = QueryFactory.create(sparql);
        ResultSet rs = QueryExecutionFactory.create(q, m).execSelect();
        while (rs.hasNext()) {
            QuerySolution row = rs.nextSolution();
            System.out.println("Title: " + row.getLiteral("paperTitle").getString());
            System.out.println("Author: " + row.getLiteral("authorName").getString());
        }
        m.close();
    }
}
