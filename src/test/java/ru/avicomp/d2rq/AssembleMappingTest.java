package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.helpers.MappingTestHelper;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

/**
 * To test mapping creation from scratch.
 * Created by @ssz on 02.01.2019.
 */
@RunWith(Parameterized.class)
public class AssembleMappingTest {

    private final ConnectionData data;

    public AssembleMappingTest(ConnectionData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static ConnectionData[] getData() {
        return ConnectionData.values();
    }

    @Test
    public void testCreateSingleClassMapping() {
        String uri = "http://map";
        String ns = uri + "#";
        Mapping mapping = MappingFactory.create();
        OntModelFactory.createModel(mapping.asModel().getGraph()).setID(uri);
        Database db = mapping.createDatabase(null)
                .setJDBCDSN(data.getJdbcIRI("iswc").getIRIString())
                .setUsername(data.getUser()).setPassword(data.getPwd())
                .addConnectionProperties(data.getConnectionProperties());

        mapping.createClassMap(null).setDatabase(db)
                .setURIPattern(data.fixIRI("conferences/@@conferences.ConfID@@"))
                .addClass(ns + "XXX");

        mapping.validate();
        MappingTestHelper.print(mapping);

        OntGraphModel m = OntModelFactory.createModel(mapping.getData());
        D2RQTestHelper.print(m);
        Assert.assertEquals(5, m.size());
    }
}
