package de.fuberlin.wiwiss.d2rq;

import com.github.owlcs.d2rq.conf.ISWCData;
import com.github.owlcs.ontapi.jena.utils.Iter;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import de.fuberlin.wiwiss.d2rq.utils.MappingUtils;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.function.BiFunction;

/**
 * Created by @ssz on 13.01.2019.
 */
public class SystemLoaderTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemLoaderTest.class);

    @Test
    public void testLoadFileAndOverrideJDBCSettingsIfURLIsGiven() {
        testLoadFileAndOverridePassword(ISWCData.POSTGRES, SystemLoader::setJdbcURL);
    }

    @Test
    public void testLoadFileAndOverrideJDBCSettingsWhenURLIsNotGiven() {
        testLoadFileAndOverridePassword(ISWCData.MYSQL, (sl, u) -> sl);
    }

    @Test
    public void testLoadFileAndNoOverride() {
        String res = ISWCData.POSTGRES.getResourcePath();
        URL url = SystemLoaderTest.class.getResource(res);
        Model model = JenaModelUtils.loadTurtle(res);
        String jdbcURL = getString(model, D2RQ.jdbcDSN);
        String user = getString(model, D2RQ.username);
        String pwd = getString(model, D2RQ.password);

        Mapping mapping = new SystemLoader().setMappingURL(url.toString()).setJdbcURL("xxx").build();
        Database db = mapping.getDatabase(jdbcURL);
        Assert.assertEquals(pwd, db.getPassword());
        Assert.assertEquals(user, db.getUsername());
    }

    private static void testLoadFileAndOverridePassword(ISWCData data,
                                                        BiFunction<SystemLoader, String, SystemLoader> op) {
        String res = data.getResourcePath();
        URL url = SystemLoaderTest.class.getResource(res);
        Model model = JenaModelUtils.loadTurtle(res);
        String jdbcURL = getString(model, D2RQ.jdbcDSN);
        String user = getString(model, D2RQ.username);

        LOGGER.debug("File: {}, jdbcURL: {}, user: {}", url, jdbcURL, user);

        String pwd = "x";
        SystemLoader loader = new SystemLoader().setMappingURL(url.toString());
        Mapping mapping = op.apply(loader, jdbcURL).setPassword(pwd).build();
        MappingUtils.print(mapping);
        Database db = mapping.getDatabase(jdbcURL);
        Assert.assertEquals(pwd, db.getPassword());
        Assert.assertEquals(user, db.getUsername());
    }

    private static String getString(Model model, Property predicate) {
        return Iter.findFirst(model.listObjectsOfProperty(predicate)
                .mapWith(RDFNode::asLiteral).mapWith(Literal::getString)).orElseThrow(AssertionError::new);
    }
}
