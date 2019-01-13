package de.fuberlin.wiwiss.d2rq.parser;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.*;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.utils.MappingUtils;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
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

    @Test
    public void testEmptyTranslationTable() {
        Model model = ModelFactory.createDefaultModel();
        Mapping mapping = MappingFactory.create(model, null);
        Resource r = addTranslationTableResource(model);
        Assert.assertEquals(1, mapping.listTranslationTables().count());
        TranslationTable table = MappingUtils.findTranslationTable(mapping);
        Assert.assertNotNull(table);
        Assert.assertEquals(r, table.asResource());
        Assert.assertEquals(0, table.listTranslations().count());
    }

    @Test
    public void testGetSameTranslationTable() {
        Model model = ModelFactory.createDefaultModel();
        Resource r = addTranslationTableResource(model);
        Resource t = addTranslationResource(r, "foo1", "bar1");
        Mapping mapping = MappingFactory.create(model, null);
        TranslationTable table1 = MappingUtils.findTranslationTable(mapping);
        TranslationTable table2 = MappingUtils.findTranslationTable(mapping);
        Assert.assertNotSame(table1, table2);
        Assert.assertEquals(table1, table2);
        Resource translation1 = MappingUtils.findTranslation(table1).asResource();
        Resource translation2 = MappingUtils.findTranslation(table2).asResource();
        Assert.assertEquals(t, translation1);
        Assert.assertEquals(t, translation2);
    }

    @Test
    public void testParseTranslationTable() {
        Model model = ModelFactory.createDefaultModel();
        Resource r = addTranslationTableResource(model);
        addTranslationResource(r, "foo", "bar");
        Mapping mapping = MappingFactory.create(model, null);
        TranslationTable table = MappingUtils.findTranslationTable(mapping, r);
        Assert.assertEquals(1, table.listTranslations().count());
        Translator translator = table.asTranslator();
        Assert.assertEquals("bar", translator.toRDFValue("foo"));
    }

    @Test
    public void testParseAlias() {
        Mapping m = MappingUtils.readFromTestFile("/parser/alias.ttl");
        MappingUtils.connectToDummyDBs(m);
        Assert.assertEquals(1, MappingHelper.asConnectingMapping(m).compiledPropertyBridges().size());
        TripleRelation bridge = MappingHelper.asConnectingMapping(m).compiledPropertyBridges().iterator().next();
        Assert.assertTrue(bridge.baseRelation().condition().isTrue());
        AliasMap aliases = bridge.baseRelation().aliases();
        AliasMap expected = new AliasMap(Collections.singleton(SQL.parseAlias("People AS Bosses")));
        Assert.assertEquals(expected, aliases);
    }

    @Test
    public void testParseResourceInsteadOfLiteral() {
        try {
            MappingUtils.readFromTestFile("/parser/resource-instead-of-literal.ttl");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_RESOURCE_INSTEADOF_LITERAL, ex.errorCode());
        }
    }

    @Test
    public void testParseLiteralInsteadOfResource() {
        try {
            MappingUtils.readFromTestFile("/parser/literal-instead-of-resource.ttl");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_LITERAL_INSTEADOF_RESOURCE, ex.errorCode());
        }
    }

    @Test
    public void testTranslationTableRDFValueCanBeLiteral() {
        Mapping m = MappingUtils.readFromTestFile("/parser/translation-table.ttl");
        TranslationTable tt = MappingUtils.findTranslationTable(m, ResourceFactory.createResource("http://example.org/tt"));
        Assert.assertEquals("http://example.org/foo", tt.asTranslator().toRDFValue("literal"));
    }

    @Test
    public void testTranslationTableRDFValueCanBeURI() {
        Mapping m = MappingUtils.readFromTestFile("/parser/translation-table.ttl");
        TranslationTable tt = MappingUtils.findTranslationTable(m, ResourceFactory.createResource("http://example.org/tt"));
        Assert.assertEquals("http://example.org/foo", tt.asTranslator().toRDFValue("uri"));
    }

    @Test
    public void testTypeConflictClassMapAndBridgeIsDetected() {
        try {
            MappingUtils.readFromTestFile("/parser/type-classmap-and-propertybridge.ttl");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_TYPECONFLICT, ex.errorCode());
        }
    }

    @Test
    public void testGenerateDownloadMap() {
        Mapping m = MappingUtils.readFromTestFile("/parser/download-map.ttl");
        MappingUtils.connectToDummyDBs(m);
        DownloadMap d = MappingUtils.findDownloadMap(m, ResourceFactory.createResource("http://example.org/dm"));
        Assert.assertEquals("image/png", MappingUtils.getMediaTypeValueMaker(d).makeValue(column -> null));
        Attribute a = MappingUtils.getContentDownloadColumnAttribute(d);
        Assert.assertNotNull(a);
        Assert.assertEquals("People.pic", a.qualifiedName());
        Assert.assertEquals("URI(Pattern(http://example.org/downloads/@@People.ID@@))", MappingUtils.getNodeMaker(d).toString());
        Relation r = MappingUtils.getRelation(d);
        Assert.assertEquals(new HashSet<ProjectionSpec>() {{
                                add(SQL.parseAttribute("People.ID"));
                                add(SQL.parseAttribute("People.pic"));
                            }},
                r.projections());
        Assert.assertTrue(r.isUnique());
        Assert.assertTrue(r.condition().isTrue());
        Assert.assertTrue(r.joinConditions().isEmpty());
    }

    private static Resource addTranslationTableResource(Model model) {
        return model.createResource(TABLE_URI, D2RQ.TranslationTable);
    }


    private static Resource addTranslationResource(Resource table, String dbValue, String rdfValue) {
        Resource res = table.getModel().createResource()
                .addProperty(D2RQ.databaseValue, dbValue)
                .addProperty(D2RQ.rdfValue, rdfValue);
        table.addProperty(D2RQ.translation, res);
        return res;
    }

}
