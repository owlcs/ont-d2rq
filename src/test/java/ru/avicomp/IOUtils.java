package ru.avicomp;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Logger;

/**
 * Utils to load/write jena models.
 * <p>
 * Created by szuev on 21.02.2017.
 */
public class IOUtils {
    public static final Logger LOGGER = Logger.getLogger(IOUtils.class);
    private static final String DESTINATION_DIR = "out";

    public static Model loadResourceTTLFile(String file) {
        return load(getResourceURI(file), null);
    }

    public static Model loadOutTTLFile(String file) {
        return load(getOutURI(file), null);
    }

    public static Model load(URI file, String f) {
        String format = f == null ? "ttl" : f;
        Model m = ModelFactory.createDefaultModel();
        LOGGER.debug("Load model from " + file);
        try (InputStream in = file.toURL().openStream()) {
            m.read(in, null, format);
            return m;
        } catch (IOException e) {
            LOGGER.fatal("Can't read model", e);
            throw new AssertionError(e);
        }
    }

    public static void print(Model model) {
        print(model, null);
    }

    public static void print(Model model, String ext) {
        LOGGER.debug("\n" + toString(model, ext));
    }

    public static String toString(Model model, String ext) {
        return toStringWriter(model, ext).toString();
    }

    public static StringWriter toStringWriter(Model model, String ext) {
        StringWriter sw = new StringWriter();
        model.write(sw, (ext == null ? "Turtle" : ext), null);
        return sw;
    }

    public static InputStream toInputStream(String txt) {
        try {
            return toInputStream(txt, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static InputStream toInputStream(Model model, String ext) {
        return toInputStream(toString(model, ext));
    }

    public static InputStream toInputStream(String input, Charset encoding) throws IOException {
        byte[] bytes = input.getBytes(encoding);
        return new ByteArrayInputStream(bytes);
    }

    public static File getResourceFile(String projectDirName, String fileName) throws URISyntaxException, FileNotFoundException {
        URL url = IOUtils.class.getResource(projectDirName.startsWith("/") ? projectDirName : "/" + projectDirName);
        if (url == null)
            throw new IllegalArgumentException("Can't find project " + projectDirName + ".");
        File dir = new File(url.toURI());
        LOGGER.debug("Directory: " + dir);
        File res = new File(dir, fileName);
        if (!res.exists()) throw new FileNotFoundException(fileName);
        return res;
    }

    public static File getResourceFile(String fileName) {
        try {
            return getResourceFile("", fileName);
        } catch (URISyntaxException | FileNotFoundException e) {
            LOGGER.fatal(e);
        }
        return null;
    }

    public static URI getResourceURI(String dir, String file) {
        try {
            return getResourceFile(dir, file).toURI();
        } catch (URISyntaxException | FileNotFoundException e) {
            LOGGER.fatal(e);
        }
        return null;
    }

    public static URI getResourceURI(String file) {
        return getResourceURI("", file);
    }

    public static URI getOutURI(String file) {
        return new File(DESTINATION_DIR, file).toURI();
    }

}
