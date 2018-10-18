package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.helpers.MappingTestHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import de.fuberlin.wiwiss.d2rq.mapgen.W3CMappingGenerator;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;

/**
 * Created by @szz on 18.10.2018.
 */
public class MappingGeneratorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MappingGeneratorTest.class);

    private static ConnectionData data = ConnectionData.POSTGRES;
    private static String dbName = MappingGeneratorTest.class.getSimpleName().toLowerCase() + "_" + System.currentTimeMillis();

    @BeforeClass
    public static void prepareData() throws Exception {
        data.createDatabase("/no_pk.sql", dbName);
    }

    @AfterClass
    public static void clear() throws Exception {
        data.dropDatabase(dbName);
    }

    @Test
    public void testW3CMappingGenerator() {
        String uri = "http://test.ex";
        try (ConnectedDB db = ConnectionData.POSTGRES.toConnectedDB(dbName)) {
            MappingGenerator g = new W3CMappingGenerator(db);
            Mapping m = MappingFactory.create(g.mappingModel(uri), uri);

            m.getConfiguration().setControlOWL(true).setServeVocabulary(true);
            MappingTestHelper.print(m);

            OntGraphModel all = OntModelFactory.createModel(m.getData(), OntModelConfig.ONT_PERSONALITY_LAX);
            D2RQTestHelper.print(all);
            Assert.assertEquals(6, all.listNamedIndividuals().peek(i -> LOGGER.debug("Named: {}", i)).count());
            Assert.assertEquals(7, all.ontObjects(OntIndividual.Anonymous.class)
                    .peek(i -> LOGGER.debug("Anonymous: {}", i)).count());

            Assert.assertEquals(13, all.ontObjects(OntIndividual.class)
                    .peek(i -> LOGGER.debug("Individual: {}", i)).count());

            Assert.assertEquals(2, all.listClasses().peek(x -> LOGGER.debug("Class: {}", x)).count());

            Assert.assertEquals(6, all.listDataProperties().peek(x -> LOGGER.debug("DatatypeProperty: {}", x)).count());
        }
    }
}
