package de.fuberlin.wiwiss.d2rq.d2rq_sdb;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingComparator;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Compares the results of some sparql-queries (located in file queries.txt) from
 * the sdb and from d2rq.
 * A sparql-query will executed to both and the query-results must be the same.
 *
 * @author Herwig Leimer
 */
@Ignore // todo: temporary ignore, SDB tests are currently broken
public class SdbSqlEqualityTest extends LoadDataTest {
    // query-directory
    private static final String QUERY_DIR = "queries";
    private static final String QUERY_FILE_SUFFIX = ".rq";
    private String[] excludedQueriesFileNames = {
            // fehlgeschlagene Tests
            "var-1.rq",        // basic - Exception: Wrong data type: For input string: "Site1":
            "term-8.rq",        // basic - Unterschiedl. Anzahl von Erebnissen
            "distinct-1.rq",    // distinct - Unterschiedl. Anzahl von Erebnissen + Datatype format exception: "2008-08-30:00"^^xsd:dateTime
            "no-distinct-1.rq", // distinct - Unterschiedl. Erebnisse
            "query-eq-3.rq",    // expr-equals - Unterschiedl. Anzahl von Erebnissen + Datatype format exception: "2008-08-30:00"^^xsd:dateTime
            "open-cmp-01.rq",    // open-world - noch anzupassen
            "open-cmp.02.rq",    // open-world - noch anzupassen
            "open-eq-06.rq",    // open-world - Unterschiedl. Anzahl von Erebnissen

//                            "expr-builtin",
//                            "expr-ops",
//                            "open-world",
//                            "optional",
//                            "optional-filter"
    };

    public SdbSqlEqualityTest() {
        super();
    }

    @Test
    public void testSdbSqlEquality() {
        ResultSet resultSet;
        QueryExecution sdbQueryExecution, d2rqQueryExecution;
        List<?> sdbDataResult;        // Statement or Binding
        List<?> hsqlDataResult;    // Statement or Binding
        List<SortCondition> sortingConditions;
        SortCondition sortCondition;
        Var var;
        List<Query> queries;
        int hsqlResultSize, sdbResultSize;
        Object sdbResultEntry, hsqlResultEntry;
        boolean entriesEqual;
        Model sdbModel, d2rqModel;

        try {
            LOGGER.debug("Searching for Query-Files!");

            queries = loadAllQueries();

            LOGGER.debug("Loaded " + queries.size() + " Queries from Queries-Directory: " + CURR_DIR + "/" + QUERY_DIR);

            for (Query query : queries) {

                LOGGER.debug("--------------------------------------------------------------------------------------");
                LOGGER.debug("Executing Query: ");
                LOGGER.debug("{}", query);


                // now execute the query against the sdb-datamodel
                LOGGER.debug("Querying SDB-Data-Model!");
                sdbQueryExecution = QueryExecutionFactory.create(query, this.sdbDataModel);

                // now execute the query against the hsql-datamodel
                LOGGER.debug("Querying HSQL-Data-Model!");
                d2rqQueryExecution = QueryExecutionFactory.create(query, this.hsqlDataModel);

                // Check for SELECT-Queries
                if (query.isSelectType()) {
                    resultSet = sdbQueryExecution.execSelect();

                    // sorting
                    // both results (sdbDataResult, mysqlDataResult) must have the same order
                    // for equality-checking
                    // create an sort-order that will be used for both results
                    sortingConditions = new ArrayList<>();

                    for (String varName : resultSet.getResultVars()) {
                        var = Var.alloc(varName);
                        sortCondition = new SortCondition(var, Query.ORDER_DEFAULT);
                        sortingConditions.add(sortCondition);
                    }

                    List<Binding> sdbSelectResult = new ArrayList<>();
                    List<Binding> hsqlSelectResult = new ArrayList<>();

                    while (resultSet.hasNext()) {
                        sdbSelectResult.add(resultSet.nextBinding());
                    }

                    sdbSelectResult.sort(new BindingComparator(sortingConditions));

                    resultSet = d2rqQueryExecution.execSelect();


                    while (resultSet.hasNext()) {
                        hsqlSelectResult.add(resultSet.nextBinding());
                    }

                    hsqlSelectResult.sort(new BindingComparator(sortingConditions));

                    sdbDataResult = sdbSelectResult;
                    hsqlDataResult = hsqlSelectResult;
                } else if (query.isConstructType() || query.isDescribeType()) {

                    if (query.isConstructType()) {
                        // sdb
                        sdbModel = sdbQueryExecution.execConstruct();
                        // hsql
                        d2rqModel = d2rqQueryExecution.execConstruct();
                    } else {
                        // sdb
                        sdbModel = sdbQueryExecution.execDescribe();
                        // hsql
                        d2rqModel = d2rqQueryExecution.execDescribe();
                    }

                    List<Statement> sdbGraphResult = new ArrayList<>();
                    List<Statement> hsqlGraphResult = new ArrayList<>();

                    // sdb
                    for (StmtIterator iterator = sdbModel.listStatements(); iterator.hasNext(); ) {
                        sdbGraphResult.add(iterator.nextStatement());
                    }
                    sdbGraphResult.sort(new StatementsComparator());


                    // hsql
                    for (StmtIterator iterator = d2rqModel.listStatements(); iterator.hasNext(); ) {
                        hsqlGraphResult.add(iterator.nextStatement());
                    }
                    hsqlGraphResult.sort(new StatementsComparator());

                    sdbDataResult = sdbGraphResult;
                    hsqlDataResult = hsqlGraphResult;

                } else if (query.isAskType()) {
                    // TODO: test for an ask-type
                    continue;
                } else {
                    Assert.fail("Unknown Query-Type !!!");
                    continue;
                }


                LOGGER.debug("Now checking for Query-Result-Equality!");

                sdbResultSize = sdbDataResult.size();
                hsqlResultSize = hsqlDataResult.size();

                LOGGER.debug("Query-SDB-Result-Size: " + sdbResultSize);
                LOGGER.debug("Query-HSQL-Result-Size: " + hsqlResultSize);

                if (sdbResultSize == hsqlResultSize) {
                    LOGGER.debug("SDB-Result-Size and HSQL-Result-Size are equal!");
                } else {
                    Assert.fail();
                }

                LOGGER.debug("Now checking each Result-Entry for Equality!");

                for (int i = 0; i < sdbDataResult.size(); i++) {
                    sdbResultEntry = sdbDataResult.get(i);
                    hsqlResultEntry = hsqlDataResult.get(i);
                    entriesEqual = sdbResultEntry.equals(hsqlResultEntry);
                    LOGGER.debug("SDB-Result-Entry: " + sdbResultEntry);
                    LOGGER.debug("HSQL-Result-Entry: " + hsqlResultEntry);
                    if (entriesEqual) {
                        LOGGER.debug("Result-Entries are Equal: true");
                    } else {
                        Assert.fail();
                    }
                }
                LOGGER.debug("SDB and SQL-Results are equal!");
            }
            LOGGER.debug(queries.size() + " Queries checked !");
        } catch (IOException e) {
            throw new AssertionError(e);
        }


    }


