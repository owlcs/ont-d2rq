package d2rq;

import d2rq.utils.ArgDecl;
import de.fuberlin.wiwiss.d2rq.map.MapParser;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Command line interface for {@link MappingGenerator}.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class MappingTool extends CommandLineTool {

    private ArgDecl baseArg = new ArgDecl(true, "b", "base");
    private ArgDecl outfileArg = new ArgDecl(true, "o", "out", "outfile");
    private ArgDecl vocabAsOutput = new ArgDecl(false, "v", "vocab");

    MappingTool(PrintStream console) {
        super(console);
    }

    @Override
    public void printUsage() {
        console.println("usage: generate-mapping [options] jdbcURL");
        console.println();
        printStandardArguments(false);
        console.println("  Options:");
        printConnectionOptions();
        console.println("    -o outfile.ttl  Output file name (default: stdout)");
        console.println("    -v              Generate RDFS+OWL vocabulary instead of mapping file");
        console.println("    -b baseURI      Base URI for RDF output");
        console.println("    --verbose       Print debug information");
        console.println();
    }

    @Override
    public void initArgs() {
        cmd.add(baseArg);
        cmd.add(outfileArg);
        cmd.add(vocabAsOutput);
    }

    @Override
    public void run() throws IOException {
        if (cmd.numItems() == 1) {
            loader.setJdbcURL(cmd.getItem(0));
        }

        PrintStream out;
        if (cmd.contains(outfileArg)) {
            File f = new File(cmd.getArgValue(outfileArg));
            LOGGER.info("Writing to {}", f);
            loader.setSystemBaseURI(MapParser.absolutizeURI(f.toURI().toString() + "#"));
            out = new PrintStream(new FileOutputStream(f));
        } else {
            LOGGER.info("Writing to stdout");
            out = System.out;
        }
        if (cmd.contains(baseArg)) {
            loader.setSystemBaseURI(cmd.getArgValue(baseArg));
        }

        Mapping generator = loader.build();
        try {
            Model model = cmd.contains(vocabAsOutput) ? generator.getVocabularyModel() : generator.asModel();
            RDFDataMgr.write(out, model, RDFLanguages.TURTLE);
        } finally {
            loader.close();
        }
    }
}
