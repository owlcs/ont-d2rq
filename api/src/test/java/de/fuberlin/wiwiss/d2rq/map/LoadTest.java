package de.fuberlin.wiwiss.d2rq.map;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.owlcs.d2rq.conf.ISWCData;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by @ssz on 13.01.2019.
 */
public class LoadTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadTest.class);

    @Test
    public void testLoadRelativePath() throws URISyntaxException, IOException {
        URI uri = LoadTest.class.getResource(ISWCData.POSTGRES.getResourcePath()).toURI();
        Path p = Paths.get(".").toRealPath().relativize(Paths.get(uri));
        loadTest(p);
    }

    @Test
    public void testLoaAbsolutePath() throws URISyntaxException, IOException {
        Path p = Paths.get(LoadTest.class.getResource(ISWCData.POSTGRES.getResourcePath()).toURI()).toRealPath();
        loadTest(p);
    }

    private static void loadTest(Path p) {
        LOGGER.debug("Path to test: {}", p);
        Assert.assertFalse(MappingFactory.load(p.toString()).asModel().isEmpty());
    }
}
