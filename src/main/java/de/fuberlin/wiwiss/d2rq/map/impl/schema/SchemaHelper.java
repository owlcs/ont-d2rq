package de.fuberlin.wiwiss.d2rq.map.impl.schema;

import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Created by @ssz on 10.10.2018.
 */
@SuppressWarnings("WeakerAccess")
class SchemaHelper {

    static final Node RDFtype = RDF.Nodes.type;
    static final Node RDFSdomain = RDFS.Nodes.domain;
    static final Node RDFSrange = RDFS.Nodes.range;
    static final Node RDFSisDefinedBy = RDFS.Nodes.isDefinedBy;
    static final Node XSDstring = XSD.xstring.asNode();
    static final Node D2RQDatabase = D2RQ.Database.asNode();
    static final Node D2RQjdbcDSN = D2RQ.jdbcDSN.asNode();
    static final Node D2RQclass = D2RQ.clazz.asNode();
    static final Node D2RQproperty = D2RQ.property.asNode();
    static final Node D2RQconstantValue = D2RQ.constantValue.asNode();
    static final Node D2RQClassMap = D2RQ.ClassMap.asNode();
    static final Node D2RQPropertyBridge = D2RQ.PropertyBridge.asNode();
    static final Node D2RQbelongsToClassMap = D2RQ.belongsToClassMap.asNode();
    static final Node D2RQrefersToClassMap = D2RQ.refersToClassMap.asNode();
    static final Node OWLClass = OWL.Class.asNode();
    static final Node OWLequivalentClass = OWL.equivalentClass.asNode();
    static final Node OWLObjectProperty = OWL.ObjectProperty.asNode();
    static final Node OWLDataProperty = OWL.DatatypeProperty.asNode();
    static final Node OWLAnnotationProperty = OWL.AnnotationProperty.asNode();
    static final Node D2RQdatatype = D2RQ.datatype.asNode();
    static final Node D2RQlang = D2RQ.lang.asNode();
    static final Node D2RQcolumn = D2RQ.column.asNode();
    static final Node D2RQpattern = D2RQ.pattern.asNode();
    static final Node D2RQsqlExpression = D2RQ.sqlExpression.asNode();

    public static ExtendedIterator<Node> listJdbsNodes(Graph g) {
        return Iter.flatMap(g.find(Node.ANY, RDFtype, D2RQDatabase).mapWith(Triple::getSubject), n -> g.find(n, D2RQjdbcDSN, Node.ANY))
                .mapWith(Triple::getObject).filterKeep(Node::isLiteral);
    }

    public static ExtendedIterator<Node> listProperties(Graph g, Node propertyBridge) {
        return g.find(propertyBridge, D2RQproperty, Node.ANY).mapWith(Triple::getObject);
    }

    public static boolean isObjectProperty(Graph g, Node propertyBridge) {
        return hasFirst(g.find(propertyBridge, D2RQrefersToClassMap, Node.ANY));
    }

    public static boolean isDataProperty(Graph g, Node propertyBridge) {
        return hasFirst(Iter.flatMap(Iter.of(D2RQcolumn, D2RQpattern, D2RQsqlExpression), p -> g.find(propertyBridge, p, Node.ANY)));
    }

    public static boolean isAnnotationProperty(Graph g, Node propertyBridge) {
        return !isObjectProperty(g, propertyBridge) && !isDataProperty(g, propertyBridge);
    }

    private static boolean hasFirst(ExtendedIterator<?> it) {
        try {
            return it.hasNext();
        } finally {
            it.close();
        }
    }

    public static ExtendedIterator<Node> listDataRanges(Graph g, Node propertyBridge) {
        Set<Node> res = g.find(propertyBridge, D2RQdatatype, Node.ANY).mapWith(Triple::getObject).toSet();
        if (!res.isEmpty()) return WrappedIterator.create(res.iterator());
        if (isDataProperty(g, propertyBridge)) {
            return Iter.of(XSDstring);
        }
        return NullIterator.instance();
    }

    public static ExtendedIterator<Node> listObjectProperties(Graph g) {
        return listOWLProperties(g, SchemaHelper::isObjectProperty);
    }

    public static ExtendedIterator<Node> listDataProperties(Graph g) {
        return listOWLProperties(g, SchemaHelper::isDataProperty);
    }

    public static ExtendedIterator<Node> listAnnotationProperties(Graph g) {
        return listOWLProperties(g, SchemaHelper::isAnnotationProperty);
    }

    private static ExtendedIterator<Node> listOWLProperties(Graph g, BiPredicate<Graph, Node> checker) {
        return Iter.flatMap(g.find(Node.ANY, RDFtype, D2RQPropertyBridge),
                t -> checker.test(g, t.getSubject()) ? listProperties(g, t.getSubject()) : NullIterator.instance());
    }

    /**
     * {@code @classMap d2rq:class @uri} + {@code @propertyBridge d2rq:property rdf:type; d2rq:constantValue @uri}
     *
     * @param g
     * @param classMap
     * @return
     */
    public static ExtendedIterator<Node> listClasses(Graph g, Node classMap) {
        ExtendedIterator<Node> a = g.find(classMap, D2RQclass, Node.ANY).mapWith(Triple::getObject);
        ExtendedIterator<Node> b = g.find(Node.ANY, D2RQbelongsToClassMap, classMap)
                .mapWith(t -> {
                    Node p = t.getSubject();
                    if (!g.contains(p, D2RQproperty, RDFtype))
                        return null;
                    List<Node> x = g.find(p, D2RQconstantValue, Node.ANY).mapWith(Triple::getObject)
                            .filterKeep(s -> s.isURI() || s.isBlank()).toList();
                    return x.isEmpty() ? null : x.get(0);
                }).filterDrop(Objects::isNull);
        // todo: AdditionalProperty ?
        return Iter.concat(a, b);
    }
}
