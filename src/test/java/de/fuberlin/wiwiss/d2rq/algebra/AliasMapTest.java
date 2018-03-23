package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import de.fuberlin.wiwiss.d2rq.expr.SQLExpression;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class AliasMapTest {
    private final static RelationName foo = new RelationName(null, "foo");
    private final static RelationName bar = new RelationName(null, "bar");
    private final static RelationName baz = new RelationName(null, "baz");
    private final static Attribute foo_col1 = new Attribute(null, "foo", "col1");
    private final static Attribute bar_col1 = new Attribute(null, "bar", "col1");
    private final static Attribute baz_col1 = new Attribute(null, "baz", "col1");
    private final static Attribute abc_col1 = new Attribute(null, "abc", "col1");
    private final static Attribute xyz_col1 = new Attribute(null, "xyz", "col1");

    private Alias fooAsBar = new Alias(foo, bar);
    private Alias fooAsBaz = new Alias(foo, baz);
    private Alias bazAsBar = new Alias(baz, bar);
    private AliasMap fooAsBarMap = new AliasMap(Collections.singleton(new Alias(foo, bar)));

    @Test
    public void testEmptyMapDoesIdentityTranslation() {
        AliasMap aliases = AliasMap.NO_ALIASES;
        Assert.assertFalse(aliases.isAlias(foo));
        Assert.assertFalse(aliases.hasAlias(foo));
        Assert.assertEquals(foo, aliases.applyTo(foo));
        Assert.assertEquals(foo, aliases.originalOf(foo));
    }

    @Test
    public void testAliasIsTranslated() {
        Assert.assertFalse(this.fooAsBarMap.isAlias(foo));
        Assert.assertTrue(this.fooAsBarMap.isAlias(bar));
        Assert.assertFalse(this.fooAsBarMap.isAlias(baz));
        Assert.assertTrue(this.fooAsBarMap.hasAlias(foo));
        Assert.assertFalse(this.fooAsBarMap.hasAlias(bar));
        Assert.assertFalse(this.fooAsBarMap.hasAlias(baz));
        Assert.assertEquals(bar, this.fooAsBarMap.applyTo(foo));
        Assert.assertEquals(baz, this.fooAsBarMap.applyTo(baz));
        Assert.assertEquals(foo, this.fooAsBarMap.originalOf(bar));
        Assert.assertEquals(baz, this.fooAsBarMap.originalOf(baz));
    }

    @Test
    public void testApplyToColumn() {
        Assert.assertEquals(baz_col1, this.fooAsBarMap.applyTo(baz_col1));
        Assert.assertEquals(bar_col1, this.fooAsBarMap.applyTo(foo_col1));
        Assert.assertEquals(bar_col1, this.fooAsBarMap.applyTo(bar_col1));
    }

    @Test
    public void testOriginalOfColumn() {
        Assert.assertEquals(baz_col1, this.fooAsBarMap.originalOf(baz_col1));
        Assert.assertEquals(foo_col1, this.fooAsBarMap.originalOf(foo_col1));
        Assert.assertEquals(foo_col1, this.fooAsBarMap.originalOf(bar_col1));
    }

    @Test
    public void testApplyToJoinSetDoesNotModifyUnaliasedJoin() {
        Join join = new Join(abc_col1, xyz_col1, Join.DIRECTION_RIGHT);
        Set<Join> joins = Collections.singleton(join);
        Assert.assertEquals(joins, this.fooAsBarMap.applyToJoinSet(joins));
    }

    @Test
    public void testApplyToJoinSetDoesModifyAliasedJoin() {
        Join join = new Join(foo_col1, foo_col1, Join.DIRECTION_RIGHT);
        Set<Join> aliasedSet = this.fooAsBarMap.applyToJoinSet(Collections.singleton(join));
        Assert.assertEquals(1, aliasedSet.size());
        Join aliased = aliasedSet.iterator().next();
        Assert.assertEquals(Collections.singletonList(bar_col1), aliased.attributes1());
        Assert.assertEquals(Collections.singletonList(bar_col1), aliased.attributes2());
    }

    @Test
    public void testApplyToSQLExpression() {
        Assert.assertEquals(SQLExpression.create("bar.col1 = 1"), fooAsBarMap.applyTo(SQLExpression.create("foo.col1 = 1")));
    }

    @Test
    public void testNoAliasesConstantEqualsNewEmptyAliasMap() {
        AliasMap noAliases = new AliasMap(Collections.emptyList());
        Assert.assertTrue(AliasMap.NO_ALIASES.equals(noAliases));
        Assert.assertTrue(noAliases.equals(AliasMap.NO_ALIASES));
    }

    @Test
    public void testEmptyMapEqualsItself() {
        Assert.assertTrue(AliasMap.NO_ALIASES.equals(AliasMap.NO_ALIASES));
    }

    @Test
    public void testEmptyMapDoesntEqualPopulatedMap() {
        Assert.assertFalse(AliasMap.NO_ALIASES.equals(fooAsBarMap));
    }

    @Test
    public void testPopulatedMapDoesntEqualEmptyMap() {
        Assert.assertFalse(fooAsBarMap.equals(AliasMap.NO_ALIASES));
    }

    @Test
    public void testPopulatedMapEqualsItself() {
        AliasMap fooAsBar2 = new AliasMap(Collections.singleton(new Alias(foo, bar)));
        Assert.assertTrue(fooAsBarMap.equals(fooAsBar2));
        Assert.assertTrue(fooAsBar2.equals(fooAsBarMap));
    }

    @Test
    public void testPopulatedMapDoesNotEqualDifferentMap() {
        AliasMap fooAsBaz = new AliasMap(Collections.singleton(new Alias(foo, baz)));
        Assert.assertFalse(fooAsBarMap.equals(fooAsBaz));
        Assert.assertFalse(fooAsBaz.equals(fooAsBarMap));
    }

    @Test
    public void testEqualMapsHaveSameHashCode() {
        AliasMap m1 = new AliasMap(new ArrayList<Alias>());
        AliasMap m2 = new AliasMap(new ArrayList<Alias>());
        Assert.assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    public void testAliasEquals() {
        Alias fooAsBar2 = new Alias(foo, bar);
        Assert.assertEquals(fooAsBar, fooAsBar2);
        Assert.assertEquals(fooAsBar2, fooAsBar);
        Assert.assertEquals(fooAsBar.hashCode(), fooAsBar2.hashCode());
    }

    @Test
    public void testAliasNotEquals() {
        Assert.assertFalse(fooAsBar.equals(fooAsBaz));
        Assert.assertFalse(fooAsBaz.equals(fooAsBar));
        Assert.assertFalse(fooAsBar.equals(bazAsBar));
        Assert.assertFalse(bazAsBar.equals(fooAsBar));
        Assert.assertFalse(fooAsBar.hashCode() == fooAsBaz.hashCode());
        Assert.assertFalse(fooAsBar.hashCode() == bazAsBar.hashCode());
    }

    @Test
    public void testAliasToString() {
        Assert.assertEquals("foo AS bar", fooAsBar.toString());
    }

    @Test
    public void testApplyToAliasEmpty() {
        Assert.assertEquals(fooAsBar, AliasMap.NO_ALIASES.applyTo(fooAsBar));
    }

    @Test
    public void testApplyToAlias() {
        Assert.assertEquals(new Alias(baz, bar), fooAsBarMap.applyTo(new Alias(baz, foo)));
    }

    @Test
    public void testOriginalOfAliasEmpty() {
        Assert.assertEquals(fooAsBar, AliasMap.NO_ALIASES.originalOf(fooAsBar));
    }

    @Test
    public void testOriginalOfAlias() {
        Assert.assertEquals(fooAsBaz, fooAsBarMap.originalOf(new Alias(bar, baz)));
    }

    @Test
    public void testToStringEmpty() {
        Assert.assertEquals("AliasMap()", AliasMap.NO_ALIASES.toString());
    }

    @Test
    public void testToStringOneAlias() {
        Assert.assertEquals("AliasMap(foo AS bar)", fooAsBarMap.toString());
    }

    @Test
    public void testToStringTwoAliases() {
        Collection<Alias> aliases = new ArrayList<>();
        aliases.add(fooAsBar);
        aliases.add(new Alias(new RelationName(null, "abc"), new RelationName(null, "xyz")));
        // Order is alphabetical by alias
        Assert.assertEquals("AliasMap(foo AS bar, abc AS xyz)", new AliasMap(aliases).toString());
    }

    @Test
    public void testWithSchema() {
        RelationName table = new RelationName(null, "table");
        RelationName schema_table = new RelationName("schema", "table");
        RelationName schema_alias = new RelationName("schema", "alias");
        AliasMap m = new AliasMap(Collections.singleton(new Alias(schema_table, schema_alias)));
        Assert.assertEquals(schema_alias, m.applyTo(schema_table));
        Assert.assertEquals(table, m.applyTo(table));
    }
}