package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.conf.ISWCData;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.utils.OWLUtils;

import java.util.function.Supplier;

/**
 * Created by @ssz on 20.10.2018.
 */
@RunWith(Parameterized.class)
public class OntMapTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OntMapTest.class);
    private final TestData data;

    public OntMapTest(TestData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static TestData[] getData() {
        return TestData.values();
    }

    @Test
    public void testCollectAddresses() throws OWLOntologyCreationException {
        D2RQGraphDocumentSource source = data.makeSource();
        Mapping d2rq = source.getMapping();

        OWLMapManager manager = Managers.createOWLMapManager();
        OntGraphModel src = manager.loadOntologyFromOntologyDocument(source).asGraphModel();

        OntGraphModel dst = assembleTarget(manager);

        MapModel spin = assembleSpinMapping(manager, src, dst, data.getVocabulary());

        LOGGER.debug("Run inference.");
        manager.getInferenceEngine().run(spin, d2rq.getData(), dst.getBaseGraph());
        LOGGER.debug("Done.");

        Assert.assertEquals(1, dst.listNamedIndividuals().count());

        OntIndividual res = dst.listNamedIndividuals().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(9, res.positiveAssertions().peek(x -> LOGGER.debug("Address: '{}'", x.getString())).count());
        D2RQTestHelper.print(dst);

        source.close();
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

    private static MapModel assembleSpinMapping(MapManager m, OntGraphModel src, OntGraphModel dst, SchemaEntities voc) {
        OntClass classOrganization = OWLUtils.findEntity(src, OntClass.class, voc.getOrganization());
        OntClass classPerson = OWLUtils.findEntity(src, OntClass.class, voc.getPerson());
        OntNDP propOrgStreet = OWLUtils.findEntity(src, OntNDP.class, voc.getOrganizationStreet());
        OntNDP propOrgCountry = OWLUtils.findEntity(src, OntNDP.class, voc.getOrganizationCountry());
        OntNDP propOrgCity = OWLUtils.findEntity(src, OntNDP.class, voc.getOrganizationCity());
        OntNDP propOrgPcode = OWLUtils.findEntity(src, OntNDP.class, voc.getOrganizationPcode());
        OntNDP propPersonAddress = OWLUtils.findEntity(src, OntNDP.class, voc.getPersonAddress());

        OntClass classDst = OWLUtils.findEntity(dst, OntClass.class, "dst:Organizations");
        OntNDP propDst = OWLUtils.findEntity(dst, OntNDP.class, "dst:address");

        MapFunction.Call target = m.getFunction(AVC.IRI).create()
                .addLiteral(SP.arg1, dst.getNsPrefixURI("dst") + "Addresses").build();

        MapFunction.Call concat = m.getFunction(SP.resource("concat")).create()
                .addProperty(AVC.vararg, propOrgCountry)
                .addLiteral(AVC.vararg, ", ")
                .addProperty(AVC.vararg, propOrgCity)
                .addLiteral(AVC.vararg, ", ")
                .addProperty(AVC.vararg, propOrgStreet)
                .addLiteral(AVC.vararg, ", ")
                .addProperty(AVC.vararg, propOrgPcode)
                .build();
        MapFunction.Call same = m.getFunction(SPINMAP.equals).create().addProperty(SP.arg1, propPersonAddress).build();

        MapModel res = m.createMapModel();
        res.createContext(classOrganization, classDst, target).addPropertyBridge(concat, propDst);
        res.createContext(classPerson, classDst, target).addPropertyBridge(same, propDst);
        return res;
    }

    private enum SchemaEntities {
        PREDEFINED {
            @Override
            String getOrganization() {
                return "x:PostalAddresses";
            }

            @Override
            String getOrganizationStreet() {
                return "vcard:Street";
            }

            @Override
            String getOrganizationCountry() {
                return "vcard:Country";
            }

            @Override
            String getOrganizationCity() {
                return "vcard:Locality";
            }

            @Override
            String getOrganizationPcode() {
                return "vcard:Pcode";
            }

            @Override
            String getPerson() {
                return "foaf:Person";
            }

            @Override
            String getPersonAddress() {
                return "iswc:address";
            }
        },
        DEFAULT {
            @Override
            String getOrganization() {
                return "vocab:organizations";
            }

            @Override
            String getOrganizationStreet() {
                return "vocab:organizations_address";
            }

            @Override
            String getOrganizationCountry() {
                return "vocab:organizations_country";
            }

            @Override
            String getOrganizationCity() {
                return "vocab:organizations_location";
            }

            @Override
            String getOrganizationPcode() {
                return "vocab:organizations_postcode";
            }

            @Override
            String getPerson() {
                return "vocab:persons";
            }

            @Override
            String getPersonAddress() {
                return "vocab:persons_address";
            }
        },
        ;

        abstract String getOrganization();

        abstract String getOrganizationStreet();

        abstract String getOrganizationCountry();

        abstract String getOrganizationCity();

        abstract String getOrganizationPcode();

        abstract String getPerson();

        abstract String getPersonAddress();
    }

    private static D2RQGraphDocumentSource makePredefinedSource() {
        Mapping d2rq = ISWCData.POSTGRES.loadMapping("http://test.ex/src/");
        d2rq.getConfiguration().setControlOWL(true).setServeVocabulary(true);
        OntGraphModel src = OntModelFactory.createModel(d2rq.getSchema());
        src.setID("http://test.ex/predefined");
        src.setNsPrefix("x", "file:///Users/richard/D2RQ/workspace/D2RQ/doc/example/mapping-iswc.ttl#");
        return D2RQGraphDocumentSource.create(d2rq);
    }

    private static D2RQGraphDocumentSource makeDefaultSource() {
        D2RQGraphDocumentSource res = ConnectionData.POSTGRES.toDocumentSource("iswc");
        OntModelFactory.createModel(res.getMapping().getSchema()).setID("http://test.ex/default");
        return res;
    }

    enum TestData {
        PREDEFINED(SchemaEntities.PREDEFINED, OntMapTest::makePredefinedSource),
        DEFAULT(SchemaEntities.DEFAULT, OntMapTest::makeDefaultSource),
        ;
        private final SchemaEntities vocabulary;
        private final Supplier<D2RQGraphDocumentSource> sourceFactory;

        TestData(SchemaEntities vocabulary, Supplier<D2RQGraphDocumentSource> sourceFactory) {
            this.vocabulary = vocabulary;
            this.sourceFactory = sourceFactory;
        }

        SchemaEntities getVocabulary() {
            return vocabulary;
        }

        D2RQGraphDocumentSource makeSource() {
            return sourceFactory.get();
        }

    }

}
