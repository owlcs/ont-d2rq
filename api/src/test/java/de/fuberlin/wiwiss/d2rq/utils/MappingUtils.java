package de.fuberlin.wiwiss.d2rq.utils;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.map.impl.DownloadMapImpl;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;
import org.apache.jena.rdf.model.Resource;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Test helper for creating and managing {@link Mapping}s.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class MappingUtils {

    /**
     * Parses a D2RQ mapping from a file located relative to the {@link JenaModelUtils} directory.
     *
     * @param testFileName Filename, relative to {@link JenaModelUtils}'s location
     * @return A mapping
     * @throws D2RQException On error during parse
     */
    public static Mapping readFromTestFile(String testFileName) {
        return MappingFactory.create(JenaModelUtils.loadTurtle(testFileName), "http://example.org/");
    }

    public static void connectToDummyDBs(Mapping m) {
        m.listDatabases().map(DummyDB::create).forEach(c -> MappingHelper.useConnectedDB(m, c));
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
        JenaModelUtils.print(m.asModel());
    }

    public static Attribute getContentDownloadColumnAttribute(DownloadMap downloadMap) {
        return ((DownloadMapImpl) downloadMap).getContentDownloadColumnAttribute();
    }

    public static ValueMaker getMediaTypeValueMaker(DownloadMap downloadMap) {
        return ((DownloadMapImpl) downloadMap).getMediaTypeValueMaker();
    }

    public static Relation getRelation(DownloadMap downloadMap) throws D2RQException {
        return ((DownloadMapImpl) downloadMap).getRelation();
    }

    public static NodeMaker getNodeMaker(DownloadMap downloadMap) throws D2RQException {
        return ((DownloadMapImpl) downloadMap).nodeMaker();
    }
}
