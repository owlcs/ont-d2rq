package com.github.owlcs.d2rq;

import com.github.owlcs.d2rq.conf.ConnectionData;
import com.github.owlcs.d2rq.utils.D2RQGraphUtils;
import com.github.owlcs.d2rq.utils.OWLUtils;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.SetOntologyID;
import org.topbraid.spin.inference.SPINInferences;
import org.topbraid.spin.system.SPINModuleRegistry;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.ontapi.OntologyManager;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntIndividualImpl;
import ru.avicomp.ontapi.jena.impl.PersonalityModel;
import ru.avicomp.ontapi.jena.impl.conf.*;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.tests.SpinMappingTest;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.util.Optional;

/**
 * To test SPIN + D2RQ + ONT-API.
 * See base class {@link SpinMappingTest}
 * It performs mapping of two columns (papers.Title and papers.Year) to the DataProperty assertion
 * (by using function: <a href='http://topbraid.org/spin/spinmapl#concatWithSeparator'>spinmapl:concatWithSeparator</a>).
 * The iri of result individuals would be the same as in the source OWL representation of DB
 * (bu using function: <a href='http://topbraid.org/spin/spinmapl#self'>spinmapl:self</a>).
 * <p>
 * Created by @szuev on 25.02.2017.
 */
@SuppressWarnings("WeakerAccess")
@RunWith(Parameterized.class)
public class D2RQSpinTest extends SpinMappingTest {

    static {
        OntModelFactory.init();
        // register explicitly due to switching from topbraid-spin to ont-map:
        SpinModelConfig.init(BuiltinPersonalities.model);
    }

    private ConnectionData data;

    public D2RQSpinTest(ConnectionData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static ConnectionData[] getData() {
        return ConnectionData.values();
    }

    @Override
    public void setUpManager(OntologyManager manager) {
        super.setUpManager(manager);
        OntPersonality newPersonality = D2RQModelConfig.D2RQ_PERSONALITY;
        manager.setOntologyLoaderConfiguration(manager.getOntologyLoaderConfiguration().setPersonality(newPersonality));
    }

    @Override
    public void validate(OntGraphModel source, OntGraphModel target) {
        OntGraphModel src = OWLUtils.toMemory(source);
        super.validate(src, target);
        Assert.assertEquals("Incorrect number of result individuals.", 7,
                target.individuals().peek(x -> LOGGER.debug("Result Individual {}", x)).count());
        OntologyModel o = manager.getOntology(IRI.create(source.getID().getURI()));
        Assert.assertNotNull(o);
        ReadWriteUtils.print(o);
        OWLUtils.closeConnections(source);
    }

    public static MappingFilter prepareDataFilter(ConnectionData data) {
        String papersTitleDataPropertyURI = ConnectionData.DEFAULT_BASE_IRI +
                MappingGenerator.DEFAULT_SCHEMA_NS.replaceAll("/$", "") + "#papers_Title";
        String papersYearDataPropertyURI = ConnectionData.DEFAULT_BASE_IRI +
                MappingGenerator.DEFAULT_SCHEMA_NS.replaceAll("/$", "") + "#papers_Year";
        MappingFilter filter = MappingFilter.create()
                .includeProperty(data.toIRI(papersTitleDataPropertyURI))
                .includeProperty(data.toIRI(papersYearDataPropertyURI));
        LOGGER.debug("{}", filter);
        return filter;
    }

    public static D2RQGraphDocumentSource createSource(ConnectionData data, String name) {
        LOGGER.info("Create source model based on {}", data.getJdbcURI(name));
        MappingFilter filter = prepareDataFilter(data);
        return data.toDocumentSource(name).filter(filter);
    }

    @Override
    public OntGraphModel createSourceModel() {
        D2RQGraphDocumentSource source = createSource(data, "iswc");
        OntologyModel res;
        try {
            res = manager.loadOntologyFromOntologyDocument(source);
        } catch (OWLOntologyCreationException e) {
            throw new AssertionError(e);
        }
        res.applyChange(new SetOntologyID(res, IRI.create("http://source.avicomp.ru")));
        return res.asGraphModel();
    }

    @Override
    public void runInferences(OntologyModel mapping, Model target) {
        // make a Virtual UnionGraph with no repetitions:
        Graph graph = OWLUtils.build(OWLUtils.getUnionGraph(mapping.asGraphModel()),
                UnionGraph::new, D2RQGraphUtils::toVirtual);
        Model source = ModelFactory.createModelForGraph(graph);
        LOGGER.info("Run Inferences");
        SPINModuleRegistry.get().init();
        SPINModuleRegistry.get().registerAll(source, null);
        SPINInferences.run(source, target, null, null, false, null);
    }

    /**
     * A Personalities holder.
     *
     * @see OntModelConfig
     */
    @SuppressWarnings("WeakerAccess")
    public static class D2RQModelConfig {
        /**
         * A {@link OntPersonality Ontology Personality},
         * that does not require explicit {@link OWL#NamedIndividual owl:NamedIndividual} declarations.
         * This personality is based on {@link OntModelConfig#ONT_PERSONALITY_LAX} and other settings are standard.
         */
        public static final OntPersonality D2RQ_PERSONALITY = PersonalityBuilder.from(OntModelConfig.ONT_PERSONALITY_LAX)
                .add(OntIndividual.Named.class, createNamedIndividualFactory())
                .build();


        public static ObjectFactory createNamedIndividualFactory() {
            OntMaker maker = new OntMaker.Default(IndividualImpl.class) {

                @Override
                public EnhNode instance(Node node, EnhGraph eg) {
                    return new IndividualImpl(node, eg);
                }
            };
            OntFinder finder = new OntFinder.ByPredicate(RDF.type);
            OntFilter filter = OntFilter.URI
                    .and(new OntFilter.HasPredicate(RDF.type))
                    .and((s, g) -> Iter.asStream(g.asGraph().find(s, RDF.type.asNode(), Node.ANY)).map(Triple::getObject)
                            .anyMatch(o -> PersonalityModel.canAs(OntCE.class, o, g)));
            return new CommonFactoryImpl(maker, finder, filter) {
                @Override
                public String toString() {
                    return "NamedIndividualFactory";
                }
            };
        }

        /**
         * Named individual which does not required explicit {@code _:x rdf:type owl:NamedIndividual} declaration, just only class.
         */
        public static class IndividualImpl extends OntIndividualImpl.NamedImpl {
            private IndividualImpl(Node n, EnhGraph m) {
                super(n, m);
            }

            @Override
            public Optional<OntStatement> findRootStatement() {
                return getOptionalRootStatement(this, OWL.NamedIndividual);
            }
        }
    }
}
