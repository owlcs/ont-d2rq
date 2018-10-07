package de.fuberlin.wiwiss.d2rq.engine;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.TestVocab;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collection;

/**
 * Helper for loading mappings as test fixtures from Turtle files.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class MapFixture {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapFixture.class);

    private final static PrefixMapping prefixes = new PrefixMappingImpl() {{
        setNsPrefixes(PrefixMapping.Standard);
        setNsPrefix("d2rq", "http://www.wiwiss.fu-berlin.de/suhl/bizer/D2RQ/0.1#");
        setNsPrefix("jdbc", "http://d2rq.org/terms/jdbc/");
        setNsPrefix("test", "http://d2rq.org/terms/test#");
        setNsPrefix("ex", "http://example.org/");
        setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/");
    }};

    public static PrefixMapping prefixes() {
        return prefixes;
    }

    public static Collection<TripleRelation> loadPropertyBridges(String mappingFileName) {
        LOGGER.debug("Mapping file {}", mappingFileName);
        Model m = ModelFactory.createDefaultModel();
        Resource dummyDB = m.getResource(TestVocab.DummyDatabase.getURI());
        dummyDB.addProperty(RDF.type, D2RQ.Database);
        if (!mappingFileName.startsWith("/")) mappingFileName = "/" + mappingFileName;
        InputStream is = D2RQTestHelper.class.getResourceAsStream(mappingFileName);
        m.read(is, null, "TURTLE");
        Mapping res = MappingFactory.create(m, null);
        MappingHelper.connectToDummyDBs(res);
        return res.compiledPropertyBridges();
    }
}
