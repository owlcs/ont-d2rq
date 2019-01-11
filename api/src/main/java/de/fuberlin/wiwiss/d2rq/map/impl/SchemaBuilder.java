package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.jena.VirtualGraph;
import org.apache.jena.graph.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import ru.avicomp.ontapi.jena.utils.BuiltIn;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.fuberlin.wiwiss.d2rq.map.impl.SchemaHelper.*;

/**
 * A helper to build dynamic part of {@link SchemaGraph schema graph} in the form of {@link VirtualGraph.DynamicTriples}.
 * TODO: package private ?
 * Created by @szz on 16.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class SchemaBuilder {
    private final Set<Node> reservedProperties, reservedClasses;
    private final boolean withEquivalent;

    public SchemaBuilder(BuiltIn.Vocabulary voc, boolean withEquivalent) {
        this(getReservedClasses(voc), getReservedProperties(voc), withEquivalent);
    }

    protected SchemaBuilder(Set<Node> builtInClasses, Set<Node> builtInProperties, boolean withEquivalent) {
        this.reservedClasses = builtInClasses;
        this.reservedProperties = builtInProperties;
        this.withEquivalent = withEquivalent;
    }

    public static Set<Node> getReservedClasses(BuiltIn.Vocabulary vocabulary) {
        Set<Node> res = vocabulary.classes().stream()
                .map(FrontsNode::asNode).collect(Collectors.toSet());
        res.add(Nodes.OWL_CLASS);
        res.add(Nodes.OWL_NAMED_INDIVIDUAL);
        return Collections.unmodifiableSet(res);
    }

    public static Set<Node> getReservedProperties(BuiltIn.Vocabulary vocabulary) {
        return vocabulary.reservedProperties().stream()
                .map(FrontsNode::asNode).collect(Iter.toUnmodifiableSet());
    }

    protected VirtualGraph.DynamicTriples propertyDeclarations(Node type, Function<Graph, ExtendedIterator<Node>> get) {
        return new VirtualGraph.DynamicTriples() {

            @Override
            public boolean test(Triple m) {
                return m.getObject().matches(type) && m.getPredicate().matches(Nodes.RDF_FTYPE);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(get.apply(g)
                        .mapWith(s -> Triple.create(s, Nodes.RDF_FTYPE, type)), m);
            }
        };
    }

    protected VirtualGraph.DynamicTriples ontologyID() {
        return (g, m) -> {
            Node ms = m.getSubject();
            Node mp = m.getPredicate();
            if (!mp.matches(Nodes.RDFS_IS_DEFINED_BY) && !mp.matches(Nodes.RDF_FTYPE)) {
                return NullIterator.instance();
            }
            Node s = Graphs.ontologyNode(g).orElse(null);
            if (s == null) return NullIterator.instance();
            if (!ms.matches(s)) {
                return NullIterator.instance();
            }
            ExtendedIterator<Triple> res = listJdbsNodes(g)
                    .mapWith(n -> Triple.create(s, Nodes.RDFS_IS_DEFINED_BY,
                            NodeFactory.createURI(n.getLiteralValue().toString())));
            return res.filterKeep(m::matches);
        };
    }

    protected VirtualGraph.DynamicTriples classDeclarations() {
        return new VirtualGraph.DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getObject().matches(Nodes.OWL_CLASS) && m.getPredicate().matches(Nodes.RDF_FTYPE);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(listOWLClasses(g)
                        .mapWith(c -> Triple.create(c, Nodes.RDF_FTYPE, Nodes.OWL_CLASS)), m);
            }
        };
    }

    protected VirtualGraph.DynamicTriples equivalentClasses() {
        if (!withEquivalent) return VirtualGraph.DynamicTriples.EMPTY;
        return new VirtualGraph.DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(Nodes.OWL_EQUIVALENT_CLASS);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(Iter.flatMap(listClassMaps(g), c -> listEquivalentClasses(g, c)), m);
            }
        };
    }

    protected VirtualGraph.DynamicTriples equivalentProperties() {
        if (!withEquivalent) return VirtualGraph.DynamicTriples.EMPTY;
        return new VirtualGraph.DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(Nodes.OWL_EQUIVALENT_PROPERTY);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(Iter.flatMap(listPropertyBridges(g).filterDrop(p -> isAnnotationProperty(g, p)),
                        p -> listEquivalentProperties(g, p)), m);
            }
        };
    }

    protected VirtualGraph.DynamicTriples domainAssertions() {
        return new VirtualGraph.DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(Nodes.RDFS_DOMAIN);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(Iter.flatMap(g.find(Node.ANY, Nodes.D2RQ_BELONGS_TO_CLASS_MAP, Node.ANY),
                        t -> listOWLProperties(g, t.getSubject())
                                .mapWith(p -> findFirstOWLClass(g, t.getObject())
                                        .map(c -> Triple.create(p, Nodes.RDFS_DOMAIN, c)).orElse(null))
                                .filterDrop(Objects::isNull)), m);
            }
        };
    }

    protected VirtualGraph.DynamicTriples rangeAssertions() {
        return new VirtualGraph.DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(Nodes.RDFS_RANGE);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(Iter.flatMap(listPropertyBridges(g),
                        pb -> Iter.flatMap(listOWLProperties(g, pb), p -> g.find(pb, Nodes.D2RQ_REFERS_TO_CLASS_MAP, Node.ANY)
                                .mapWith(x -> findFirstOWLClass(g, x.getObject()).orElse(null))
                                .filterDrop(Objects::isNull)
                                .andThen(listDataRanges(g, pb))
                                .mapWith(r -> Triple.create(p, Nodes.RDFS_RANGE, r)))), m);
            }
        };
    }

    protected VirtualGraph.DynamicTriples additionalAssertions() {
        return (g, m) -> distinctMatch(Iter.flatMap(g.find(Node.ANY, Nodes.D2RQ_PROPERTY_NAME, m.getPredicate()), t -> {
            Node a = t.getSubject();
            Set<Node> objects = g.find(a, Nodes.D2RQ_PROPERTY_VALUE, m.getObject()).mapWith(Triple::getObject).toSet();
            if (objects.isEmpty()) return NullIterator.instance();
            Set<Node> predicates = g.find(a, Nodes.D2RQ_PROPERTY_NAME, m.getPredicate()).mapWith(Triple::getObject).toSet();
            if (predicates.isEmpty()) return NullIterator.instance();
            Node o = objects.iterator().next();
            Node p = predicates.iterator().next();
            return listAdditionalAssertions(g, a, p, o);
        }), m);
    }

    private ExtendedIterator<Triple> listAdditionalAssertions(Graph g, Node a, Node p, Node o) {
        return g.find(Node.ANY, Nodes.D2RQ_ADDITIONAL_CLASS_DEFINITION_PROPERTY, a)
                .mapWith(x -> findFirstOWLClass(g, x.getSubject()).orElse(null))
                .andThen(g.find(Node.ANY, Nodes.D2RQ_ADDITIONAL_PROPERTY_DEFINITION_PROPERTY, a)
                        .mapWith(x -> findFirstOWLProperty(g, x.getSubject()).orElse(null)))
                .filterDrop(Objects::isNull)
                .mapWith(s -> Triple.create(s, p, o));
    }

    private VirtualGraph.DynamicTriples annotations(Node desiredPredicate,
                                                    Node mappingClassPredicate,
                                                    Node mappingPropertyPredicate) {
        return new VirtualGraph.DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(desiredPredicate);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph graph, Triple m) {
                ExtendedIterator<Triple> a = listLiteralAnnotations(graph, desiredPredicate, mappingClassPredicate,
                        (g, c) -> listOWLClasses(g, c));
                ExtendedIterator<Triple> b = listLiteralAnnotations(graph, desiredPredicate, mappingPropertyPredicate,
                        (g, p) -> listOWLProperties(g, p));
                return distinctMatch(Iter.concat(a, b), m);
            }
        };
    }

    protected VirtualGraph.DynamicTriples objectPropertyDeclarations() {
        return propertyDeclarations(Nodes.OWL_OBJECT_PROPERTY, g -> listOWLProperties(g, SchemaHelper::isObjectProperty));
    }

    protected VirtualGraph.DynamicTriples dataPropertyDeclarations() {
        return propertyDeclarations(Nodes.OWL_DATA_PROPERTY, g -> listOWLProperties(g, SchemaHelper::isDataProperty));
    }

    protected VirtualGraph.DynamicTriples annotationPropertyDeclarations() {
        return propertyDeclarations(Nodes.OWL_ANNOTATION_PROPERTY,
                g -> listOWLProperties(g, SchemaHelper::isAnnotationProperty));
    }

    protected VirtualGraph.DynamicTriples labels() {
        return annotations(Nodes.RDFS_LABEL, Nodes.D2RQ_CLASS_DEFINITION_LABEL, Nodes.D2RQ_PROPERTY_DEFINITION_LABEL);
    }

    protected VirtualGraph.DynamicTriples comments() {
        return annotations(Nodes.RDFS_COMMENT, Nodes.D2RQ_CLASS_DEFINITION_COMMENT, Nodes.D2RQ_PROPERTY_DEFINITION_COMMENT);
    }

    protected ExtendedIterator<Triple> listEquivalentClasses(Graph g, Node classMap) {
        ExtendedIterator<Node> additional = g.find(classMap, Nodes.D2RQ_ADDITIONAL_CLASS_DEFINITION_PROPERTY, Node.ANY)
                .mapWith(Triple::getObject);
        Set<Node> exclude = Iter.flatMap(additional.filterKeep(a -> g.contains(a, Nodes.D2RQ_PROPERTY_NAME, Nodes.RDFS_SUB_CLASS_OF)
                        || g.contains(a, Nodes.D2RQ_PROPERTY_NAME, Nodes.OWL_EQUIVALENT_CLASS)),
                a -> g.find(a, Nodes.D2RQ_PROPERTY_VALUE, Node.ANY).mapWith(Triple::getObject)).toSet();
        return listEquivalent(listOWLClasses(g, classMap), exclude, Nodes.OWL_EQUIVALENT_CLASS);
    }

    protected ExtendedIterator<Triple> listEquivalentProperties(Graph g, Node propertyBridge) {
        ExtendedIterator<Node> additional = g.find(propertyBridge, Nodes.D2RQ_ADDITIONAL_PROPERTY_DEFINITION_PROPERTY, Node.ANY)
                .mapWith(Triple::getObject);
        Set<Node> exclude = Iter.flatMap(additional.filterKeep(a -> g.contains(a, Nodes.D2RQ_PROPERTY_NAME, Nodes.RDFS_SUB_PROPERTY_OF)
                        || g.contains(a, Nodes.D2RQ_PROPERTY_NAME, Nodes.OWL_EQUIVALENT_PROPERTY)),
                a -> g.find(a, Nodes.D2RQ_PROPERTY_VALUE, Node.ANY).mapWith(Triple::getObject)).toSet();
        return listEquivalent(listOWLProperties(g, propertyBridge), exclude, Nodes.OWL_EQUIVALENT_PROPERTY);
    }

    public Optional<Node> findFirstOWLClass(Graph g, Node classMap) {
        return findFirst(listOWLClasses(g, classMap).toList());
    }

    public Optional<Node> findFirstOWLProperty(Graph g, Node propertyBridge) {
        return findFirst(listOWLProperties(g, propertyBridge).toList());
    }

    /**
     * Lists all {@code ClassMap}s except for auto-generated.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link ExtendedIterator} of {@link Node}s
     * @see SchemaHelper#listD2RQClassMaps(Graph)
     */
    protected ExtendedIterator<Node> listClassMaps(Graph g) {
        return listD2RQClassMaps(g).filterDrop(x -> isAutoGenerated(g, x));
    }

    /**
     * Lists all {@code PropertyBridge}s except for auto-generated.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link ExtendedIterator} of {@link Node}s
     * @see SchemaHelper#listD2RQPropertyBridges(Graph)
     */
    protected ExtendedIterator<Node> listPropertyBridges(Graph g) {
        return listD2RQPropertyBridges(g).filterDrop(x -> isAutoGenerated(g, x));
    }

    /**
     * Lists all owl properties for all {@code PropertyBridge}s
     * that satisfy the given condition (parameter {@code propertyBridgeTester}).
     *
     * @param g                    {@link Graph}, not {@code null}
     * @param propertyBridgeTester {@code BiPredicate} to test {@code PropertyBridge} nodes in a graph, not {@code null}
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    protected ExtendedIterator<Node> listOWLProperties(Graph g, BiPredicate<Graph, Node> propertyBridgeTester) {
        return Iter.flatMap(listPropertyBridges(g),
                p -> propertyBridgeTester.test(g, p) ? listOWLProperties(g, p) : NullIterator.instance());
    }

    /**
     * Lists all OWL properties that are attached to the given {@code PropertyBridge}
     * with the exception of reserved (built-in) properties.
     *
     * @param g              {@link Graph} to search in
     * @param propertyBridge {@link Node} PropertyBridge
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    public ExtendedIterator<Node> listOWLProperties(Graph g, Node propertyBridge) {
        return SchemaHelper.listProperties(g, propertyBridge).filterDrop(reservedProperties::contains);
    }

    /**
     * Lists all inferred OWL classes.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    protected ExtendedIterator<Node> listOWLClasses(Graph g) {
        return Iter.flatMap(listClassMaps(g), t -> listOWLClasses(g, t));
    }

    /**
     * Lists all OWL classes that are attached to the given {@code ClassMap}
     * with the exception of reserved (built-in) classes.
     *
     * @param g        {@link Graph} to search in
     * @param classMap {@link Node} ClassMap
     * @return {@link ExtendedIterator} of {@link Node}s
     * @see SchemaHelper#listClasses(Graph, Node)
     */
    public ExtendedIterator<Node> listOWLClasses(Graph g, Node classMap) {
        return listClasses(g, classMap).filterDrop(reservedClasses::contains);
    }

    /**
     * Answers {@code true} if the given {@code resourceMap} is auto-generated.
     *
     * @param g           {@link Graph}, not {@code null}
     * @param resourceMap {@link Node} representing {@code d2rq:ClassMap} or {@code d2rq:PropertyBridge}
     * @return boolean
     */
    protected boolean isAutoGenerated(Graph g, Node resourceMap) {
        return g.contains(resourceMap, Nodes.AVC_AUTO_GENERATED, Nodes.XSD_BOOLEAN_TRUE);
    }

    /**
     * The main method to build dynamic triples.
     *
     * @return {@link VirtualGraph.DynamicTriples}
     */
    public VirtualGraph.DynamicTriples buildDynamicGraph() {
        return ontologyID()
                .andThen(classDeclarations())
                .andThen(equivalentClasses())
                .andThen(objectPropertyDeclarations())
                .andThen(dataPropertyDeclarations())
                .andThen(annotationPropertyDeclarations())
                .andThen(equivalentProperties())
                .andThen(domainAssertions())
                .andThen(rangeAssertions())
                .andThen(additionalAssertions())
                .andThen(labels())
                .andThen(comments())
                ;
    }

}
