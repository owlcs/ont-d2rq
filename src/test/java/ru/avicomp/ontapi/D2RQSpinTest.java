package ru.avicomp.ontapi;

import java.util.List;

import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.Model;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.SetOntologyID;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import ru.avicomp.ontapi.jena.Hybrid;
import ru.avicomp.ontapi.jena.impl.configuration.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.configuration.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
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

    private ONTAPITests.ConnectionData data;

    public D2RQSpinTest(ONTAPITests.ConnectionData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<ONTAPITests.ConnectionData> getData() {
        return ONTAPITests.ConnectionData.asList();
    }

    @Override
    public void prepare() {
        super.prepare();
        OntPersonality newGlobalPersonality = ONTAPITests.createD2RQPersonality();
        OntModelConfig.setPersonality(newGlobalPersonality);
    }

    @Override
    public void validate(OntGraphModel source, OntGraphModel target) {
        super.validate(source, target);
        target.listNamedIndividuals().forEach(LOGGER::debug);
        Assert.assertEquals("Incorrect number of result individuals.", 7, target.listNamedIndividuals().count());
        OntologyModel o = manager.getOntology(IRI.create(source.getID().getURI()));
        ONTAPITests.switchTo(o, GraphMem.class).close();
        ReadWriteUtils.print(o);
    }

    public MappingFilter prepareDataFilter() {
        String papersTitleDataPropertyURI = D2RQGraphDocumentSource.DEFAULT_BASE_IRI + MappingGenerator.DEFAULT_SCHEMA_NS.replaceAll("/$", "") + "#papers_Title";
        String papersYearDataPropertyURI = D2RQGraphDocumentSource.DEFAULT_BASE_IRI + MappingGenerator.DEFAULT_SCHEMA_NS.replaceAll("/$", "") + "#papers_Year";
        MappingFilter filter = MappingFilter.create().includeProperty(data.toIRI(papersTitleDataPropertyURI)).includeProperty(data.toIRI(papersYearDataPropertyURI));
        LOGGER.debug(filter);
        return filter;
    }

    @Override
    public OntGraphModel createSourceModel() throws Exception {
        LOGGER.info("Create source model based on " + data.getIRI());
        MappingFilter filter = prepareDataFilter();
        D2RQGraphDocumentSource source = data.toDocumentSource().filter(filter);
        OntologyModel res = (OntologyModel) manager.loadOntologyFromOntologyDocument(source);
        res.applyChange(new SetOntologyID(res, IRI.create("http://source.avicomp.ru")));
        return res.asGraphModel();
    }

    @Override
    public void runInferences(OntologyModel mapping, Model target) {
        OntologyModel source = mapping.imports().map(OntologyModel.class::cast)
                .filter(m -> Hybrid.class.isInstance(m.asGraphModel().getBaseGraph())).findFirst().orElse(null);
        Assert.assertNotNull("Can't find source model.", source);
        // todo: need assemble new graph-model for inference, initial graph (base, facade) should be unchanged.
        ONTAPITests.switchTo(source, GraphD2RQ.class);
        super.runInferences(mapping, target);
    }
}
