package de.fuberlin.wiwiss.d2rq.map.impl.schema;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.util.NodeUtils;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Helper to parse different OWL2 things from the mapping graph.
 * <p>
 * Created by @ssz on 10.10.2018.
 */
@SuppressWarnings("WeakerAccess")
class SchemaHelper {

    public static ExtendedIterator<Node> listJdbsNodes(Graph g) {
        return Iter.flatMap(g.find(Node.ANY, Nodes.RDFtype, Nodes.D2RQDatabase).mapWith(Triple::getSubject), n -> g.find(n, Nodes.D2RQjdbcDSN, Node.ANY))
                .mapWith(Triple::getObject).filterKeep(Node::isLiteral);
    }

    public static boolean isObjectProperty(Graph g, Node propertyBridge) {
        return hasFirst(g.find(propertyBridge, Nodes.D2RQrefersToClassMap, Node.ANY));
    }

    public static boolean isDataProperty(Graph g, Node propertyBridge) {
        return hasFirst(Iter.flatMap(Iter.of(Nodes.D2RQcolumn, Nodes.D2RQpattern, Nodes.D2RQsqlExpression), p -> g.find(propertyBridge, p, Node.ANY)));
    }

    public static boolean isAnnotationProperty(Graph g, Node propertyBridge) {
        return !isObjectProperty(g, propertyBridge) && !isDataProperty(g, propertyBridge);
    }

    public static ExtendedIterator<Node> listDataRanges(Graph g, Node propertyBridge) {
        Set<Node> res = g.find(propertyBridge, Nodes.D2RQdatatype, Node.ANY).mapWith(Triple::getObject).toSet();
        if (!res.isEmpty()) return WrappedIterator.create(res.iterator());
        if (isDataProperty(g, propertyBridge)) {
            return Iter.of(Nodes.XSDstring);
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
        return Iter.flatMap(g.find(Node.ANY, Nodes.RDFtype, Nodes.D2RQPropertyBridge),
                t -> checker.test(g, t.getSubject()) ? listProperties(g, t.getSubject()) : NullIterator.instance());
    }

    /**
     * Lists all properties form the given {@link de.fuberlin.wiwiss.d2rq.map.PropertyBridge PropertyBridge}.
     * @param g        {@link Graph}, not {@code null}
     * @param propertyBridge {@link Node}, property-bridge
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    public static ExtendedIterator<Node> listProperties(Graph g, Node propertyBridge) {
        return g.find(propertyBridge, Nodes.D2RQproperty, Node.ANY).mapWith(Triple::getObject)
                .andThen(listAdditional(g, propertyBridge, Nodes.D2RQadditionalPropertyDefinitionProperty, Nodes.symmetricPropertyAssertions));
    }

    /**
     * Lists all OWL classes from the given {@link de.fuberlin.wiwiss.d2rq.map.ClassMap ClassMap}.
     * Classes can be attached to the ClassMap directly, i.e {@code @classMap d2rq:class @uri}
     * or indirectly, through {@link de.fuberlin.wiwiss.d2rq.map.PropertyBridge PropertyBridge}
     * (i.e. {@code @propertyBridge d2rq:property rdf:type; d2rq:constantValue @uri})
     * or using {@link de.fuberlin.wiwiss.d2rq.map.AdditionalProperty AdditionalProperty}.
     *
     * @param g        {@link Graph}, not {@code null}
     * @param classMap {@link Node}, class-map
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    public static ExtendedIterator<Node> listClasses(Graph g, Node classMap) {
        ExtendedIterator<Node> a = g.find(classMap, Nodes.D2RQclass, Node.ANY).mapWith(Triple::getObject);
        ExtendedIterator<Node> b = g.find(Node.ANY, Nodes.D2RQbelongsToClassMap, classMap)
                .mapWith(t -> {
                    Node p = t.getSubject();
                    if (!g.contains(p, Nodes.D2RQproperty, Nodes.RDFtype))
                        return null;
                    return findFirst(g.find(p, Nodes.D2RQconstantValue, Node.ANY).mapWith(Triple::getObject)
                            .filterKeep(SchemaHelper::isResource).toList()).orElse(null);
                }).filterDrop(Objects::isNull);

        ExtendedIterator<Node> c = listAdditional(g, classMap, Nodes.D2RQadditionalClassDefinitionProperty, Nodes.symmetricClassAssertions);
        return a.andThen(b).andThen(c)
                .filterKeep(SchemaHelper::isResource);
    }

    public static ExtendedIterator<Node> listAdditional(Graph g, Node subject, Node predicate, Set<Node> allowedProperties) {
        return Iter.flatMap(g.find(subject, predicate, Node.ANY).mapWith(Triple::getObject),
                p -> {
                    if (hasFirst(g.find(p, Nodes.D2RQpropertyName, Node.ANY).mapWith(Triple::getObject)
                            .filterKeep(allowedProperties::contains))) {
                        return g.find(p, Nodes.D2RQpropertyValue, Node.ANY).mapWith(Triple::getObject);
                    }
                    return NullIterator.instance();
                });
    }

    static Optional<Node> findFirst(List<Node> list) {
        if (list.isEmpty()) return Optional.empty();
        list.sort(NodeUtils::compareRDFTerms);
        return Optional.of(list.get(0));
    }

    static boolean isResource(Node n) {
        return n.isURI() || n.isBlank();
    }

    static boolean hasFirst(ExtendedIterator<?> it) {
        try {
            return it.hasNext();
        } finally {
            it.close();
        }
    }
}
