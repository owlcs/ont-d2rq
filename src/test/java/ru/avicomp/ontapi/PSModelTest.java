package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.config.OntLoaderConfiguration;
import ru.avicomp.ontapi.internal.AxiomParserProvider;
import ru.avicomp.ontapi.internal.ONTObject;
import ru.avicomp.ontapi.jena.impl.conf.D2RQModelConfig;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.D2RQGraphs;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 29.03.2018.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PSModelTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PSModelTest.class);

    private static ConnectionData psConnectionData = ConnectionData.POSTGRES;
    private static String psDbName = PSModelTest.class.getSimpleName().toLowerCase() + "_" + System.currentTimeMillis();

    @BeforeClass
    public static void prepareData() throws Exception {
        psConnectionData.createDatabase("/no_pk.sql", psDbName);
    }

    @AfterClass
    public static void clear() throws Exception {
        psConnectionData.dropDatabase(psDbName);
    }

    private static IRI psIRI = IRI.create("d2rq://no-pk.test/");
    private static Mapping psMapping;

    @Test
    public void test01ValidatePSDB() throws OWLOntologyCreationException {
        D2RQGraphDocumentSource src = psConnectionData.toDocumentSource(psIRI, psDbName);
        OntologyManager m = OntManagers.createONT();
        m.getOntologyConfigurator().setPerformTransformation(false);
        OntologyModel o = m.loadOntologyFromOntologyDocument(src);
        LOGGER.debug("Scheme:");
        ReadWriteUtils.print(o);

        OntGraphModel data = D2RQGraphs.reassembly(o.asGraphModel());
        // axioms:
        List<OWLAxiom> axioms = AxiomType.AXIOM_TYPES.stream()
                .map(AxiomParserProvider::get)
                .flatMap(t -> t.axioms(data))
                .map(ONTObject::getObject)
                .collect(Collectors.toList());
        axioms.forEach(x -> LOGGER.debug("AXIOM:::{}", x));
        Assert.assertEquals(34, axioms.size());


        LOGGER.debug("Mapping:");
        psMapping = src.getMapping();
        Assert.assertNotNull(psMapping);
        ReadWriteUtils.print(psMapping.asModel());

        // simple validation of all data in graphs
        validatePSDatabase(data);
    }

    @Test
    public void test02ReloadPSOntByMapping() {
        Assume.assumeNotNull(psMapping);
        // reload using mapping only
        OntologyModel o = OntManagers.createONT().addOntology(D2RQGraphDocumentSource.create(psMapping).getGraph());
        OntGraphModel data = D2RQGraphs.reassembly(o.asGraphModel());
        LOGGER.debug("Scheme+Data:");
        ReadWriteUtils.print(data);
        validatePSDatabase(data);
    }

    @Test
    public void test03CombineDifferentSources() throws OWLOntologyCreationException {
        D2RQGraphDocumentSource src1 = psConnectionData.toDocumentSource(psIRI, psDbName);
        D2RQGraphDocumentSource src2 = ConnectionData.MYSQL.toDocumentSource("iswc");
        OntologyManager m1 = OntManagers.createONT();
        OntologyModel o1 = m1.loadOntologyFromOntologyDocument(src1);
        OntologyModel o2 = m1.loadOntologyFromOntologyDocument(src2);
        String iri1 = psIRI + "postgres";
        o1.asGraphModel().setID(iri1);
        o2.asGraphModel().addImport(o1.asGraphModel());

        OntologyManager m2 = OntManagers.createONT();
        OntLoaderConfiguration conf = m2.getOntologyLoaderConfiguration()
                .setPerformTransformation(false)
                .setPersonality(D2RQModelConfig.D2RQ_PERSONALITY);
        OntologyModel o3 = m2.addOntology(D2RQGraphs.reassembly(o2.asGraphModel()).getGraph(), conf);

        Assert.assertEquals(2, m2.ontologies().count());

        List<OWLNamedIndividual> individuals = o3.individualsInSignature(Imports.INCLUDED).collect(Collectors.toList());
        individuals.forEach(i -> LOGGER.debug("Individual: {}", i));
        // 56 from iswc + 7 from no_pk (6 from pk_table, 1 from no_pk_table)
        Assert.assertEquals(63, individuals.size());

        D2RQGraphs.close(o3.asGraphModel());
    }

    @After
    public void close() {
        Assume.assumeNotNull(psMapping);
        LOGGER.info("Close mapping");
        psMapping.close();
    }

    private void validatePSDatabase(OntGraphModel model) {
        LOGGER.info("Validate data from pk_table");
        List<String> pkTableColumns = Arrays.asList("id", "numeric_column", "text_column");
        for (String col : pkTableColumns) {

            Property predicate = ResourceFactory.createProperty(psIRI + MappingFactory.Prefixes.VOCAB_PREFIX + "#pk_table_" + col);
            List<OntStatement> statements = model.statements(null, predicate, null).collect(Collectors.toList());
            Assert.assertEquals("Statements for " + predicate, 6, statements.size());
            Assert.assertEquals("Individuals for " + predicate, 6, statements.stream().map(OntStatement::getSubject)
                    .filter(RDFNode::isURIResource).distinct().count());
            Assert.assertEquals("Literals for " + predicate, 6, statements.stream().map(OntStatement::getObject)
                    .filter(RDFNode::isLiteral).count());
        }
        LOGGER.info("Validate data from no_k_table (WARNING: only single individual since table has no primary key)");
        List<String> noPkTableColumns = Arrays.asList("value", "number", "parameter");
        for (String col : noPkTableColumns) {
            Property predicate = ResourceFactory.createProperty(psIRI + MappingFactory.Prefixes.VOCAB_PREFIX + "#no_pk_table_" + col);
            List<OntStatement> statements = model.statements(null, predicate, null).collect(Collectors.toList());
            Assert.assertEquals("Statements for " + predicate, 8, statements.size());
            Assert.assertEquals("Distinct statements for " + predicate, 7, statements.stream().distinct().count());
            Assert.assertEquals("Individuals for " + predicate, 1, statements.stream().map(OntStatement::getSubject)
                    .filter(RDFNode::isURIResource).distinct().count());
            Assert.assertEquals("Literals for " + predicate, 8, statements.stream().map(OntStatement::getObject).filter(RDFNode::isLiteral).count());
        }
    }
}
