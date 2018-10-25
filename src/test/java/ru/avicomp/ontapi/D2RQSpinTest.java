package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.Graph;
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
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.map.spin.SpinModelConfig;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.D2RQModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.utils.D2RQGraphs;
import ru.avicomp.ontapi.tests.SpinMappingTest;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

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
@RunWith(Parameterized.class)
public class D2RQSpinTest extends SpinMappingTest {

    static {
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
        OntGraphModel src = D2RQGraphs.reassembly(source);
        super.validate(src, target);
        target.listNamedIndividuals().forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect number of result individuals.", 7, target.listNamedIndividuals().count());
        OntologyModel o = manager.getOntology(IRI.create(source.getID().getURI()));
        Assert.assertNotNull(o);
        ReadWriteUtils.print(o);
        D2RQGraphs.close((UnionGraph) source.getGraph());
    }

    public MappingFilter prepareDataFilter() {
        String papersTitleDataPropertyURI = ConnectionData.DEFAULT_BASE_IRI + MappingGenerator.DEFAULT_SCHEMA_NS.replaceAll("/$", "") + "#papers_Title";
        String papersYearDataPropertyURI = ConnectionData.DEFAULT_BASE_IRI + MappingGenerator.DEFAULT_SCHEMA_NS.replaceAll("/$", "") + "#papers_Year";
        MappingFilter filter = MappingFilter.create().includeProperty(data.toIRI(papersTitleDataPropertyURI)).includeProperty(data.toIRI(papersYearDataPropertyURI));
        LOGGER.debug("{}", filter);
        return filter;
    }

    @Override
    public OntGraphModel createSourceModel() {
        LOGGER.info("Create source model based on {}", data.getJdbcIRI("iswc"));
        MappingFilter filter = prepareDataFilter();
        D2RQGraphDocumentSource source = data.toDocumentSource("iswc").filter(filter);
        //MappingTestHelper.print(source.getMapping());

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
        Graph graph = D2RQGraphs.reassembly((UnionGraph) mapping.asGraphModel().getGraph());
        Model source = ModelFactory.createModelForGraph(graph);
        LOGGER.info("Run Inferences");
        SPINModuleRegistry.get().init();
        SPINModuleRegistry.get().registerAll(source, null);
        SPINInferences.run(source, target, null, null, false, null);
    }

}
