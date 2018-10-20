package de.fuberlin.wiwiss.d2rq.map.impl.schema;

import de.fuberlin.wiwiss.d2rq.jena.VirtualGraph;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.PrefixMappingMem;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.utils.BuiltIn;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * A factory to provide Dynamic Schema Graph.
 * Created by @ssz on 22.09.2018.
 */
@SuppressWarnings("WeakerAccess")
public class SchemaGenerator {

    private static SchemaGenerator generator = createDefault(BuiltIn.OWL_VOCABULARY, true);

    private final SchemaAssembler assembler;

    public SchemaGenerator(SchemaAssembler assembler) {
        this.assembler = Objects.requireNonNull(assembler);
    }

    public static SchemaGenerator getInstance() {
        return generator;
    }

    /**
     * Sets a new instance.
     * Just in case, in order to leave the ability to change behavior in static way.
     *
     * @param g {@link SchemaGenerator} to set, not {@code null}
     * @return {@link SchemaGenerator} the previously associated schema generator factory
     */
    public static SchemaGenerator setInstance(SchemaGenerator g) {
        SchemaGenerator prev = generator;
        generator = Objects.requireNonNull(g);
        return prev;
    }

    public static SchemaGenerator createDefault(BuiltIn.Vocabulary vocabulary, boolean withEquivalent) {
        SchemaAssembler assembler = new SchemaAssembler(asSet(vocabulary.classes()),
                asSet(vocabulary.reservedProperties()),
                withEquivalent);
        return new SchemaGenerator(assembler);
    }

    private static Set<Node> asSet(Collection<? extends RDFNode> from) {
        return from.stream().map(FrontsNode::asNode).collect(Iter.toUnmodifiableSet());
    }


    /**
     * Creates a virtual schema graph that reflects the given mapping graph.
     * The return graph is an {@link Union Union Graph} which consist of two parts:
     * the left is a {@code MaskGraph}, which hides everything related to D2RQ language,
     * and the right is a {@code DynamicGraph}, which maps D2RQ instructions to the OWL2 assertions.
     * The adding and removing are performed transitively on the base (specified) graph.
     *
     * @param base {@link Graph}, not {@code null}, containing D2RQ instructions
     * @return {@link Graph}, virtual graph, containing only the OWL2 assertions
     * @see VirtualGraph#createMaskGraph(Graph, BiPredicate)
     * @see VirtualGraph#createDynamicGraph(Graph, VirtualGraph.DynamicTriples)
     */
    public Graph createMagicGraph(Graph base) {
        Objects.requireNonNull(base, "Null base");
        Graph left = VirtualGraph.createMaskGraph(base, buildMaskGraph());
        Graph right = VirtualGraph.createDynamicGraph(base, assembler.buildDynamicGraph());
        Graph res = new Union(left, right) {

            @Override
            protected PrefixMapping createPrefixMapping() {
                PrefixMapping pm = base.getPrefixMapping();
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
                };
            }

            @Override
            protected ExtendedIterator<Triple> _graphBaseFind(final Triple t) {
                // the duplicate checking is not needed in this case
                return L.find(t).andThen(R.find(t));
            }

            @Override
            public void performAdd(Triple t) {
                if (R.contains(t)) {
                    // do not add inferred triples
                    return;
                }
                base.add(t);
            }

            @Override
            public void performDelete(Triple t) {
                base.delete(t);
            }

            @Override
            public String toString() {
                // do not allow printing of all data content
                return "MagicGraph@" + Integer.toHexString(hashCode());
            }
        };
        res.getPrefixMapping().setNsPrefixes(createSchemaPrefixes(base));
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

    public ExtendedIterator<Resource> listClasses(Model m, Resource classMap) {
        return assembler.listClasses(m.getGraph(), classMap.asNode()).mapWith(n -> m.getRDFNode(n).asResource());
    }

}
