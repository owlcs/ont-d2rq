package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.map.TranslationTable.Translation;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the TranslationTable functionality
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class TranslationTableTest {
    Resource table1 = ResourceFactory.createResource("http://test/table1");

    @Test
    public void testNewTranslationTableIsEmpty() {
        TranslationTable table = new TranslationTable(table1);
        Assert.assertEquals(0, table.size());
    }

    @Test
    public void testTranslationTableIsSizeOneAfterAddingOneTranslation() {
        TranslationTable table = new TranslationTable(table1);
        table.addTranslation("key1", "value1");
        Assert.assertEquals(1, table.size());
    }

    @Test
    public void testTranslationTableTranslator() {
        TranslationTable table = new TranslationTable(table1);
        table.addTranslation("key1", "value1");
        table.addTranslation("key2", "value2");
        table.addTranslation("key3", "value3");
        Translator translator = table.translator();
        Assert.assertEquals("value1", translator.toRDFValue("key1"));
        Assert.assertEquals("value2", translator.toRDFValue("key2"));
        Assert.assertEquals("key1", translator.toDBValue("value1"));
        Assert.assertEquals("key2", translator.toDBValue("value2"));
    }

    @Test
    public void testUndefinedTranslation() {
        TranslationTable table = new TranslationTable(table1);
        table.addTranslation("key1", "value1");
        Translator translator = table.translator();
        Assert.assertNull(translator.toRDFValue("unknownKey"));
        Assert.assertNull(translator.toDBValue("http://example.org/"));
    }

    @Test
    public void testNullTranslation() {
        TranslationTable table = new TranslationTable(table1);
        table.addTranslation("key1", "value1");
        Translator translator = table.translator();
        Assert.assertNull(translator.toRDFValue(null));
        Assert.assertNull(translator.toDBValue(null));
    }

    @Test
    public void testTranslationsWithSameValuesAreEqual() {
        Translation t1 = new Translation("foo", "bar");
        Translation t2 = new Translation("foo", "bar");
        Assert.assertEquals(t1, t2);
        Assert.assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    public void testTranslationsWithDifferentValuesAreNotEqual() {
        Translation t1 = new Translation("foo", "bar");
        Translation t2 = new Translation("foo", "bar2");
        Translation t3 = new Translation("foo2", "bar");
        Assert.assertFalse(t1.equals(t2));
        Assert.assertFalse(t2.equals(t1));
        Assert.assertFalse(t1.hashCode() == t2.hashCode());
        Assert.assertFalse(t1.equals(t3));
        Assert.assertFalse(t3.equals(t1));
        Assert.assertFalse(t1.hashCode() == t3.hashCode());
    }
}