package de.fuberlin.wiwiss.d2rq.map.impl.schema;

import de.fuberlin.wiwiss.d2rq.jena.DynamicGraph;
import de.fuberlin.wiwiss.d2rq.jena.DynamicTriples;
import de.fuberlin.wiwiss.d2rq.jena.MaskGraph;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.graph.*;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import ru.avicomp.ontapi.jena.utils.BuiltIn;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static de.fuberlin.wiwiss.d2rq.map.impl.schema.SchemaHelper.*;

/**
 * Created by @ssz on 22.09.2018.
 * todo: handle multiple classes and properties as equivalent ?
 *
 * @see MaskGraph
 * @see DynamicGraph
 */
@SuppressWarnings("WeakerAccess")
public class SchemaGenerator {

    private final Set<Node> reservedProperties, classes;

    public SchemaGenerator(BuiltIn.Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        this.reservedProperties = asSet(vocabulary.reservedProperties());
        this.classes = asSet(vocabulary.classes());
    }

    private static Set<Node> asSet(Collection<? extends Resource> from) {
        return from.stream().map(FrontsNode::asNode).collect(Iter.toUnmodifiableSet());
    }

    /**
     * Creates a virtual schema graph that reflects the given mapping graph.
     * The return graph is an {@link Union Union Graph} which consist of two parts:
     * the left is a {@link MaskGraph MaskGraph}, which hides everything related to D2RQ language,
     * and the right is a {@link DynamicGraph DynamicGraph}, which maps D2RQ instructions to the OWL2 assertions.
     * The adding and removing are performed transitively on the base (specified) graph.
     *
     * @param base {@link Graph}, not {@code null}, containing D2RQ instructions
     * @return {@link Graph}, virtual graph, containing only the OWL2 assertions
     */
    public Graph createMagicGraph(Graph base) {
        Graph left = new MaskGraph(base, buildMaskGraph());
        Graph right = new DynamicGraph(base, buildDynamicGraph());
        Graph res = new Union(left, right) {

            @Override
            protected ExtendedIterator<Triple> _graphBaseFind(final Triple t) {
                // the duplicate checking is not needed in our case:
                return L.find(t).andThen(R.find(t));
            }

            @Override
            public void performDelete(Triple t) {
                // both the right and the left graphs reflect the same base graph,
                // so there is no need to perform deletion also on the right graph
                L.delete(t);
            }

            @Override
            public String toString() {
                // do not allow to print all data content
                return "MagicGraph@" + Integer.toHexString(hashCode());
            }
        };
        res.getPrefixMapping().clearNsPrefixMap().setNsPrefixes(createSchemaPrefixes(base));
        return res;
    }

    public static PrefixMapping createSchemaPrefixes(Graph mapping) {
        return PrefixMapping.Factory.create()
                .setNsPrefixes(MappingFactory.SCHEMA)
                .setNsPrefixes(mapping.getPrefixMapping())
                .removeNsPrefix(MappingFactory.D2RQ_PREFIX)
                .removeNsPrefix(MappingFactory.JDBC_PREFIX)
                .removeNsPrefix(MappingFactory.MAP_PREFIX).lock();
    }

    private static DynamicTriples propertyDeclarations(Node type,
                                                       Function<Graph, ExtendedIterator<Node>> get) {
        return new DynamicTriples() {

            @Override
            public boolean test(Triple m) {
                return m.getObject().matches(type) && m.getPredicate().matches(Nodes.RDFtype);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(get.apply(g)
                        .mapWith(s -> Triple.create(s, Nodes.RDFtype, type)), m);
            }
        };
    }

    private static ExtendedIterator<Triple> listLiteralAnnotations(Graph g,
                                                                   Node desiredPredicate,
                                                                   Node mappingPredicate,
                                                                   BiFunction<Graph, Node, ExtendedIterator<Node>> get) {
        return Iter.flatMap(g.find(Node.ANY, mappingPredicate, Node.ANY).filterKeep(t -> t.getObject().isLiteral()),
                t -> get.apply(g, t.getSubject()).mapWith(s -> Triple.create(s, desiredPredicate, t.getObject())));
    }

