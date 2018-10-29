package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.helpers.MappingTestHelper;
import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.apache.jena.graph.Graph;
import org.apache.log4j.Level;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.OWLMapManager;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A tester (not a test) for checking inference performance.
 * For investigating and found an optimal way to inference D2RQ graph using ONT-MAP.
 * <p>
 * Execution stats:
 * no cache:
 * 1000 ~ 23s
 * 2000 ~ 33s
 * 3000 ~ 52s
 * 5000 ~ 2m23s
 * 7000 ~ 2m17s
 * 10_000 ~ 2m18s, 1m36s
 * 20_000 ~ 4m21s, 3m36s
 * 50_000 ~ 9m24s
 * <p>
 * cache (jena-guava, 100):
 * 1000 ~ 34s
 * 5000 ~ 2m50s
 * <p>
 * cache (jena-guava, 10_000):
 * 1000 ~ 21s
 * 5000 ~ 54s
 * 7000 ~ 1m29s
 * 10_000 ~ 2m11s
 * 20_000 ~ 4m33s, 3m29s
 * 50_000 ~ 9m36s, 7m52s, 7m29s
 * <p>
 * cache (original, 10_000):
 * 1000 ~ 22s
 * 5000 ~ 1m
 * 7000 ~ 1m24s
 * 10_000 ~ 2m22s
 * 20_000 ~ 7m49s ?? 4m35s
 * 50_000 ~ 8m18s
 * <p>
 * Created by @ssz on 28.10.2018.
 */
@SuppressWarnings("WeakerAccess")
@Ignore // not a test - ignore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InfrPerfTester {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfrPerfTester.class);

    private static Level log4jLevel;

    private static final String DATABASE_NAME = "iswc_test";
    private static final String DATABASE_SCRIPT = "./doc/example/iswc-postgres.sql";
    private static ConnectionData data = ConnectionData.POSTGRES;
    private static final int INIT_ROW_NUMBER = 7;
    private static final int INIT_PAPER_ID = 8;

    private final int numberRowsToInsert;
    private final boolean withCache;

    public InfrPerfTester() {
        this(50_000, false);
    }

    protected InfrPerfTester(int numberRowsToInsert, boolean useCache) {
        this.numberRowsToInsert = numberRowsToInsert;
        this.withCache = useCache;
    }

    @BeforeClass
    public static void createDB() throws Exception {
        Path script = Paths.get(DATABASE_SCRIPT).toRealPath();
        log4jLevel = org.apache.log4j.Logger.getRootLogger().getLevel();
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
        try {
            data.dropDatabase(DATABASE_NAME);
        } catch (SQLException s) {
            LOGGER.debug("{}|{}: '{}'", s.getErrorCode(), s.getSQLState(), s.getMessage());
            if (!"3D000".equals(s.getSQLState())) {
                throw s;
            }
        }
        LOGGER.info("Execute script: {}", script);
        data.createDatabase(script, DATABASE_NAME);
    }

    @AfterClass
    public static void after() {
        org.apache.log4j.Logger.getRootLogger().setLevel(log4jLevel);
        LOGGER.info("Fin.");
    }

    @Test
    public void test01Insert() throws SQLException {
        LOGGER.info("Insert {} rows", numberRowsToInsert);
        try (ConnectedDB db = data.toConnectedDB(DATABASE_NAME);
             Connection conn = db.connection();
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(false);
            for (int i = 1; i <= numberRowsToInsert; i++) {
                String sql = String.format("INSERT INTO papers (paperid, title, year) values (%d, '%s', %d)",
                        INIT_PAPER_ID + i, "test-xxx-#" + i, 1944 + i);
                LOGGER.debug("SQL: {}", sql);
                st.execute(sql);
            }
            conn.commit();
        }
    }

    @Test
    public void test02Inference() throws Exception {
        LOGGER.info("Test inference (number={}, withCache={})", numberRowsToInsert, withCache);
        D2RQGraphDocumentSource source = D2RQSpinTest.createSource(data, DATABASE_NAME);
        MappingTestHelper.print(source.getMapping());

        OWLMapManager manager = Managers.createOWLMapManager();
        OntGraphModel target = OntMapSimpleTest.createTargetModel(manager);
        OntologyModel o = manager.loadOntologyFromOntologyDocument(source);
        o.asGraphModel().setID("http://source");
        MapModel map = OntMapSimpleTest.composeMapping(manager, o.asGraphModel(), target);

        Graph data = source.getMapping().getData();
        if (withCache) {
            data = new CachingGraph(data);
        }
        manager.getInferenceEngine().run(map, data, target.getBaseGraph());
        LOGGER.debug("Done.");

        target.listNamedIndividuals().forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect number of result individuals.",
                INIT_ROW_NUMBER + numberRowsToInsert, target.listNamedIndividuals().count());
    }
}
