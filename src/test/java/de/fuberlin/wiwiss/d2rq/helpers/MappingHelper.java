package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

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
        m.listDatabases().forEach(d -> d.useConnectedDB(DummyDB.create(d)));
    }

    public static TranslationTable.Entry findTranslation(TranslationTable table) {
        return table.listTranslations().findFirst().orElseThrow(AssertionError::new);
    }

    public static TranslationTable findTranslationTable(Mapping mapping) {
        return mapping.listTranslationTables().findFirst().orElseThrow(AssertionError::new);
    }

    public static TranslationTable findTranslationTable(Mapping mapping, Resource name) {
        return find(mapping, Mapping::listTranslationTables, name);
    }

    public static DownloadMap findDownloadMap(Mapping mapping, Resource name) {
        return find(mapping, Mapping::listDownloadMaps, name);
    }

    public static PropertyBridge findPropertyBridge(Mapping mapping, Resource name) {
        return find(mapping, Mapping::listPropertyBridges, name);
    }

    public static PropertyBridge findPropertyBridge(Mapping mapping, String localName) {
        return find(mapping, Mapping::listPropertyBridges, localName);
    }

    public static ClassMap findClassMap(Mapping mapping, Resource name) {
        return find(mapping, Mapping::listClassMaps, name);
    }

    public static ClassMap findClassMap(Mapping mapping, String localName) {
        return find(mapping, Mapping::listClassMaps, localName);
    }

    private static <X extends MapObject> X find(Mapping m, Function<Mapping, Stream<X>> get, Resource name) {
        return get.apply(m)
                .filter(x -> Objects.equals(name, x.asResource()))
                .findFirst().orElseThrow(AssertionError::new);
    }

    private static <X extends MapObject> X find(Mapping m, Function<Mapping, Stream<X>> get, String localName) {
        return get.apply(m)
                .filter(x -> Objects.equals(localName, x.asResource().getLocalName()))
                .findFirst().orElseThrow(() -> new AssertionError("Can't find name " + localName));
    }

    public static void print(Mapping m) {
        print(m.asModel());
    }

    public static void print(Model m) {
        ReadWriteUtils.print(m);
    }
}
