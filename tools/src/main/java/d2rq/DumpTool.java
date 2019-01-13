package d2rq;

import d2rq.utils.ArgDecl;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.MapParser;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFWriter;
import org.apache.jena.shared.NoWriterForLangException;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Command line utility for dumping a database to RDF, using the
 * {@link MappingGenerator} or a mapping file.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class DumpTool extends CommandLineTool {
    private final static int DUMP_DEFAULT_FETCH_SIZE = 500;

    private ArgDecl baseArg = new ArgDecl(true, "b", "base");
    private ArgDecl formatArg = new ArgDecl(true, "f", "format");
    private ArgDecl outfileArg = new ArgDecl(true, "o", "out", "outfile");

    DumpTool(PrintStream console) {
        super(console);
    }

    @Override
    public void printUsage() {
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
    }

    @Override
    public void initArgs() {
        cmd.add(baseArg);
        cmd.add(formatArg);
        cmd.add(outfileArg);
    }

    @Override
    public void run() throws IOException {
        if (cmd.numItems() == 1) {
            loader.setMappingFileOrJdbcURL(cmd.getItem(0));
        }

        String format = "N-TRIPLE";
        if (cmd.contains(formatArg)) {
            format = cmd.getArgValue(formatArg);
        }
        PrintStream out;
        if (cmd.contains(outfileArg)) {
            File f = new File(cmd.getArgValue(outfileArg));
            LOGGER.info("Writing to {}", f);
            out = new PrintStream(new FileOutputStream(f));
            loader.setSystemBaseURI(MapParser.absolutizeURI(f.toURI().toString() + "#"));
        } else {
            LOGGER.info("Writing to stdout");
            out = System.out;
        }
        if (cmd.contains(baseArg)) {
            loader.setSystemBaseURI(cmd.getArgValue(baseArg));
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
