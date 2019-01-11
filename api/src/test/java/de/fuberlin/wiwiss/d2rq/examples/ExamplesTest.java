package de.fuberlin.wiwiss.d2rq.examples;

import org.apache.jena.util.FileManager;
import org.junit.Test;

/**
 * Just run all examples as tests.
 * Created by szuev on 22.02.2017.
 */
public class ExamplesTest {
    private static final String[] args = new String[]{};
    private static final String SCHEMA_URI = "http://d2rq.org/terms/d2rq";

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
