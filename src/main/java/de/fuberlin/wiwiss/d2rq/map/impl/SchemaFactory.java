package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.jena.VirtualGraph;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.PropertyBridge;
import de.fuberlin.wiwiss.d2rq.vocab.AVC;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.PrefixMappingMem;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.utils.BuiltIn;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * A factory-jelper to produce {@link SchemaGraph D2RQ Schema Graph}, that is assumed to be a valid OWL2 ontology.
 * It contains two important methods: {@link #inferSchema(MappingImpl)} and {@link #compileSchema(MappingImpl)}.
 * The first one is for reading a mapping graph, and the second one is for writing to a mapping graph.
 * Both methods together ensure a valid OWL2 schema,
 * but (in case of dynamic properties) this schema may partially lies not in memory.
 * <p>
 * Created by @ssz on 22.09.2018.
 *
 * @see <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL 2 Quick Reference Guide</a>
 */
@SuppressWarnings("WeakerAccess")
public class SchemaFactory {

    private static SchemaFactory factory = createDefault(BuiltIn.OWL_VOCABULARY, true);

    private final VirtualGraph.DynamicTriples virtualGraphBuilder;
    private final BiPredicate<Graph, Triple> maskGraphBuilder;
    private final BiFunction<Mapping, ClassMap, ExtendedIterator<Resource>> listClasses;

    /**
     * Constructs an instance.
     *
     * @param virtualGraph {@link VirtualGraph.DynamicTriples} - to build the right part of a schema graph,
     *                     to infer data from a mapping
     * @param maskGraph    {@link BiPredicate} - to build the left part of a schema graph,
     *                     to hide data from a mapping
     * @param listClasses  {@link BiFunction} - a facility to retrieve all OWL-classes from a mapping,
     *                     it is used while compile schema
     */
    public SchemaFactory(VirtualGraph.DynamicTriples virtualGraph,
                         BiPredicate<Graph, Triple> maskGraph,
                         BiFunction<Mapping, ClassMap, ExtendedIterator<Resource>> listClasses) {
        this.virtualGraphBuilder = Objects.requireNonNull(virtualGraph);
        this.maskGraphBuilder = Objects.requireNonNull(maskGraph);
        this.listClasses = Objects.requireNonNull(listClasses);
    }

    /**
     * Returns a global saved factory instance.
     *
     * @return {@link SchemaFactory}, not {@code null}
     */
    public static SchemaFactory getInstance() {
        return factory;
    }

    /**
     * Sets a new factory instance.
     * Just in case,
     * and in order to leave the ability to change the behavior in a static way.
     *
     * @param g {@link SchemaFactory} to set, not {@code null}
     * @return {@link SchemaFactory} the previously associated schema generator factory
     */
    public static SchemaFactory setInstance(SchemaFactory g) {
        SchemaFactory prev = factory;
        factory = Objects.requireNonNull(g);
        return prev;
    }

    /**
     * Creates a default factory instance.
     *
     * @param vocabulary     {@link BuiltIn.Vocabulary}
     * @param withEquivalent boolean to control {@code owl:equivalentClass} and {@code owl:equivalentProperty}
     * @return {@link SchemaFactory}, not {@code null}
     */
    public static SchemaFactory createDefault(BuiltIn.Vocabulary vocabulary, boolean withEquivalent) {
        SchemaBuilder builder = new SchemaBuilder(vocabulary, withEquivalent);
        BiFunction<Mapping, ClassMap, ExtendedIterator<Resource>> listClasses = (m, c) -> listClasses(builder, m, c);
        VirtualGraph.DynamicTriples virtualGraph = builder.buildDynamicGraph();
        BiPredicate<Graph, Triple> maskGraph = buildMaskGraph();
        return new SchemaFactory(virtualGraph, maskGraph, listClasses);
    }

    /**
     * Creates a virtual schema graph that reflects the given mapping graph.
     * The return graph is an union of two parts:
     * the left is a {@link VirtualGraph}, that hides everything related to D2RQ language,
     * and the right is a {@link VirtualGraph}, that maps D2RQ instructions to the OWL2 assertions.
     * <p>
     * It's a read operation, no changes on the base (mapping) graph is made.
     * <p>
     * Note that the resulting graph is transparent to change.
     * This means that any changes made later on the resulting graph will fall directly into the base graph.
     * So, if you add an OWL class, the corresponding triples will be added directly to the mapping graph
     * but will be also seen in the returning graph.
     *
     * @param impl {@link MappingImpl}, not {@code null}, a mapping impl containing D2RQ instructions
     * @return {@link Graph}, virtual graph, containing only the OWL2 assertions
     * @see VirtualGraph#createMaskGraph(Graph, BiPredicate)
     * @see VirtualGraph#createDynamicGraph(Graph, VirtualGraph.DynamicTriples)
     */
    public Graph inferSchema(MappingImpl impl) {
        Graph map = Objects.requireNonNull(impl, "Null mapping").asModel().getGraph();
        Graph left = VirtualGraph.createMaskGraph(map, maskGraphBuilder);
        Graph right = VirtualGraph.createDynamicGraph(map, virtualGraphBuilder);
        return new SchemaGraph(impl) {

            @Override
            protected PrefixMapping createPrefixMapping() {
                PrefixMapping from = collectUserDefinedPrefixes(map);
                PrefixMapping pm = map.getPrefixMapping();
                return new PrefixMappingMem() {
                    @Override
                    protected void add(String prefix, String uri) {
                        super.add(prefix, uri);
                        pm.setNsPrefix(prefix, uri);
                    }

                    @Override
                    protected void remove(String prefix) {
                        super.remove(prefix);
                        pm.removeNsPrefix(prefix);
                    }
                }.setNsPrefixes(from).setNsPrefixes(MappingFactory.SCHEMA);
            }

            @Override
            protected ExtendedIterator<Triple> graphBaseFind(Triple m) {
                if (!getMapping().isEmpty()) {
                    // to avoid java.util.ConcurrentModificationException:
                    mapping.compiledPropertyBridges();
                }
                // the duplicate checking is not needed in this case
                return left.find(m).andThen(right.find(m));
            }

            @Override
            public void performAdd(Triple t) {
                if (right.contains(t)) {
                    // do not add inferred triples
                    return;
                }
                map.add(t);
            }

            @Override
            public void performDelete(Triple t) {
                map.delete(t);
            }
        };
    }

    /**
     * Compiles the schema for the given mapping.
     * This method generates additional different {@link ClassMap}s and {@link PropertyBridge}s in order
     * to make data satisfy OWL2 requirements.
     * In contradistinction to the {@link #inferSchema(MappingImpl)} method, it is a write operation.
     * But any changes are annotated with the property {@link AVC#autoGenerated} and can be safely undone.
     * <p>
     * Currently this method handles the following cases:
     * <ul>
     * <li>a class type ({@code d2rq:class}) for {@code ClassMap}s if it is missed</li>
     * <li>{@code owl:NamedIndividual} declaration for {@code ClassMap}s, if the desired individual is named (i.e. it has IRI)</li>
     * <li>{@code owl:sameAs} and {@code owl:differentFrom} for {@code PropertyBridge}s
     * (the right parts of these statements should also have it is own declaration)</li>
     * <li>dynamic class-maps for {@code PropertyBridge}s with {@code rdf:type}</li>
     * <li>dynamic properties OWL-declarations</li>
     * </ul>
     *
     * @param impl {@link MappingImpl}, not {@code null}, a mapping impl containing D2RQ instructions
     */
    public void compileSchema(MappingImpl impl) {
        // owl:NamedIndividual declaration + class type for anonymous individuals:
        impl.classMaps()
                .filterDrop(ResourceMap::isAutoGenerated)
                .forEachRemaining(c -> {
                    Set<Resource> classes = listClasses.apply(impl, c).toSet();
                    if (classes.isEmpty()) {
                        Resource clazz = c.asResource();
                        if (clazz.isAnon()) {
                            // require all classes to be named:
                            clazz = AVC.UnknownClass(clazz.getId().toString());
                        }
                        MappingUtils.fetchPropertyBridge(c, clazz);
                    }
                    if (c.getBNodeIdColumns() == null && c.constantValue().map(RDFNode::isURIResource).orElse(true)) {
                        // explicit declaration for named individuals
                        MappingUtils.fetchPropertyBridge(c, OWL.NamedIndividual);
                    }
                });

        // owl:sameAs, owl:differentFrom individual assertions:
        impl.propertyBridges()
                .filterDrop(ResourceMap::isAutoGenerated)
                .filterKeep(p -> p.listProperties().map(FrontsNode::asNode)
                        .anyMatch(Nodes.SYMMETRIC_INDIVIDUAL_PREDICATES::contains))
                .filterDrop(p -> p.getURIColumn() == null)
                .forEachRemaining(p -> MappingUtils.fetchClassMap(p, OWL.NamedIndividual, D2RQ.uriColumn));

        // creates dynamic class map for property bridge with rdf:type
        impl.propertyBridges()
                .filterDrop(ResourceMap::isAutoGenerated)
                .filterKeep(p -> p.listProperties().anyMatch(RDF.type::equals))
                .filterDrop(p -> p.getURIPattern() == null)
                .forEachRemaining(p -> MappingUtils.fetchClassMap(p, OWL.Class, D2RQ.uriPattern)
                        .setContainsDuplicates(true));

        // dynamic properties:
        impl.propertyBridges()
                .filterKeep(x -> Iter.findFirst(x.dynamicProperties()).isPresent())
                .forEachRemaining(p -> {
                    Set<Resource> types = new HashSet<>();
                    if (isDataProperty(impl, p)) {
                        types.add(OWL.DatatypeProperty);
                    }
                    if (isObjectProperty(impl, p)) {
                        types.add(OWL.ObjectProperty);
                    }
                    if (types.isEmpty()) {
                        types.add(OWL.AnnotationProperty);
                    }
                    p.dynamicProperties().forEachRemaining(s -> {
                        ClassMapImpl x = MappingUtils.fetchClassMap(p, s);
                        types.forEach(x::addClass);
                    });
                });
    }

    /**
     * Retrieves all user-defined prefixes form a mapping graph.
     *
     * @param mapping {@link Graph} with D2RQ instructions
     * @return {@link PrefixMapping}
     */
    public static PrefixMapping collectUserDefinedPrefixes(Graph mapping) {
        Map<String, String> res = mapping.getPrefixMapping().getNsPrefixMap();
        MappingFactory.MAPPING.getNsPrefixMap().forEach(res::remove);
        return PrefixMapping.Factory.create().setNsPrefixes(res);
    }

    public static BiPredicate<Graph, Triple> buildMaskGraph() {
        BiPredicate<Graph, Triple> res = null;
        for (Node type : Nodes.D2RQ_TYPES) {
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
        return (g, t) -> g.contains(t.getSubject(), Nodes.RDF_FTYPE, type);
    }

    public static boolean isDataProperty(Mapping m, PropertyBridge propertyBridge) {
        return SchemaHelper.isDataProperty(m.asModel().getGraph(), propertyBridge.asResource().asNode());
    }

    public static boolean isObjectProperty(Mapping m, PropertyBridge propertyBridge) {
        return SchemaHelper.isObjectProperty(m.asModel().getGraph(), propertyBridge.asResource().asNode());
    }

    private static ExtendedIterator<Resource> listClasses(SchemaBuilder helper, Mapping m, ClassMap c) {
        Model model = m.asModel();
        return helper.listOWLClasses(m.asModel().getGraph(), c.asResource().asNode()).mapWith(model::wrapAsResource);
    }

}
