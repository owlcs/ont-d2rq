package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.map.impl.TranslationTableImpl;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the TranslationTable functionality
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class TranslationTableTest {
    private String table1 = "http://test/table1";

    @Test
    public void testNewTranslationTableIsEmpty() {
        Mapping m = MappingFactory.createEmpty();
        TranslationTable table = m.createTranslationTable(table1);
        Assert.assertEquals(0, table.size());
    }

    @Test
    public void testTranslationTableIsSizeOneAfterAddingOneTranslation() {
        Mapping m = MappingFactory.createEmpty();
        TranslationTable table = m.createTranslationTable(table1);
        table.addTranslation("key1", "value1");
        Assert.assertEquals(1, table.size());
    }

    @Test
    public void testTranslationTableTranslator() {
        Mapping m = MappingFactory.createEmpty();
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
        Mapping m = MappingFactory.createEmpty();
        TranslationTable table = m.createTranslationTable(table1).addTranslation("key1", "value1");
        Translator translator = table.asTranslator();
        Assert.assertNull(translator.toRDFValue("unknownKey"));
        Assert.assertNull(translator.toDBValue("http://example.org/"));
    }

    @Test
    public void testNullTranslation() {
        Mapping m = MappingFactory.createEmpty();
        TranslationTable table = m.createTranslationTable(table1);
        table.addTranslation("key1", "value1");
        Translator translator = table.asTranslator();
        Assert.assertNull(translator.toRDFValue(null));
        Assert.assertNull(translator.toDBValue(null));
    }

    @Test
    public void testTranslationsWithSameValuesAreEqual() {
        TranslationTableImpl.Entry t1 = new TranslationTableImpl.Entry("foo", "bar");
        TranslationTableImpl.Entry t2 = new TranslationTableImpl.Entry("foo", "bar");
        Assert.assertEquals(t1, t2);
        Assert.assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    public void testTranslationsWithDifferentValuesAreNotEqual() {
        TranslationTableImpl.Entry t1 = new TranslationTableImpl.Entry("foo", "bar");
        TranslationTableImpl.Entry t2 = new TranslationTableImpl.Entry("foo", "bar2");
        TranslationTableImpl.Entry t3 = new TranslationTableImpl.Entry("foo2", "bar");
        Assert.assertNotEquals(t1, t2);
        Assert.assertNotEquals(t2, t1);
        Assert.assertNotEquals(t1.hashCode(), t2.hashCode());
        Assert.assertNotEquals(t1, t3);
        Assert.assertNotEquals(t3, t1);
        Assert.assertNotEquals(t1.hashCode(), t3.hashCode());
    }
}