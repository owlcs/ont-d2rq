package de.fuberlin.wiwiss.d2rq.examples;

import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.FileManager;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.owlcs.d2rq.conf.ConnectionData;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Just run all examples as tests.
 * Created by szuev on 22.02.2017.
 */
public class ExamplesTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExamplesTest.class);

    private static final String[] args = new String[]{};
    private static final String SCHEMA_URI = "http://d2rq.org/terms/d2rq";

    @BeforeClass
    public static void before() throws IOException {
        Model m = ModelFactory.createDefaultModel();
        Path p = Paths.get(TestConstants.MAPPING).toRealPath();
        LOGGER.debug("Validate that model {} is valid to run this test", p);
        try (InputStream in = Files.newInputStream(p)) {
            m.read(in, null, "ttl");
        }
        String uri = Iter.findFirst(m.listObjectsOfProperty(D2RQ.jdbcDSN).mapWith(x -> x.asLiteral().getString()))
                .orElseThrow(IllegalStateException::new);
        LOGGER.debug("Detected JDBC uri: {}", uri);
        Assume.assumeTrue("Test can be run only for local configuration",
                uri.startsWith(ConnectionData.MYSQL.getBase()));
    }

    @Test
    public void testAssemblerExample() {
        FileManager.get().getLocationMapper().addAltEntry(SCHEMA_URI, TestConstants.SCHEMA);
        AssemblerExample.main(args);
    }

    @Test
    public void testJenaGraphExample() {
        JenaGraphExample.main(args);
    }

    @Test
    public void testJenaModelExample() {
        JenaModelExample.main(args);
    }

    @Test
    public void testSPARQLExample() {
        SPARQLExample.main(args);
    }

    @Test
    public void testSystemLoaderExample() {
        SystemLoaderExample.main(args);
    }
}
