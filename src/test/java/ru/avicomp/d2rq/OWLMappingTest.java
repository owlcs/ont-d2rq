package ru.avicomp.d2rq;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import de.fuberlin.wiwiss.d2rq.SystemLoader;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

/**
 * compare original d2rq default OWL representation of ISWC database with new one (ont-d2rq changes).
 * <p>
 * Created by szuev on 21.02.2017.
 */
public class OWLMappingTest {

    private static final Logger LOGGER = Logger.getLogger(OWLMappingTest.class);
    private static final String JDBC_URI = "jdbc:mysql://127.0.0.1/iswc?user=root";

    @Test
    public void test() {
        Model original = ReadWriteUtils.loadResourceTTLFile("original.iswc.owl.ttl");
        SystemLoader loader = new SystemLoader();
        loader.setJdbcURL(JDBC_URI);
        loader.setSystemBaseURI("http://db#");
        LOGGER.info("System base URI: " + loader.getSystemBaseURI());
        try {
            LOGGER.info("Load schema with data.");
            Model actual = loader.getMapping().getDataModel();
            ReadWriteUtils.print(actual);
            Stream.of(OWL.Class, OWL.DatatypeProperty, OWL.ObjectProperty).forEach(t -> {
                LOGGER.debug("Test " + t);
                Set<Resource> classes_ex = subjects(original, t).collect(Collectors.toSet());
                Set<Resource> classes_ac = subjects(actual, t).collect(Collectors.toSet());
                Assert.assertEquals(String.valueOf(t) + ": expected=" + classes_ex.size() + ", actual=" + classes_ac.size(), classes_ex, classes_ac);
            });
        } finally {
            loader.closeMappingGenerator();
        }
    }

    private static Stream<Resource> subjects(Model m, Resource type) {
        return Iter.asStream(m.listSubjectsWithProperty(RDF.type, type)).filter(RDFNode::isURIResource);
    }
}
