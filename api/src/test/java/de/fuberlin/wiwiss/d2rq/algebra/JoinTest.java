package de.fuberlin.wiwiss.d2rq.algebra;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class JoinTest {
    private final static Attribute table1foo = new Attribute(null, "table1", "foo");
    private final static Attribute table1bar = new Attribute(null, "table1", "bar");
    private final static Attribute table2foo = new Attribute(null, "table2", "foo");
    private final static Attribute table2bar = new Attribute(null, "table2", "bar");
    private final static RelationName table1 = new RelationName(null, "table1");
    private final static RelationName table2 = new RelationName(null, "table2");

    @Test
    public void testToString() {
        Join join = new Join(table1foo, table2foo, Join.DIRECTION_UNDIRECTED);
        Assert.assertEquals("Join(table1.foo <=> table2.foo)", join.toString());
    }

    @Test
    public void testToStringRetainsTableOrder() {
        Join join = new Join(table2foo, table1foo, Join.DIRECTION_RIGHT);
        Assert.assertEquals("Join(table2.foo => table1.foo)", join.toString());
    }

    @Test
    public void testToStringRetainsAttributeOrder() {
        Join join = new Join(
                Arrays.asList(table1foo, table1bar),
                Arrays.asList(table2bar, table2foo), Join.DIRECTION_RIGHT);
        Assert.assertEquals("Join(table1.foo, table1.bar => table2.bar, table2.foo)", join.toString());
    }

    @Test
    public void testRenameColumns() {
        ColumnRenamer renamer = new ColumnRenamerMap(Collections.singletonMap(table1foo, table1bar));
        Join join = new Join(table1foo, table2foo, Join.DIRECTION_RIGHT);
        Assert.assertEquals("Join(table1.bar => table2.foo)", join.renameColumns(renamer).toString());
    }

    @Test
    public void testTableOrderIsRetained() {
        Assert.assertEquals(table1, new Join(table1foo, table2foo, Join.DIRECTION_RIGHT).table1());
        Assert.assertEquals(table2, new Join(table2foo, table1foo, Join.DIRECTION_RIGHT).table1());
    }

    @Test
    public void testJoinOverSameAttributesIsEqual() {
        Join j1 = new Join(table1foo, table2foo, Join.DIRECTION_RIGHT);
        Join j2 = new Join(table1foo, table2foo, Join.DIRECTION_RIGHT);
        Assert.assertEquals(j1, j2);
        Assert.assertEquals(j2, j1);
        Assert.assertEquals(j1.hashCode(), j2.hashCode());
    }

    @Test
    public void testSideOrderDoesNotAffectEquality1() {
        Join j1 = new Join(table1foo, table2foo, Join.DIRECTION_RIGHT);
        Join j2 = new Join(table2foo, table1foo, Join.DIRECTION_LEFT);
        Assert.assertEquals(j1, j2);
        Assert.assertEquals(j2, j1);
        Assert.assertEquals(j1.hashCode(), j2.hashCode());
    }

    @Test
    public void testSideOrderDoesNotAffectEquality2() {
        Join j1 = new Join(table1foo, table2foo, Join.DIRECTION_UNDIRECTED);
        Join j2 = new Join(table2foo, table1foo, Join.DIRECTION_UNDIRECTED);
        Assert.assertEquals(j1, j2);
        Assert.assertEquals(j2, j1);
        Assert.assertEquals(j1.hashCode(), j2.hashCode());
    }

    @Test
    public void testDifferentAttributesNotEqual() {
        Join j1 = new Join(table1foo, table2foo, Join.DIRECTION_RIGHT);
        Join j2 = new Join(table1foo, table2bar, Join.DIRECTION_RIGHT);
        Assert.assertNotEquals(j1, j2);
        Assert.assertNotEquals(j2, j1);
        Assert.assertNotEquals(j1.hashCode(), j2.hashCode());
    }

    @Test
    public void testDifferentDirectionsNotEqual() {
        Join j1 = new Join(table1foo, table2foo, Join.DIRECTION_RIGHT);
        Join j2 = new Join(table1foo, table2foo, Join.DIRECTION_UNDIRECTED);
        Assert.assertNotEquals(j1, j2);
        Assert.assertNotEquals(j2, j1);
        Assert.assertNotEquals(j1.hashCode(), j2.hashCode());
    }
}