    private static ExtendedIterator<Triple> distinctMatch(ExtendedIterator<Triple> triples, Triple m) {
        return WrappedIterator.create(triples.filterKeep(m::matches).toSet().iterator());
    }

    public static BiPredicate<Graph, Triple> buildMaskGraph() {
        BiPredicate<Graph, Triple> res = null;
        for (Node type : Arrays.asList(Nodes.D2RQConfiguration
                , Nodes.D2RQDatabase
                , Nodes.D2RQClassMap
                , Nodes.D2RQPropertyBridge
                , Nodes.D2RQDownloadMap
                , Nodes.D2RQAdditionalProperty)) {
            BiPredicate<Graph, Triple> b = hidePart(type);
            if (res == null) {
                res = b;
            } else {
                res = res.or(b);
            }
        }
        return res;
    }

    private static BiPredicate<Graph, Triple> hidePart(Node type) {
        return (g, t) -> g.contains(t.getSubject(), Nodes.RDFtype, type);
    }

    protected DynamicTriples ontologyID() {
        return (g, m) -> {
            Node ms = m.getSubject();
            Node mp = m.getPredicate();
            if (!mp.matches(Nodes.RDFSisDefinedBy) && !mp.matches(Nodes.RDFtype)) {
                return NullIterator.instance();
            }
            Node s = Graphs.ontologyNode(g).orElse(null);
            if (s == null) return NullIterator.instance();
            if (!ms.matches(s)) {
                return NullIterator.instance();
            }
            ExtendedIterator<Triple> res = listJdbsNodes(g)
                    .mapWith(n -> Triple.create(s, Nodes.RDFSisDefinedBy,
                            NodeFactory.createURI(n.getLiteralValue().toString())));
            return res.filterKeep(m::matches);
        };
    }

