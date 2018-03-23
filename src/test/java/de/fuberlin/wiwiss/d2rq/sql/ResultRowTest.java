package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class ResultRowTest {
    private static final Attribute col1 = new Attribute(null, "foo", "col1");
    private static final Attribute col2 = new Attribute(null, "foo", "col2");

    @Test
    public void testGetUndefinedReturnsNull() {
        ResultRow r = new ResultRowMap(Collections.emptyMap());
        Assert.assertNull(r.get(col1));
        Assert.assertNull(r.get(col2));
    }

    @Test
    public void testGetColumnReturnsValue() {
        Map<ProjectionSpec, String> m = new HashMap<>();
        m.put(col1, "value1");
        ResultRow r = new ResultRowMap(m);
        Assert.assertEquals("value1", r.get(col1));
        Assert.assertNull(r.get(col2));
    }

    @Test
    public void testEmptyRowToString() {
        Assert.assertEquals("{}", new ResultRowMap(Collections.emptyMap()).toString());
    }

    @Test
    public void testTwoItemsToString() {
        Map<ProjectionSpec, String> m = new HashMap<>();
        m.put(col1, "value1");
        m.put(col2, "value2");
        // columns sorted alphabetically
        Assert.assertEquals("{@@foo.col1@@ => 'value1', @@foo.col2@@ => 'value2'}", new ResultRowMap(m).toString());
    }
}
