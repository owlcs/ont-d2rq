package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import de.fuberlin.wiwiss.d2rq.vocab.ISWC;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.*;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * For test misc mapping functionality.
 * <p>
 * Created by szuev on 21.02.2017.
 */
public class OWLMappingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OWLMappingTest.class);

    private static final String JDBC_URI = ConnectionData.MYSQL.getJdbcConnectionString("iswc");

    /**
     * @see <a href='https://github.com/avicomp/ont-d2rq/issues/22'>bug #22</a>
     */
    @Test
    public void testEditMappingAndSchemaSimultaneously() {
        ConnectionData cd = ConnectionData.MYSQL;
        String ns = "http://m#";
        Mapping m = MappingFactory.create();
        OntGraphModel o = OntModelFactory.createModel(m.getSchema());

        // add d2rq:ClassMap and owl:Class
        m.createClassMap(ns + "Y")
                .addClass(o.createOntClass(ns + "X"))
                .setURIColumn("topics.URI");
        // create d2rq:Database
        m.createDatabase(ns + "DB").setPassword(cd.getPwd()).setUsername(cd.getUser()).setJDBCDSN(cd.getJdbcURI("iswc"));
        // add rdfs:subClassOf and owl:Axiom (annotation)
        o.classes().findFirst().orElseThrow(AssertionError::new)
                .addSubClassOfStatement(o.getOWLThing()).annotate(o.getRDFSComment(), "Super class relation");
        // add d2rq:database ref
        m.classMaps().findFirst().orElseThrow(AssertionError::new)
                .setDatabase(m.databases().findFirst().orElseThrow(AssertionError::new));

        // validate mapping
        JenaModelUtils.print(m.asModel());
        Assert.assertEquals(15, m.asModel().size());

        try {
            OntGraphModel res = OntModelFactory.createModel(m.getData())
                    .setNsPrefix("schema", ns)
                    .setNsPrefix("iswc", ISWC.getURI());
            String txt = JenaModelUtils.toTurtleString(res);
            LOGGER.debug(":\n{}", txt);

            // validate whole graph
            OntClass c = res.getOntClass(res.expandPrefix("schema:X"));
            Assert.assertNotNull(c);
            OntIndividual i = res.getIndividual(res.expandPrefix("iswc:e-Business"));
            Assert.assertNotNull(i);
            List<OntCE> classes = i.classes().collect(Collectors.toList());
            Assert.assertEquals(1, classes.size());
            Assert.assertEquals(c, classes.get(0));

            Assert.assertEquals(22, res.size());
        } finally {
            m.close();
        }
    }

    @Test
    public void testMappingDerivingNamedIndividuals() {
        testMappingDerivingNamedIndividuals(false);
    }

    @Test
    public void testMappingDerivingNamedIndividualsWithControlOWL() {
        testMappingDerivingNamedIndividuals(true);
    }

    /**
     * Tests compare original d2rq default OWL representation of ISWC database with new one (ont-d2rq changes).
     */
    @Test
    public void testCompareOWLRepresentations() {
        Model original = ReadWriteUtils.loadResourceTTLFile("original.iswc.owl.ttl");
        try (SystemLoader loader = new SystemLoader().setJdbcURL(JDBC_URI).setSystemBaseURI("http://db#")) {
            LOGGER.info("System base URI: {}", loader.getSystemBaseURI());
            LOGGER.debug("Load schema with data.");
            Model actual = loader.build().getDataModel();
            JenaModelUtils.print(actual);
            Stream.of(OWL.Class, OWL.DatatypeProperty, OWL.ObjectProperty).forEach(t -> {
                LOGGER.debug("Test {}", t);
                Set<Resource> classes_ex = subjects(original, t).collect(Collectors.toSet());
                Set<Resource> classes_ac = subjects(actual, t).collect(Collectors.toSet());
                Assert.assertEquals(t + ": expected=" + classes_ex.size() +
                                ", actual=" + classes_ac.size(),
                        classes_ex, classes_ac);
            });
        }
    }

    private static void testMappingDerivingNamedIndividuals(boolean withControlOWL) {
        String map_ns = "http://map#";
        String iswc_ns = "http://annotation.semanticweb.org/iswc/iswc.daml#";
        String foaf_ns = "http://xmlns.com/foaf/0.1/";

        try (Mapping m = MappingFactory.create()) {
            ConnectionData cd = ConnectionData.MYSQL;
            Database db = m.createDatabase(map_ns + "database")
                    .setUsername(cd.getUser())
                    .setPassword(cd.getPwd())
                    .setJDBCDSN(cd.getJdbcURI("iswc"));

            if (withControlOWL)
                m.getConfiguration().setControlOWL(true);

            m.createClassMap(map_ns + "topics").setDatabase(db)
                    .addClass(OWL.NamedIndividual).setURIColumn("topics.URI");

            m.asModel()
                    .setNsPrefix("map", map_ns).setNsPrefix("iswc", iswc_ns).setNsPrefix("foaf", foaf_ns);
            JenaModelUtils.print(m.asModel());

            Model res = ModelFactory.createModelForGraph(m.getData())
                    .setNsPrefix("jswc", iswc_ns);
            String txt = JenaModelUtils.toTurtleString(res);
            LOGGER.debug(":\n{}", txt);

            JenaModelUtils.print(m.asModel());

            List<Resource> individuals = res.listStatements(null, RDF.type, OWL.NamedIndividual)
                    .mapWith(Statement::getSubject).toList();
            Assert.assertEquals(15, individuals.size());
            individuals.forEach(x -> Assert.assertEquals("Test individual: " + x, 1, x.listProperties().toList().size()));
        }

    }

    private static Stream<Resource> subjects(Model m, Resource type) {
        return Iter.asStream(m.listSubjectsWithProperty(RDF.type, type)).filter(RDFNode::isURIResource);
    }
}
