package de.fuberlin.wiwiss.d2rq.algebra;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit test cases for {@link Attribute}
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class AttributeTest {
    private final static RelationName table1 = new RelationName(null, "table1");
    private final static RelationName table1b = new RelationName(null, "table1");
    private final static RelationName table2 = new RelationName(null, "table2");
    private final static RelationName xTable1 = new RelationName("x", "table1");
    private final static RelationName xTable2 = new RelationName("x", "table2");
    private final static RelationName yTable1 = new RelationName("y", "table1");

    private final static Attribute fooCol1 = new Attribute(null, "foo", "col1");
    private final static Attribute fooCol2 = new Attribute(null, "foo", "col2");
    private final static Attribute barCol1 = new Attribute(null, "bar", "col1");
    private final static Attribute barCol2 = new Attribute(null, "bar", "col2");

    @Test
    public void testAttributeNames() {
        Attribute col = new Attribute(null, "table", "column");
        Assert.assertEquals("table.column", col.qualifiedName());
        Assert.assertNull(col.schemaName());
        Assert.assertEquals("table", col.tableName());
        Assert.assertEquals("column", col.attributeName());
        Assert.assertEquals(new RelationName(null, "table"), col.relationName());
    }

    @Test
    public void testAttributeNameWithSchema() {
        Attribute column = new Attribute("schema", "table", "column");
        Assert.assertEquals("schema.table.column", column.qualifiedName());
        Assert.assertEquals("schema", column.schemaName());
        Assert.assertEquals("table", column.tableName());
        Assert.assertEquals("column", column.attributeName());
        Assert.assertEquals(new RelationName("schema", "table"), column.relationName());
    }

    @Test
    public void testAttributeEquality() {
        Attribute col1 = new Attribute(null, "table", "col1");
        Attribute col1b = new Attribute(null, "table", "col1");
        Attribute col2 = new Attribute(null, "table", "col2");
        Attribute col3 = new Attribute(null, "table2", "col1");
        Integer other = 42;
        Assert.assertFalse(col1.equals(col2));
        Assert.assertFalse(col1.equals(col3));
        Assert.assertFalse(col1.equals(other));
        Assert.assertFalse(col2.equals(col1));
        Assert.assertFalse(col3.equals(col1));
        Assert.assertFalse(other.equals(col1));
        Assert.assertTrue(col1.equals(col1b));
        Assert.assertFalse(col1.equals(null));
    }

    @Test
    public void testAttributeEqualityWithSchema() {
        Attribute schema0 = new Attribute(null, "table", "column");
        Attribute schema1 = new Attribute("schema1", "table", "column");
        Attribute schema2 = new Attribute("schema2", "table", "column");
        Attribute schema2b = new Attribute("schema2", "table", "column");
        Assert.assertFalse(schema0.equals(schema1));
        Assert.assertFalse(schema1.equals(schema2));
        Assert.assertTrue(schema2.equals(schema2b));
    }

    @Test
    public void testAttributeHashCode() {
        Map<Attribute, String> map = new HashMap<Attribute, String>();
        Attribute col1 = new Attribute(null, "table", "col1");
        Attribute col1b = new Attribute(null, "table", "col1");
        Attribute col2 = new Attribute(null, "table", "col2");
        Attribute col3 = new Attribute(null, "table", "col3");
        Attribute col1schema = new Attribute("schema", "table", "col1");
        map.put(col1, "foo");
        map.put(col2, "");
        map.put(col1schema, "bar");
        Assert.assertEquals("foo", map.get(col1));
        Assert.assertEquals("foo", map.get(col1b));
        Assert.assertEquals("", map.get(col2));
        Assert.assertNull(map.get(col3));
        Assert.assertEquals("bar", map.get(col1schema));
    }

    @Test
    public void testAttributeToString() {
        Assert.assertEquals("@@foo.bar@@", new Attribute(null, "foo", "bar").toString());
        Assert.assertEquals("@@schema.foo.bar@@", new Attribute("schema", "foo", "bar").toString());
    }

    @Test
    public void testCompareSameAttribute() {
        Assert.assertEquals(0, fooCol1.compareTo(fooCol1));
    }

    @Test
    public void testCompareSameTableDifferentAttribute() {
        Assert.assertTrue(fooCol1.compareTo(fooCol2) < 0);
        Assert.assertTrue(fooCol2.compareTo(fooCol1) > 0);
    }

    @Test
    public void testCompareSameAttributeDifferentTable() {
        Assert.assertTrue(barCol1.compareTo(fooCol1) < 0);
        Assert.assertTrue(fooCol1.compareTo(barCol2) > 0);
    }

    @Test
    public void testCompareDifferentAttributeDifferentTable() {
        Assert.assertTrue(barCol2.compareTo(fooCol1) < 0);
        Assert.assertTrue(fooCol1.compareTo(barCol2) > 0);
    }

    @Test
    public void testNoSchemaAttributeSmallerThanSchemaAttribute() {
        Attribute noSchema = new Attribute(null, "z", "col");
        Attribute schema = new Attribute("schema", "a", "col");
        Assert.assertTrue(noSchema.compareTo(schema) < 0);
        Assert.assertTrue(schema.compareTo(noSchema) > 0);
    }

    @Test
    public void testRelationNameWithoutSchema() {
        RelationName r = new RelationName(null, "table");
        Assert.assertEquals("table", r.tableName());
        Assert.assertNull(r.schemaName());
        Assert.assertEquals("table", r.qualifiedName());
    }

    @Test
    public void testRelationNameWithSchema() {
        RelationName r = new RelationName("schema", "table");
        Assert.assertEquals("table", r.tableName());
        Assert.assertEquals("schema", r.schemaName());
        Assert.assertEquals("schema.table", r.qualifiedName());
    }

    @Test
    public void testRelationNameToString() {
        Assert.assertEquals("table",
                new RelationName(null, "table").toString());
        Assert.assertEquals("schema.table",
                new RelationName("schema", "table").toString());
    }

    @Test
    public void testSameRelationNameIsEqual() {
        Assert.assertEquals(table1, table1b);
        Assert.assertEquals(table1b, table1);
        Assert.assertEquals(table1.hashCode(), table1b.hashCode());
    }

    @Test
    public void testDifferentRelationNamesAreNotEqual() {
        Assert.assertFalse(table1.equals(table2));
        Assert.assertFalse(table2.equals(table1));
        Assert.assertFalse(table1.hashCode() == table2.hashCode());
    }

    @Test
    public void testSameRelationAndSchemaNameIsEqual() {
        Assert.assertEquals(table1, table1b);
        Assert.assertEquals(table1b, table1);
        Assert.assertEquals(table1.hashCode(), table1b.hashCode());
    }

    @Test
    public void testDifferentSchemaNamesAreNotEqual() {
        Assert.assertFalse(xTable1.equals(yTable1));
        Assert.assertFalse(yTable1.equals(xTable1));
        Assert.assertFalse(xTable1.hashCode() == yTable1.hashCode());
    }

    @Test
    public void testSchemaAndNoSchemaAreNotEqual() {
        Assert.assertFalse(xTable1.equals(table1));
        Assert.assertFalse(table1.equals(xTable1));
        Assert.assertFalse(table1.hashCode() == xTable1.hashCode());
    }

    @Test
    public void testCompareRelationNamesDifferentSchema() {
        Assert.assertTrue(xTable1.compareTo(yTable1) < 0);
        Assert.assertTrue(yTable1.compareTo(xTable1) > 0);
    }

    @Test
    public void testCompareRelationNamesSameSchema() {
        Assert.assertTrue(table1.compareTo(table2) < 0);
        Assert.assertTrue(table2.compareTo(table1) > 0);
        Assert.assertTrue(xTable1.compareTo(xTable2) < 0);
        Assert.assertTrue(xTable2.compareTo(xTable1) > 0);
    }

    @Test
    public void testNoSchemaRelationNameSmallerSchemaRelationName() {
        RelationName noSchema = new RelationName(null, "z");
        RelationName schema = new RelationName("schema", "a");
        Assert.assertTrue(noSchema.compareTo(schema) < 0);
        Assert.assertTrue(schema.compareTo(noSchema) > 0);
    }

    @Test
    public void testCompareSameRelationName() {
        Assert.assertEquals(0, table1.compareTo(table1));
        Assert.assertEquals(0, xTable1.compareTo(xTable1));
    }

    @Test
    public void testRelationNameWithPrefixNoSchema() {
        Assert.assertEquals("T42_table1", table1.withPrefix(42).qualifiedName());
    }

    @Test
    public void testRelationNameWithPrefixWithSchema() {
        Assert.assertEquals("T42_x_table1", xTable1.withPrefix(42).qualifiedName());
    }
}
