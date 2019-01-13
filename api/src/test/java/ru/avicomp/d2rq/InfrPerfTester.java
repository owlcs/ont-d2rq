package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.utils.MappingUtils;
import org.apache.jena.graph.Graph;
import org.apache.log4j.Level;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ConnectionData;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.OWLMapManager;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A tester (not a test) for checking inference performance,
 * for investigation and finding the optimal way to infer D2RQ graph using ONT-MAP.
 * <p>
 * Created by @ssz on 28.10.2018.
 */
@Ignore // not a test - ignore
@RunWith(Parameterized.class)
public class InfrPerfTester {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfrPerfTester.class);

    private static Level log4jLevel;

    private static final String DATABASE_NAME = "iswc_test";
    private static final String DATABASE_SCRIPT = "./doc/example/iswc-postgres.sql";
    private static ConnectionData data = ConnectionData.POSTGRES;
    private static final int INIT_ROW_NUMBER = 7;
    private static final int INIT_PAPER_ID = 8;

    private static final int NUMBER_ROWS_TO_INSERT = 100_000;
    private final Data withCache;

    public InfrPerfTester(Data data) {
        this.withCache = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Data[] getData() {
        return Data.values();
    }

    @BeforeClass
    public static void createDB() throws Exception {
        Path script = Paths.get(DATABASE_SCRIPT).toRealPath();
        int step = NUMBER_ROWS_TO_INSERT / 20;

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

        LOGGER.info("Insert {} rows", NUMBER_ROWS_TO_INSERT);
        try (ConnectedDB db = data.toConnectedDB(DATABASE_NAME);
             Connection conn = db.connection();
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(false);
            for (int i = 1; i <= NUMBER_ROWS_TO_INSERT; i++) {
                String sql = String.format("INSERT INTO papers (paperid, title, year) values (%d, '%s', %d)",
                        INIT_PAPER_ID + i, "test-xxx-#" + i, 1944 + i);
                if (i % step == 0) {
                    LOGGER.debug("#{}:::SQL: {}", i, sql);
                }
                st.execute(sql);
            }
            conn.commit();
        }

        log4jLevel = org.apache.log4j.Logger.getRootLogger().getLevel();
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    }

    @AfterClass
    public static void after() {
        org.apache.log4j.Logger.getRootLogger().setLevel(log4jLevel);
        LOGGER.info("Fin.");
    }

    @Test
    public void tesInference() throws Exception {
        LOGGER.info("Test inference (number={}, withCache={})", NUMBER_ROWS_TO_INSERT, withCache);
        D2RQGraphDocumentSource source = D2RQSpinTest.createSource(data, DATABASE_NAME);
        source.getMapping().getConfiguration().setWithCache(Data.WITH_CACHE.equals(withCache));
        MappingUtils.print(source.getMapping());

        OWLMapManager manager = Managers.createOWLMapManager();
        OntGraphModel target = OntMapSimpleTest.createTargetModel(manager);
        OntologyModel o = manager.loadOntologyFromOntologyDocument(source);
        o.asGraphModel().setID("http://source");
        MapModel map = OntMapSimpleTest.composeMapping(manager, o.asGraphModel(), target);

        Graph data = source.getMapping().getData();
        manager.getInferenceEngine(map).run(data, target.getBaseGraph());
        LOGGER.debug("Done.");

        target.listNamedIndividuals().forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect number of result individuals.",
                INIT_ROW_NUMBER + NUMBER_ROWS_TO_INSERT, target.listNamedIndividuals().count());
    }

    enum Data {
        WITH_CACHE,
        WITHOUT_CACHE,
    }

}
