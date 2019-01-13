package de.fuberlin.wiwiss.d2rq.utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import ru.avicomp.ontapi.OntFormat;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test suite for D2RQ
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class JenaModelUtils {

    public static Model loadTurtle(String resourceName) {
        Model m = ModelFactory.createDefaultModel();
        try (InputStream in = JenaModelUtils.class.getResourceAsStream(resourceName)) {
            m.read(in, null, "ttl");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return m;
    }

    public static String getRelativeResourcePath(String path) {
        try {
            Path file = Paths.get(JenaModelUtils.class.getResource(path).toURI());
            return Paths.get(".").toRealPath().relativize(file).toString().replace("\\", "/");
        } catch (URISyntaxException | IOException e) {
            throw new AssertionError(e);
        }
    }

    public static void print(Model m) {
        ReadWriteUtils.print(m);
    }

    public static String toTurtleString(Model m) {
        return ReadWriteUtils.toString(m, OntFormat.TURTLE);
    }

    public static Model loadFromString(String turtle) {
        return ReadWriteUtils.loadFromString(turtle, OntFormat.TURTLE);
    }

}