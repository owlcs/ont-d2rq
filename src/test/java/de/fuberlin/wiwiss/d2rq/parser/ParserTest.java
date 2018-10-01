package de.fuberlin.wiwiss.d2rq.parser;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;

/**
 * Unit tests for mapping parsing.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class ParserTest {
    private final static String TABLE_URI = "http://example.org/map#table1";

    private Model model;

    @Before
    public void setUp() {
        this.model = ModelFactory.createDefaultModel();
    }

    @Test
    public void testEmptyTranslationTable() {
        Resource r = addTranslationTableResource();
        Mapping mapping = MappingFactory.create(this.model, null);
        TranslationTable table = mapping.findTranslationTable(r);
        Assert.assertNotNull(table);
        Assert.assertEquals(0, table.size());
    }

    @Test
    public void testGetSameTranslationTable() {
        Resource r = addTranslationTableResource();
        addTranslationResource(r, "foo", "bar");
        Mapping mapping = MappingFactory.create(this.model, null);
        TranslationTable table1 = mapping.findTranslationTable(r);
        TranslationTable table2 = mapping.findTranslationTable(r);
        Assert.assertSame(table1, table2);
    }

    @Test
    public void testParseTranslationTable() {
        Resource r = addTranslationTableResource();
        addTranslationResource(r, "foo", "bar");
        Mapping mapping = MappingFactory.create(this.model, null);
        TranslationTable table = mapping.findTranslationTable(r);
        Assert.assertEquals(1, table.size());
        Translator translator = table.asTranslator();
        Assert.assertEquals("bar", translator.toRDFValue("foo"));
    }

    @Test
    public void testParseAlias() {
        Mapping mapping = MappingHelper.readFromTestFile("/parser/alias.ttl");
        MappingHelper.connectToDummyDBs(mapping);
        Assert.assertEquals(1, mapping.compiledPropertyBridges().size());
        TripleRelation bridge = mapping.compiledPropertyBridges().iterator().next();
        Assert.assertTrue(bridge.baseRelation().condition().isTrue());
        AliasMap aliases = bridge.baseRelation().aliases();
        AliasMap expected = new AliasMap(Collections.singleton(SQL.parseAlias("People AS Bosses")));
        Assert.assertEquals(expected, aliases);
    }

    @Test
    public void testParseResourceInsteadOfLiteral() {
        try {
            MappingHelper.readFromTestFile("/parser/resource-instead-of-literal.ttl");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_RESOURCE_INSTEADOF_LITERAL, ex.errorCode());
        }
    }

    @Test
    public void testParseLiteralInsteadOfResource() {
        try {
            MappingHelper.readFromTestFile("/parser/literal-instead-of-resource.ttl");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_LITERAL_INSTEADOF_RESOURCE, ex.errorCode());
        }
    }

    @Test
    public void testTranslationTableRDFValueCanBeLiteral() {
        Mapping m = MappingHelper.readFromTestFile("/parser/translation-table.ttl");
        TranslationTable tt = m.findTranslationTable(ResourceFactory.createResource("http://example.org/tt"));
        Assert.assertEquals("http://example.org/foo", tt.asTranslator().toRDFValue("literal"));
    }

    @Test
    public void testTranslationTableRDFValueCanBeURI() {
        Mapping m = MappingHelper.readFromTestFile("/parser/translation-table.ttl");
        TranslationTable tt = m.findTranslationTable(ResourceFactory.createResource("http://example.org/tt"));
        Assert.assertEquals("http://example.org/foo", tt.asTranslator().toRDFValue("uri"));
    }

    @Test
    public void testTypeConflictClassMapAndBridgeIsDetected() {
        try {
            MappingHelper.readFromTestFile("/parser/type-classmap-and-propertybridge.ttl");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_TYPECONFLICT, ex.errorCode());
        }
    }

    @Test
    public void testGenerateDownloadMap() {
        Mapping m = MappingHelper.readFromTestFile("/parser/download-map.ttl");
        MappingHelper.connectToDummyDBs(m);
        Resource name = ResourceFactory.createResource("http://example.org/dm");
        Assert.assertTrue(m.listDownloadMaps().map(MapObject::asResource).anyMatch(name::equals));
        DownloadMap d = m.findDownloadMap(name);
        Assert.assertNotNull(d);
        Assert.assertEquals("image/png", d.getMediaTypeValueMaker().makeValue(column -> null));
        Assert.assertEquals("People.pic", d.getContentDownloadColumn().qualifiedName());
        Assert.assertEquals("URI(Pattern(http://example.org/downloads/@@People.ID@@))", d.nodeMaker().toString());
        Assert.assertEquals(new HashSet<ProjectionSpec>() {{
                                add(SQL.parseAttribute("People.ID"));
                                add(SQL.parseAttribute("People.pic"));
                            }},
                d.getRelation().projections());
        Assert.assertTrue(d.getRelation().isUnique());
        Assert.assertTrue(d.getRelation().condition().isTrue());
        Assert.assertTrue(d.getRelation().joinConditions().isEmpty());
    }

    private Resource addTranslationTableResource() {
        return this.model.createResource(TABLE_URI, D2RQ.TranslationTable);
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    private Resource addTranslationResource(Resource table, String dbValue, String rdfValue) {
        Resource translation = this.model.createResource();
        translation.addProperty(D2RQ.databaseValue, dbValue);
        translation.addProperty(D2RQ.rdfValue, rdfValue);
        table.addProperty(D2RQ.translation, translation);
        return translation;
    }
}
