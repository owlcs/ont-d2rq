package d2rq;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.impl.MapParser;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFWriter;
import org.apache.jena.shared.NoWriterForLangException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Command line utility for dumping a database to RDF, using the
 * {@link MappingGenerator} or a mapping file.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class DumpTool extends CommandLineTool {
    private final static Logger LOGGER = LoggerFactory.getLogger(DumpTool.class);

    private final static int DUMP_DEFAULT_FETCH_SIZE = 500;

    public void usage() {
        CONSOLE.println("usage:");
        CONSOLE.println("  dump-rdf [output-options] mappingFile");
        CONSOLE.println("  dump-rdf [output-options] [connection-options] jdbcURL");
        CONSOLE.println("  dump-rdf [output-options] [connection-options] -l script.sql");
        CONSOLE.println();
        printStandardArguments(true);
        CONSOLE.println();
        CONSOLE.println("  RDF output options:");
        CONSOLE.println("    -b baseURI      Base URI for RDF output");
        CONSOLE.println("    -f format       One of N-TRIPLE (default), RDF/XML, RDF/XML-ABBREV, TURTLE");
        CONSOLE.println("    -o outfile      Output file name (default: stdout)");
        CONSOLE.println("    --verbose       Print debug information");
        CONSOLE.println();
        CONSOLE.println("  Database connection options (only with jdbcURL):");
        printConnectionOptions();
        CONSOLE.println();
        throw new ExitException(1);
    }

    private ArgDecl baseArg = new ArgDecl(true, "b", "base");
    private ArgDecl formatArg = new ArgDecl(true, "f", "format");
    private ArgDecl outfileArg = new ArgDecl(true, "o", "out", "outfile");

    public void initArgs(CommandLine cmd) {
        cmd.add(baseArg);
        cmd.add(formatArg);
        cmd.add(outfileArg);
    }

    public void run(CommandLine cmd, SystemLoader loader) throws IOException {
        if (cmd.numItems() == 1) {
            loader.setMappingFileOrJdbcURL(cmd.getItem(0));
        }

        String format = "N-TRIPLE";
        if (cmd.hasArg(formatArg)) {
            format = cmd.getArg(formatArg).getValue();
        }
        PrintStream out;
        if (cmd.hasArg(outfileArg)) {
            File f = new File(cmd.getArg(outfileArg).getValue());
            LOGGER.info("Writing to " + f);
            out = new PrintStream(new FileOutputStream(f));
            loader.setSystemBaseURI(MapParser.absolutizeURI(f.toURI().toString() + "#"));
        } else {
            LOGGER.info("Writing to stdout");
            out = System.out;
        }
        if (cmd.hasArg(baseArg)) {
            loader.setSystemBaseURI(cmd.getArg(baseArg).getValue());
        }

        loader.setResultSizeLimit(Database.NO_LIMIT);
        Mapping mapping = loader.getMapping();
        try {
            // Override the d2rq:fetchSize given in the mapping
            mapping.listDatabases().forEach(db -> db.setFetchSize(DUMP_DEFAULT_FETCH_SIZE));

            Model d2rqModel = loader.getMapping().getDataModel();

            try {
                RDFWriter writer = d2rqModel.getWriter(format.toUpperCase());
                // todo: no need anymore
                if (format.equals("RDF/XML") || format.equals("RDF/XML-ABBREV")) {
                    writer.setProperty("showXmlDeclaration", "true");
                    if (loader.getResourceBaseURI() != null) {
                        writer.setProperty("xmlbase", loader.getResourceBaseURI());
                    }
                }
                writer.write(d2rqModel, new OutputStreamWriter(out, StandardCharsets.UTF_8), loader.getResourceBaseURI());
            } catch (NoWriterForLangException ex) {
                throw new D2RQException("Unknown format '" + format + "'", D2RQException.STARTUP_UNKNOWN_FORMAT);
            }
        } finally {
            out.close();
            mapping.close();
        }
    }
}
