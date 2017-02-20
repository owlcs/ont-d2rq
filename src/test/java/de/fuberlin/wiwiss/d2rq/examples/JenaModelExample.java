package de.fuberlin.wiwiss.d2rq.examples;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DC;
import org.apache.jena.vocabulary.RDF;

import de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.ISWC;

public class JenaModelExample {

    public static void main(String[] args) {
        // Set up the ModelD2RQ using a mapping file
        ModelD2RQ m = new ModelD2RQ("file:doc/example/mapping-iswc.mysql.ttl");

        // Find anything with an rdf:type of iswc:InProceedings
        StmtIterator paperIt = m.listStatements(null, RDF.type, ISWC.InProceedings);

        // List found papers and print their titles
        while (paperIt.hasNext()) {
            Resource paper = paperIt.nextStatement().getSubject();
            System.out.println("Paper: " + paper.getProperty(DC.title).getString());

            // List authors of the paper and print their names
            StmtIterator authorIt = paper.listProperties(DC.creator);
            while (authorIt.hasNext()) {
                Resource author = authorIt.nextStatement().getResource();
                System.out.println("Author: " + author.getProperty(FOAF.name).getString());
            }
            System.out.println();
        }
        m.close();
    }
}
