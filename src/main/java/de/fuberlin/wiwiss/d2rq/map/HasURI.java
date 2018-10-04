package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;

/**
 * A technical interface to provide access to different uri settings.
 * Applicable for {@link ClassMap}, {@link DownloadMap} and {@link PropertyBridge}.
 * To handle {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#uriPattern d2rq:uriPattern},
 * {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#uriColumn d2rq:uriColumn},
 * {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#constantValue d2rq:constnatValue} and
 * {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#uriSqlExpression d2rq:uriSqlExpression} parameters.
 * <p>
 * Created by @ssz on 30.09.2018.
 *
 * @param <R> subtype of {@link MapObject}
 */
interface HasURI<R extends MapObject> {

    /**
     * Sets {@code d2rq:uriPattern} property value.
     * <p>
     * For {@link ClassMap}: specifies a URI pattern that will be used to identify instances of this class map.
     * <p>
     * For {@link PropertyBridge}: for properties where the value is supposed to be a URI instead of a literal.
     * They work the same as on class maps.
     * <p>
     * For {@link DownloadMap}: specifies the URI where the server will make downloadable URI available.
     * Details are the same as for {@code d2rq:ClassMap} and {@code d2rq:PropertyBridge}.
     *
     * @param pattern String, not {@code null}
     * @return this instance to allow cascading calls
     * @see <a href='http://d2rq.org/d2rq-language#resource-identity'>5.1 Resource Identity</a>
     */
    R setURIPattern(String pattern);

    /**
     * Returns {@code d2rq:uriPattern} property value.
     *
     * @return String or {@code null}
     */
    String getURIPattern();

    /**
     * Sets {@code d2rq:uriColumn} property value.
     * A database column containing URI refs for identifying instances of this map.
     * The column name has to be in the form {@code TableName.ColumnName}.
     *
     * @param column String, not {@code null}
     * @return this instance to allow cascading calls
     */
    R setURIColumn(String column);

    /**
     * Returns {@code d2rq:uriColumn} property value.
     * <p>
     * For {@link ClassMap}: a database column containing URIrefs for identifying instances of this class map.
     * The column name has to be in the form {@code TableName.ColumnName}.
     * <p>
     * For {@link PropertyBridge}: for properties where the value is supposed to be a URI instead of a literal.
     * They work the same as on class maps.
     * <p>
     * For {@link DownloadMap}: specifies the URI where the server will make downloadable URI available.
     * Details are the same as for {@code d2rq:ClassMap} and {@code d2rq:PropertyBridge}.
     *
     * @return String or {@code null}
     */
    String getURIColumn();

    /**
     * Sets {@code d2rq:uriSqlExpression} property value, which is a literal containing
     * s SQL expression that generates the URI identifiers for instances of this class map.
     * Similar to {@code d2rq:sqlExpression}.
     * The output must be a valid URI.
     * Note that querying for such a computed value might put a heavy load on the database.
     * See example:
     * <pre>{@code map:HomepageURL a d2rq:PropertyBridge;
     * d2rq:belongsToClassMap map:PersonsClassMap;
     * d2rq:property foaf:homepage;
     * d2rq:uriSqlExpression "CONCAT('http://www.company.com/homepages/', user.username)";
     * .}</pre>
     *
     * @param expr String, not {@code null}
     * @return this instance to allow cascading calls
     */
    R setUriSQLExpression(String expr);

    /**
     * Returns {@code d2rq:uriSqlExpression} property value.
     *
     * @return String or {@code null}
     */
    String getUriSQLExpression();

    /**
     * Sets {@code d2rq:constantValue} property uri.
     *
     * @param uri String, not {@code null}
     * @return this instance to allow cascading calls
     * @see PropertyBridge#setConstantValue(Literal) to produce literal constaint
     * @see ClassMap#setConstantValue() to produce anonymous constant
     * @see PropertyBridge#setConstantValue() to produce anonymous constant
     */
    R setConstantValue(String uri);

    /**
     * Returns {@code d2rq:constantValue} property node, which may be uri,
     * blank-node (for {@link ClassMap} and {@link PropertyBridge}) or literal (for {@link PropertyBridge}).
     *
     * @return {@link RDFNode} or {@code null}
     */
    RDFNode getConstantValue();

}
