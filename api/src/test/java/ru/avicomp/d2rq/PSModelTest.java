package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ConnectionData;
import ru.avicomp.d2rq.utils.OWLUtils;
import ru.avicomp.ontapi.OntManagers;
import ru.avicomp.ontapi.OntologyManager;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.internal.AxiomParserProvider;
import ru.avicomp.ontapi.internal.ONTObject;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntStatement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 29.03.2018.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PSModelTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PSModelTest.class);

    private static ConnectionData psConnectedDB = ConnectionData.POSTGRES;

    private static String psDatabaseName = PSModelTest.class.getSimpleName().toLowerCase() + "_" + System.currentTimeMillis();
    private static IRI psBaseIRI = IRI.create("d2rq://no-pk.test/");

    @BeforeClass
    public static void createDatabase() throws Exception {
        psConnectedDB.createDatabase("/no_pk.sql", psDatabaseName);
    }

    @AfterClass
    public static void dropDatabase() throws Exception {
        psConnectedDB.dropDatabase(psDatabaseName);
    }

    private D2RQGraphDocumentSource psDataSource;

    @Before
    public void before() {
        psDataSource = D2RQGraphDocumentSource.wrap(createMapping(psConnectedDB, psBaseIRI, psDatabaseName));
    }

    private static Mapping createMapping(ConnectionData data, IRI base, String dbName) {
        return new SystemLoader()
                .setJdbcURL(data.getJdbcURI(dbName))
                .setServeVocabulary(false)
                .setSystemBaseURI(base.getIRIString())
                .setControlOWL(true)
                .setUsername(data.getUser())
                .setPassword(data.getPwd())
                .build();
    }

    @After
    public void after() {
        Assume.assumeNotNull(psDataSource);
        LOGGER.info("Close mapping");
        psDataSource.close();
    }

    @Test
    public void test01ValidatePSDB() throws OWLOntologyCreationException {
        Assert.assertNotNull(psDataSource.getMapping());
        OntologyModel o = OntManagers.createONT().loadOntologyFromOntologyDocument(psDataSource);
        LOGGER.debug("Schema:");
        JenaModelUtils.print(o.asGraphModel());

        // in memory, no duplicates:
        OntGraphModel data = OWLUtils.toMemory(o.asGraphModel());
        // axioms:
        List<OWLAxiom> axioms = AxiomType.AXIOM_TYPES.stream()
                .map(AxiomParserProvider::get)
                .flatMap(t -> t.axioms(data))
                .map(ONTObject::getOWLObject)
                .collect(Collectors.toList());
        axioms.forEach(x -> LOGGER.debug("AXIOM:::{}", x));
        Assert.assertEquals(80, axioms.size());

        LOGGER.debug("Mapping:");
        JenaModelUtils.print(psDataSource.getMapping().asModel());

        // simple validation of all data in the graphs
        validatePSDatabase(data);
    }

    @Test
    public void test02ReloadPSOntByMapping() {
        Mapping m = psDataSource.getMapping();
        // reload using mapping only
        OntologyModel o = OntManagers.createONT().addOntology(D2RQGraphDocumentSource.wrap(m).getGraph());
        OntGraphModel data = OWLUtils.toVirtual(o.asGraphModel());
        LOGGER.debug("Scheme+Data:");
        JenaModelUtils.print(data);
        validatePSDatabase(data);
    }

    @Test
    public void test03CombineDifferentSources() throws OWLOntologyCreationException {
        OntologyManager m = OntManagers.createONT();
        OntologyModel ps = m.loadOntologyFromOntologyDocument(psDataSource);
        OntologyModel iswc = m.loadOntologyFromOntologyDocument(ConnectionData.MYSQL.toDocumentSource("iswc"));

        String iri1 = psBaseIRI + "postgres";
        ps.asGraphModel().setID(iri1);
        iswc.asGraphModel().addImport(ps.asGraphModel());

        OntologyManager m2 = OntManagers.createONT();
        OntologyModel reloaded = m2.addOntology(OWLUtils.toVirtual(iswc.asGraphModel()).getGraph());

        Assert.assertEquals(2, m2.ontologies().count());

        List<OWLNamedIndividual> individuals = reloaded.individualsInSignature(Imports.INCLUDED).collect(Collectors.toList());
        individuals.forEach(i -> LOGGER.debug("Individual: {}", i));
        // 56 from iswc + 7 from no_pk (6 from pk_table, 1 from no_pk_table)
        Assert.assertEquals(63, individuals.size());

        OWLUtils.closeConnections(reloaded);
    }

    private void validatePSDatabase(OntGraphModel model) {
        boolean isInMemory = OWLUtils.isInMemory(model.getGraph());
        LOGGER.info("Validate data from pk_table ({})", isInMemory ? "InMemory" : "Virtual");
        List<String> pkTableColumns = Arrays.asList("id", "numeric_column", "text_column");
        for (String col : pkTableColumns) {

            Property predicate = ResourceFactory.createProperty(psBaseIRI + MappingFactory.VOCAB_PREFIX + "#pk_table_" + col);
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
            Property predicate = ResourceFactory.createProperty(psBaseIRI + MappingFactory.VOCAB_PREFIX + "#no_pk_table_" + col);
            List<OntStatement> statements = model.statements(null, predicate, null).collect(Collectors.toList());
            Assert.assertEquals("Statements for " + predicate, isInMemory ? 7 : 8, statements.size());
            Assert.assertEquals("Distinct statements for " + predicate, 7, statements.stream().distinct().count());
            Assert.assertEquals("Individuals for " + predicate, 1, statements.stream().map(OntStatement::getSubject)
                    .filter(RDFNode::isURIResource).distinct().count());
            Assert.assertEquals("Literals for " + predicate, isInMemory ? 7 : 8, statements.stream()
                    .map(OntStatement::getObject).filter(RDFNode::isLiteral).count());
        }
    }
}
