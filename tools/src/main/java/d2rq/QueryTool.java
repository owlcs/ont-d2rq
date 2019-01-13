package d2rq;

import d2rq.utils.ArgDecl;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.engine.QueryEngineD2RQ;
import org.apache.jena.query.QueryCancelledException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.apache.jena.sparql.util.QueryExecUtils;

import java.io.PrintStream;

/**
 * Command line utility for executing SPARQL queries against a D2RQ-mapped database.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class QueryTool extends CommandLineTool {

    private ArgDecl baseArg = new ArgDecl(true, "b", "base");
    private ArgDecl formatArg = new ArgDecl(true, "f", "format");
    private ArgDecl timeoutArg = new ArgDecl(true, "t", "timeout");

    QueryTool(PrintStream console) {
        super(console, 1, 2);
    }

    @Override
    public void printUsage() {
        console.println("usage:");
        console.println("  d2r-query [query-options] mappingFile query");
        console.println("  d2r-query [query-options] [connection-options] jdbcURL query");
        console.println("  d2r-query [query-options] [connection-options] -l script.sql query");
        console.println();
        printStandardArguments(true);
        console.println("    query           A SPARQL query, e.g., \"SELECT * {?s rdf:type ?o} LIMIT 10\"");
        console.println("                    A value of @file.sparql reads the query from a file.");
        console.println("  Query options:");
        console.println("    -b baseURI      Base URI for RDF output");
        console.println("    -f format       One of text (default), xml, json, csv, tsv, srb, ttl");
        console.println("    -t timeout      Query timeout in seconds");
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
        cmd.add(timeoutArg);
    }

    @Override
    public void run() {
        String query = null;
        if (cmd.numItems() == 1) {
            query = cmd.getItem(0, INDIRECTION_MARKER);
        } else if (cmd.numItems() == 2) {
            loader.setMappingFileOrJdbcURL(cmd.getItem(0));
            query = cmd.getItem(1, INDIRECTION_MARKER);
        }

        String format = null;
        if (cmd.contains(formatArg)) {
            format = cmd.getArgValue(formatArg);
        }
        if (cmd.contains(baseArg)) {
            loader.setSystemBaseURI(cmd.getArgValue(baseArg));
        }
        double timeout = -1;
        if (cmd.contains(timeoutArg)) {
            String t = cmd.getArgValue(timeoutArg);
            try {
                timeout = Double.parseDouble(t);
            } catch (NumberFormatException ex) {
                throw new D2RQException("Timeout must be a number in seconds: '" + t + "'",
                        D2RQException.MUST_BE_NUMERIC);
            }
        }

        loader.setFastMode(true);
        Model d2rqModel = loader.build().getDataModel();

        StringBuilder prefixes = new StringBuilder();
        for (String prefix : d2rqModel.getNsPrefixMap().keySet()) {
            prefixes.append("PREFIX ").append(prefix).append(": <").append(d2rqModel.getNsPrefixURI(prefix)).append(">\n");
        }
        query = prefixes + query;
        LOGGER.info("Query:\n{}", query);

        try {
            QueryEngineD2RQ.register();
            org.apache.jena.query.Query q = QueryFactory.create(query, loader.getResourceBaseURI());
            QueryExecution qe = QueryExecutionFactory.create(q, d2rqModel);
            if (timeout > 0) {
                qe.setTimeout(Math.round(timeout * 1000));
            }
            QueryExecUtils.executeQuery(q, qe, ResultsFormat.lookup(format));
        } catch (QueryCancelledException ex) {
            throw new D2RQException("Query timeout", ex, D2RQException.QUERY_TIMEOUT);
        } finally {
            d2rqModel.close();
        }
    }
}
