package de.fuberlin.wiwiss.d2rq.csv;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable.Translation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;

/**
 * Unit tests for {@link TranslationTableParser}
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class TranslationTableParserTest {
    private Collection<Translation> simpleTranslations;

    @Before
    public void setUp() {
        this.simpleTranslations = new HashSet<>();
        this.simpleTranslations.add(new Translation("db1", "rdf1"));
        this.simpleTranslations.add(new Translation("db2", "rdf2"));
    }

    @Test
    public void testEmpty() {
        Collection<Translation> translations = new TranslationTableParser(
                new StringReader("")).parseTranslations();
        Assert.assertTrue(translations.isEmpty());
    }

    @Test
    public void testSimple() {
        String csv = "key,value";
        Collection<Translation> translations = new TranslationTableParser(
                new StringReader(csv)).parseTranslations();
        Assert.assertEquals(1, translations.size());
        Translation t = translations.iterator().next();
        Assert.assertEquals("key", t.dbValue());
        Assert.assertEquals("value", t.rdfValue());
    }

    @Test
    public void testTwoRows() {
        String csv = "db1,rdf1\ndb2,rdf2";
        Collection<Translation> translations = new TranslationTableParser(
                new StringReader(csv)).parseTranslations();
        Assert.assertEquals(2, translations.size());
        Assert.assertEquals(this.simpleTranslations, new HashSet<Translation>(translations));
    }

    @Test
    public void testParseFromFile() {
        Collection<Translation> translations = new TranslationTableParser(
                D2RQTestHelper.DIRECTORY + "csv/translationtable.csv").parseTranslations();
        Assert.assertEquals(this.simpleTranslations, new HashSet<Translation>(translations));
    }

    @Test
    public void testParseFromFileWithProtocol() {
        Collection<Translation> translations = new TranslationTableParser(
                D2RQTestHelper.DIRECTORY_URL + "csv/translationtable.csv").parseTranslations();
        Assert.assertEquals(this.simpleTranslations, new HashSet<Translation>(translations));
    }
}
