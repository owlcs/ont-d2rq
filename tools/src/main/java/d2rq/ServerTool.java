package d2rq;

import d2rq.utils.ArgDecl;
import d2rq.utils.CommandLine;
import d2rq.utils.ServerHelper;
import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.utils.D2RQGraphUtils;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Created by @ssz on 12.01.2019.
 */
public class ServerTool extends CommandLineTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTool.class);
    private static final int DEFAULT_SERVER_PORT = 2020;
    private static final String DEFAULT_BASE = "http://localhost:%d/";

    protected ServerTool(PrintStream out) {
        super(out);
    }

    @Override
    public void usage() {
        console.println("usage:");
        console.println("  d2r-server [server-options] mappingFile");
        console.println("  d2r-server [server-options] [connection-options] jdbcURL");
        console.println("  d2r-server [server-options] [connection-options] -l script.sql");
        console.println();
        printStandardArguments(true);
        console.println();
        console.println("  Server options:");
        console.println("    --port number   Port where to start up the server (default: 2020)");
        console.println("    -b baseURI      Base URI to generate RDF dataset");
        console.println("    --fast          Use all engine optimizations (recommended)");
        console.println("    --verbose       Print debug information");
        console.println();
        console.println("  Database connection options (only with jdbcURL):");
        printConnectionOptions();
        console.println();
    }

    private ArgDecl portArg = new ArgDecl(true, "port");
    private ArgDecl baseArg = new ArgDecl(true, "b", "base");
    private ArgDecl fastArg = new ArgDecl(false, "fast");

    @Override
    public void initArgs(CommandLine cmd) {
        cmd.add(portArg);
        cmd.add(baseArg);
        cmd.add(fastArg);
    }

    @Override
    public void run(CommandLine cmd, SystemLoader loader) {
        if (cmd.numItems() == 1) {
            loader.setMappingFileOrJdbcURL(cmd.getItem(0));
        }
        if (cmd.contains(fastArg)) {
            loader.setFastMode(true);
        }
        // todo: add configurable parameters:
        loader.setServeVocabulary(true).setControlOWL(true);
        // todo: add also a cache control ?
        int port;
        if (cmd.contains(portArg)) {
            port = Integer.parseInt(cmd.getArg(portArg).getValue());
        } else {
            port = DEFAULT_SERVER_PORT;
        }
        String host = String.format(DEFAULT_BASE, port);
        String base;
        if (cmd.contains(baseArg)) {
            base = cmd.getArg(baseArg).getValue();
            if (!base.endsWith("/")) base += "/";
        } else {
            base = host;
        }
        loader.setSystemBaseURI(base).setResourceStem("resource/");

        Path webPages = ServerHelper.getSystemDirectory("/web-pages");
        LOGGER.debug("Web-pages path: {}", webPages);

        // TODO: for some unclear to me reason the following way (Data+Schema in one GraphD2RQ) works incorrectly..
        //  unable to find a schema part for several queries,
        //  e.g. "SELECT ?s { ?s a <http://www.w3.org/2002/07/owl#Class> }",
        //  but it's OK if a composite graph is specified.
        //  Investigate! this must be a D2RQ bug.
        //Mapping m = loader.build();
        //Model data = m.getDataModel()'

        // no schema in GraphD2RQ, see above
        Mapping m = loader.setServeVocabulary(false).build();
        // use union graph, see above
        Model data = ModelFactory.createModelForGraph(D2RQGraphUtils.createUnionGraph(m.getSchema()).addGraph(m.getData()));

        FusekiServer server = ServerHelper.buildServer(webPages, port, DatasetFactory.wrap(data).asDatasetGraph());
        LOGGER.debug("Start server {}", server);
        server.start();
        console.println("The server <" + host + "> is ready to use.");
        server.join();
    }
}
