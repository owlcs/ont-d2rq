package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.expr.SQLExpression;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class ColumnRenamerTest {
    private final static Attribute col1 = new Attribute(null, "foo", "col1");
    private final static Attribute col2 = new Attribute(null, "foo", "col2");
    private final static Attribute col3 = new Attribute(null, "foo", "col3");

    private ColumnRenamerMap col1ToCol2;

    @Before
    public void setUp() {
        Map<Attribute, Attribute> m = new HashMap<>();
        m.put(col1, col2);
        this.col1ToCol2 = new ColumnRenamerMap(m);
    }

    @Test
    public void testApplyToUnmappedColumnReturnsSameColumn() {
        Assert.assertEquals(col3, this.col1ToCol2.applyTo(col3));
    }

    @Test
    public void testApplyToMappedColumnReturnsNewName() {
        Assert.assertEquals(col2, this.col1ToCol2.applyTo(col1));
    }

    @Test
    public void testApplyToNewNameReturnsNewName() {
        Assert.assertEquals(col2, this.col1ToCol2.applyTo(col2));
    }

    @Test
    public void testApplyToExpressionReplacesMappedColumns() {
        Expression e = SQLExpression.create("foo.col1=foo.col3");
        Assert.assertEquals(SQLExpression.create("foo.col2=foo.col3"), this.col1ToCol2.applyTo(e));
    }

    @Test
    public void testApplyToAliasMapReturnsOriginal() {
        AliasMap aliases = new AliasMap(Collections.singleton(new Alias(
                new RelationName(null, "foo"), new RelationName(null, "bar"))));
        Assert.assertEquals(aliases, this.col1ToCol2.applyTo(aliases));
    }

    @Test
    public void testNullRenamerToStringEmpty() {
        Assert.assertEquals("ColumnRenamer.NULL", ColumnRenamer.NULL.toString());
    }

    @Test
    public void testEmptyRenamerToStringEmpty() {
        Assert.assertEquals("ColumnRenamerMap()", new ColumnRenamerMap(Collections.emptyMap()).toString());
    }

    @Test
    public void testToStringOneAlias() {
        Assert.assertEquals("ColumnRenamerMap(foo.col1 => foo.col2)", col1ToCol2.toString());
    }

    @Test
    public void testToStringTwoAliases() {
        Map<Attribute, Attribute> m = new HashMap<>();
        m.put(col1, col3);
        m.put(col2, col3);
        // Order is alphabetical by original column name
        Assert.assertEquals("ColumnRenamerMap(foo.col1 => foo.col3, foo.col2 => foo.col3)", new ColumnRenamerMap(m).toString());
    }

    @Test
    public void testRenameWithSchema() {
        Attribute foo_c1 = new Attribute("schema", "foo", "col1");
        Attribute bar_c2 = new Attribute("schema", "bar", "col2");
        ColumnRenamer renamer = new ColumnRenamerMap(Collections.singletonMap(foo_c1, bar_c2));
        Assert.assertEquals(bar_c2, renamer.applyTo(foo_c1));
        Assert.assertEquals(col1, renamer.applyTo(col1));
    }
}
