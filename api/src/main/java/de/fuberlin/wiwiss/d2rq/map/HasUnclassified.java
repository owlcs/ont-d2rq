package de.fuberlin.wiwiss.d2rq.map;

import java.util.stream.Stream;

/**
 * A technical interface to provide access to different stuff that are difficult to classify.
 * Applicable for {@link ClassMap} and {@link PropertyBridge}.
 * <p>
 * Created by @ssz on 30.09.2018.
 *
 * @param <R> subtype of {@link MapObject}
 */
interface HasUnclassified<R extends MapObject> extends HasTranslateWith<R> {

    /**
     * Sets a literal value for the {@code d2rq:bNodeIdColumns} predicate, where its lexical form is
     * a comma-separated list of column names in {@code TableName.ColumnName} notation.
     * The instances of this object will be blank nodes,
     * one distinct blank node per distinct tuple of these columns.
     *
     * @param columns String, not {@code null}
     * @return this instance to allow cascading calls
     */
    R setBNodeIdColumns(String columns);

    /**
     * Gets {@code d2rq:bNodeIdColumns} string literal value
     *
     * @return String or {@code null}
     */
    String getBNodeIdColumns();

    /**
     * Adds {@code d2rq:valueRegex} value to the object map, that
     * asserts that all values of this bridge match a given regular expression.
     * This allows D2RQ to speed up queries.
     * Most useful in conjunction with {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#column d2rq:column}
     * on columns whose values are very different from other columns in the database.
     *
     * @param regex String, not {@code null}
     * @return this instance to allow cascading calls
     * @see <a href='http://d2rq.org/d2rq-language#hint'>11.2 Example: Providing a regular expression</a>
     */
    R addValueRegex(String regex);

    /**
     * Lists all {@code d2rq:valueRegex} literals.
     *
     * @return Stream of String's
     */
    Stream<String> listValueRegex();

    /**
     * Adds {@code d2rq:valueContains} value to the object map, that
     * asserts that all values of this bridge always contain a given string.
     * This allows D2RQ to speed up queries.
     * Most useful in conjunction with {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#column d2rq:column}.
     *
     * @param contains String, not {@code null}
     * @return this instance to allow cascading calls
     */
    R addValueContains(String contains);

    /**
     * Lists all {@code d2rq:valueContains} literals.
     *
     * @return Stream of String's
     */
    Stream<String> listValueContains();

    /**
     * Adds {@code d2rq:valueMaxLength} integer value to the RDF, that
     * asserts that all values of this bridge are not longer than a number of characters.
     * This allows D2RQ to speed up queries.
     *
     * @param maxLength positive int
     * @return this instance to allow cascading calls
     */
    R setValueMaxLength(int maxLength);

    /**
     * Returns {@code d2rq:valueMaxLength} integer value form the RDF.
     *
     * @return {@link Integer} or {@code null} if undefined
     */
    Integer getValueMaxLength();
}
