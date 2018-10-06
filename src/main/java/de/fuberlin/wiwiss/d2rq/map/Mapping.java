package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A D2RQ mapping.
 * Consists of {@link ClassMap}s, {@link PropertyBridge}s, and several other {@link MapObject Map Object}s.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * Created by @ssz on 25.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#database'>The D2RQ Mapping Language</a>
 */
public interface Mapping extends AutoCloseable {

    // todo: hide from this interface
    PrefixMapping getPrefixMapping();

    // todo: hide from this interface
    Collection<TripleRelation> compiledPropertyBridges();

    // todo: hide from the interface or remove at all
    void connect();

    void close();

    /**
     * Returns the model that is backed by this mapping and vice versa.
     * Since any changes in the model is reflected in the mapping (and vice versa),
     * don't forget to call {@link #validate()}.
     *
     * @return {@link Model}, not {@code null}
     */
    Model asModel();

    Graph getSchema();

    Graph getData();

    /**
     * Gets the mapping's configuration.
     *
     * @return {@link Configuration}, not {@code null}
     */
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
     * Creates a {@code d2rq:AdditionalProperty} typed resource and wraps it as {@link AdditionalProperty}.
     *
     * @param uri a resource uri, can be {@code null} for anonymous resource
     * @return {@link AdditionalProperty}, not {@code null}
     * @see <a href='http://d2rq.org/d2rq-language#additionalproperty'>9.2 AdditionalProperty</a>
     */
    AdditionalProperty createAdditionalProperty(String uri);

    /**
     * Creates a {@code d2rq:DownloadMap} typed resource and wraps it as {@link DownloadMap}.
     *
     * @param uri an uri, can be {@code null} to create an anonymous resource
     * @return {@link DownloadMap}, not {@code null}
     * @see <a href='http://d2rq.org/d2rq-language#download-map'>8. Enabling HTTP access to CLOBs/BLOBs (d2rq:DownloadMap)</a>
     */
    DownloadMap createDownloadMap(String uri);

    /**
     * Creates a {@code d2rq:ClassMap} typed resource and wraps it as {@link ClassMap}.
     *
     * @param uri an uri, can be {@code null} to create an anonymous resource
     * @return {@link ClassMap}, not {@code null}
     * @see <a href='http://d2rq.org/d2rq-language#classmap'>5. Creating RDF resources (d2rq:ClassMap)</a>
     */
    ClassMap createClassMap(String uri);

    /**
     * Creates a {@code d2rq:PropertyBridge} typed resource and wraps it as {@link PropertyBridge}.
     *
     * @param uri an uri or {@code null} to create an anonymous resource
     * @return {@link PropertyBridge}, not {@code null}
     * @see <a href='http://d2rq.org/d2rq-language#propertybridge'>6. Adding properties to resources (d2rq:PropertyBridge)</a>
     */
    PropertyBridge createPropertyBridge(String uri);

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
     * Lists all {@link AdditionalProperty}s.
     *
     * @return Stream of {@link AdditionalProperty}s
     */
    Stream<AdditionalProperty> listAdditionalProperties();

    /**
     * Lists all {@link DownloadMap Download Map}s that are declared in the mapping graph.
     *
     * @return Stream of {@link DownloadMap}s
     */
    Stream<DownloadMap> listDownloadMaps();

    /**
     * Lists all {@link ClassMap Class Map}s that are declared in the mapping graph.
     *
     * @return Stream of {@link ClassMap}s
     */
    Stream<ClassMap> listClassMaps();

    /**
     * Lists all {@link PropertyBridge Property Bridge}s that are declared in the mapping graph.
     *
     * @return Stream of {@link PropertyBridge}s
     */
    Stream<PropertyBridge> listPropertyBridges();

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

    /**
     * Appends the specified additional property {@link MapObject map object} into the mapping.
     * No op in case the given {@link AdditionalProperty} is already present in the graph.
     *
     * @param prop {@link AdditionalProperty}, not {@code null}
     * @return this mapping model to allow cascading calls
     */
    Mapping addAdditionalProperty(AdditionalProperty prop);

    /**
     * Appends the given{@link DownloadMap download map} into the mapping.
     * No op in case the specified map object is already in the graph.
     *
     * @param dm {@link DownloadMap}, not {@code null}
     * @return this instance
     */
    Mapping addDownloadMap(DownloadMap dm);

    /**
     * Appends the given{@link ClassMap class map} into the mapping.
     * No op in case the specified map object is already in the graph.
     *
     * @param c {@link ClassMap}, not {@code null}
     * @return this instance
     */
    Mapping addClassMap(ClassMap c);

    /**
     * Appends the given{@link PropertyBridge class map} into the mapping.
     * No op in case the specified map object is already in the graph.
     *
     * @param p {@link PropertyBridge}, not {@code null}
     * @return this instance
     */
    Mapping addPropertyBridge(PropertyBridge p);

    /**
     * Validates the mapping is correct.
     *
     * @throws D2RQException if the mapping cannot be used to build relations
     * @see MapObject#validate()
     */
    void validate() throws D2RQException;

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

    default Model getDataModel() {
        return ModelFactory.createModelForGraph(getData());
    }

    default Model getVocabularyModel() {
        return ModelFactory.createModelForGraph(getSchema());
    }
}
