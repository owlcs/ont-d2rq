package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.values.Translator;

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

    int size();

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
     *
     * @return String or {@code null}
     */
    String getHref();

    TranslationTable addTranslation(String dbValue, String rdfValue);

    /**
     * TODO: not sure it is appropriate place for the method that can work with I/O.
     * TODO: move to the Mapping helper ?
     *
     * @return {@link Translator}
     */
    Translator asTranslator();

}
