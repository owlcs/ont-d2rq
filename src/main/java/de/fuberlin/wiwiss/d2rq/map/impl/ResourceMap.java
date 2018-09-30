package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.*;
import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import de.fuberlin.wiwiss.d2rq.expr.SQLExpression;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker.NodeType;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.values.*;
import de.fuberlin.wiwiss.d2rq.values.ValueDecorator.ValueConstraint;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public abstract class ResourceMap extends MapObjectImpl {

    // These can be set on PropertyBridges and ClassMaps
    private TranslationTable translateWith = null;

    // These can be set only on a PropertyBridge
    protected String column = null;
    protected String pattern = null;
    protected String sqlExpression = null;
    protected String datatype = null;
    protected String lang = null;
    protected ClassMap refersToClassMap = null;

    Collection<Literal> definitionLabels = new ArrayList<>();
    Collection<Literal> definitionComments = new ArrayList<>();

    /**
     * List of D2RQ.AdditionalProperty
     */
    Collection<Resource> additionalDefinitionProperties = new ArrayList<>();

    public ResourceMap(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    public ResourceMap setBNodeIdColumns(String columns) {
        return setLiteral(D2RQ.bNodeIdColumns, columns);
    }

    public String getBNodeIdColumns() {
        return findString(D2RQ.bNodeIdColumns).orElse(null);
    }

    public ResourceMap setURIColumn(String column) {
        return setLiteral(D2RQ.uriColumn, column);
    }

    public String getURIColumn() {
        return findString(D2RQ.uriColumn).orElse(null);
    }

    public ResourceMap setURIPattern(String pattern) {
        return setLiteral(D2RQ.uriPattern, pattern);
    }

    public String getURIPattern() {
        return findString(D2RQ.uriPattern).orElse(null);
    }

    public ResourceMap setUriSQLExpression(String uriSqlExpression) {
        return setLiteral(D2RQ.uriSqlExpression, uriSqlExpression);
    }

    public String getUriSQLExpression() {
        return findString(D2RQ.uriSqlExpression).orElse(null);
    }

    public ResourceMap setConstantValue(RDFNode value) {
        return setRDFNode(D2RQ.constantValue, value);
    }

    public RDFNode getConstantValue() {
        return findFirst(D2RQ.constantValue, Statement::getObject).orElse(null);
    }

    public ResourceMap setConstantValue(String uri) {
        return setURI(D2RQ.constantValue, uri);
    }

    public ResourceMap addValueRegex(String regex) {
        return addLiteral(D2RQ.valueRegex, regex);
    }

    public ExtendedIterator<String> getValueRegexesIterator() {
        return listStrings(D2RQ.valueRegex);
    }

    public Stream<String> listValueRegex() {
        return Iter.asStream(getValueRegexesIterator());
    }

    public ResourceMap addValueContains(String contains) {
        return addLiteral(D2RQ.valueContains, contains);
    }

    public ExtendedIterator<String> getValueContainsIterator() {
        return listStrings(D2RQ.valueContains);
    }

    public Stream<String> listValueContains() {
        return Iter.asStream(getValueContainsIterator());
    }

    public ResourceMap setValueMaxLength(int maxLength) {
        return setLiteral(D2RQ.valueMaxLength, maxLength);
    }

    public Integer getValueMaxLength() {
        return findFirst(D2RQ.valueMaxLength, s -> s.getLiteral().getInt()).orElse(null);
    }

    public void setTranslateWith(TranslationTable table) {
        assertNotYetDefined(getTranslateWith(), D2RQ.translateWith,
                D2RQException.RESOURCEMAP_DUPLICATE_TRANSLATEWITH);
        assertArgumentNotNull(table, D2RQ.translateWith, D2RQException.RESOURCEMAP_INVALID_TRANSLATEWITH);
        this.translateWith = table;
    }

    public TranslationTable getTranslateWith() {
        return translateWith;
    }

    public ResourceMap addJoin(String join) {
        return addLiteral(D2RQ.join, join);
    }

    public Stream<String> joins() {
        return Iter.asStream(listJoins());
    }

    public ExtendedIterator<String> listJoins() {
        return listStrings(D2RQ.join);
    }

    public ResourceMap addCondition(String condition) {
        return addLiteral(D2RQ.condition, condition);
    }

    public ExtendedIterator<String> listConditions() {
        return listStrings(D2RQ.condition);
    }

    public Stream<String> conditions() {
        return Iter.asStream(listConditions());
    }

    public ResourceMap addAlias(String alias) {
        return addLiteral(D2RQ.alias, alias);
    }

    public ExtendedIterator<String> listAliases() {
        return listStrings(D2RQ.alias);
    }

    public Stream<String> aliases() {
        return Iter.asStream(listAliases());
    }

    public String getColumn() {
        return column;
    }

    public String getPattern() {
        return pattern;
    }

    public String getSQLExpression() {
        return sqlExpression;
    }

    public String getDatatype() {
        return datatype;
    }

    public String getLang() {
        return lang;
    }

    public boolean isContainsDuplicates() {
        return getBoolean(D2RQ.containsDuplicates, false);
    }

    protected Set<Alias> getAliases() {
        return listAliases().mapWith(SQL::parseAlias).toSet();
    }

    public ClassMap getRefersToClassMap() {
        return refersToClassMap;
    }

    public RelationBuilder relationBuilder(ConnectedDB cd) {
        return relationBuilder(cd, isContainsDuplicates());
    }

    public RelationBuilder relationBuilder(ConnectedDB cd, boolean containsDuplicates) {
        RelationBuilder res = new RelationBuilder(cd);
        for (Join join : SQL.parseJoins(listJoins().toList())) {
            res.addJoinCondition(join);
        }
        listConditions().forEachRemaining(res::addCondition);
        res.addAliases(getAliases());
        for (ProjectionSpec projection : nodeMaker().projectionSpecs()) {
            res.addProjection(projection);
        }
        if (!containsDuplicates) {
            res.setIsUnique(true);
        }
        return res;
    }

    public Relation getRelation() {
        return buildRelation();
    }

    protected abstract Relation buildRelation();

    public NodeMaker nodeMaker() {
        boolean isUnique = !isContainsDuplicates();
        RDFNode constantValue = getConstantValue();
        if (constantValue != null) {
            return new FixedNodeMaker(constantValue.asNode(), isUnique);
        }
        ClassMap refersToClassMap = getRefersToClassMap();
        if (refersToClassMap == null) {
            return buildNodeMaker(wrapValueSource(buildValueSourceBase()), isUnique);
        }
        return ((ClassMapImpl) refersToClassMap).buildAliasedNodeMaker(new AliasMap(getAliases()), isUnique);
    }

    public NodeMaker buildAliasedNodeMaker(AliasMap aliases, boolean unique) {
        ValueMaker values = wrapValueSource(buildValueSourceBase()).renameAttributes(aliases);
        return buildNodeMaker(values, unique);
    }

    protected ValueMaker buildValueSourceBase() {
        String bNodeIdColumns = getBNodeIdColumns();
        if (bNodeIdColumns != null) {
            return new BlankNodeID(PrettyPrinter.toString(this.asResource()), parseColumnList(bNodeIdColumns));
        }
        String uriColumn = getURIColumn();
        if (uriColumn != null) {
            return new Column(SQL.parseAttribute(uriColumn));
        }
        String uriPattern = getURIPattern();
        if (uriPattern != null) {
            return new PropertyMap(uriPattern).buildValueSourceBase();
        }
        String column = getColumn();
        if (column != null) {
            return new Column(SQL.parseAttribute(column));
        }
        String pattern = getPattern();
        if (pattern != null) {
            return new Pattern(pattern);
        }
        String sqlExpression = getSQLExpression();
        if (sqlExpression != null) {
            return new SQLExpressionValueMaker(SQLExpression.create(sqlExpression));
        }
        String uriSqlExpression = getUriSQLExpression();
        if (uriSqlExpression != null) {
            return new SQLExpressionValueMaker(SQLExpression.create(uriSqlExpression));
        }
        throw new D2RQException(this + " needs a column/pattern/bNodeID specification");
    }

    public ValueMaker wrapValueSource(ValueMaker values) {
        List<ValueConstraint> constraints = new ArrayList<>();
        Integer valueMaxLength = getValueMaxLength();
        if (valueMaxLength != null) {
            constraints.add(ValueDecorator.maxLengthConstraint(valueMaxLength));
        }
        getValueContainsIterator().mapWith(ValueDecorator::containsConstraint).forEachRemaining(constraints::add);
        getValueRegexesIterator().mapWith(ValueDecorator::regexConstraint).forEachRemaining(constraints::add);
        TranslationTable translateWith = getTranslateWith();
        if (translateWith == null) {
            if (constraints.isEmpty()) {
                return values;
            }
            return new ValueDecorator(values, constraints);
        }
        return new ValueDecorator(values, constraints, translateWith.translator());
    }

    protected NodeMaker buildNodeMaker(ValueMaker values, boolean isUnique) {
        return new TypedNodeMaker(nodeType(), values, isUnique);
    }

    protected NodeType nodeType() {
        if (getBNodeIdColumns() != null) {
            return TypedNodeMaker.BLANK;
        }
        if (getURIColumn() != null || getURIPattern() != null) {
            return TypedNodeMaker.URI;
        }
        if (getUriSQLExpression() != null) {
            return TypedNodeMaker.URI;
        }
        // literals
        if (getColumn() == null && getPattern() == null && getSQLExpression() == null) {
            throw new D2RQException(this + " needs a column/pattern/bNodeID/sqlExpression/uriSqlExpression specification");
        }
        String datatype = getDatatype();
        String lang = getLang();
        if (datatype != null && lang != null) {
            throw new D2RQException(this + " has both d2rq:lang and d2rq:datatype");
        }
        if (datatype != null) {
            return TypedNodeMaker.typedLiteral(buildDatatype(datatype));
        }
        if (lang != null) {
            return TypedNodeMaker.languageLiteral(lang);
        }
        return TypedNodeMaker.PLAIN_LITERAL;
    }

    private RDFDatatype buildDatatype(String datatypeURI) {
        return TypeMapper.getInstance().getSafeTypeByName(datatypeURI);
    }

    private static List<Attribute> parseColumnList(String commaSeperated) {
        List<Attribute> result = new ArrayList<>();
        for (String attr : commaSeperated.split(",")) {
            result.add(SQL.parseAttribute(attr));
        }
        return result;
    }

    protected void assertHasPrimarySpec(Property... allowedSpecs) {
        List<Property> definedSpecs = new ArrayList<>();
        for (Property allowedProperty : allowedSpecs) {
            if (hasPrimarySpec(allowedProperty)) {
                definedSpecs.add(allowedProperty);
            }
        }
        if (definedSpecs.isEmpty()) {
            StringBuilder error = new StringBuilder(toString());
            error.append(" needs one of ");
            for (int i = 0; i < allowedSpecs.length; i++) {
                if (i > 0) {
                    error.append(", ");
                }
                error.append(PrettyPrinter.toString(allowedSpecs[i]));
            }
            throw new D2RQException(error.toString(), D2RQException.RESOURCEMAP_MISSING_PRIMARYSPEC);
        }
        if (definedSpecs.size() > 1) {
            throw new D2RQException(toString() + " can't have both " +
                    PrettyPrinter.toString(definedSpecs.get(0)) +
                    " and " +
                    PrettyPrinter.toString(definedSpecs.get(1)));
        }
    }

    private boolean hasPrimarySpec(Property property) {
        if (property.equals(D2RQ.bNodeIdColumns)) return getBNodeIdColumns() != null;
        if (property.equals(D2RQ.uriColumn)) return getURIColumn() != null;
        if (property.equals(D2RQ.uriPattern)) return getURIPattern() != null;
        if (property.equals(D2RQ.column)) return getColumn() != null;
        if (property.equals(D2RQ.pattern)) return getPattern() != null;
        if (property.equals(D2RQ.sqlExpression)) return getSQLExpression() != null;
        if (property.equals(D2RQ.uriSqlExpression)) return getUriSQLExpression() != null;
        if (property.equals(D2RQ.refersToClassMap)) return getRefersToClassMap() != null;
        if (property.equals(D2RQ.constantValue)) return getConstantValue() != null;
        throw new D2RQException("No primary spec: " + property);
    }

    /**
     * To validate all those things which are described in the {@link de.fuberlin.wiwiss.d2rq.map.HasURI} interface.
     */
    protected void commonValidateURI() {
        Validator v = new Validator(this);
        Validator.ForProperty uriPattertn = v.forProperty(D2RQ.uriPattern);
        if (uriPattertn.exists()) {
            uriPattertn.requireHasNoDuplicates(D2RQException.RESOURCEMAP_DUPLICATE_URIPATTERN)
                    .requireIsStringLiteral(D2RQException.RESOURCEMAP_ILLEGAL_URIPATTERN);
        }
        Validator.ForProperty uriColumn = v.forProperty(D2RQ.uriColumn);
        if (uriColumn.exists()) {
            uriColumn.requireHasNoDuplicates(D2RQException.RESOURCEMAP_DUPLICATE_URICOLUMN)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
        Validator.ForProperty constantValue = v.forProperty(D2RQ.constantValue);
        if (constantValue.exists())
            constantValue.requireHasNoDuplicates(D2RQException.RESOURCEMAP_DUPLICATE_CONSTANTVALUE);

        Validator.ForProperty uriSQLExpression = v.forProperty(D2RQ.uriSqlExpression);
        if (uriSQLExpression.exists()) {
            uriSQLExpression.requireHasNoDuplicates(D2RQException.PROPERTYBRIDGE_DUPLICATE_URI_SQL_EXPRESSION)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
    }

    /**
     * To validate all those things which are described in the {@link de.fuberlin.wiwiss.d2rq.map.HasSQL} interface.
     */
    protected void commonValidateSQLAdditions() {
        Validator v = new Validator(this);
        Stream.of(D2RQ.alias, D2RQ.join, D2RQ.condition)
                .map(v::forProperty)
                .forEach(p -> p.requireContainsOnlyStrings(D2RQException.UNSPECIFIED));
    }

    /**
     * To validate all those things which are described in the {@link de.fuberlin.wiwiss.d2rq.map.HasUnclassified} interface.
     */
    protected void commonValidateUnclassifiedAdditions() {
        Validator v = new Validator(this);
        Validator.ForProperty bNodeIdColumns = v.forProperty(D2RQ.bNodeIdColumns);
        if (bNodeIdColumns.exists()) {
            bNodeIdColumns
                    .requireHasNoDuplicates(D2RQException.RESOURCEMAP_DUPLICATE_BNODEIDCOLUMNS)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
        v.forProperty(D2RQ.valueRegex).requireContainsOnlyStrings(D2RQException.UNSPECIFIED);
        v.forProperty(D2RQ.valueContains).requireContainsOnlyStrings(D2RQException.UNSPECIFIED);

        Validator.ForProperty valueMaxLength = v.forProperty(D2RQ.valueMaxLength);
        if (valueMaxLength.exists()) {
            valueMaxLength
                    .requireHasNoDuplicates(D2RQException.PROPERTYBRIDGE_DUPLICATE_VALUEMAXLENGTH)
                    .requireIsIntegerLiteral(D2RQException.UNSPECIFIED);
        }
    }

    public Collection<Literal> getDefinitionLabels() {
        return definitionLabels;
    }

    public Collection<Literal> getDefinitionComments() {
        return definitionComments;
    }

    public Collection<Resource> getAdditionalDefinitionProperties() {
        return additionalDefinitionProperties;
    }

    public void addDefinitionLabel(Literal definitionLabel) {
        definitionLabels.add(definitionLabel);
    }

    public void addDefinitionComment(Literal definitionComment) {
        definitionComments.add(definitionComment);
    }

    public void addDefinitionProperty(Resource additionalProperty) {
        additionalDefinitionProperties.add(additionalProperty);
    }
}