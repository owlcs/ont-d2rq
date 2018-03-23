package de.fuberlin.wiwiss.d2rq.mapgen;

import de.fuberlin.wiwiss.d2rq.mapgen.Filter.IdentifierMatcher;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterParser.ParseException;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class FilterParserTest {

    @Test
    public void testEmpty() throws ParseException {
        Assert.assertEquals("", toString(new FilterParser("").parse()));
    }

    @Test
    public void testSimple() throws ParseException {
        Assert.assertEquals("'foo'", toString(new FilterParser("foo").parse()));
    }

    @Test
    public void testMultipleStrings() throws ParseException {
        Assert.assertEquals("'foo'.'bar'", toString(new FilterParser("foo.bar").parse()));
    }

    @Test
    public void testMultipleFilters() throws ParseException {
        Assert.assertEquals("'foo','bar'", toString(new FilterParser("foo,bar").parse()));
    }

    @Test
    public void testMultipleFiltersNewline() throws ParseException {
        Assert.assertEquals("'foo','bar'", toString(new FilterParser("foo\n\rbar").parse()));
    }

    @Test
    public void testRegex() throws ParseException {
        Assert.assertEquals("/foo/0", toString(new FilterParser("/foo/").parse()));
    }

    @Test
    public void testRegexWithFlag() throws ParseException {
        Assert.assertEquals("/foo/2", toString(new FilterParser("/foo/i").parse()));
    }

    @Test
    public void testMutlipleRegexes() throws ParseException {
        Assert.assertEquals("/foo/0./bar/0", toString(new FilterParser("/foo/./bar/").parse()));
    }

    @Test
    public void testMutlipleRegexFilters() throws ParseException {
        Assert.assertEquals("/foo/0,/bar/0", toString(new FilterParser("/foo/,/bar/").parse()));
    }

    @Test
    public void testDotInRegex() throws ParseException {
        Assert.assertEquals("/foo.bar/0", toString(new FilterParser("/foo.bar/").parse()));
    }

    @Test
    public void testEscapedDotInRegex() throws ParseException {
        Assert.assertEquals("/foo\\.bar/0", toString(new FilterParser("/foo\\.bar/").parse()));
    }

    @Test
    public void testCommaInRegex() throws ParseException {
        Assert.assertEquals("/foo,bar/0", toString(new FilterParser("/foo,bar/").parse()));
    }

    @Test
    public void testIncompleteRegex() {
        try {
            new FilterParser("/foo").parse();
            Assert.fail("Should have thrown ParseException because of unterminated regex");
        } catch (ParseException ex) {
            // expected
        }
    }

    @Test
    public void testIncompleteRegexNewline() {
        try {
            new FilterParser("/foo\nbar/").parse();
            Assert.fail("Should have thrown ParseException because of unterminated regex");
        } catch (ParseException ex) {
            // expected
        }
    }

    @Test
    public void testComplex() throws ParseException {
        Assert.assertEquals("/.*/0.'CHECKSUM','USER'.'PASSWORD'",
                toString(new FilterParser("/.*/.CHECKSUM,USER.PASSWORD").parse()));
    }

    @Test
    public void testParseAsSchemaFilter() throws ParseException {
        Filter result = new FilterParser("schema1,schema2").parseSchemaFilter();
        Assert.assertTrue(result.matchesSchema("schema1"));
        Assert.assertTrue(result.matchesSchema("schema2"));
        Assert.assertFalse(result.matchesSchema("schema3"));
        Assert.assertFalse(result.matchesSchema(null));
    }

    @Test
    public void testParseAsSchemaFilterWithRegex() throws ParseException {
        Filter result = new FilterParser("/schema[12]/i").parseSchemaFilter();
        Assert.assertTrue(result.matchesSchema("schema1"));
        Assert.assertTrue(result.matchesSchema("SCHEMA2"));
        Assert.assertFalse(result.matchesSchema("schema3"));
        Assert.assertFalse(result.matchesSchema(null));
    }

    @Test
    public void testParseAsSchemaFilterFail() {
        try {
            new FilterParser("schema.table").parseSchemaFilter();
            Assert.fail("Should have failed because schema.table is not in schema notation");
        } catch (ParseException ex) {
            // expected
        }
    }

    @Test
    public void testParseAsTableFilter() throws ParseException {
        Filter result = new FilterParser("schema.table1,schema.table2,table3").parseTableFilter(false);
        Assert.assertTrue(result.matchesTable("schema", "table1"));
        Assert.assertTrue(result.matchesTable("schema", "table2"));
        Assert.assertTrue(result.matchesTable(null, "table3"));
        Assert.assertFalse(result.matchesTable("schema", "table3"));
        Assert.assertFalse(result.matchesTable("schema", "table4"));
        Assert.assertFalse(result.matchesTable("schema2", "table1"));
        Assert.assertFalse(result.matchesTable(null, "table1"));
        Assert.assertFalse(result.matchesTable(null, "table4"));
    }

    @Test
    public void testTableFilterTooMany() {
        try {
            new FilterParser("a.b.c").parseTableFilter(true);
            Assert.fail("Should have failed because not in schema notation");
        } catch (ParseException ex) {
            // expected
        }
    }

    @Test
    public void testParseAsColumnFilter() throws ParseException {
        Filter result = new FilterParser("s.t1.c1,t2.c2,t2.c3").parseColumnFilter(false);
        Assert.assertTrue(result.matchesColumn("s", "t1", "c1"));
        Assert.assertTrue(result.matchesColumn(null, "t2", "c2"));
        Assert.assertTrue(result.matchesColumn(null, "t2", "c3"));
        Assert.assertFalse(result.matchesColumn(null, "t1", "c1"));
        Assert.assertFalse(result.matchesColumn("s", "t2", "c2"));
        Assert.assertFalse(result.matchesColumn(null, "t1", "c3"));
    }

    private String toString(List<List<IdentifierMatcher>> filters) {
        StringBuilder result = new StringBuilder();
        for (List<IdentifierMatcher> l : filters) {
            for (IdentifierMatcher m : l) {
                result.append(m.toString());
                result.append('.');
            }
            if (!l.isEmpty()) {
                result.deleteCharAt(result.length() - 1);
            }
            result.append(',');
        }
        if (!filters.isEmpty()) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }
}
