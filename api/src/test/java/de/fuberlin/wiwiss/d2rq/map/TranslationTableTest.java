package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.RDFNode;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests the TranslationTable functionality
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class TranslationTableTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationTableTest.class);
    private String table1 = "http://test/table1";

    @Test
    public void testNewTranslationTableIsEmpty() {
        Mapping m = MappingFactory.create();
        TranslationTable table = m.createTranslationTable(table1);
        Assert.assertEquals(0, table.listTranslations().count());
    }

    @Test
    public void testTranslationTableIsSizeOneAfterAddingOneTranslation() {
        Mapping m = MappingFactory.create();
        TranslationTable table = m.createTranslationTable(table1);
        table.addTranslation("key1", "value1");
        Assert.assertEquals(1, table.listTranslations().count());
    }

    @Test
    public void testTranslationTableTranslator() {
        Mapping m = MappingFactory.create();
        TranslationTable table = m.createTranslationTable(table1);
        table.addTranslation("key1", "value1");
        table.addTranslation("key2", "value2");
        table.addTranslation("key3", "value3");
        Translator translator = table.asTranslator();
        Assert.assertEquals("value1", translator.toRDFValue("key1"));
        Assert.assertEquals("value2", translator.toRDFValue("key2"));
        Assert.assertEquals("key1", translator.toDBValue("value1"));
        Assert.assertEquals("key2", translator.toDBValue("value2"));
    }

    @Test
    public void testUndefinedTranslation() {
        Mapping m = MappingFactory.create();
        TranslationTable table = m.createTranslationTable(table1).addTranslation("key1", "value1");
        Translator translator = table.asTranslator();
        Assert.assertNull(translator.toRDFValue("unknownKey"));
        Assert.assertNull(translator.toDBValue("http://example.org/"));
    }

    @Test
    public void testNullTranslation() {
        Mapping m = MappingFactory.create();
        TranslationTable table = m.createTranslationTable(table1);
        table.addTranslation("key1", "value1");
        Translator translator = table.asTranslator();
        Assert.assertNull(translator.toRDFValue(null));
        Assert.assertNull(translator.toDBValue(null));
    }

    @Test
    public void testCreationDuplicateTranslationsCausesValidationError() {
        Mapping m = MappingFactory.create();
        TranslationTable tb = m.createTranslationTable("table").addTranslation("foo", "bar");
        tb.validate();
        tb.createTranslation().setDatabaseValue("foo").setLiteral("bar");
        try {
            tb.validate();
            Assert.fail("Expected error");
        } catch (D2RQException e) {
            LOGGER.debug("Expected: '{}'", e.getMessage());
        }
    }

    @Test
    public void testAddMultipleTranslations() {
        Mapping m = MappingFactory.create();
        TranslationTable tb1 = m.createTranslationTable("tb1").addTranslation("foo", "bar").addTranslation("foo", "bar2");
        Assert.assertEquals(1, m.listTranslationTables().count());
        TranslationTable tb2 = m.createTranslationTable("tb2")
                .createTranslation().setDatabaseValue("foo2").setLiteral("bar").getTable();
        Assert.assertEquals(2, m.listTranslationTables().count());

        Assert.assertEquals(2, tb1.listTranslations().count());
        Assert.assertEquals(1, tb2.listTranslations().count());
        Assert.assertEquals(2, tb1.listTranslations().filter(s -> "foo".equals(s.getDatabaseValue())).count());
        Assert.assertEquals(1, tb1.listTranslations().filter(s -> "bar2".equals(s.getRDFValue())).count());
        Assert.assertEquals(1, tb1.listTranslations().filter(s -> "bar".equals(s.getRDFValue())).count());

        tb1.validate();
        tb2.validate();

        m.createDatabase("db").setJDBCDSN("jdbc:").getMapping().validate();
    }

    @Test
    public void testAddExternalTable() {
        TranslationTable ex = MappingFactory.create()
                .createTranslationTable("tb").addTranslation("a", "b").addTranslation("c", "c");

        Mapping m = MappingFactory.create().addTranslationTable(ex);
        Assert.assertEquals(1, m.listTranslationTables().count());
        Assert.assertEquals(1, m.asModel().listStatements(null, RDF.type, D2RQ.TranslationTable).toSet().size());
        Assert.assertEquals(2, m.asModel().listStatements(null, D2RQ.translation, (RDFNode) null).toSet().size());
        Assert.assertEquals(2, m.listTranslationTables().flatMap(TranslationTable::listTranslations).count());

        List<String> expected = m.listTranslationTables()
                .flatMap(TranslationTable::listTranslations)
                .flatMap(s -> Stream.of(s.getDatabaseValue(), s.getRDFValue()))
                .sorted()
                .collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList("a", "b", "c", "c"), expected);
    }

    @Test
    public void testAddTranslationClass() {
        String clazz = TestTranslator.class.getName();
        LOGGER.debug("Set {}", clazz);
        TranslationTable tb = MappingFactory.create()
                .createTranslationTable("tb").setJavaClass(clazz);
        Assert.assertEquals(clazz, tb.getJavaClass());
        tb.validate();
        Assert.assertEquals("A", tb.asTranslator().toRDFValue(null));
        Translator tr = tb.asTranslator();
        Assert.assertEquals("XZ", tr.toDBValue(null));
        Assert.assertEquals("B", tr.toDBValue("A"));
    }

    @Test
    public void testAddTranslationFile() {
        String url = TranslationTableTest.class.getResource("/csv/translationtable.csv").toString();
        LOGGER.debug("Set {}", url);
        TranslationTable tb = MappingFactory.create()
                .createTranslationTable("tb").setHref(url);
        Assert.assertEquals(url, tb.getHref());
        tb.validate();
        Translator tr = tb.asTranslator();
        Assert.assertEquals("db1", tr.toDBValue("rdf1"));
        Assert.assertEquals("rdf2", tr.toRDFValue("db2"));
        Assert.assertNull(tr.toDBValue("sss"));
    }

    // must be public
    public static class TestTranslator implements Translator {
        @Override
        public String toRDFValue(String dbValue) {
            return "A";
        }

        @Override
        public String toDBValue(String rdfValue) {
            return Objects.equals(rdfValue, "A") ? "B" : "XZ";
        }

    }

}