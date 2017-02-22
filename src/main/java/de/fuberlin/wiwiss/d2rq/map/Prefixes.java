package de.fuberlin.wiwiss.d2rq.map;

import java.util.Objects;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.JDBC;

/**
 * Just a set of constants and auxiliary methods to work with prefixes (mapping + schema)
 * It is used by {@link de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator} and {@link Mapping}.
 * <p>
 * Created by szuev on 22.02.2017.
 */
public class Prefixes {
    public static final String VOCAB_PREFIX = "vocab";
    public static final String MAP_PREFIX = "map";
    public static final String D2RQ_PREFIX = "d2rq";
    public static final String JDBC_PREFIX = "jdbc";
    public static final PrefixMapping STANDARD = PrefixMapping.Factory.create()
            .setNsPrefix("rdf", RDF.getURI())
            .setNsPrefix("rdfs", RDFS.getURI())
            .setNsPrefix("xsd", XSD.getURI())
            .setNsPrefix(D2RQ_PREFIX, D2RQ.getURI())
            .setNsPrefix(JDBC_PREFIX, JDBC.getURI()).lock();
    public static final PrefixMapping DEFAULT = PrefixMapping.Factory.create()
            .setNsPrefix("rdfs", RDFS.getURI())
            .setNsPrefix("rdf", RDF.getURI())
            .setNsPrefix("owl", OWL.getURI())
            .setNsPrefix("xsd", XSD.getURI()).lock();

    public static PrefixMapping createPrefixes(PrefixMapping model) {
        PrefixMapping res = PrefixMapping.Factory.create().withDefaultMappings(Prefixes.DEFAULT);
        // copy prefixes from model:
        model.getNsPrefixMap().forEach((prefix, uri) -> {
            if (Objects.equals(STANDARD.getNsPrefixURI(prefix), uri)) {
                return;
            }
            res.setNsPrefix(prefix, uri);
        });
        return res;
    }
}
