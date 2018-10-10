package de.fuberlin.wiwiss.d2rq.map.impl.schema;

import de.fuberlin.wiwiss.d2rq.jena.DynamicGraph;
import de.fuberlin.wiwiss.d2rq.jena.DynamicTriples;
import de.fuberlin.wiwiss.d2rq.jena.MaskGraph;
import org.apache.jena.graph.*;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import ru.avicomp.ontapi.jena.utils.BuiltIn;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by @ssz on 22.09.2018.
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

    public static Graph createMagicGraph(Graph base) {
        BiPredicate<Graph, Triple> toHide = buildHiddenPart();
        DynamicTriples toShow = buildVisiblePart();
        return new Union(new MaskGraph(base, toHide), new DynamicGraph(base, toShow));
    }

    public DynamicTriples ontologyID() {
        return (g, m) -> {
            Node ms = m.getSubject();
            Node mp = m.getPredicate();
            if (!mp.matches(SchemaHelper.RDFSisDefinedBy) && !mp.matches(SchemaHelper.RDFtype)) {
                return NullIterator.instance();
            }
            Node s = Graphs.ontologyNode(g).orElse(null);
            if (s == null) return NullIterator.instance();
            if (!ms.matches(s)) {
                return NullIterator.instance();
            }
            ExtendedIterator<Triple> res = SchemaHelper.listJdbsNodes(g)
                    .mapWith(n -> Triple.create(s, SchemaHelper.RDFSisDefinedBy, NodeFactory.createURI(n.getLiteralValue().toString())));
            return res.filterKeep(m::matches);
        };
    }

    private static DynamicTriples classDefinitions(Set<Node> builtins) {
        return new DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                Node mp = m.getPredicate();
                if (mp.matches(SchemaHelper.RDFtype) && m.getObject().matches(SchemaHelper.OWLClass))
                    return true;
                return mp.matches(SchemaHelper.OWLequivalentClass);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return Iter.flatMap(g.find(Node.ANY, SchemaHelper.RDFtype, SchemaHelper.D2RQClassMap).mapWith(Triple::getSubject),
                        n -> {
                            Set<Node> classes = SchemaHelper.listClasses(g, n).toSet();
                            Set<Triple> res = classes.stream()
                                    .filter(c -> !builtins.contains(c))
                                    .map(c -> Triple.create(c, SchemaHelper.RDFtype, SchemaHelper.OWLClass))
                                    .collect(Collectors.toSet());
                            if (classes.size() > 1) {
                                Iterator<Node> nodes = classes.iterator();
                                Node first = nodes.next();
                                while (nodes.hasNext()) {
                                    res.add(Triple.create(first, SchemaHelper.OWLequivalentClass, nodes.next()));
                                }
                            }
                            return WrappedIterator.create(res.iterator());
                        }).filterKeep(m::matches);
            }

        };
    }

    private static DynamicTriples propertyDeclarations(Node type,
                                                       Function<Graph, ExtendedIterator<Node>> extractor,
                                                       Set<Node> builtIns) {
        return new DynamicTriples() {

            @Override
            public boolean test(Triple m) {
                return m.getObject().matches(type) && m.getPredicate().matches(SchemaHelper.RDFtype);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return Iter.distinct(extractor.apply(g)
                        .filterDrop(builtIns::contains)
                        .mapWith(s -> Triple.create(s, SchemaHelper.RDFtype, type))
                        .filterKeep(m::matches));
            }
        };
    }

    public static DynamicTriples domainAssertions(Set<Node> builtIns) {
        return new DynamicTriples() {

            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(SchemaHelper.RDFSdomain);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                return Iter.distinct(Iter.flatMap(g.find(Node.ANY, SchemaHelper.D2RQbelongsToClassMap, Node.ANY),
                        t -> Iter.flatMap(SchemaHelper.listProperties(g, t.getSubject()).filterDrop(builtIns::contains),
                                s -> SchemaHelper.listClasses(g, t.getObject()).mapWith(o -> Triple.create(s, SchemaHelper.RDFSdomain, o))))
                        .filterKeep(m::matches));
            }
        };
    }

    public static DynamicTriples rangeAssertions(Set<Node> builtIns) {
        return new DynamicTriples() {
            @Override
            public boolean test(Triple m) {
                return m.getPredicate().matches(SchemaHelper.RDFSrange);
            }

            @Override
            public ExtendedIterator<Triple> list(Graph g, Triple m) {
                ExtendedIterator<Triple> op = Iter.flatMap(g.find(Node.ANY, SchemaHelper.D2RQrefersToClassMap, Node.ANY),
                        t -> Iter.flatMap(SchemaHelper.listProperties(g, t.getSubject()).filterDrop(builtIns::contains),
                                s -> SchemaHelper.listClasses(g, t.getObject()).mapWith(o -> Triple.create(s, SchemaHelper.RDFSrange, o))));

                ExtendedIterator<Triple> dp = Iter.flatMap(g.find(Node.ANY, SchemaHelper.RDFtype, SchemaHelper.D2RQPropertyBridge),
                        t -> Iter.flatMap(SchemaHelper.listProperties(g, t.getSubject()).filterDrop(builtIns::contains),
                                s -> SchemaHelper.listDataRanges(g, t.getSubject()).mapWith(o -> Triple.create(s, SchemaHelper.RDFSrange, o))));

                return Iter.distinct(Iter.concat(op, dp)).filterKeep(m::matches);
            }
        };
    }

    public static DynamicTriples buildVisiblePart(BuiltIn.Vocabulary builtIn) {
        return new SchemaGenerator(builtIn).build();
    }

    public static DynamicTriples buildVisiblePart() {
        return buildVisiblePart(BuiltIn.OWL_VOCABULARY);
    }

    public static BiPredicate<Graph, Triple> buildHiddenPart() {
        BiPredicate<Graph, Triple> res = null;
        for (Node type : Arrays.asList(SchemaHelper.D2RQClassMap, SchemaHelper.D2RQPropertyBridge)) {
            BiPredicate<Graph, Triple> b = hidePart(type);
            if (res == null) {
                res = b;
            } else {
                res = res.or(b);
            }
        }
        return res;
    }

    public static BiPredicate<Graph, Triple> hidePart(Node type) {
        return (g, t) -> g.contains(t.getSubject(), SchemaHelper.RDFtype, type);
    }

    public DynamicTriples classDefinitions() {
        return classDefinitions(classes);
    }

    public DynamicTriples objectPropertyDeclarations() {
        return propertyDeclarations(SchemaHelper.OWLObjectProperty, SchemaHelper::listObjectProperties, reservedProperties);
    }

    public DynamicTriples dataPropertyDeclarations() {
        return propertyDeclarations(SchemaHelper.OWLDataProperty, SchemaHelper::listDataProperties, reservedProperties);
    }

    public DynamicTriples annotationPropertyDeclarations() {
        return propertyDeclarations(SchemaHelper.OWLAnnotationProperty, SchemaHelper::listAnnotationProperties, reservedProperties);
    }

    public DynamicTriples domainAssertions() {
        return domainAssertions(reservedProperties);
    }

    public DynamicTriples rangeAssertions() {
        return rangeAssertions(reservedProperties);
    }

    public DynamicTriples build() {
        return ontologyID()
                .andThen(classDefinitions())
                .andThen(objectPropertyDeclarations())
                .andThen(dataPropertyDeclarations())
                .andThen(annotationPropertyDeclarations())
                .andThen(domainAssertions())
                .andThen(rangeAssertions());
    }

}
