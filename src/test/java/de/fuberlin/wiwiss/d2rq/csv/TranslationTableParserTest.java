package de.fuberlin.wiwiss.d2rq.csv;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;

/**
 * Unit tests for {@link TranslationTableParser}
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class TranslationTableParserTest {
    private Collection<TranslationTableParser.Row> simpleTranslations;

    @Before
    public void setUp() {
        this.simpleTranslations = new HashSet<>();
        this.simpleTranslations.add(new TranslationTableParser.Row("db1", "rdf1"));
        this.simpleTranslations.add(new TranslationTableParser.Row("db2", "rdf2"));
    }

    @Test
    public void testEmpty() {
        Collection<TranslationTableParser.Row> translations = new TranslationTableParser(new StringReader("")).parseTranslations();
        Assert.assertTrue(translations.isEmpty());
    }

    @Test
    public void testSimple() {
        String csv = "key,value";
        Collection<TranslationTableParser.Row> translations = new TranslationTableParser(new StringReader(csv)).parseTranslations();
        Assert.assertEquals(1, translations.size());
        TranslationTableParser.Row t = translations.iterator().next();
        Assert.assertEquals("key", t.first());
        Assert.assertEquals("value", t.second());
    }

    @Test
    public void testTwoRows() {
        String csv = "db1,rdf1\ndb2,rdf2";
        Collection<TranslationTableParser.Row> translations = new TranslationTableParser(new StringReader(csv)).parseTranslations();
        Assert.assertEquals(2, translations.size());
        Assert.assertEquals(this.simpleTranslations, new HashSet<>(translations));
    }

    @Test
    public void testParseFromFile() {
        String file = D2RQTestHelper.getRelativeResourcePath("/csv/translationtable.csv");
        Collection<TranslationTableParser.Row> translations = new TranslationTableParser(file).parseTranslations();
        Assert.assertEquals(this.simpleTranslations, new HashSet<>(translations));
    }

    @Test
    public void testParseFromFileWithProtocol() {
        URL url = TranslationTableParser.class.getResource("/csv/translationtable.csv");
        Collection<TranslationTableParser.Row> translations = new TranslationTableParser(url.toString()).parseTranslations();
        Assert.assertEquals(this.simpleTranslations, new HashSet<>(translations));
    }
}
