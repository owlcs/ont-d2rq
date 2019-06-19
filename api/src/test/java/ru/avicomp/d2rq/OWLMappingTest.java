package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ConnectionData;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * compare original d2rq default OWL representation of ISWC database with new one (ont-d2rq changes).
 * <p>
 * Created by szuev on 21.02.2017.
 */
public class OWLMappingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OWLMappingTest.class);
    private static final String JDBC_URI = ConnectionData.MYSQL.getJdbcConnectionString("iswc");

    @Test
    public void test() {
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

    private static Stream<Resource> subjects(Model m, Resource type) {
        return Iter.asStream(m.listSubjectsWithProperty(RDF.type, type)).filter(RDFNode::isURIResource);
    }
}
