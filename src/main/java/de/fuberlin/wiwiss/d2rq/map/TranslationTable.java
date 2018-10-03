package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.values.Translator;

import java.util.stream.Stream;

/**
 * Represents a {@code d2rq:TranslationTable}.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @author zazi (http://github.com/zazi)
 * <p>
 * Created by @ssz on 26.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#translationtable'>7. Translating values (d2rq:TranslationTable)</a>
 */
public interface TranslationTable extends MapObject {

    /**
     * Creates an empty {@code d2rq:translation} instance.
     * Notice that duplicated entries are not allowed: the method {@link #validate()} will throw an error.
     * Two entries are considered as duplicate
     * if they have the same {@link Entry#getRDFValue() d2rq:rdfValue}s and
     * the same {@link Entry#getDatabaseValue() d2rq:databaseValue}s.
     *
     * @return {@link Entry}, not {@code null}
     */
    Entry createTranslation();

    /**
     * Lists all {@code d2rq:translation} entries.
     *
     * @return Stream of {@link Entry}.
     */
    Stream<Entry> listTranslations();

    /**
     * Links to a java {@link Translator} implementation using predicate {@code d2rq:javaClass}.
     * The qualified name of a Java class that performs the mapping.
     * The class must implement the {@link Translator} interface.
     * Custom {@link Translator}s might be useful for encoding and decoding values,
     * but are limited to 1:1 translations.
     *
     * @param className String, not {@code null}
     * @return this instance to allow cascading calls
     */
    TranslationTable setJavaClass(String className);

    /**
     * Returns string literal lexical form from the statement with the {@code d2rq:javaClass} predicate.
     * The returned string is expected to be a valid class implementation of the {@link Translator} interface,
     * otherwise the method {@link #validate()} will throw an exception.
     *
     * @return String or {@code null}
     */
    String getJavaClass();

    /**
     * Links to a CSV file containing translations using predicate {@code d2rq:href}.
     * Each line of the file is a translation and contains two strings separated by a comma.
     * The first one is the DB value, the second the RDF value.
     *
     * @param href String, not {@code null}
     * @return this instance to allow cascading calls
     */
    TranslationTable setHref(String href);

    /**
     * Returns an uri from the statement with the {@code d2rq:href} predicate.
     * The return string is expected to be a valid {@link java.net.URL},
     * otherwise the method {@link #validate()} will throw an exception.
     *
     * @return String or {@code null}
     */
    String getHref();

    /**
     * Creates {@code d2rq:translation} b-node and adds the mapping {@code d2rq:databaseValue <=> d2rq:rdfValue}
     * to the model for this {@code d2rq:TranslationTable}.
     * Notice that {@code d2rq:rdfValue} will be an URI, not string literal.
     *
     * @param dbValue  String (literal value), not {@code null}
     * @param rdfValue String (uri value), not {@code null}
     * @return this {@link TranslationTable} to allow cascading calls
     */
    default TranslationTable addTranslation(String dbValue, String rdfValue) {
        createTranslation().setDatabaseValue(dbValue).setURI(rdfValue);
        return this;
    }

    /**
     * Creates a {@link Translator}
     * from the different settings that are described in the graph for this {@code d2rq:TranslationTable}.
     * TODO: not sure it is appropriate place for the method that can work with I/O. Move to the Mapping helper ?
     *
     * @return {@link Translator}, not {@code null}
     */
    Translator asTranslator();

    /**
     * Represents a {@code d2rq:translation}.
     */
    interface Entry extends MapObject {

        /**
         * Creates {@code _:x d2rq:rdfValue uri} statement.
         * The previous value for the predicate {@code d2rq:rdfValue} will be deleted.
         *
         * @param uri String, not {@code null}
         * @return this instance to allow cascading call
         */
        Entry setURI(String uri);

        /**
         * Creates {@code _:x d2rq:rdfValue value^^xsd:string} statement.
         * The previous value for the predicate {@code d2rq:rdfValue} will be deleted.
         *
         * @param value String, not {@code null}
         * @return this instance to allow cascading call
         */
        Entry setLiteral(String value);

        /**
         * Creates {@code _:x d2rq:databaseValue value^^xsd:string} statement.
         * The previous value for the predicate {@code d2rq:databaseValue} will be deleted.
         *
         * @param value String, not {@code null}
         * @return this instance to allow cascading call
         */
        Entry setDatabaseValue(String value);

        /**
         * Gets a table that owns this translation.
         *
         * @return {@link TranslationTable}
         */
        TranslationTable getTable();

        /**
         * Returns the value for the {@code d2rq:rdfValue} predicate.
         * Please note: D2RQ allows both uri and plain literals for that property.
         *
         * @return String or {@code null}
         */
        String getRDFValue();

        /**
         * Returns the value for the {@code d2rq:databaseValue} predicate.
         *
         * @return String or {@code null}
         */
        String getDatabaseValue();
    }

}
