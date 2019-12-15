package com.github.owlcs.d2rq;

import com.github.owlcs.d2rq.conf.ConnectionData;
import com.github.owlcs.d2rq.conf.ISWCData;
import com.github.owlcs.d2rq.utils.OWLUtils;
import com.github.owlcs.map.*;
import com.github.owlcs.map.spin.vocabulary.AVC;
import com.github.owlcs.ontapi.OntologyManager;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDataProperty;
import com.github.owlcs.ontapi.jena.model.OntIndividual;
import com.github.owlcs.ontapi.jena.model.OntModel;
import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import org.apache.jena.graph.Graph;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPINMAP;

import java.util.function.Supplier;

/**
 * Test collecting all addresses (skipping incomplete)
 * using ont-mapping with {@link AVC#UUID avc:UUID} as target function and {@code sp:concat{} as property function.
 * Database: postgres.
 *
 * Created by @ssz on 20.10.2018.
 */
@RunWith(Parameterized.class)
public class OntMapWholeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OntMapWholeTest.class);
    private final TestData test;

    public OntMapWholeTest(TestData test) {
        this.test = test;
    }

    @Parameterized.Parameters(name = "{0}")
    public static TestData[] getData() {
        return TestData.values();
    }

    private static OntModel assembleTarget(OntologyManager m) {
        String dstURI = "http://test.ex/dst";
        String dstNS = dstURI + "#";
        OntModel res = m.createGraphModel(dstURI)
                .setNsPrefix("dst", dstNS).setNsPrefixes(OntModelFactory.STANDARD);
        OntClass dstClass = res.createOntClass(dstNS + "Organizations");
        OntDataProperty dstProp = res.createDataProperty(dstNS + "address");
        dstProp.addDomain(dstClass);
        return res;
    }

    private static MapModel assembleSpinMapping(MapManager m, OntModel src, OntModel dst, SchemaEntities voc) {
        OntClass classOrganization = OWLUtils.findEntity(src, OntClass.Named.class, voc.getOrganization());
        OntClass classPerson = OWLUtils.findEntity(src, OntClass.Named.class, voc.getPerson());
        OntDataProperty propOrgStreet = OWLUtils.findEntity(src, OntDataProperty.class, voc.getOrganizationStreet());
        OntDataProperty propOrgCountry = OWLUtils.findEntity(src, OntDataProperty.class, voc.getOrganizationCountry());
        OntDataProperty propOrgCity = OWLUtils.findEntity(src, OntDataProperty.class, voc.getOrganizationCity());
        OntDataProperty propOrgPcode = OWLUtils.findEntity(src, OntDataProperty.class, voc.getOrganizationPcode());
        OntDataProperty propPersonAddress = OWLUtils.findEntity(src, OntDataProperty.class, voc.getPersonAddress());

        OntClass classDst = OWLUtils.findEntity(dst, OntClass.Named.class, "dst:Organizations");
        OntDataProperty propDst = OWLUtils.findEntity(dst, OntDataProperty.class, "dst:address");

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

    private static D2RQGraphDocumentSource makePredefinedSource() {
        Mapping d2rq = ISWCData.POSTGRES.loadMapping("http://test.ex/src/");
        d2rq.getConfiguration().setControlOWL(true).setServeVocabulary(true);
        OntModel src = OntModelFactory.createModel(d2rq.getSchema());
        src.setID("http://test.ex/predefined");
        src.setNsPrefix("x", "file:///Users/richard/D2RQ/workspace/D2RQ/doc/example/mapping-iswc.ttl#");
        return D2RQGraphDocumentSource.wrap(d2rq);
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

    @Test
    public void testCollectAddresses() throws OWLOntologyCreationException {
        D2RQGraphDocumentSource source = test.makeSource();
        Mapping d2rq = source.getMapping();
        d2rq.getConfiguration().setWithCache(test.withCache());
        Assert.assertEquals(test.withCache(), d2rq.getConfiguration().getWithCache());

        OWLMapManager manager = Managers.createOWLMapManager();
        OntModel src = manager.loadOntologyFromOntologyDocument(source).asGraphModel();

        OntModel dst = assembleTarget(manager);

        MapModel spin = assembleSpinMapping(manager, src, dst, test.getVocabulary());

        LOGGER.debug("Run inference.");
        Graph data = d2rq.getData();
        Assert.assertTrue(test.getGraphType().isInstance(data));
        manager.getInferenceEngine(spin).run(data, dst.getBaseGraph());
        LOGGER.debug("Done.");

        Assert.assertEquals(1, dst.individuals().count());

        OntIndividual res = dst.individuals().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(9, res.positiveAssertions().peek(x -> LOGGER.debug("Address: '{}'", x.getString())).count());
        JenaModelUtils.print(dst);

        source.close();
    }

    private static D2RQGraphDocumentSource makeDefaultSource() {
        D2RQGraphDocumentSource res = ConnectionData.POSTGRES.toDocumentSource("iswc");
        OntModelFactory.createModel(res.getMapping().getSchema()).setID("http://test.ex/default");
        return res;
    }

    enum TestData {
        PREDEFINED(SchemaEntities.PREDEFINED, OntMapWholeTest::makePredefinedSource, false),
        DEFAULT(SchemaEntities.DEFAULT, OntMapWholeTest::makeDefaultSource, false),
        PREDEFINED_WITH_CACHE(SchemaEntities.PREDEFINED, OntMapWholeTest::makePredefinedSource, true),
        DEFAULT_WITH_CACHE(SchemaEntities.DEFAULT, OntMapWholeTest::makeDefaultSource, true),
        ;
        private final SchemaEntities vocabulary;
        private final Supplier<D2RQGraphDocumentSource> sourceFactory;
        private final boolean withCache;

        TestData(SchemaEntities vocabulary, Supplier<D2RQGraphDocumentSource> sourceFactory, boolean useCache) {
            this.vocabulary = vocabulary;
            this.sourceFactory = sourceFactory;
            this.withCache = useCache;
        }

        SchemaEntities getVocabulary() {
            return vocabulary;
        }

        D2RQGraphDocumentSource makeSource() {
            return sourceFactory.get();
        }

        boolean withCache() {
            return withCache;
        }

        Class<? extends Graph> getGraphType() {
            return withCache ? CachingGraph.class : GraphD2RQ.class;
        }

    }

}
