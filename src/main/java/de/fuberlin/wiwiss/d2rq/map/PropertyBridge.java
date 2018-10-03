package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;

import java.util.Collection;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface PropertyBridge extends MapObject,
        HasURI<PropertyBridge>, HasSQL<PropertyBridge>, HasUnclassified<PropertyBridge>, HasProperties<PropertyBridge> {

    void addProperty(Resource r);

    Collection<Resource> properties();

    ClassMap getRefersToClassMap();

    void setRefersToClassMap(ClassMap c);

    ClassMap getBelongsToClassMap();

    void setBelongsToClassMap(ClassMap c);

    /**
     * Sets {@code d2rq:sqlExpression} literal value.
     * Generates literal values by evaluating a SQL expression.
     * Note that querying for such a computed value might put a heavy load on the database.
     * See example:
     * <pre>{@code map:UserEmailSHA1 a d2rq:PropertyBridge;
     * d2rq:belongsToClassMap map:User;
     * d2rq:property foaf:mbox_sha1sum;
     * d2rq:sqlExpression "SHA1(CONCAT('mailto:', user.email))" .
     * }</pre>
     *
     * @param sqlExpression String not {@code null}
     * @return this instance
     */
    PropertyBridge setSQLExpression(String sqlExpression);

    /**
     * Returns the {@code d2rq:sqlExpression} literal lexical form.
     *
     * @return String or {@code null}
     */
    String getSQLExpression();

    /**
     * Sets {@code d2rq:pattern} literal value.
     * Can be used to extend and combine column values before they are used as a literal property value.
     * If a pattern contains more than one column, then a separating string,
     * which cannot occur in the column values, has to be used between the column names,
     * in order to allow D2RQ reversing given literals into column values.
     *
     * @param pattern String not {@code null}
     * @return this instance
     */
    PropertyBridge setPattern(String pattern);

    /**
     * Returns the {@code d2rq:pattern} literal lexical form.
     *
     * @return String or {@code null}
     */
    String getPattern();

    /**
     * Sets {@code d2rq:column} literal value.
     * To handle the database column that contains the literal values.
     * Column names have to be given in the form {@code TableName.ColumnName}.
     *
     * @param column String not {@code null}
     * @return this instance
     */
    PropertyBridge setColumn(String column);

    /**
     * Returns the {@code d2rq:column} literal lexical form.
     *
     * @return String or {@code null}
     */
    String getColumn();

    /**
     * Sets {@code d2rq:datatype} uri value
     * that will be used as RDF datatype of the literals.
     *
     * @param uri String not {@code null}
     * @return this instance
     */
    PropertyBridge setDatatype(String uri);

    /**
     * Returns the {@code d2rq:datatype} node uri.
     *
     * @return String or {@code null}
     */
    String getDatatype();

    /**
     * Sets {@code d2rq:lang} literal value
     * that will be used as language tag of the literals.
     *
     * @param lang String not {@code null}
     * @return this instance
     */
    PropertyBridge setLang(String lang);

    /**
     * Returns the {@code d2rq:lang} literal value.
     *
     * @return String or {@code null}
     */
    String getLang();

    /**
     * Sets the given literal as {@code d2rq:constantValue}.
     *
     * @param literal {@link Literal}, not {@code null}
     * @return this object to allow cascading calls
     * @see HasURI#setConstantValue(String)
     */
    PropertyBridge setConstantValue(Literal literal);

    /**
     * Assigns a blank node for the {@code d2rq:constantValue} predicate.
     *
     * @return this object to allow cascading calls
     * @see HasURI#setConstantValue(String)
     */
    PropertyBridge setConstantValue();

}