    protected DynamicTriples classDeclarations() {
        return new DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getObject().matches(Nodes.OWLClass) && m.getPredicate().matches(Nodes.RDFtype);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(Iter.flatMap(g.find(Node.ANY, Nodes.RDFtype, Nodes.D2RQClassMap),
                        t -> listClasses(g, t.getSubject()))
                        .mapWith(c -> Triple.create(c, Nodes.RDFtype, Nodes.OWLClass)), m);
            }
        };
    }

    protected DynamicTriples domainAssertions() {
        return new DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(Nodes.RDFSdomain);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(Iter.flatMap(g.find(Node.ANY, Nodes.D2RQbelongsToClassMap, Node.ANY),
                        t -> listProperties(g, t.getSubject())
                                .mapWith(p -> findFirstClass(g, t.getObject())
                                        .map(c -> Triple.create(p, Nodes.RDFSdomain, c)).orElse(null))
                                .filterDrop(Objects::isNull)), m);
            }
        };
    }

    protected DynamicTriples rangeAssertions() {
        return new DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(Nodes.RDFSrange);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return distinctMatch(Iter.flatMap(g.find(Node.ANY, Nodes.RDFtype, Nodes.D2RQPropertyBridge),
                        t -> Iter.flatMap(listProperties(g, t.getSubject()),
                                p -> g.find(t.getSubject(), Nodes.D2RQrefersToClassMap, Node.ANY)
                                        .mapWith(x -> findFirstClass(g, x.getObject()).orElse(null))
                                        .filterDrop(Objects::isNull)
                                        .andThen(listDataRanges(g, t.getSubject()))
                                        .mapWith(r -> Triple.create(p, Nodes.RDFSrange, r)))), m);
            }
        };
    }

    public Optional<Node> findFirstClass(Graph g, Node classMap) {
        return findFirst(listClasses(g, classMap).toList());
    }

    public Optional<Node> findFirstProperty(Graph g, Node propertyBridge) {
        return findFirst(listProperties(g, propertyBridge).toList());
    }

    protected DynamicTriples additionalAssertions() {
        return (g, m) -> distinctMatch(Iter.flatMap(g.find(Node.ANY, Nodes.D2RQpropertyName, m.getPredicate()), t -> {
            Node a = t.getSubject();
            Set<Node> objects = g.find(a, Nodes.D2RQpropertyValue, m.getObject()).mapWith(Triple::getObject).toSet();
            if (objects.isEmpty()) return NullIterator.instance();
            Set<Node> predicates = g.find(a, Nodes.D2RQpropertyName, m.getObject()).mapWith(Triple::getObject).toSet();
            if (predicates.isEmpty()) return NullIterator.instance();
            Node o = objects.iterator().next();
            Node p = predicates.iterator().next();
            return g.find(Node.ANY, Nodes.D2RQadditionalClassDefinitionProperty, a)
                    .mapWith(x -> findFirstClass(g, x.getSubject()).orElse(null))
                    .andThen(g.find(Node.ANY, Nodes.D2RQadditionalClassDefinitionProperty, a)
                            .mapWith(x -> findFirstProperty(g, x.getSubject()).orElse(null)))
                    .filterDrop(Objects::isNull)
                    .mapWith(s -> Triple.create(s, p, o));
        }), m);
    }

    private DynamicTriples annotations(Node desiredPredicate,
                                       Node mappingClassPredicate,
                                       Node mappingPropertyPredicate) {
        return new DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(desiredPredicate);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph graph, Triple m) {
                ExtendedIterator<Triple> a = listLiteralAnnotations(graph, desiredPredicate, mappingClassPredicate,
                        (g, c) -> listClasses(g, c));
                ExtendedIterator<Triple> b = listLiteralAnnotations(graph, desiredPredicate, mappingPropertyPredicate,
                        (g, p) -> listProperties(g, p));
                return distinctMatch(Iter.concat(a, b), m);
            }
        };
    }

    protected DynamicTriples objectPropertyDeclarations() {
        return propertyDeclarations(Nodes.OWLObjectProperty,
                g -> listObjectProperties(g).filterDrop(reservedProperties::contains));
    }

    protected DynamicTriples dataPropertyDeclarations() {
        return propertyDeclarations(Nodes.OWLDataProperty,
                g -> listDataProperties(g).filterDrop(reservedProperties::contains));
    }

    protected DynamicTriples annotationPropertyDeclarations() {
        return propertyDeclarations(Nodes.OWLAnnotationProperty,
                g -> listAnnotationProperties(g).filterDrop(reservedProperties::contains));
    }

    protected DynamicTriples labels() {
        return annotations(Nodes.RDFSlabel, Nodes.D2RQclassDefinitionLabel, Nodes.D2RQpropertyDefinitionLabel);
    }

    protected DynamicTriples comments() {
        return annotations(Nodes.RDFScomment, Nodes.D2RQclassDefinitionComment, Nodes.D2RQpropertyDefinitionComment);
    }

    public ExtendedIterator<Resource> listClasses(Model m, Resource classMap) {
        return listClasses(m.getGraph(), classMap.asNode()).mapWith(n -> m.getRDFNode(n).asResource());
    }

    /**
     * Lists all OWL classes that are attached to the given ClassMap.
     *
     * @param g        {@link Graph} to search in
     * @param classMap {@link Node} ClassMap
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    public ExtendedIterator<Node> listClasses(Graph g, Node classMap) {
        return SchemaHelper.listClasses(g, classMap)
                .filterDrop(n -> Nodes.OWLClass.equals(n) || Nodes.OWLNamedIndividual.equals(n))
                .filterDrop(classes::contains);
    }

    /**
     * Lists all OWL properties that are attached to the given PropertyBridge.
     *
     * @param g              {@link Graph} to search in
     * @param propertyBridge {@link Node} PropertyBridge
     * @return {@link ExtendedIterator} of {@link Node}s
     */
    public ExtendedIterator<Node> listProperties(Graph g, Node propertyBridge) {
        return SchemaHelper.listProperties(g, propertyBridge).filterDrop(reservedProperties::contains);
    }

    public DynamicTriples buildDynamicGraph() {
        return ontologyID()
                .andThen(classDeclarations())
                .andThen(objectPropertyDeclarations())
                .andThen(dataPropertyDeclarations())
                .andThen(annotationPropertyDeclarations())
                .andThen(domainAssertions())
                .andThen(rangeAssertions())
                .andThen(additionalAssertions())
                .andThen(labels())
                .andThen(comments())
                ;
    }

}
