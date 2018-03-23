package de.fuberlin.wiwiss.d2rq.values;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ValueMakerTest {
    private final static Attribute foo_col1 = new Attribute(null, "foo", "col1");
    private final static Attribute foo_col2 = new Attribute(null, "foo", "col2");

    @Test
    public void testBlankNodeIDToString() {
        BlankNodeID b = new BlankNodeID("classmap1", Arrays.asList(foo_col1, foo_col2));
        Assert.assertEquals("BlankNodeID(foo.col1,foo.col2)", b.toString());
    }

    @Test
    public void testColumnToString() {
        Assert.assertEquals("Column(foo.col1)", new Column(foo_col1).toString());
    }

    @Test
    public void testPatternToString() {
        Assert.assertEquals("Pattern(http://test/@@foo.bar@@)", new Pattern("http://test/@@foo.bar@@").toString());
    }

    @Test
    public void testValueDecoratorWithoutTranslatorToString() {
        Assert.assertEquals("Column(foo.col1):maxLength=10",
                new ValueDecorator(new Column(foo_col1),
                        Collections.singletonList(ValueDecorator.maxLengthConstraint(10))).toString());
    }

    @Test
    public void testMaxLengthConstraint() {
        DummyValueMaker source = new DummyValueMaker("foo");
        ValueDecorator values = new ValueDecorator(source, Collections.singletonList(ValueDecorator.maxLengthConstraint(5)));
        Assert.assertFalse(matches(values, null));
        Assert.assertTrue(matches(values, ""));
        Assert.assertTrue(matches(values, "foo"));
        Assert.assertTrue(matches(values, "fooba"));
        Assert.assertFalse(matches(values, "foobar"));
        source.setSelectCondition(Expression.FALSE);
        Assert.assertFalse(matches(values, "foo"));
    }

    @Test
    public void testContainsConstraint() {
        DummyValueMaker source = new DummyValueMaker("foo");
        ValueDecorator values = new ValueDecorator(source, Collections.singletonList(ValueDecorator.containsConstraint("foo")));
        Assert.assertFalse(matches(values, null));
        Assert.assertTrue(matches(values, "foo"));
        Assert.assertTrue(matches(values, "barfoobaz"));
        Assert.assertFalse(matches(values, ""));
        Assert.assertFalse(matches(values, "bar"));
        values = new ValueDecorator(source, Collections.singletonList(ValueDecorator.containsConstraint("")));
        Assert.assertFalse(matches(values, null));
        Assert.assertTrue(matches(values, ""));
        Assert.assertTrue(matches(values, "a"));
        source.setSelectCondition(Expression.FALSE);
        Assert.assertFalse(matches(values, "a"));
    }

    @Test
    public void testRegexConstraint() {
        DummyValueMaker source = new DummyValueMaker("foo");
        ValueDecorator values = new ValueDecorator(source, Collections.singletonList(ValueDecorator.regexConstraint("^[0-9]{5}$")));
        Assert.assertFalse(matches(values, null));
        Assert.assertTrue(matches(values, "12345"));
        Assert.assertFalse(matches(values, "abc"));
        source.setSelectCondition(Expression.FALSE);
        Assert.assertFalse(matches(values, "12345"));
    }

    @Test
    public void testColumnDoesNotMatchNull() {
        Column column = new Column(foo_col1);
        Assert.assertFalse(matches(column, null));
    }

    @Test
    public void testPatternDoesNotMatchNull() {
        Pattern pattern = new Pattern("foo/@@foo.bar@@");
        Assert.assertFalse(matches(pattern, null));
    }

    @Test
    public void testBlankNodeIDDoesNotMatchNull() {
        BlankNodeID bNodeID = new BlankNodeID("classmap", Collections.singletonList(foo_col1));
        Assert.assertFalse(matches(bNodeID, null));
    }

    private boolean matches(ValueMaker valueMaker, String value) {
        return !valueMaker.valueExpression(value).isFalse();
    }
}
