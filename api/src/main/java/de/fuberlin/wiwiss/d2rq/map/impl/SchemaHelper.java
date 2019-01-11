package de.fuberlin.wiwiss.d2rq.map.impl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.util.NodeUtils;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.*;
import java.util.function.BiFunction;

/**
 * A helper with static utils to parse different OWL2 and D2RQ things from the mapping graph.
 * This class has been invited mostly to relieve the class {@link SchemaBuilder}.
 * <p>
 * Created by @ssz on 10.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class SchemaHelper {

    public static ExtendedIterator<Node> listJdbsNodes(Graph g) {
        return Iter.flatMap(g.find(Node.ANY, Nodes.RDF_FTYPE, Nodes.D2RQ_DATABASE).mapWith(Triple::getSubject),
                n -> g.find(n, Nodes.D2RQ_JDBC_DSN, Node.ANY))
                .mapWith(Triple::getObject).filterKeep(Node::isLiteral);
    }

    public static boolean isObjectProperty(Graph g, Node propertyBridge) {
        return hasFirst(g.find(propertyBridge, Nodes.D2RQ_REFERS_TO_CLASS_MAP, Node.ANY));
    }

    public static boolean isDataProperty(Graph g, Node propertyBridge) {
        return hasFirst(Iter.flatMap(Iter.of(Nodes.D2RQ_COLUMN, Nodes.D2RQ_PATTERN, Nodes.D2RQ_SQL_EXPRESSION),
                p -> g.find(propertyBridge, p, Node.ANY)));
    }

    public static boolean isAnnotationProperty(Graph g, Node propertyBridge) {
        return !isObjectProperty(g, propertyBridge) && !isDataProperty(g, propertyBridge);
    }

    public static ExtendedIterator<Node> listDataRanges(Graph g, Node propertyBridge) {
        Set<Node> res = g.find(propertyBridge, Nodes.D2RQ_DATATYPE, Node.ANY).mapWith(Triple::getObject).toSet();
        if (!res.isEmpty()) return WrappedIterator.create(res.iterator());
        if (isDataProperty(g, propertyBridge)) {
            return Iter.of(Nodes.XSD_STRING);
        }
        return NullIterator.instance();
    }

    public static ExtendedIterator<Node> listD2RQPropertyBridges(Graph g) {
        return g.find(Node.ANY, Nodes.RDF_FTYPE, Nodes.D2RQ_PROPERTY_BRIDGE).mapWith(Triple::getSubject);
    }

    public static ExtendedIterator<Node> listD2RQClassMaps(Graph g) {
        return g.find(Node.ANY, Nodes.RDF_FTYPE, Nodes.D2RQ_CLASS_MAP).mapWith(Triple::getSubject);
    }

    /**
     * Lists all properties form the given {@link de.fuberlin.wiwiss.d2rq.map.PropertyBridge PropertyBridge}.
     *
     * @param g              {@link Graph}, not {@code null}
     * @param propertyBridge {@link Node}, property-bridge
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    public static ExtendedIterator<Node> listProperties(Graph g, Node propertyBridge) {
        return g.find(propertyBridge, Nodes.D2RQ_PROPERTY, Node.ANY).mapWith(Triple::getObject)
                .andThen(listAdditional(g, propertyBridge,
                        Nodes.D2RQ_ADDITIONAL_PROPERTY_DEFINITION_PROPERTY, Nodes.SYMMETRIC_PROPERTY_ASSERTIONS));
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
        ExtendedIterator<Node> a = g.find(classMap, Nodes.D2RQ_CLASS, Node.ANY).mapWith(Triple::getObject);
        ExtendedIterator<Node> b = g.find(Node.ANY, Nodes.D2RQ_BELONGS_TO_CLASS_MAP, classMap)
                .mapWith(t -> {
                    Node p = t.getSubject();
                    if (!g.contains(p, Nodes.D2RQ_PROPERTY, Nodes.RDF_FTYPE))
                        return null;
                    return findFirst(g.find(p, Nodes.D2RQ_CONSTANT_VALUE, Node.ANY).mapWith(Triple::getObject)
                            .filterKeep(SchemaHelper::isResource).toList()).orElse(null);
                }).filterDrop(Objects::isNull);

        ExtendedIterator<Node> c = listAdditional(g, classMap,
                Nodes.D2RQ_ADDITIONAL_CLASS_DEFINITION_PROPERTY,
                Nodes.SYMMETRIC_CLASS_ASSERTIONS);
        return a.andThen(b).andThen(c)
                .filterKeep(SchemaHelper::isResource);
    }

    public static ExtendedIterator<Node> listAdditional(Graph g,
                                                        Node subject,
                                                        Node predicate,
                                                        Set<Node> allowedProperties) {
        return Iter.flatMap(g.find(subject, predicate, Node.ANY).mapWith(Triple::getObject),
                p -> {
                    if (hasFirst(g.find(p, Nodes.D2RQ_PROPERTY_NAME, Node.ANY).mapWith(Triple::getObject)
                            .filterKeep(allowedProperties::contains))) {
                        return g.find(p, Nodes.D2RQ_PROPERTY_VALUE, Node.ANY).mapWith(Triple::getObject);
                    }
                    return NullIterator.instance();
                });
    }

    static Optional<Node> findFirst(List<Node> list) {
        if (list.isEmpty()) return Optional.empty();
        sort(list);
        return Optional.of(list.get(0));
    }

    static void sort(List<Node> list) {
        list.sort(NodeUtils::compareRDFTerms);
    }

    public static boolean isResource(Node n) {
        return n.isURI() || n.isBlank();
    }

    static boolean hasFirst(ExtendedIterator<?> it) {
        return Iter.findFirst(it).isPresent();
    }

    static ExtendedIterator<Triple> listLiteralAnnotations(Graph g,
                                                           Node desiredPredicate,
                                                           Node mappingPredicate,
                                                           BiFunction<Graph, Node, ExtendedIterator<Node>> get) {
        return Iter.flatMap(g.find(Node.ANY, mappingPredicate, Node.ANY).filterKeep(t -> t.getObject().isLiteral()),
                t -> get.apply(g, t.getSubject()).mapWith(s -> Triple.create(s, desiredPredicate, t.getObject())));
    }

    public static ExtendedIterator<Triple> distinctMatch(ExtendedIterator<Triple> triples, Triple m) {
        return WrappedIterator.create(triples.filterKeep(m::matches).toSet().iterator());
    }

    static ExtendedIterator<Triple> listEquivalent(ExtendedIterator<Node> nodes, Set<Node> exclude, Node predicate) {
        List<Node> list = nodes.toList();
        if (list.isEmpty() || list.size() == 1) return NullIterator.instance();
        sort(list);
        Node primary = list.get(0);
        Set<Node> rest = new HashSet<>(list);
        rest.remove(primary);
        if (rest.isEmpty()) return NullIterator.instance();
        rest.removeAll(exclude);
        if (rest.isEmpty()) return NullIterator.instance();
        return WrappedIterator.create(rest.iterator()).mapWith(r -> Triple.create(primary, predicate, r));
    }
}
