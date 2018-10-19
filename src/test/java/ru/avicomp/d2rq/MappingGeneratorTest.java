package ru.avicomp.d2rq;

import com.google.common.collect.LinkedListMultimap;
import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.helpers.MappingTestHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import de.fuberlin.wiwiss.d2rq.mapgen.W3CMappingGenerator;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.XSD;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.model.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

            OntNDP pkTableId = all.getOntEntity(OntNDP.class, String.format("%s/vocab#pk_table#id", uri));
            OntNDP pkTableNumericColumn = all.getOntEntity(OntNDP.class, String.format("%s/vocab#pk_table#numeric_column", uri));
            OntNDP pkTableTextColumn = all.getOntEntity(OntNDP.class, String.format("%s/vocab#pk_table#text_column", uri));
            LinkedListMultimap<OntPE, Resource> assertions = LinkedListMultimap.create();
            assertions.put(pkTableId, XSD.decimal);
            assertions.put(pkTableNumericColumn, XSD.decimal);
            assertions.put(pkTableTextColumn, XSD.xstring);

            IntStream.rangeClosed(1, 6).mapToObj(i -> String.format("%s#pk_table/id=%d", uri, i))
                    .map(all::getResource)
                    .map(r -> r.as(OntIndividual.Named.class))
                    .forEach(i -> checkNamedNoPKIndividualAssertions(i, assertions));

            OntNDP noPkTableNumber = all.getOntEntity(OntNDP.class, String.format("%s/vocab#no_pk_table#number", uri));
            OntNDP noPkTableParameter = all.getOntEntity(OntNDP.class, String.format("%s/vocab#no_pk_table#parameter", uri));
            OntNDP noPkTableValue = all.getOntEntity(OntNDP.class, String.format("%s/vocab#no_pk_table#value", uri));
            List<OntIndividual> anons = all.ontObjects(OntIndividual.Anonymous.class).collect(Collectors.toList());
            for (OntIndividual i : anons) {
                int n = i.hasProperty(noPkTableParameter, "duplicate") ? 2 : 1;
                LinkedListMultimap<OntPE, Resource> noPkAssertions = LinkedListMultimap.create();
                for (int j = 0; j < n; j++)
                    noPkAssertions.put(noPkTableNumber, XSD.integer);
                for (int j = 0; j < n; j++)
                    noPkAssertions.put(noPkTableParameter, XSD.xstring);
                for (int j = 0; j < n; j++)
                    noPkAssertions.put(noPkTableValue, XSD.decimal);
                checkNamedNoPKIndividualAssertions(i, noPkAssertions);
            }
        }
    }

    @Test
    public void testDefaultMappingGenerator() {
        String uri = "http://test.ex";
        try (ConnectedDB db = ConnectionData.POSTGRES.toConnectedDB(dbName)) {
            MappingGenerator g = new MappingGenerator(db);
            Mapping m = MappingFactory.create(g.mappingModel(uri), uri);

            m.getConfiguration().setControlOWL(true).setServeVocabulary(true);
            MappingTestHelper.print(m);

            OntGraphModel all = OntModelFactory.createModel(m.getData(), OntModelConfig.ONT_PERSONALITY_LAX);
            D2RQTestHelper.print(all);
            Assert.assertEquals(7, all.listNamedIndividuals().peek(i -> LOGGER.debug("Named: {}", i)).count());
            Assert.assertEquals(0, all.ontObjects(OntIndividual.Anonymous.class)
                    .peek(i -> LOGGER.debug("Anonymous: {}", i)).count());

            Assert.assertEquals(7, all.ontObjects(OntIndividual.class)
                    .peek(i -> LOGGER.debug("Individual: {}", i)).count());

            Assert.assertEquals(2, all.listClasses().peek(x -> LOGGER.debug("Class: {}", x)).count());

            Assert.assertEquals(6, all.listDataProperties().peek(x -> LOGGER.debug("DatatypeProperty: {}", x)).count());

            OntNDP pkTableId = all.getOntEntity(OntNDP.class, all.expandPrefix("vocab:pk_table_id"));
            OntNDP pkTableNumericColumn = all.getOntEntity(OntNDP.class, all.expandPrefix("vocab:pk_table_numeric_column"));
            OntNDP pkTableTextColumn = all.getOntEntity(OntNDP.class, all.expandPrefix("vocab:pk_table_text_column"));
            OntNAP label = all.getRDFSLabel();
            LinkedListMultimap<OntPE, Resource> pkAssertions = LinkedListMultimap.create();
            pkAssertions.put(pkTableId, XSD.decimal);
            pkAssertions.put(pkTableNumericColumn, XSD.decimal);
            pkAssertions.put(pkTableTextColumn, XSD.xstring);
            pkAssertions.put(label, XSD.xstring);

            IntStream.rangeClosed(1, 6).mapToObj(i -> String.format("%s#pk_table/%d", uri, i))
                    .map(all::getResource)
                    .map(r -> r.as(OntIndividual.Named.class))
                    .forEach(i -> checkNamedNoPKIndividualAssertions(i, pkAssertions));

            OntIndividual noPkIndividual = all.getOntEntity(OntIndividual.Named.class, uri + "#no_pk_table");
            LinkedListMultimap<OntPE, Resource> noPkAssertions = LinkedListMultimap.create();
            OntNDP noPkTableNumber = all.getOntEntity(OntNDP.class, all.expandPrefix("vocab:no_pk_table_number"));
            OntNDP noPkTableParameter = all.getOntEntity(OntNDP.class, all.expandPrefix("vocab:no_pk_table_parameter"));
            OntNDP noPkTableValue = all.getOntEntity(OntNDP.class, all.expandPrefix("vocab:no_pk_table_value"));
            IntStream.rangeClosed(1, 8).forEach(i -> {
                noPkAssertions.put(noPkTableNumber, XSD.integer);
                noPkAssertions.put(noPkTableParameter, XSD.xstring);
                noPkAssertions.put(noPkTableValue, XSD.decimal);
            });
            checkNamedNoPKIndividualAssertions(noPkIndividual, noPkAssertions);
        }
    }

    private static void checkNamedNoPKIndividualAssertions(OntIndividual i, LinkedListMultimap<OntPE, Resource> assertions) {
        List<OntStatement> statements = i.positiveAssertions()
                .peek(s -> LOGGER.debug("{} assertion: {}", PrettyPrinter.toString(i), PrettyPrinter.toString(s)))
                .collect(Collectors.toList());
        Assert.assertEquals("Wrong assertions count for " + PrettyPrinter.toString(i),
                assertions.size(), statements.size());

        assertions.forEach((p, r) -> Assert.assertEquals(r.getURI(), statements.stream()
                .filter(s -> s.getPredicate().equals(p))
                .map(Statement::getLiteral).findFirst().orElseThrow(AssertionError::new).getDatatypeURI()));

    }
}
