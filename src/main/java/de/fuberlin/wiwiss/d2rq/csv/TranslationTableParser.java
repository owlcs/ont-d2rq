package de.fuberlin.wiwiss.d2rq.csv;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable.Translation;
import org.apache.jena.riot.system.IRIResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parses the contents of a CSV file into a collection of
 * <tt>Translation</tt>s. The CVS file must contain exactly
 * two columns. DB values come from the first, RDF values
 * from the second.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class TranslationTableParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationTableParser.class);
    private BufferedReader reader;
    private CSV csvLineParser = new CSV();
    private String url;

    public TranslationTableParser(Reader reader) {
        this.reader = new BufferedReader(reader);
    }

    public TranslationTableParser(String url) {
        try {
            this.url = IRIResolver.resolveFileURL(url);
            this.reader = new BufferedReader(new FileReader(new File(new URI(this.url))));
        } catch (FileNotFoundException fnfex) {
            throw new D2RQException("File not found at URL: " + this.url);
        } catch (URISyntaxException usynex) {
            throw new D2RQException("Malformed URI: " + this.url);
        }
    }

    public Collection<Translation> parseTranslations() {
        try {
            List<Translation> result = new ArrayList<>();
            while (true) {
                String line = this.reader.readLine();
                if (line == null) {
                    break;
                }
                String[] fields = this.csvLineParser.parse(line);
                if (fields.length != 2) {
                    LOGGER.warn("Skipping line with {} instead of 2 columns in CSV file {}", fields.length, url);
                    continue;
                }
                result.add(new Translation(fields[0], fields[1]));
            }
            return result;
        } catch (IOException iex) {
            throw new D2RQException(iex);
        }
    }
}
