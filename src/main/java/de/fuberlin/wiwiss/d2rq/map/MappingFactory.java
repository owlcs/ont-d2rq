package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.map.impl.MapParser;
import de.fuberlin.wiwiss.d2rq.map.impl.MappingImpl;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.JDBC;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Helper-factory to create/load {@link Mapping}.
 * <p>
 * Created by szuev on 22.02.2017.
 */
public class MappingFactory {
    private static MapParser helper = new MapParser();

    /**
     * Creates a fresh mapping.
     * todo: rename
     *
     * @return {@link Mapping}
     */
    public static Mapping createEmpty() {
        return new MappingImpl(ModelFactory.createDefaultModel());
    }

    public static Mapping wrap(Model m) {
        return new MappingImpl(m);
    }

    /**
     * creates a mapping.
     *
     * @param mapModel {@link Model} the mapping model contained D2RQ rules.
     * @return {@link Mapping}
     */
    public static Mapping create(Model mapModel) {
        return create(mapModel, null);
    }

    /**
     * Creates a non-RDF database based model. The model is created
     * from a D2RQ map that may be in "RDF/XML", "N-TRIPLES" or "TURTLE" format.
     * Initially it was a constructor inside {@code de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ}.
     *
     * @param mapURL              URL of the D2RQ map to be used for this model
     * @param serializationFormat the format of the map, or <tt>null</tt> for guessing based on the file extension
     * @param baseURIForData      Base URI for turning relative URI patterns into absolute URIs; if <tt>null</tt>, then D2RQ will pick a base URI
     * @return {@link Mapping}
     */
    public static Mapping load(String mapURL, String serializationFormat, String baseURIForData) {
        Model model = FileManager.get().loadModel(mapURL, serializationFormat);
        String base = baseURIForData == null ? mapURL + "#" : baseURIForData;
        return create(model, base);
    }

    /**
     * Creates a non-RDF database based model.
     * The model is created from a D2RQ map that will be loaded from the given URL.
     * Its serialization format will be guessed from the file extension and defaults to RDF/XML.
     * Initially it was a constructor inside {@code de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ}.
     *
     * @param mapURL URL of the D2RQ map to be used for this model
     * @return {@link Mapping}
     */
    public static Mapping load(String mapURL) {
        return create(FileManager.get().loadModel(mapURL), mapURL + "#");
    }

    /**
     * Creates a mapping based on specified model.
     *
     * @param mapModel {@link Model} the mapping model contained D2RQ rules.
     * @param baseURI  the URL to fix relative URIs inside model. Optional.
     * @return {@link Mapping}
     */
    public static Mapping create(Model mapModel, String baseURI) {
        return helper.apply(mapModel, baseURI);
    }

    /**
     * Prefix helper.
     * Just a set of constants and auxiliary methods to work with prefixes (mapping + schema)
     * It is used by {@link de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator} and {@link MappingImpl}.
     * <p>
     * Created by szuev on 22.02.2017.
     */
    @SuppressWarnings("WeakerAccess")
    public static class Prefixes {
        public static final String VOCAB_PREFIX = "vocab";
        public static final String MAP_PREFIX = "map";
        public static final String D2RQ_PREFIX = "d2rq";
        public static final String JDBC_PREFIX = "jdbc";

        private static final PrefixMapping COMMON = PrefixMapping.Factory.create()
                .setNsPrefix("rdf", RDF.getURI())
                .setNsPrefix("rdfs", RDFS.getURI())
                .setNsPrefix("xsd", XSD.getURI()).lock();

        public static final PrefixMapping MAPPING = PrefixMapping.Factory.create()
                .setNsPrefixes(COMMON)
                .setNsPrefix(D2RQ_PREFIX, D2RQ.getURI())
                .setNsPrefix(JDBC_PREFIX, JDBC.getURI()).lock();

        public static final PrefixMapping SCHEMA = PrefixMapping.Factory.create()
                .setNsPrefixes(COMMON)
                .setNsPrefix("owl", OWL.getURI()).lock();

        public static PrefixMapping createSchemaPrefixes(Model mapping) {
            PrefixMapping res = PrefixMapping.Factory.create().withDefaultMappings(Prefixes.SCHEMA);
            Map<String, String> add = mapping.getNsPrefixMap();
            Map<String, String> ignore = calcMapSpecificPrefixes(mapping);
            ignore.forEach(add::remove);
            res.setNsPrefixes(add);
            return res;
        }

        private static Map<String, String> calcMapSpecificPrefixes(Model mapping) {
            Map<String, String> res = new HashMap<>();
            Stream.of(D2RQ_PREFIX, JDBC_PREFIX).forEach(p -> {
                String ns = mapping.getNsPrefixURI(p);
                if (ns == null) return;
                res.put(p, ns);
            });
            mapping.listSubjectsWithProperty(RDF.type, D2RQ.Database).forEachRemaining(r -> {
                String ns = r.getNameSpace();
                if (ns == null) return;
                String p = mapping.getNsURIPrefix(ns);
                if (p == null) return;
                res.put(p, ns);
            });
            return res;
        }
    }
}
