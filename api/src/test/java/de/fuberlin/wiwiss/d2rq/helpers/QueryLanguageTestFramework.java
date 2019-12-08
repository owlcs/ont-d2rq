package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.sql.BeanCounter;
import de.fuberlin.wiwiss.d2rq.vocab.ISWC;
import de.fuberlin.wiwiss.d2rq.vocab.SKOS;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DC;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import com.github.owlcs.d2rq.conf.ISWCData;

import java.util.*;

/**
 * TODO: What's all the logger stuff doing? Needs to be more obvious or better documented
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @author jgarbers
 */
@SuppressWarnings("WeakerAccess")
public abstract class QueryLanguageTestFramework {
    protected Model model;
    protected Set<Map<String, RDFNode>> results;
    protected String queryString;
    protected Map<String, RDFNode> currentSolution = new HashMap<>();

    // compare fields
    int nTimes = 1;
    BeanCounter startInst;
    boolean compareQueryHandlers = false;
    int configs;
    BeanCounter[] diffInfo;
    Set<Map<String, RDFNode>>[] resultMaps;
    String[] printed;
    String[] handlerDescription;
    boolean[] usingD2RQ;
    boolean[] verbatim;


    @SuppressWarnings("unchecked")
    protected void setUpHandlers() {
        configs = 2;
        diffInfo = new BeanCounter[configs];
        resultMaps = new HashSet[configs];
        printed = new String[configs];
        handlerDescription = new String[]{"SimpleQueryHandler", "D2RQQueryHandler"};
        usingD2RQ = new boolean[]{false, true};
        verbatim = new boolean[]{false, true};
    }

    private final static String pckg = "de.fuberlin.wiwiss.d2rq.testing.";
    protected static Logger bigStringInResultLogger = Logger.getLogger(pckg + "BigStringInResult");
    // Loggers used for switching on/off output
    protected Logger dumpLogger = Logger.getLogger(pckg + "Dump");
    protected Logger usingLogger = Logger.getLogger(pckg + "Using");
    protected Logger testCaseSeparatorLogger = Logger.getLogger(pckg + "TestCaseSeparator");
    protected Logger performanceLogger = Logger.getLogger(pckg + "Performance");
    protected Logger queryLogger = Logger.getLogger(pckg + "Query");
    protected Logger differentLogger = Logger.getLogger(pckg + "Different");
    protected Logger differenceLogger = Logger.getLogger(pckg + "Difference");
    protected Logger sqlResultSetLogger = Logger.getLogger(pckg + "SQLResultSet");
    protected Logger oldSQLResultSetLogger;
    protected Logger oldSQLResultSetSeparatorLogger;

    public QueryLanguageTestFramework() {
        super();
        setUpHandlers();
    }

    protected void setUpShowPerformance() {
        nTimes = 10;
        compareQueryHandlers = true;
        queryLogger.setLevel(Level.DEBUG);
        queryLogger.setLevel(Level.DEBUG);
        performanceLogger.setLevel(Level.DEBUG);
    }

    protected void setUpMixOutputs(boolean v) {
        compareQueryHandlers = v;
        usingLogger.setLevel(v ? Level.DEBUG : Level.INFO);
        testCaseSeparatorLogger.setLevel(v ? Level.DEBUG : Level.INFO);
    }

    protected void setUpShowStatements() {
        queryLogger.setLevel(Level.DEBUG);
        sqlResultSetLogger.setLevel(Level.DEBUG);
    }

    protected void setUpShowErrors() {
        differentLogger.setLevel(Level.DEBUG);
        differenceLogger.setLevel(Level.DEBUG);
    }

    protected void setUpShowWarnings() {
        bigStringInResultLogger.setLevel(Level.DEBUG);
    }

    protected void setUpShowAll() {
        setUpMixOutputs(true);
        verbatim[0] = true;
        setUpShowPerformance();
        setUpShowStatements();
        setUpShowErrors();
        setUpShowWarnings();
    }

    protected abstract ISWCData data();

    @Before
    public void setUp() {
        this.model = data().loadMapping("http://test/").getDataModel();
//		this.model.enableDebug();
        setUpShowErrors(); // should be activated all the time
//	    setUpShowPerformance(); // activate (only) to test performance (only)
        //setUpShowStatements(); // activate to analyse generated SQL statements
        //setUpMixOutputs(true); // activate to mix output from two QueryHandlers nicely
        //setUpShowAll(); // activate to get most verbatim output
        runTest();
    }

    @After
    public void tearDown() {
        this.model.close();
        this.results = null;
    }

