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
     * Lists all {@link Database Database}s in the mapping graph.
     * Each mapping database corresponds the type {@code d2rq:Database}.
     *
     * @return Stream of {@link Database}s
     */
    Stream<Database> listDatabases();

    /**
     * Appends the specified database map object into the mapping.
     * No op in case the {@link Database} is already present.
     *
     * @param database {@link Database}, not {@code null}
     * @return this mapping model to allow cascading calls
     */
    Mapping addDatabase(Database database);

    // todo: should accept String, not Resource
    ClassMap createClassMap(Resource r);

    Stream<ClassMap> listClassMaps();

    // todo: change or remove
    void addClassMap(ClassMap classMap);

    // todo: should accept String, not Resource
    PropertyBridge createPropertyBridge(Resource r);

    TranslationTable createTranslationTable(String uri);

    Stream<TranslationTable> listTranslationTables();

    // todo: should accept String, not Resource
    DownloadMap createDownloadMap(Resource r);

    Stream<DownloadMap> listDownloadMaps();

    // todo: hide from the interface
    Collection<TripleRelation> compiledPropertyBridges();

    void validate();

    // todo: hide from the interface
    void connect();

    void close();

    /**
     * Finds the database with the given jdbc-uri.
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

    default TranslationTable findTranslationTable(Resource name) {
        return listTranslationTables().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElse(null);
    }

    default Model getDataModel() {
        return ModelFactory.createModelForGraph(getData());
    }

    default Model getVocabularyModel() {
        return ModelFactory.createModelForGraph(getSchema());
    }
}
