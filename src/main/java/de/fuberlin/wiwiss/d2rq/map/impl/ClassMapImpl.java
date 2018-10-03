package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("WeakerAccess")
public class ClassMapImpl extends ResourceMap implements ClassMap {

    private List<PropertyBridge> propertyBridges = new ArrayList<>();
    private Collection<TripleRelation> compiledPropertyBridges = null;

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
    public void addPropertyBridge(PropertyBridge bridge) {
        this.propertyBridges.add(bridge);
    }

    @Override
    public List<PropertyBridge> getPropertyBridges() {
        return this.propertyBridges;
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

        commonValidateURI();
        commonValidateSQLAdditions();
        commonValidateUnclassifiedAdditions();
        assertHasPrimarySpec(D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.uriSqlExpression, D2RQ.bNodeIdColumns, D2RQ.constantValue);
        RDFNode constantValue = getConstantValue();
        if (constantValue != null && constantValue.isLiteral()) {
            throw new D2RQException("d2rq:constantValue for class map " + toString() + " must be a URI or blank node",
                    D2RQException.CLASSMAP_INVALID_CONSTANTVALUE);
        }
        PropertyMap.checkURIPattern(this);
        for (PropertyBridge bridge : getPropertyBridges()) {
            bridge.validate();
        }
        // TODO
    }

    public boolean hasProperties() {
        return listClasses().count() != 0 || !getPropertyBridges().isEmpty();
    }

    public Collection<TripleRelation> compiledPropertyBridges() {
        if (this.compiledPropertyBridges == null) {
            compile();
        }
        return this.compiledPropertyBridges;
    }

    private void compile() {
        this.compiledPropertyBridges = new ArrayList<>();
        for (PropertyBridge bridge : getPropertyBridges()) {
            this.compiledPropertyBridges.addAll(((PropertyBridgeImpl) bridge).toTripleRelations());
        }
        listClasses().forEach(clazz -> {
            PropertyBridgeImpl bridge = mapping.asPropertyBridge(mapping.asModel().createResource());
            bridge.setBelongsToClassMap(ClassMapImpl.this);
            bridge.addProperty(RDF.type);
            bridge.setConstantValue(clazz);
            ClassMapImpl.this.compiledPropertyBridges.addAll(bridge.toTripleRelations());
        });
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
