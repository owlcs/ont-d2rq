package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.DownloadMap;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import org.apache.jena.rdf.model.Resource;

import java.util.Objects;

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

    public static TranslationTable.Entry findTranslation(TranslationTable table) {
        return table.listTranslations().findFirst().orElseThrow(AssertionError::new);
    }

    public static TranslationTable findTranslationTable(Mapping mapping) {
        return mapping.listTranslationTables().findFirst().orElseThrow(AssertionError::new);
    }

    public static TranslationTable findTranslationTable(Mapping mapping, Resource resource) {
        return mapping.listTranslationTables()
                .filter(t -> resource.equals(t.asResource())).findFirst().orElseThrow(AssertionError::new);
    }

    public static DownloadMap findDownloadMap(Mapping mapping, Resource name) {
        return mapping.listDownloadMaps().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElseThrow(AssertionError::new);
    }
}
