package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * TODO: introduced as part of bulky refactoring. everything can be changed.
 * Created by @ssz on 25.09.2018.
 *
 * @see <a href='http://d2rq.org/d2rq-language#database'>The D2RQ Mapping Language</a>
 */
public interface Mapping extends AutoCloseable {

    // todo: hide from this interface
    PrefixMapping getPrefixMapping();

    Model asModel();

    Graph getSchema();

    Graph getData();

    Configuration getConfiguration();

    /**
     * Creates a {@code d2rq:Database} typed resource and wraps it as {@link Database}.
     *
     * @param uri a resource uri, can be {@code null} for anonymous resource
     * @return {@link Database}, not {@code null}
     * @see <a href='http://d2rq.org/d2rq-language#database'>3. Database connection (d2rq:Database)</a>
     */
    Database createDatabase(String uri);

    /**
     * Creates a {@code d2rq:TranslationTable} typed resource and wraps it as {@link TranslationTable}.
     *
     * @param uri a resource uri, can be {@code null} for anonymous resource
     * @return {@link TranslationTable}, not {@code null}
     * @see <a href='http://d2rq.org/d2rq-language#translationtable'>7. Translating values (d2rq:TranslationTable)</a>
     */
    TranslationTable createTranslationTable(String uri);

    /**
     * Lists all {@link Database Database}s in the mapping graph.
     * Each mapping database corresponds the {@code d2rq:Database} type.
     *
     * @return Stream of {@link Database}s
     */
    Stream<Database> listDatabases();

    /**
     * Lists all {@link TranslationTable Translation Table}s that are declared in the mapping graph.
     * Each mapping translation table corresponds the {@code d2rq:TranslationTable} type.
     *
     * @return Stream of {@link TranslationTable}s
     */
    Stream<TranslationTable> listTranslationTables();

    /**
     * Appends the specified database {@link MapObject map object} into the mapping.
     * No op in case the given {@link Database} is already present in the graph.
     *
     * @param database {@link Database}, not {@code null}
     * @return this mapping model to allow cascading calls
     */
    Mapping addDatabase(Database database);

    /**
     * Appends the specified translation table {@link MapObject map object} into the mapping.
     * No op in case the given {@link TranslationTable} is already present in the graph.
     *
     * @param table {@link TranslationTable}, not {@code null}
     * @return this mapping model to allow cascading calls
     */
    Mapping addTranslationTable(TranslationTable table);

    // todo: should accept String, not Resource
    ClassMap createClassMap(Resource r);

    Stream<ClassMap> listClassMaps();

    // todo: change or remove
    void addClassMap(ClassMap classMap);

    // todo: should accept String, not Resource
    PropertyBridge createPropertyBridge(Resource r);

    // todo: should accept String, not Resource
    DownloadMap createDownloadMap(Resource r);

    Stream<DownloadMap> listDownloadMaps();

    // todo: hide from this interface
    Collection<TripleRelation> compiledPropertyBridges();

    void validate();

    // todo: hide from the interface or remove at all
    void connect();

    void close();

    /**
     * Finds a database with the given jdbc-uri.
     *
     * @param jdbcURL db connection string for looking for, not {@code null}
     * @return {@link Optional} of the {@link Database}, can be empty
     */
    default Optional<Database> findDatabase(String jdbcURL) {
        Objects.requireNonNull(jdbcURL, "Null JDBC URL");
        return listDatabases().filter(c -> Objects.equals(c.getJDBCDSN(), jdbcURL)).findFirst();
    }

    default ClassMap findClassMap(Resource name) {
        return listClassMaps().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElse(null);
    }

    default DownloadMap findDownloadMap(Resource name) {
        return listDownloadMaps().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElse(null);
    }

    default Model getDataModel() {
        return ModelFactory.createModelForGraph(getData());
    }

    default Model getVocabularyModel() {
        return ModelFactory.createModelForGraph(getSchema());
    }
}
