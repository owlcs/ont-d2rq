package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A D2RQ mapping.
 * Consists of {@link ClassMap}s, {@link PropertyBridge}s, and several other {@link MapObject Map Object}s.
 * To get an instance of this class use {@link MappingFactory}.
 * Some useful operations, which cannot be in the model interface due to architecture reasons,
 * are located in the {@link MappingHelper Mappings Utils} class.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * Created by @ssz on 25.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#database'>The D2RQ Mapping Language</a>
 */
public interface Mapping extends AutoCloseable {

    /**
     * Closes any existing connections.
     * Further use of the mapping, for example, iterating over {@link #getData()} triples,
     * will restore required db-connections automatically.
     * Please note: if there is a {@link Database#getStartupSQLScript() database startup script} inside the mapping,
     * reconnection will cause rerunning that script.
     */
    void close();

    /**
     * Locks the mapping so that changes can no longer be made to the underlying graph.
     * No-op in case the mapping is already locked.
     * The lock and unlock operations refer to changes made through any interface:
     * this {@link Mapping D2RQ mapping}, {@link #asModel() Jena Model}, {@link #getSchema() Schema},
     * since all these interfaces reflect the same base graph.
     *
     * @see #isLocked()
     */
    void lock();

    /**
     * Unlocks the mapping, allowing it to be modified.
     * No-op in case the mapping is already unlocked.
     *
     * @see #lock()
     */
    void unlock();

    /**
     * Answers {@code true} if this mapping is locked.
     * A locked mapping is unmodifiable and, therefore, thread-safe.
     *
     * @return {@code true} if the mapping model is locked
     * @see #lock()
     */
    boolean isLocked();

    /**
     * Returns a {@link Model} view of this mapping.
     * The model is backed by the mapping, so changes to the mapping are reflected in the model, and vice-versa.
     * Since it is possible to encode any RDF structure through model view,
     * the method {@link #validate(boolean)} should be used to verify that mapping is correct.
     *
     * @return {@link Model}, not {@code null}
     */
    Model asModel();

    /**
     * Returns an OWL2 schema part of this mapping.
     * It is a dynamic virtual graph which is partially backed by the mapping graph.
     * This means that it responses axioms which are either contained in the base mapping graph
     * or inferred from the D2RQ instructions.
     * This graph is transitive: any changes are redirected to the mapping graph.
     * But if the adding triple does not answer OWL2 specification, it will be unseen in the schema view.
     * The {@link org.apache.jena.shared.PrefixMapping} attached to the schema graph
     * is also reflected by the mapping graph prefixes and vice verse.
     * Adding or removing a prefix pair to or from the schema will be reflected in the mapping graph.
     * Please note: currently, like a {@link #getData() Data Graph}, iterating over this graph requires db connection.
     * Also note:
     * this graph depends on the {@link de.fuberlin.wiwiss.d2rq.vocab.AVC#controlOWL avc:controlOWL} setting.
     * If this setting is turn on, it is guaranteed, that both the schema and the data is fully corresponds OWL2,
     * but this also means some dynamic changes in the mapping.
     *
     * @return {@link Graph}, an OWL2 schema
     * @see ru.avicomp.ontapi.jena.model.OntGraphModel
     * @see ru.avicomp.ontapi.OntologyModel
     * @see <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL 2 Quick Reference Guide</a>
     */
    Graph getSchema();

    /**
     * Returns a dynamic RDF view of the referenced relational database structure.
     * The returning graph is read only.
     * In case {@link #validate() validation} is failed
     * any attempt to retrieve data through this graph will result {@link D2RQException}.
     * If {@link Configuration#getServeVocabulary()} is {@code true},
     * the returning graph will also include {@link #getSchema()} triples.
     *
     * @return virtual D2RQ {@link Graph Graph}, not {@code null}
     * @see #getConfiguration()
     */
    Graph getData();

    /**
     * Validates the RDF structure and (if the flag {@code onlyRDF} equals {@code true}) the DB connectivity
     * including compilation of relations.
     *
     * @param withDBConnectivity boolean, if {@code true} performs also checks DB connectivity,
     *                           otherwise does only RDF validation
     * @throws D2RQException if the mapping cannot be used to build relations
     * @see #validate()
     */
    void validate(boolean withDBConnectivity) throws D2RQException;

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
     * This checking includes both verifying the RDF structure and the DB connectivity.
     *
     * @throws D2RQException if the mapping cannot be used to build relations
     * @see MapObject#validate()
     */
    default void validate() throws D2RQException {
        validate(true);
    }

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
