package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.utils.MappingUtils;
import org.apache.jena.graph.Graph;

import org.apache.log4j.Level;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ConnectionData;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.OWLMapManager;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tester (not a test) for checking inference performance,
 * for investigation and finding the an optimal way to infer D2RQ graph using ONT-MAP.
 * <p>
 * TODO: Add D2RQ Mapping to compare
 * <p>
 * Created by @ssz on 28.10.2018.
 */
@Ignore // not a test - ignore
@RunWith(Parameterized.class)
public class InfrPerfTester {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfrPerfTester.class);
    private static final String DATABASE_NAME = "iswc_test";
    private static final String DATABASE_SCRIPT = "./doc/example/iswc-postgres.sql";
    private static final int INIT_ROW_NUMBER = 7;
    private static final int INIT_PAPER_ID = 8;
    private static final int NUMBER_OF_INDIVIDUALS = 20_000;

    private static Level log4jLevel;
    private static ConnectionData connection = ConnectionData.POSTGRES;
    private static Map<TestData, Object> result = new EnumMap<>(TestData.class);
    private static Graph tempSource;
    private final TestData testData;

    public InfrPerfTester(TestData data) {
        this.testData = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static TestData[] getData() {
        return TestData.values();
    }

    @BeforeClass
    public static void before() throws Exception {
        createDB();
        log4jLevel = org.apache.log4j.Logger.getRootLogger().getLevel();
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    }

    @AfterClass
    public static void after() {
        if (log4jLevel != null)
            org.apache.log4j.Logger.getRootLogger().setLevel(log4jLevel);
        LOGGER.info("Fin.");
        Assert.assertFalse(result.isEmpty());
        LinkedHashMap<TestData, Double> res = new LinkedHashMap<>();
        result.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<TestData, Object> o) -> ((Duration) o.getValue())))
                .forEach(e -> res.put(e.getKey(), toMinutes((Duration) e.getValue())));
        double min = res.values().iterator().next();
        res.forEach((k, v) -> LOGGER.info("{}:::{} ({}m)", k, round(v / min), v));
        result.clear();
    }

    private static void createDB() throws Exception {
        Path script = Paths.get(".").toRealPath().getParent().resolve(DATABASE_SCRIPT).toRealPath();
        LOGGER.debug("SQL Dump: {}", script);

        dropDB();

        int rowsNumberToInsert = NUMBER_OF_INDIVIDUALS - INIT_ROW_NUMBER;
        int step = rowsNumberToInsert / 20;

        LOGGER.info("Execute script: {}", script);
        connection.createDatabase(script, DATABASE_NAME);

        LOGGER.info("Insert {} rows", rowsNumberToInsert);
        try (ConnectedDB db = connection.toConnectedDB(DATABASE_NAME);
             Connection conn = db.connection();
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(false); // to mem ?
            for (int i = 1; i <= rowsNumberToInsert; i++) {
                String sql = String.format("INSERT INTO papers (paperid, title, year) values (%d, '%s', %d)",
                        INIT_PAPER_ID + i, "test-xxx-#" + i, 1944 + i);
                if (i % step == 0) {
                    LOGGER.debug("#{}:::SQL: {}", i, sql);
                }
                st.execute(sql);
            }
            conn.commit();
        }
    }

    private static void dropDB() throws Exception {
        try {
            connection.dropDatabase(DATABASE_NAME);
        } catch (SQLException s) {
            LOGGER.debug("{}|{}: '{}'", s.getErrorCode(), s.getSQLState(), s.getMessage());
            if (!"3D000".equals(s.getSQLState())) {
                throw s;
            }
        }
    }

    private static double toSeconds(Duration d) {
        return d.get(ChronoUnit.SECONDS) + d.get(ChronoUnit.NANOS) / 1_000_000_000d;
    }

    private static double toMinutes(Duration d) {
        return toSeconds(d) / 60;
    }

    private static double round(double a) {
        return Math.round(a * 100.0) / 100.0;
    }

    private static OntGraphModel deriveTargetFromDBWithOntMap(boolean withCache) throws OWLOntologyCreationException {
        LOGGER.info("Test inference (number={}, withCache={})", NUMBER_OF_INDIVIDUALS, withCache);
        D2RQGraphDocumentSource src = D2RQSpinTest.createSource(connection, DATABASE_NAME);
        src.getMapping().getConfiguration().setWithCache(withCache);
        MappingUtils.print(src.getMapping());

        OWLMapManager manager = Managers.createOWLMapManager();
        OntGraphModel target = OntMapSimpleTest.createTargetModel(manager);
        OntGraphModel source = manager.loadOntologyFromOntologyDocument(src).asGraphModel();
        source.setID("http://source");

        runOntMapInference(manager, source, target, src.getMapping().getData(), target.getBaseGraph());

        return target;
    }

    private static void runOntMapInference(MapManager manager,
                                           OntGraphModel sourceSchema,
                                           OntGraphModel targetSchema,
                                           Graph sourceData,
                                           Graph targetData) {
        LOGGER.debug("Run SPIN inference.");
        MapModel map = OntMapSimpleTest.composeMapping(manager, sourceSchema, targetSchema);
        manager.getInferenceEngine(map).run(sourceData, targetData);
        LOGGER.debug("Done.");
    }

    private static Graph createBigGraphInMem() {
        LOGGER.info("Create a memory graph with {} individuals", NUMBER_OF_INDIVIDUALS);
        String uri = "http://source";
        String ns = uri + "#";
        OntGraphModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("src", ns);
        m.setID(uri);
        OntClass c = m.createOntClass(ns + "Papers");
        OntDT dt = m.getDatatype(XSD.integer);
        OntNDP d1 = m.createDataProperty(ns + "title").addDomain(c).addRange(XSD.xstring);
        OntNDP d2 = m.createDataProperty(ns + "year").addDomain(c).addRange(dt);
        for (int i = 0; i < NUMBER_OF_INDIVIDUALS; i++) {
            c.createIndividual(ns + "Ind#" + i).addProperty(d1, "test-xxx-#" + i)
                    .addProperty(d2, dt.createLiteral(1944 + i));
        }
        LOGGER.debug("Done.");
        return m.getBaseGraph();
    }

    @Before
    public void beforeTest() {
        testData.before();
        Instant s = Instant.now();
        result.put(testData, s);
    }

    @Test
    public void tesInference() throws Exception {
        OntGraphModel target = testData.deriveTarget();
        LOGGER.debug("Validate");
        long actual = target.individuals().peek(x -> LOGGER.debug("{}", x)).count();
        Assert.assertEquals("Incorrect number of result individuals.", NUMBER_OF_INDIVIDUALS, actual);
    }

    @After
    public void afterTest() {
        Instant e = Instant.now();
        Instant s = (Instant) result.get(testData);
        result.put(testData, Duration.between(s, e));
        testData.after();
    }

    enum TestData {
        DEF_DB_ONT_MAP_WITHOUT_CACHE,
        DEF_DB_ONT_MAP_WITH_CACHE,

        // to compare
        MEM_ONT_MAP_WITHOUT_CACHE {
            @Override
            public void before() {
                tempSource = createBigGraphInMem();
            }

            @Override
            public void after() {
                tempSource = null;
            }

            @Override
            public OntGraphModel deriveTarget() {
                Assume.assumeNotNull(tempSource);
                OntGraphModel source = OntModelFactory.createModel(tempSource);
                OWLMapManager manager = Managers.createOWLMapManager();
                OntGraphModel target = OntMapSimpleTest.createTargetModel(manager);
                runOntMapInference(manager, source, target, source.getBaseGraph(), target.getBaseGraph());
                return target;
            }
        };

        public OntGraphModel deriveTarget() throws Exception {
            return deriveTargetFromDBWithOntMap(DEF_DB_ONT_MAP_WITH_CACHE == this);
        }

        public void before() {
        }

        public void after() {
        }
    }


}
