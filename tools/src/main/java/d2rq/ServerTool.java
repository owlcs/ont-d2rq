package d2rq;

import d2rq.utils.ArgDecl;
import d2rq.utils.ServerHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import ru.avicomp.d2rq.utils.D2RQGraphUtils;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Created by @ssz on 12.01.2019.
 */
public class ServerTool extends CommandLineTool {

    private static final int DEFAULT_SERVER_PORT = 2020;
    private static final String DEFAULT_BASE = "http://localhost:%d/";

    private ArgDecl portArg = new ArgDecl(true, "port");
    private ArgDecl baseArg = new ArgDecl(true, "b", "base");
    private ArgDecl fastArg = new ArgDecl(false, "fast");

    ServerTool(PrintStream out) {
        super(out);
    }

    @Override
    public void printUsage() {
        console.println("usage:");
        console.println("  d2r-server [server-options] mappingFile");
        console.println("  d2r-server [server-options] [connection-options] jdbcURL");
        console.println("  d2r-server [server-options] [connection-options] -l script.sql");
        console.println();
        printStandardArguments(true);
        console.println();
        console.println("  Server options:");
        console.println("    --port number   Port where to start up the server (default: " + DEFAULT_SERVER_PORT + ")");
        console.println("    -b baseURI      Base URI to generate RDF dataset");
        console.println("    --fast          Use all engine optimizations (recommended)");
        console.println("    --verbose       Print debug information");
        console.println();
        console.println("  Database connection options (only with jdbcURL):");
        printConnectionOptions();
        console.println();
    }

    @Override
    public void initArgs() {
        cmd.add(portArg);
        cmd.add(baseArg);
        cmd.add(fastArg);
    }

    @Override
    public void run() {
        if (cmd.numItems() == 1) {
            loader.setMappingFileOrJdbcURL(cmd.getItem(0));
        }
        if (cmd.contains(fastArg)) {
            loader.setFastMode(true);
        }
        // todo: add configurable parameters:
        loader.setControlOWL(true);
        // todo: and also for a cache control ?
        int port;
        if (cmd.contains(portArg)) {
            port = Integer.parseInt(cmd.getArgValue(portArg));
        } else {
            port = DEFAULT_SERVER_PORT;
        }
        String host = String.format(DEFAULT_BASE, port);
        String base;
        if (cmd.contains(baseArg)) {
            base = cmd.getArgValue(baseArg);
            if (!base.endsWith("/")) base += "/";
        } else {
            base = host;
        }
        loader.setSystemBaseURI(base).setResourceStem("resource/");

        Path webPages = ServerHelper.getSystemDirectory("/web-pages");
        LOGGER.debug("Web-pages path: {}", webPages);

        Mapping m;
        Model data;
        // TODO: for some unclear to me reason the following way (Data+Schema in one GraphD2RQ) works incorrectly:
        //  unable to find a schema part for several queries,
        //  e.g. for "SELECT ?s { ?s a <http://www.w3.org/2002/07/owl#Class> }",
        //  but it's OK if a composite graph is specified.
        //  Investigate! this must be a D2RQ bug.
        //m = loader.build();
        //data = m.getDataModel();

        // no schema in GraphD2RQ, see above
        m = loader.setServeVocabulary(false).build();
        // use union graph, see above
        data = ModelFactory.createModelForGraph(D2RQGraphUtils.createUnionGraph(m.getSchema()).addGraph(m.getData()));

        FusekiServer server = ServerHelper.buildServer(webPages, port, DatasetFactory.wrap(data).asDatasetGraph());
        LOGGER.debug("Start server {}", server);
        server.start();
        console.println("The server <" + host + "> is ready to use.");
        server.join();
    }
}
