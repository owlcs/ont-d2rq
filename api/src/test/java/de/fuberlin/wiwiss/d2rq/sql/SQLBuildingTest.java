package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.sql.vendor.Vendor;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class SQLBuildingTest {
    private final static Attribute foo = new Attribute(null, "table", "foo");

    @Test
    public void testSingleQuoteEscapeMySQL() {
        Vendor vendor = Vendor.MySQL;
        Assert.assertEquals("'a'", vendor.quoteStringLiteral("a"));
        Assert.assertEquals("''''", vendor.quoteStringLiteral("'"));
        Assert.assertEquals("'\\\\'", vendor.quoteStringLiteral("\\"));
        Assert.assertEquals("'Joe''s'", vendor.quoteStringLiteral("Joe's"));
        Assert.assertEquals("'\\\\''\\\\''\\\\'", vendor.quoteStringLiteral("\\'\\'\\"));
        Assert.assertEquals("'\"'", vendor.quoteStringLiteral("\""));
        Assert.assertEquals("'`'", vendor.quoteStringLiteral("`"));
    }

    @Test
    public void testSingleQuoteEscape() {
        Vendor vendor = Vendor.SQL92;
        Assert.assertEquals("'a'", vendor.quoteStringLiteral("a"));
        Assert.assertEquals("''''", vendor.quoteStringLiteral("'"));
        Assert.assertEquals("'\\'", vendor.quoteStringLiteral("\\"));
        Assert.assertEquals("'Joe''s'", vendor.quoteStringLiteral("Joe's"));
        Assert.assertEquals("'\\''\\''\\'", vendor.quoteStringLiteral("\\'\\'\\"));
        Assert.assertEquals("'\"'", vendor.quoteStringLiteral("\""));
        Assert.assertEquals("'`'", vendor.quoteStringLiteral("`"));
    }

    @Test
    public void testQuoteIdentifierEscape() {
        Vendor db = Vendor.SQL92;
        Assert.assertEquals("\"a\"", db.quoteIdentifier("a"));
        Assert.assertEquals("\"'\"", db.quoteIdentifier("'"));
        Assert.assertEquals("\"\"\"\"", db.quoteIdentifier("\""));
        Assert.assertEquals("\"`\"", db.quoteIdentifier("`"));
        Assert.assertEquals("\"\\\"", db.quoteIdentifier("\\"));
        Assert.assertEquals("\"A \"\"good\"\" idea\"", db.quoteIdentifier("A \"good\" idea"));
    }

    @Test
    public void testQuoteIdentifierEscapeMySQL() {
        Vendor db = Vendor.MySQL;
        Assert.assertEquals("`a`", db.quoteIdentifier("a"));
        Assert.assertEquals("````", db.quoteIdentifier("`"));
        Assert.assertEquals("`\\\\`", db.quoteIdentifier("\\"));
        Assert.assertEquals("`Joe``s`", db.quoteIdentifier("Joe`s"));
        Assert.assertEquals("`\\\\``\\\\``\\\\`", db.quoteIdentifier("\\`\\`\\"));
        Assert.assertEquals("`'`", db.quoteIdentifier("'"));
    }

    @Test
    public void testAttributeQuoting() {
        Vendor db = Vendor.SQL92;
        Assert.assertEquals("\"schema\".\"table\".\"column\"",
                db.quoteAttribute(new Attribute("schema", "table", "column")));
        Assert.assertEquals("\"table\".\"column\"",
                db.quoteAttribute(new Attribute(null, "table", "column")));
    }

    @Test
    public void testDoubleQuotesInAttributesAreEscaped() {
        Vendor db = Vendor.SQL92;
        Assert.assertEquals("\"sch\"\"ema\".\"ta\"\"ble\".\"col\"\"umn\"",
                db.quoteAttribute(new Attribute("sch\"ema", "ta\"ble", "col\"umn")));
    }

    @Test
    public void testAttributeQuotingMySQL() {
        Vendor db = Vendor.MySQL;
        Assert.assertEquals("`table`.`column`",
                db.quoteAttribute(new Attribute(null, "table", "column")));
    }

    @Test
    public void testRelationNameQuoting() {
        Vendor db = DummyDB.create().vendor();
        Assert.assertEquals("\"schema\".\"table\"", db.quoteRelationName(new RelationName("schema", "table")));
        Assert.assertEquals("\"table\"", db.quoteRelationName(new RelationName(null, "table")));
    }

    @Test
    public void testBackticksInRelationsAreEscapedMySQL() {
        Vendor db = Vendor.MySQL;
        Assert.assertEquals("`ta``ble`",
                db.quoteRelationName(new RelationName(null, "ta`ble")));
    }

    @Test
    public void testRelationNameQuotingMySQL() {
        Vendor db = Vendor.MySQL;
        Assert.assertEquals("`table`",
                db.quoteRelationName(new RelationName(null, "table")));
    }

    @Test
    public void testNoLimit() {
        ConnectedDB db = DummyDB.create();
        Relation r = Relation.createSimpleRelation(db, new Attribute[]{foo});
        Assert.assertEquals("SELECT DISTINCT \"table\".\"foo\" FROM \"table\"",
                new SelectStatementBuilder(r).getSQLStatement());
    }

    @Test
    public void testLimitStandard() {
        DummyDB db = DummyDB.create();
        db.setLimit(100);
        Relation r = Relation.createSimpleRelation(db, new Attribute[]{foo});
        Assert.assertEquals("SELECT DISTINCT \"table\".\"foo\" FROM \"table\" LIMIT 100",
                new SelectStatementBuilder(r).getSQLStatement());
    }

    @Test
    public void testNoLimitMSSQL() {
        DummyDB db = DummyDB.create(Vendor.SQLServer);
        db.setLimit(100);
        Relation r = Relation.createSimpleRelation(db, new Attribute[]{foo});
        Assert.assertEquals("SELECT DISTINCT TOP 100 \"table\".\"foo\" FROM \"table\"",
                new SelectStatementBuilder(r).getSQLStatement());
    }

    @Test
    public void testNoLimitOracle() {
        DummyDB db = DummyDB.create(Vendor.Oracle);
        db.setLimit(100);
        Relation r = Relation.createSimpleRelation(db, new Attribute[]{foo});
        Assert.assertEquals("SELECT DISTINCT \"table\".\"foo\" FROM \"table\" WHERE (ROWNUM <= 100)",
                new SelectStatementBuilder(r).getSQLStatement());
    }
}
