package de.fuberlin.wiwiss.d2rq.parser;

import de.fuberlin.wiwiss.d2rq.map.impl.MapParser;
import org.junit.Assert;
import org.junit.Test;

public class URITest {

    @Test
    public void testAbsoluteHTTPURIIsNotChanged() {
        Assert.assertEquals("http://example.org/foo", MapParser.absolutizeURI("http://example.org/foo"));
    }

    @Test
    public void testAbsoluteFileURIIsNotChanged() {
        Assert.assertEquals("file://C:/autoexec.bat", MapParser.absolutizeURI("file://C:/autoexec.bat"));
    }

    @Test
    public void testRelativeFileURIIsAbsolutized() {
        String uri = MapParser.absolutizeURI("foo/bar");
        Assert.assertTrue(uri.startsWith("file://"));
        Assert.assertTrue(uri.endsWith("/foo/bar"));
    }

    @Test
    public void testRootlessFileURIIsAbsolutized() {
        String uri = MapParser.absolutizeURI("file:foo/bar");
        Assert.assertTrue(uri.startsWith("file://"));
        Assert.assertTrue(uri.endsWith("/foo/bar"));
    }
}