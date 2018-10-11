package de.fuberlin.wiwiss.d2rq.map.impl.schema;

import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.Node;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Set;
import java.util.stream.Stream;

/**
 * A collection of different resources that are used while schema inference.
 * <p>
 * Created by @ssz on 11.10.2018.
 */
@SuppressWarnings("WeakerAccess")
class Nodes {
    static final Node RDFtype = RDF.Nodes.type;
    static final Node RDFSdomain = RDFS.Nodes.domain;
    static final Node RDFSrange = RDFS.Nodes.range;
    static final Node RDFSisDefinedBy = RDFS.Nodes.isDefinedBy;
    static final Node RDFSsubClassOf = RDFS.Nodes.subClassOf;
    static final Node RDFSsubPropertyOf = RDFS.Nodes.subPropertyOf;
    static final Node RDFSlabel = RDFS.Nodes.label;
    static final Node RDFScomment = RDFS.Nodes.comment;
    static final Node XSDstring = XSD.xstring.asNode();

    static final Node OWLClass = OWL.Class.asNode();
    static final Node OWLNamedIndividual = OWL.NamedIndividual.asNode();
    static final Node OWLObjectProperty = OWL.ObjectProperty.asNode();
    static final Node OWLDataProperty = OWL.DatatypeProperty.asNode();
    static final Node OWLAnnotationProperty = OWL.AnnotationProperty.asNode();
    static final Node OWLequivalentClass = OWL.equivalentClass.asNode();
    static final Node OWLdisjointWith = OWL.disjointWith.asNode();
    static final Node OWLpropertyDisjointWith = OWL.propertyDisjointWith.asNode();
    static final Node OWLequivalentProperty = OWL.equivalentProperty.asNode();

    static final Node D2RQDatabase = D2RQ.Database.asNode();
    static final Node D2RQConfiguration = D2RQ.Configuration.asNode();
    static final Node D2RQClassMap = D2RQ.ClassMap.asNode();
    static final Node D2RQPropertyBridge = D2RQ.PropertyBridge.asNode();
    static final Node D2RQAdditionalProperty = D2RQ.AdditionalProperty.asNode();
    static final Node D2RQDownloadMap = D2RQ.DownloadMap.asNode();
    static final Node D2RQjdbcDSN = D2RQ.jdbcDSN.asNode();
    static final Node D2RQclass = D2RQ.clazz.asNode();
    static final Node D2RQproperty = D2RQ.property.asNode();
    static final Node D2RQconstantValue = D2RQ.constantValue.asNode();
    static final Node D2RQbelongsToClassMap = D2RQ.belongsToClassMap.asNode();
    static final Node D2RQrefersToClassMap = D2RQ.refersToClassMap.asNode();
    static final Node D2RQdatatype = D2RQ.datatype.asNode();
    static final Node D2RQcolumn = D2RQ.column.asNode();
    static final Node D2RQpattern = D2RQ.pattern.asNode();
    static final Node D2RQsqlExpression = D2RQ.sqlExpression.asNode();
    static final Node D2RQadditionalClassDefinitionProperty = D2RQ.additionalClassDefinitionProperty.asNode();
    static final Node D2RQadditionalPropertyDefinitionProperty = D2RQ.additionalPropertyDefinitionProperty.asNode();
    static final Node D2RQpropertyValue = D2RQ.propertyValue.asNode();
    static final Node D2RQpropertyName = D2RQ.propertyName.asNode();
    static final Node D2RQclassDefinitionLabel = D2RQ.classDefinitionLabel.asNode();
    static final Node D2RQpropertyDefinitionLabel = D2RQ.propertyDefinitionLabel.asNode();
    static final Node D2RQclassDefinitionComment = D2RQ.classDefinitionComment.asNode();
    static final Node D2RQpropertyDefinitionComment = D2RQ.propertyDefinitionComment.asNode();

    static final Set<Node> symmetricPropertyAssertions = Stream.of(RDFSsubPropertyOf, OWLequivalentProperty, OWLpropertyDisjointWith)
            .collect(Iter.toUnmodifiableSet());

    static final Set<Node> symmetricClassAssertions = Stream.of(RDFSsubClassOf, OWLequivalentClass, OWLdisjointWith)
            .collect(Iter.toUnmodifiableSet());
}
