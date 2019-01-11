package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;

import java.util.stream.Stream;

/**
 * Representation of a {@code d2rq:PropertyBridge} from the mapping graph.
 * <p>
 * Created by @ssz on 26.09.2018.
 *
 * @see <a href='http://d2rq.org/d2rq-language#propertybridge'>6. Adding properties to resources (d2rq:PropertyBridge)</a>
 */
public interface PropertyBridge extends MapObject,
        HasURI<PropertyBridge>, HasSQL<PropertyBridge>, HasUnclassified<PropertyBridge>, HasProperties<PropertyBridge> {

    /**
     * Sets {@link ClassMap} for the {@code d2rq:refersToClassMap} predicate.
     * For properties that correspond to a foreign key.
     * References another {@link ClassMap d2rq:ClassMap}
     * that creates the instances which are used as the values of this bridge.
     * If these instances come from another table,
     * then one or more {@code d2rq:join} properties must be specified to select the correct instances.
     *
     * @param c {@link ClassMap}, not {@code null}
     * @return this bridge
     */
    PropertyBridge setRefersToClassMap(ClassMap c);

    /**
     * Returns {@link ClassMap} that is attached on the {@code d2rq:refersToClassMap} predicate.
     *
     * @return {@link ClassMap} or {@code null}
     */
    ClassMap getRefersToClassMap();

    /**
     * Specifies that the property bridge belongs to the given {@link ClassMap d2rq:ClassMap}.
     * Must be specified for every property bridge.
     *
     * @param c {@link ClassMap}, not {@code null}
     * @return this bridge
     */
    PropertyBridge setBelongsToClassMap(ClassMap c);

    /**
     * Returns {@link ClassMap} that is attached on the {@code d2rq:belongsToClassMap} predicate.
     *
     * @return {@link ClassMap} or {@code null}
     */
    ClassMap getBelongsToClassMap();

    /**
     * Adds {@code d2rq:property} uri to the object.
     * The RDF property that connects the ClassMap with the object or literal created by the bridge.
     * Must be specified for every property bridge.
     * If multiple {@code d2rq:property} are specified,
     * then one triple with each property is generated per resource.
     *
     * @param uri String, not {@code null}
     * @return this instance
     */
    PropertyBridge addProperty(String uri);

    /**
     * Lists all properties for the {@code d2rq:property} predicate.
     *
     * @return Stream of {@link Property}s
     */
    Stream<Property> listProperties();

    /**
     * Adds {@code d2rq:dynamicProperty} pattern to the object.
     * The argument is an URI pattern that is used to generate the property at runtime.
     * If multiple {@code d2rq:dynamicProperty} are specified,
     * then one triple with each property is generated per resource.
     *
     * @param pattern String, not {@code null}
     * @return this instance
     */
    PropertyBridge addDynamicProperty(String pattern);

    /**
     * Lists all patterns for the {@code d2rq:dynamicProperty} predicate.
     *
     * @return Stream of Strings
     */
    Stream<String> listDynamicProperties();

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
     * Sets an integer value for the {@code d2rq:limit} predicate.
     * That is the maximum number of results to retrieve from the database for this PropertyBridge.
     * Also see {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#resultSizeLimit d2rq:resultSizeLimit}.
     *
     * @param limit nonnegative integer number
     * @return this instance
     * @see Database#setResultSizeLimit(int)
     */
    PropertyBridge setLimit(int limit);

    /**
     * Returns the {@code d2rq:limit} literal int value.
     *
     * @return {@code Integer} or {@code null} if undefined
     */
    Integer getLimit();

    /**
     * Sets an integer value for the {@code d2rq:limitInverse} predicate.
     * That is the maximum number of results to retrieve from the database
     * for the inverse statements for this PropertyBridge.
     *
     * @param limit nonnegative integer number
     * @return this instance
     */
    PropertyBridge setLimitInverse(int limit);

    /**
     * Returns the {@code d2rq:limitInverse} literal int value.
     *
     * @return {@code Integer} or {@code null} if undefined
     */
    Integer getLimitInverse();

    /**
     * Sets the column after which to sort results in
     * ascending (if second parameter is {@code false}) or descending (if {@code desc = true}) order
     * for this PropertyBridge.
     * Useful when results are limited using {@code d2rq:limit}.
     *
     * @param column String column name, not {@code null}
     * @param desc   {@code true} if desc, {@code false} is asc
     * @return this instance
     * @see #setLimit(int)
     */
    PropertyBridge setOrder(String column, boolean desc);

    /**
     * Returns the literal value for the {@code d2rq:orderDesc} or {@code d2rq:orderAsc} predicate,
     * depending what is present in the RDF Graph.
     *
     * @return String or {@code null}
     */
    String getOrderColumn();

    /**
     * Answers {@code true} or {@code false}
     * if the {@code d2rq:orderDesc} or {@code d2rq:ordersAsc} (respectively)
     * is present inside model for this PropertyBridge.
     * Returns {@code null} if none of them are found.
     *
     * @return boolean or {@code null} if undefined
     */
    Boolean getOrderDesc();

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

    default PropertyBridge addProperty(Property p) {
        return addProperty(p.getURI());
    }

}
