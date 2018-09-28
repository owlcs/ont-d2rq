package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;

/**
 * Test helper for creating {@link Mapping}s.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class MappingHelper {

    /**
     * Parses a D2RQ mapping from a file located relative to
     * the {@link D2RQTestHelper} directory.
     *
     * @param testFileName Filename, relative to {@link D2RQTestHelper}'s location
     * @return A mapping
     * @throws D2RQException On error during parse
     */
    public static Mapping readFromTestFile(String testFileName) {
        return MappingFactory.create(D2RQTestHelper.loadTurtle(testFileName), "http://example.org/");
    }

    public static void connectToDummyDBs(Mapping m) {
        m.listDatabases().forEach(d -> d.useConnectedDB(new DummyDB()));
    }
}
