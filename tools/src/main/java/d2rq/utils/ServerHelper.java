package d2rq.utils;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A helper to prepare and build {@link FusekiServer}.
 * Created by @ssz on 12.01.2019.
 */
public class ServerHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerHelper.class);

    private static Map<String, Path> systemDirs = new ConcurrentHashMap<>();

    /**
     * Returns a system resource directory with a given name.
     * If the application is packet into jar-file,
     * then the specified resource directory is extracted into the file system.
     *
     * @param dir {@link Path} the name to a directory in {@code /resources}
     * @return {@link Path}
     */
    public static Path getSystemDirectory(String dir) {
        URL url = ServerHelper.class.getResource(dir);
        if (url == null) {
            throw new IllegalArgumentException("Can't find the resource dir " + dir);
        }
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Can't ger URI from " + url, e);
        }
        if ("jar".equalsIgnoreCase(uri.getScheme())) {
            return systemDirs.computeIfAbsent(dir, s -> unpackDirectory(uri));
        }
        return Paths.get(uri);
    }

    /**
     * Unpacks a jar resource directory to file-system directory.
     * jar:file:///G:/work/avicomp/ont-d2rq/tools/target/d2rq.jar!/web-pages
     *
     * @param uri {@link URI}, example: {@code jar:file:///../ont-d2rq/tools/target/d2rq.jar!/web-pages}
     * @return {@link Path} in file-system
     */
    private static Path unpackDirectory(URI uri) {
        FileSystem jar;
        try {
            jar = FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException fsNotFound) {
            try {
                jar = FileSystems.newFileSystem(uri, new HashMap<>());
            } catch (IOException ex) {
                throw new UncheckedIOException("Can't init jar system", ex);
            }
        }
        URI inJar = URI.create(uri.getSchemeSpecificPart());
        String name = Paths.get(inJar).getFileName().toString();
        if (name.isEmpty()) throw new IllegalArgumentException("Can't find dir name from " + uri);
        Path src = jar.getPath(name);
        Path res = createTempDirectory(name);
        LOGGER.debug("The directory {} is created", res);
        Stream<Path> sources;
        try {
            sources = Files.list(src);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't list directory " + uri, e);
        }
        IOException copyError = new IOException("Can't copy content");
        sources.forEach(s -> {
            Path t = res.resolve(s.getFileName().toString());
            try {
                LOGGER.debug("Unpack {} -> {}", s, t);
                Files.copy(s, t);
            } catch (IOException e) {
                copyError.addSuppressed(e);
            }
        });
        if (copyError.getSuppressed().length != 0) {
            throw new UncheckedIOException("Can't unpack", copyError);
        }
        return res;
    }

    /**
     * Creates a temporary directory, which will be deleted when system is shutting down.
     *
     * @param prefix String
     * @return {@link Path}
     */
    private static Path createTempDirectory(String prefix) {
        try {
            Path res = Files.createTempDirectory(prefix);
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> {
                        try {
                            Files.walkFileTree(res, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes a)
                                        throws IOException {
                                    LOGGER.debug("Delete file {}", file);
                                    Files.delete(file);
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                                        throws IOException {
                                    if (e == null) {
                                        LOGGER.debug("Delete dir {}", dir);
                                        Files.delete(dir);
                                        return FileVisitResult.CONTINUE;
                                    }
                                    throw e;
                                }
                            });
                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to delete " + res, e);
                        }
                    }));
            return res;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create temporary dir", e);
        }
    }

    /**
     * Creates a Fuseki server for the specified dataset and with given static content, port
     *
     * @param webPages {@link Path} static content
     * @param port     int, port number
     * @param dsg      {@link DatasetGraph}
     * @return {@link FusekiServer}
     */
    public static FusekiServer buildServer(Path webPages, int port, DatasetGraph dsg) {
        return FusekiServer.create()
                .port(port)
                .loopback(true)
                .add("sparql", dsg, false)
                .staticFileBase(webPages.toString())
                .enablePing(true)
                .build();
    }
}