    public void runTest() {
        testCaseSeparatorLogger.debug("");
        if (!compareQueryHandlers) {
            return;
        }
        Level oldQueryLoggerState = queryLogger.getLevel();
        Level oldSqlResultSetLoggerState = sqlResultSetLogger.getLevel();
        try {
            for (int i = 0; i < configs; i++) {
                queryLogger.setLevel(verbatim[i] ? oldQueryLoggerState : Level.INFO);
                sqlResultSetLogger.setLevel(verbatim[i] ? oldSqlResultSetLoggerState : Level.INFO);
                usingLogger.debug("using " + handlerDescription[i] + " ...");
                startInst = BeanCounter.instance();
                for (int j = 0; j < nTimes; j++) {
                    if (j > 0) {
                        queryLogger.setLevel(Level.INFO);
                        sqlResultSetLogger.setLevel(Level.INFO);
                    }
                    //super.runTest();
                }
                diffInfo[i] = BeanCounter.instanceMinus(startInst);
                diffInfo[i].div(nTimes);
                resultMaps[i] = results;
            }
            performanceLogger.debug(handlerDescription[0] + " vs. " + handlerDescription[1] + " = " +
                    diffInfo[0].sqlPerformanceString() + " : " + diffInfo[1].sqlPerformanceString() +
                    " (duration - SQL queries/rows/fields)");
            if (!resultMaps[0].equals(resultMaps[1])) {
                differentLogger.debug(handlerDescription[0] + " vs. " + handlerDescription[1] + " different results (" +
                        resultMaps[0].size() + ":" + resultMaps[1].size() + ")");
                differenceLogger.debug("Query: " + queryString);
                printed[0] = printObject(resultMaps[0]);
                printed[1] = printObject(resultMaps[1]);
                if (printed[0].equals(printed[1])) {
                    differentLogger.debug("... but printed the same.");
                } else {
                    differenceLogger.debug("first Result:");
                    differenceLogger.debug(printed[0]);
                    differenceLogger.debug("----------------");
                    differenceLogger.debug("second Result:");
                    differenceLogger.debug(printed[1]);
                }
            }
            Assert.assertEquals(resultMaps[0], resultMaps[1]);
        } finally {
            queryLogger.setLevel(oldQueryLoggerState);
            sqlResultSetLogger.setLevel(oldSqlResultSetLoggerState);
        }
    }


    private String printObject(Object obj) {
        if (obj instanceof Collection) {
            return printCollection((Collection<?>) obj);
        }
        if (obj instanceof Map) {
            return printMap((Map<?, ?>) obj);
        } else {
            return obj.toString();
        }
    }

    private String printArray(String[] a) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0)
                b.append(",");
            b.append(a[i]);
        }
        b.append("]\n");
        return b.toString();
    }

    private String printCollection(Collection<?> c) {
        String[] a = new String[c.size()];
        Iterator<?> it = c.iterator();
        int i = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            a[i] = printObject(obj);
            i++;
        }
        Arrays.sort(a);
        return printArray(a);
    }

    private String printMap(Map<?, ?> m) {
        String[] a = new String[m.size()];
        Iterator<?> it = m.entrySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) it.next();
            a[i] = printObject(e.getKey()) + " = " + printObject(e.getValue());
            i++;
        }
        Arrays.sort(a);
        return printArray(a);
    }

    protected void sparql(String sparql) {
        queryString = sparql;
        sparql = "PREFIX dc: <" + DC.NS + ">\n" +
                        "PREFIX foaf: <" + FOAF.NS + ">\n" +
                        "PREFIX skos: <" + SKOS.NS + ">\n" +
                        "PREFIX iswc: <" + ISWC.NS + ">\n" +
                        sparql;
        Query query = QueryFactory.create(sparql);
        QueryExecution qe = QueryExecutionFactory.create(query, this.model);
        this.results = new HashSet<>();
        ResultSet resultSet = qe.execSelect();
        while (resultSet.hasNext()) {
            QuerySolution solution = resultSet.nextSolution();
            addSolution(solution);
        }
    }

    private void addSolution(QuerySolution solution) {
        Map<String, RDFNode> map = new HashMap<>();
        Iterator<String> it = solution.varNames();
        while (it.hasNext()) {
            String variable = it.next();
            RDFNode value = solution.get(variable);
            map.put(variable, value);
        }
        this.results.add(map);
    }

    protected void assertResultCount(int count) {
        Assert.assertEquals(count, this.results.size());
    }

    protected void expectVariable(String variableName, RDFNode value) {
        this.currentSolution.put(variableName, value);
    }

    protected void assertSolution() {
        if (!this.results.contains(this.currentSolution)) {
            Assert.fail();
        }
        this.currentSolution.clear();
    }

    public static Map<String, RDFNode> solutionToMap(QuerySolution solution, List<String> variables) {
        Map<String, RDFNode> result = new HashMap<>();
        Iterator<String> it = solution.varNames();
        while (it.hasNext()) {
            String variableName = it.next();
            if (!variables.contains(variableName)) {
                continue;
            }
            RDFNode value = solution.get(variableName);
            int size = value.toString().length();
            if (size > 250) {
                bigStringInResultLogger.debug("Big string (" + size + ") in resultBinding:\n" + value);
            }
            result.put(variableName, value);
        }
        return result;
    }

    protected void dump() {
        System.out.println("\n#Results: " + results.size() + ":");
        int count = 1;
        for (Map<String, RDFNode> binding : results) {
            System.out.println("Result binding " + count + ":");
            for (String varName : binding.keySet()) {
                RDFNode val = binding.get(varName);
                System.out.println("    " + varName + " => " + val);
            }
            count++;
        }
    }
}
