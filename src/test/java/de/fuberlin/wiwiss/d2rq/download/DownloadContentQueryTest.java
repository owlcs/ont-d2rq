package de.fuberlin.wiwiss.d2rq.download;


import de.fuberlin.wiwiss.d2rq.helpers.HSQLDatabase;
import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.map.DownloadMap;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.Mappings;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class DownloadContentQueryTest {
    private HSQLDatabase db;
    private DownloadMap downloadCLOB;
    private DownloadMap downloadBLOB;
    private DownloadContentQuery q;

    @Before
    public void setUp() {
        db = new HSQLDatabase("test");
        db.executeSQL("CREATE TABLE People (ID INT NOT NULL PRIMARY KEY, PIC_CLOB CLOB NULL, PIC_BLOB BLOB NULL)");
        db.executeSQL("INSERT INTO People VALUES (1, 'Hello World!', NULL)");
        db.executeSQL("INSERT INTO People VALUES (2, NULL, HEXTORAW('404040'))");
        Mapping m = MappingHelper.readFromTestFile("/download/download-map.ttl");
        downloadCLOB = MappingHelper.findDownloadMap(m, ResourceFactory.createResource("http://example.org/downloadCLOB"));
        downloadBLOB = MappingHelper.findDownloadMap(m, ResourceFactory.createResource("http://example.org/downloadBLOB"));
    }

    @After
    public void tearDown() {
        db.close(true);
        if (q != null) q.close();
    }

    @Test
    public void testFixture() {
        Assert.assertNotNull(downloadCLOB);
        Assert.assertNotNull(downloadBLOB);
    }

    @Test
    public void testNullForNonDownloadURI() {
        q = Mappings.getDownloadContentQuery(downloadCLOB, "http://not-in-the-mapping");
        Assert.assertFalse(q.hasContent());
        Assert.assertNull(q.getContentStream());
    }

    @Test
    public void testNullForNonExistingRecord() {
        // There is no People.ID=42 in the table
        q = Mappings.getDownloadContentQuery(downloadCLOB, "http://example.org/downloads/clob/42");
        Assert.assertFalse(q.hasContent());
        Assert.assertNull(q.getContentStream());
    }

    @Test
    public void testReturnCLOBContentForExistingRecord() throws IOException {
        q = Mappings.getDownloadContentQuery(downloadCLOB, "http://example.org/downloads/clob/1");
        Assert.assertTrue(q.hasContent());
        Assert.assertEquals("Hello World!", inputStreamToString(q.getContentStream()));
    }

    @Test
    public void testNULLContent() {
        q = Mappings.getDownloadContentQuery(downloadCLOB, "http://example.org/downloads/clob/2");
        Assert.assertFalse(q.hasContent());
        Assert.assertNull(q.getContentStream());
    }

    @Test
    public void testReturnBLOBContentForExistingRecord() throws IOException {
        q = Mappings.getDownloadContentQuery(downloadBLOB, "http://example.org/downloads/blob/2");
        Assert.assertTrue(q.hasContent());
        Assert.assertEquals("@@@", inputStreamToString(q.getContentStream()));
    }

    private String inputStreamToString(InputStream is) throws IOException {
        final char[] buffer = new char[0x10000];
        StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(is, StandardCharsets.UTF_8);
        int read;
        do {
            read = in.read(buffer, 0, buffer.length);
            if (read > 0) {
                out.append(buffer, 0, read);
            }
        } while (read >= 0);
        return out.toString();
    }
}
