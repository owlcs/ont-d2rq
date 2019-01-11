package d2rq;

import d2rq.utils.ArgDecl;
import d2rq.utils.CommandLine;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.MapParser;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
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

    DumpTool(PrintStream console) {
        super(console);
    }

    @Override
    public void usage() {
        console.println("usage:");
        console.println("  dump-rdf [output-options] mappingFile");
        console.println("  dump-rdf [output-options] [connection-options] jdbcURL");
        console.println("  dump-rdf [output-options] [connection-options] -l script.sql");
        console.println();
        printStandardArguments(true);
        console.println();
        console.println("  RDF output options:");
        console.println("    -b baseURI      Base URI for RDF output");
        console.println("    -f format       One of N-TRIPLE (default), RDF/XML, RDF/XML-ABBREV, TURTLE");
        console.println("    -o outfile      Output file name (default: stdout)");
        console.println("    --verbose       Print debug information");
        console.println();
        console.println("  Database connection options (only with jdbcURL):");
        printConnectionOptions();
        console.println();
        throw new Exit(1);
    }

    private ArgDecl baseArg = new ArgDecl(true, "b", "base");
    private ArgDecl formatArg = new ArgDecl(true, "f", "format");
    private ArgDecl outfileArg = new ArgDecl(true, "o", "out", "outfile");

    @Override
    public void initArgs(CommandLine cmd) {
        cmd.add(baseArg);
        cmd.add(formatArg);
        cmd.add(outfileArg);
    }

    @Override
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
            LOGGER.info("Writing to {}", f);
            out = new PrintStream(new FileOutputStream(f));
            loader.setSystemBaseURI(MapParser.absolutizeURI(f.toURI().toString() + "#"));
        } else {
            LOGGER.info("Writing to stdout");
            out = System.out;
        }
        if (cmd.hasArg(baseArg)) {
            loader.setSystemBaseURI(cmd.getArg(baseArg).getValue());
        }

        Mapping mapping = loader.setResultSizeLimit(Database.NO_LIMIT).setFetchSize(DUMP_DEFAULT_FETCH_SIZE).build();
        try {
            Model d2rqModel = mapping.getDataModel();
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
