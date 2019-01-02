package de.fuberlin.wiwiss.d2rq.functional_tests;

import de.fuberlin.wiwiss.d2rq.helpers.FindTestFramework;
import de.fuberlin.wiwiss.d2rq.vocab.ISWC;
import de.fuberlin.wiwiss.d2rq.vocab.SKOS;
import org.apache.jena.JenaRuntime;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DC;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.VCARD;
import org.junit.Ignore;
import org.junit.Test;
import ru.avicomp.conf.ISWCData;
import ru.avicomp.d2rq.ISWCModelDataTest;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Functional tests for the find(spo) operation of {@link de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ}.
 * From AllTests:
 * Functional test suite for D2RQ.
 * These are functional tests (as opposed to unit tests).
 * The suite runs different find queries against the ISWC database,
 * using the example map provided with the D2RQ manual.
 * To run the test, you must have either the MySQL or the MS Access version accessible.
 * Maybe you must adapt the connection information at the beginning of the map file to fit your database server.
 *
 * <p>
 * Each test method runs one or more find queries and automatically compares the actual
 * results to the expected results.
 * For some tests, only the number of returned triples is checked.
 * For others, the returned triples are compared against expected triples.
 * TODO: test postgers also
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class FindTest extends FindTestFramework {

    public FindTest(ISWCData data) {
        super(data);
    }

    @Test
    public void testListTypeStatements() {
        find(null, RDF.type, null);
        assertStatement(resource("papers/1"), RDF.type, ISWC.InProceedings);
        // Paper6 is filtered by d2rq:condition
        assertNoStatement(resource("papers/6"), RDF.type, ISWC.InProceedings);
        assertStatement(resource("conferences/23541"), RDF.type, ISWC.Conference);
        assertStatement(resource("topics/15"), RDF.type, SKOS.Concept);
        // schema is turned off:
        assertNoStatement(FOAF.name, RDF.type, OWL.DatatypeProperty);

        assertStatementCount(62);
    }

    @Test
    public void testListTopicInstances() {
        find(null, RDF.type, SKOS.Concept);
        assertStatement(resource("topics/1"), RDF.type, SKOS.Concept);
        assertStatement(resource("topics/15"), RDF.type, SKOS.Concept);
        assertStatementCount(15);
    }

    @Test
    public void testListTopicNames() {
        find(null, SKOS.prefLabel, null);
        assertStatement(resource("topics/1"), SKOS.prefLabel, m.createTypedLiteral(
                "Knowledge Representation Languages"));
        assertStatement(resource("topics/15"), SKOS.prefLabel, m.createTypedLiteral(
                "Knowledge Management"));
        assertStatementCount(15);
    }

    @Test
    public void testListAuthors() {
        find(null, DC.creator, null);
        assertStatement(resource("papers/1"), DC.creator, resource("persons/1"));
        assertStatement(resource("papers/1"), DC.creator, resource("persons/2"));
        assertStatementCount(8);
    }

    @Test
    public void testDatatypeFindByYear() {
        find(null, DC.date, m.createTypedLiteral("2003", XSDDatatype.XSDgYear));
        assertStatement(resource("papers/4"), DC.date, m.createTypedLiteral("2003", XSDDatatype.XSDgYear));
        assertStatementCount(1);
    }

    @Test
    public void testDatatypeFindByString() {
        find(null, SKOS.prefLabel, m.createTypedLiteral("E-Business", XSDDatatype.XSDstring));
        assertStatement(resource("topics/13"), SKOS.prefLabel, m.createTypedLiteral("E-Business", XSDDatatype.XSDstring));
        assertStatementCount(1);
    }

    @Test
    public void testXSDStringDoesntMatchPlainLiteral() {
        // in RDF 1.1 plain it is the same
        if (JenaRuntime.isRDF11) return;
        find(null, SKOS.prefLabel, m.createLiteral("E-Business"));
        assertStatementCount(0);
    }

    @Test
    public void testDatatypeFindYear() {
        find(resource("papers/2"), DC.date, null);
        assertStatement(resource("papers/2"), DC.date, m.createTypedLiteral("2002", XSDDatatype.XSDgYear));
        assertStatementCount(1);
    }

    @Test
    public void testDatatypeYearContains() {
        find(resource("papers/2"), DC.date, m.createTypedLiteral("2002", XSDDatatype.XSDgYear));
        assertStatement(resource("papers/2"), DC.date, m.createTypedLiteral("2002", XSDDatatype.XSDgYear));
        assertStatementCount(1);
        assertStatementCount(1);
    }

    @Test
    public void testLiteralLanguage() {
        find(null, DC.title, m.createLiteral("Trusting Information Sources One Citizen at a Time", "en"));
        assertStatement(resource("papers/1"), DC.title, m.createLiteral("Trusting Information Sources One Citizen at a Time", "en"));
        assertStatementCount(1);
    }

    @Test
    public void testFindSubjectWhereObjectURIColumn() {
        find(null, DC.creator, resource("persons/4"));
        assertStatement(resource("papers/2"), DC.creator, resource("persons/4"));
        assertStatementCount(1);
    }

    @Test
    public void testFindSubjectWithConditionalObject() {
        // The paper is not published, therefore no result triples
        find(null, DC.creator, resource("persons/5"));
        assertStatementCount(0);
    }

    @Test
    public void testFindSubjectWhereObjectURIPattern() {
        find(null, FOAF.mbox, m.createResource("mailto:andy.seaborne@hpl.hp.com"));
        assertStatement(resource("persons/6"), FOAF.mbox, m.createResource("mailto:andy.seaborne@hpl.hp.com"));
        assertStatementCount(1);
    }

    @Test
    public void testFindAnonymousNode() {
        AnonId id = new AnonId("map:PostalAddresses@@7");
        find(null, VCARD.Pcode, m.createLiteral("BS34 8QZ"));
        assertStatement(m.createResource(id), VCARD.Pcode, m.createLiteral("BS34 8QZ"));
        assertStatementCount(1);
    }

    @Test
    public void testMatchAnonymousSubject() {
        AnonId id = new AnonId("map:PostalAddresses@@7");
        find(m.createResource(id), VCARD.Pcode, null);
        assertStatement(m.createResource(id), VCARD.Pcode, m.createLiteral("BS34 8QZ"));
        assertStatementCount(1);
    }

    @Test
    public void testMatchAnonymousObject() {
        AnonId id = new AnonId("map:PostalAddresses@@7");
        find(null, VCARD.ADR, m.createResource(id));
        assertStatement(resource("organizations/7"), VCARD.ADR, m.createResource(id));
        assertStatementCount(1);
    }

    @Test
    public void testDump() {
        find(null, null, null);
        // no schema:
        assertStatementCount(283);
    }

    @Test
    public void testFindPredicate() {
        find(resource("papers/2"), null, m.createTypedLiteral("2002", XSDDatatype.XSDgYear));
        assertStatement(resource("papers/2"), DC.date, m.createTypedLiteral("2002", XSDDatatype.XSDgYear));
        assertStatementCount(1);
    }

    @Test
    public void testReverseFetchWithDatatype() {
        find(null, null, m.createTypedLiteral("2002", XSDDatatype.XSDgYear));
        assertStatementCount(3);
    }

    @Test
    public void testReverseFetchWithURI() {
        find(null, null, resource("topics/11"));
        assertStatementCount(2);
    }

    @Test
    public void testFindAliasedPropertyBridge() {
        find(null, SKOS.broader, null);
        assertStatement(resource("topics/1"), SKOS.broader, resource("topics/3"));
        assertStatementCount(10);
    }

    /**
     * @see ru.avicomp.d2rq.DynamicSchemaTest
     * @see ISWCModelDataTest
     */
    @Ignore // schema part are excluded from the graph, there is now separated test for it
    @Test
    public void testDefinitions() {
        ModelFactory.createModelForGraph(graph).write(System.err, "ttl");
        find(ISWC.Conference, null, null);
        assertStatement(ISWC.Conference, RDF.type, OWL.Class);
        assertStatement(ISWC.Conference, RDFS.label, m.createLiteral("conference"));
        assertStatement(ISWC.Conference, RDFS.comment, m.createLiteral("A conference"));
        assertStatement(ISWC.Conference, RDFS.subClassOf, ISWC.Event);
        find(RDFS.label, null, null);
        // rdfs:label is a datatype property since it is redefined in map (and belongs to several tables (conferences.Name, etc))
        assertStatement(RDFS.label, RDF.type, OWL.DatatypeProperty);
        assertStatement(RDFS.label, RDFS.label, m.createLiteral("label"));
        assertStatement(RDFS.label, RDFS.comment, m.createLiteral("A human-readable name for the subject."));
        assertStatement(RDFS.label, RDFS.domain, RDFS.Resource);
    }
}