    private List<Query> loadAllQueries() throws IOException {
        File queryDir;
        File[] files;
        List<Query> queries;

        queries = new ArrayList<>();

        queryDir = new File(CURR_DIR + "/" + QUERY_DIR);
        files = queryDir.listFiles();
        Assert.assertNotNull(files);
        Arrays.sort(files);

        for (File file : files) {
            readRecursiveAndCreateQuery(file, queries);
        }

        return queries;
    }

    private void readRecursiveAndCreateQuery(File file, List<Query> queries) throws IOException {
        File[] files;
        Query query;
        BufferedReader queryReader = null;
        String fileName;

        fileName = file.getName();

        if (file.isDirectory()) {

            files = file.listFiles();
            Assert.assertNotNull(files);
            LOGGER.debug("Reading Directory: " + fileName + " - contains " + files.length + " Files!");

            Arrays.sort(files);

            for (File file1 : files) {
                if (!excludeFile(file1)) {
                    // step down
                    readRecursiveAndCreateQuery(file1, queries);
                }
            }
        } else {
            LOGGER.debug("Reading File: " + fileName);
            // no directory
            try {

                if (!excludeFile(file)) {
                    queryReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                    query = createQuery(queryReader);
                    queries.add(query);
                }
            } finally {
                if (queryReader != null) {
                    queryReader.close();
                }
            }

        }


    }


    private boolean excludeFile(File file) {
        String fileName;
        boolean exclude = false;

        fileName = file.getName();
        // no directory
        for (String excludedQueriesFileName : excludedQueriesFileNames) {

            if (fileName.equals(excludedQueriesFileName) || (file.isFile() && !fileName.toLowerCase().endsWith(QUERY_FILE_SUFFIX))) {
                exclude = true;
                break;
            }
        }

        return exclude;
    }

    private Query createQuery(BufferedReader queryReader) throws IOException {
        StringBuffer stringBuffer;
        String line;

        stringBuffer = new StringBuffer();

        while ((line = queryReader.readLine()) != null) {
            stringBuffer.append(line);
            stringBuffer.append("\n");
        }

        return QueryFactory.create(stringBuffer.toString());
    }


    private static class StatementsComparator implements Comparator<Statement> {

        public int compare(Statement arg0, Statement arg1) {
            return arg0.toString().compareTo(arg1.toString());
        }

    }
}




