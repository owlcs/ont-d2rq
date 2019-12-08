package com.github.owlcs.d2rq;

import com.google.common.collect.LinkedListMultimap;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.MappingHelper;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import de.fuberlin.wiwiss.d2rq.mapgen.W3CMappingGenerator;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import de.fuberlin.wiwiss.d2rq.utils.MappingUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.XSD;
import org.junit.*;
import org.semanticweb.owlapi.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.owlcs.d2rq.conf.ConnectionData;
import com.github.owlcs.d2rq.utils.D2RQGraphUtils;
import com.github.owlcs.d2rq.utils.OWLUtils;
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

    private ConnectedDB db;

    @BeforeClass
    public static void prepareData() throws Exception {
        data.createDatabase("/no_pk.sql", dbName);
    }

    @AfterClass
    public static void clear() throws Exception {
        data.dropDatabase(dbName);
    }

    @Before
    public void before() {
        db = ConnectionData.POSTGRES.toConnectedDB(dbName);
    }

    @After
    public void after() {
        db.close();
    }

    @Test
    public void testW3CMappingGenerator() {
        String uri = "http://test.ex/w3c";
        MappingGenerator g = new W3CMappingGenerator(db);
        Mapping m = MappingFactory.create(g.mappingModel(uri), uri);

        m.getConfiguration().setControlOWL(true).setServeVocabulary(true);
        MappingUtils.print(m);

        OntGraphModel all = OntModelFactory.createModel(m.getData(), OntModelConfig.ONT_PERSONALITY_LAX);
        JenaModelUtils.print(all);
        OWLUtils.validateOWLEntities(all, 2, 0, 6, 0, 6, 7);

        validateNamedIndividuals(all, 6, uri, "%s#pk_table/id=%d", "%s/vocab#pk_table#%s", false);

        validateAnonymousIndividuals(all, 7, uri, "%s/vocab#no_pk_table#%s", false);
    }

    @Test
    public void testDefaultMappingGenerator() {
        String uri = "http://test.ex/def";
        MappingGenerator g = new MappingGenerator(db);
        Mapping m = MappingFactory.create(g.mappingModel(uri), uri);

        m.getConfiguration().setControlOWL(true).setServeVocabulary(true);
        MappingUtils.print(m);

        OntGraphModel all = OntModelFactory.createModel(m.getData(), OntModelConfig.ONT_PERSONALITY_LAX);
        JenaModelUtils.print(all);
        OWLUtils.validateOWLEntities(all, 2, 0, 6, 0, 7, 0);

        validateNamedIndividuals(all, 6, uri, "%s#pk_table/%d", "%s/vocab#pk_table_%s", true);

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
        checkIndividualAssertions(noPkIndividual, noPkAssertions);
    }

    @Test
    public void testDefaultDocumentSource() {
        String uri = "http://test.ex/ogds";
        D2RQGraphDocumentSource source = D2RQGraphDocumentSource.create(IRI.create(uri),
                IRI.create(db.getJdbcURL()), db.getUsername(), db.getPassword(), null);
        MappingHelper.useConnectedDB(source.getMapping(), db);
        Mapping m = source.getMapping();
        MappingUtils.print(m);

        OntGraphModel all = OntModelFactory.createModel(D2RQGraphUtils.toVirtual(source.getGraph()),
                OntModelConfig.ONT_PERSONALITY_LAX);
        JenaModelUtils.print(all);
        OWLUtils.validateOWLEntities(all, 2, 0, 6, 0, 6, 7);

        validateNamedIndividuals(all, 6, uri, "%s#pk_table/%d", "%s/vocab#pk_table_%s", true);

        validateAnonymousIndividuals(all, 7, uri, "%s/vocab#no_pk_table_%s", true);
    }

    private static void validateNamedIndividuals(OntGraphModel m,
                                                 @SuppressWarnings("SameParameterValue") int num,
                                                 String uri,
                                                 String individual,
                                                 String property,
                                                 boolean withLabel) {
        LinkedListMultimap<OntPE, Resource> pkAssertions = LinkedListMultimap.create();
        OntNDP pkTableId = m.getOntEntity(OntNDP.class, String.format(property, uri, "id"));
        OntNDP pkTableNumericColumn = m.getOntEntity(OntNDP.class, String.format(property, uri, "numeric_column"));
        OntNDP pkTableTextColumn = m.getOntEntity(OntNDP.class, String.format(property, uri, "text_column"));
        pkAssertions.put(pkTableId, XSD.decimal);
        pkAssertions.put(pkTableNumericColumn, XSD.decimal);
        pkAssertions.put(pkTableTextColumn, XSD.xstring);
        if (withLabel)
            pkAssertions.put(m.getRDFSLabel(), XSD.xstring);
        IntStream.rangeClosed(1, num).mapToObj(i -> String.format(individual, uri, i))
                .map(m::getResource)
                .map(r -> r.as(OntIndividual.Named.class))
                .forEach(i -> checkIndividualAssertions(i, pkAssertions));
    }

    private static void validateAnonymousIndividuals(OntGraphModel m,
                                                     @SuppressWarnings("SameParameterValue") int num,
                                                     String uri,
                                                     String property,
                                                     boolean withLabel) {
        OntNDP noPkTableNumber = m.getOntEntity(OntNDP.class, String.format(property, uri, "number"));
        OntNDP noPkTableParameter = m.getOntEntity(OntNDP.class, String.format(property, uri, "parameter"));
        OntNDP noPkTableValue = m.getOntEntity(OntNDP.class, String.format(property, uri, "value"));
        OntNAP label = m.getRDFSLabel();

        List<OntIndividual> anons = m.ontObjects(OntIndividual.Anonymous.class).collect(Collectors.toList());
        Assert.assertEquals(num, anons.size());
        for (OntIndividual i : anons) {
            int n = i.hasProperty(noPkTableParameter, "duplicate") ? 2 : 1;
            LinkedListMultimap<OntPE, Resource> noPkAssertions = LinkedListMultimap.create();
            for (int j = 0; j < n; j++)
                noPkAssertions.put(noPkTableNumber, XSD.integer);
            for (int j = 0; j < n; j++)
                noPkAssertions.put(noPkTableParameter, XSD.xstring);
            for (int j = 0; j < n; j++)
                noPkAssertions.put(noPkTableValue, XSD.decimal);
            if (withLabel) {
                for (int j = 0; j < n; j++)
                    noPkAssertions.put(label, XSD.xstring);
            }
            checkIndividualAssertions(i, noPkAssertions);
        }
    }

    private static void checkIndividualAssertions(OntIndividual i, LinkedListMultimap<OntPE, Resource> assertions) {
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
