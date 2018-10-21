package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.conf.ISWCData;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.utils.OWLUtils;

/**
 * Created by @ssz on 20.10.2018.
 */
public class OntMapTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OntMapTest.class);

    @Test
    public void testCollectAddresses() throws OWLOntologyCreationException {
        Mapping d2rq = ISWCData.POSTGRES.loadMapping("http://test.ex/src/");
        d2rq.getConfiguration().setControlOWL(true).setServeVocabulary(true);

        OWLMapManager manager = Managers.createOWLMapManager();
        OntGraphModel src = manager.loadOntologyFromOntologyDocument(D2RQGraphDocumentSource.create(d2rq)).asGraphModel();
        src.setID("http://test.ex/src");
        src.setNsPrefix("x", "file:///Users/richard/D2RQ/workspace/D2RQ/doc/example/mapping-iswc.ttl#");

        OntGraphModel dst = assembleTarget(manager);

        MapModel spin = assembleSpinMapping(manager, src, dst);

        LOGGER.debug("Run inference.");
        manager.getInferenceEngine().run(spin, d2rq.getData(), dst.getBaseGraph());
        LOGGER.debug("Done.");

        Assert.assertEquals(1, dst.listNamedIndividuals().count());

        OntIndividual res = dst.listNamedIndividuals().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(9, res.positiveAssertions().peek(x -> LOGGER.debug("Address: '{}'", x.getString())).count());
        D2RQTestHelper.print(dst);
    }

    private static OntGraphModel assembleTarget(OntologyManager m) {
        String dstURI = "http://test.ex/dst";
        String dstNS = dstURI + "#";
        OntGraphModel res = m.createGraphModel(dstURI)
                .setNsPrefix("dst", dstNS).setNsPrefixes(OntModelFactory.STANDARD);
        OntClass dstClass = res.createOntEntity(OntClass.class, dstNS + "Organizations");
        OntNDP dstProp = res.createOntEntity(OntNDP.class, dstNS + "address");
        dstProp.addDomain(dstClass);
        return res;
    }

    private static MapModel assembleSpinMapping(MapManager m, OntGraphModel src, OntGraphModel dst) {
        OntClass classPostalAddresses = OWLUtils.findEntity(src, OntClass.class, "x:PostalAddresses");
        OntClass classFoafPerson = OWLUtils.findEntity(src, OntClass.class, "foaf:Person");
        OntNDP propVcardStreet = OWLUtils.findEntity(src, OntNDP.class, "vcard:Street");
        OntNDP propVcardCountry = OWLUtils.findEntity(src, OntNDP.class, "vcard:Country");
        OntNDP propVcardLocality = OWLUtils.findEntity(src, OntNDP.class, "vcard:Locality");
        OntNDP propVcardPcode = OWLUtils.findEntity(src, OntNDP.class, "vcard:Pcode");
        OntNDP propIswcAddress = OWLUtils.findEntity(src, OntNDP.class, "iswc:address");

        OntClass classDst = OWLUtils.findEntity(dst, OntClass.class, "dst:Organizations");
        OntNDP propDst = OWLUtils.findEntity(dst, OntNDP.class, "dst:address");

        MapFunction.Call target = m.getFunction(AVC.IRI).create()
                .addLiteral(SP.arg1, dst.getNsPrefixURI("dst") + "Addresses").build();

        MapFunction.Call concat = m.getFunction(SP.resource("concat")).create()
                .addProperty(AVC.vararg, propVcardCountry)
                .addLiteral(AVC.vararg, ", ")
                .addProperty(AVC.vararg, propVcardLocality)
                .addLiteral(AVC.vararg, ", ")
                .addProperty(AVC.vararg, propVcardStreet)
                .addLiteral(AVC.vararg, ", ")
                .addProperty(AVC.vararg, propVcardPcode)
                .build();
        MapFunction.Call same = m.getFunction(SPINMAP.equals).create().addProperty(SP.arg1, propIswcAddress).build();

        MapModel res = m.createMapModel();
        res.createContext(classPostalAddresses, classDst, target).addPropertyBridge(concat, propDst);
        res.createContext(classFoafPerson, classDst, target).addPropertyBridge(same, propDst);
        return res;
    }

}
