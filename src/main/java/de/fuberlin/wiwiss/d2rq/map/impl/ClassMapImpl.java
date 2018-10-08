package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("WeakerAccess")
public class ClassMapImpl extends ResourceMap implements ClassMap {

    public ClassMapImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @Override
    public ClassMapImpl addClass(Resource clazz) {
        return addRDFNode(D2RQ.clazz, clazz);
    }

    @Override
    public Stream<Resource> listClasses() {
        return Iter.asStream(classes());
    }

    public ExtendedIterator<Resource> classes() {
        return listStatements(D2RQ.clazz).mapWith(Statement::getResource);
    }

    @Override
    public ClassMapImpl setDatabase(Database database) {
        return (ClassMapImpl) super.setDatabase(database);
    }

    @Override
    public ClassMapImpl addAdditionalProperty(AdditionalProperty property) {
        return (ClassMapImpl) super.addAdditionalProperty(property);
    }

    @Override
    public ClassMapImpl addComment(Literal value) {
        return (ClassMapImpl) super.addComment(value);
    }

    @Override
    public ClassMapImpl addLabel(Literal value) {
        return (ClassMapImpl) super.addLabel(value);
    }

    @Override
    Property definitionLabelPredicate() {
        return D2RQ.classDefinitionLabel;
    }

    @Override
    Property definitionCommentPredicate() {
        return D2RQ.classDefinitionComment;
    }

    @Override
    Property additionalPropertyPredicate() {
        return D2RQ.additionalClassDefinitionProperty;
    }

    @Override
    public ClassMapImpl setURIPattern(String pattern) {
        return (ClassMapImpl) super.setURIPattern(pattern);
    }

    @Override
    public ClassMapImpl setURIColumn(String column) {
        return (ClassMapImpl) super.setURIColumn(column);
    }

    @Override
    public ClassMapImpl setConstantValue(String uri) {
        return (ClassMapImpl) super.setConstantValue(uri);
    }

    @Override
    public ClassMapImpl setConstantValue() {
        return (ClassMapImpl) super.setConstantValue(mapping.asModel().createResource());
    }

    @Override
    public ClassMapImpl setUriSQLExpression(String uriSqlExpression) {
        return (ClassMapImpl) super.setUriSQLExpression(uriSqlExpression);
    }

    @Override
    public ClassMapImpl addJoin(String join) {
        return (ClassMapImpl) super.addJoin(join);
    }

    @Override
    public ClassMapImpl addCondition(String condition) {
        return (ClassMapImpl) super.addCondition(condition);
    }

    @Override
    public ClassMapImpl addAlias(String alias) {
        return (ClassMapImpl) super.addAlias(alias);
    }

    @Override
    public ClassMapImpl setBNodeIdColumns(String columns) {
        return (ClassMapImpl) super.setBNodeIdColumns(columns);
    }

    @Override
    public ClassMapImpl addValueRegex(String regex) {
        return (ClassMapImpl) super.addValueRegex(regex);
    }

    @Override
    public ClassMapImpl addValueContains(String contains) {
        return (ClassMapImpl) super.addValueContains(contains);
    }

    @Override
    public ClassMapImpl setValueMaxLength(int maxLength) {
        return (ClassMapImpl) super.setValueMaxLength(maxLength);
    }

    @Override
    public ClassMapImpl setTranslateWith(TranslationTable table) {
        return (ClassMapImpl) super.setTranslateWith(table);
    }

    @Override
    public ClassMapImpl addPropertyBridge(PropertyBridge bridge) {
        PropertyBridgeImpl res = mapping.asPropertyBridge(bridge.asResource()).copy(bridge);
        res.setBelongsToClassMap(this);
        return this;
    }

    @Override
    public Stream<PropertyBridge> listPropertyBridges() {
        return Iter.asStream(propertyBridges()).map(Function.identity());
    }

    public ExtendedIterator<PropertyBridgeImpl> propertyBridges() {
        return getModel().listResourcesWithProperty(D2RQ.belongsToClassMap, resource).mapWith(mapping::asPropertyBridge);
    }

    @Override
    public ClassMapImpl setContainsDuplicates(boolean b) {
        return setBoolean(D2RQ.containsDuplicates, b);
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        v.forProperty(D2RQ.dataStorage)
                .requireExists(D2RQException.CLASSMAP_NO_DATABASE)
                .requireHasNoDuplicates(D2RQException.CLASSMAP_DUPLICATE_DATABASE)
                .requireIsResource(D2RQException.CLASSMAP_INVALID_DATABASE);

        v.requireHasOnlyOneOf(D2RQException.RESOURCEMAP_MISSING_PRIMARYSPEC,
                D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.uriSqlExpression, D2RQ.bNodeIdColumns, D2RQ.constantValue);

        Validator.ForProperty containsDuplicates = v.forProperty(D2RQ.containsDuplicates);
        if (containsDuplicates.exists()) {
            containsDuplicates.requireHasNoDuplicates(D2RQException.RESOURCEMAP_ILLEGAL_CONTAINSDUPLICATE)
                    .requireIsBooleanLiteral(D2RQException.RESOURCEMAP_ILLEGAL_CONTAINSDUPLICATE);
        }
        commonValidateURI();
        commonValidateSQLAdditions();
        commonValidateUnclassifiedAdditions();
        RDFNode constantValue = getConstantValue();
        if (constantValue != null && constantValue.isLiteral()) {
            throw new D2RQException("d2rq:constantValue for class map " + toString() + " must be a URI or blank node",
                    D2RQException.CLASSMAP_INVALID_CONSTANTVALUE);
        }
        PropertyMap.checkURIPattern(this);
    }

    public boolean hasContent() {
        return listClasses().count() != 0 || listPropertyBridges().count() != 0;
    }

    public Collection<TripleRelation> toTripleRelations() {
        return propertyBridges().andThen(classes().mapWith(this::fetchPropertyForClass))
                .toSet() // no duplicates
                .stream()
                .map(PropertyBridgeImpl::toTripleRelations)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public PropertyBridgeImpl fetchPropertyForClass(Resource clazz) {
        ExtendedIterator<PropertyBridgeImpl> res = getModel()
                .listResourcesWithProperty(D2RQ.constantValue, clazz)
                .filterKeep(r -> r.hasProperty(D2RQ.belongsToClassMap, ClassMapImpl.this.resource)
                        && r.hasProperty(D2RQ.property, RDF.type))
                .mapWith(mapping::asPropertyBridge);
        try {
            if (res.hasNext()) return res.next();
        } finally {
            res.close();
        }
        return mapping.createPropertyBridge(null)
                .setBelongsToClassMap(ClassMapImpl.this)
                .addProperty(RDF.type)
                .setConstantValue(clazz);
    }

    @Override
    protected Relation buildRelation() {
        return this.relationBuilder(mapping.getConnectedDB(getDatabase())).buildRelation();
    }

    @Override
    public String toString() {
        return "d2rq:ClassMap " + PrettyPrinter.toString(this.resource);
    }

}
