package ru.avicomp.ontapi.conf;

import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.ontapi.D2RQGraphDocumentSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Created by @szuev on 29.03.2018.
 */
public enum ConnectionData {
    /**
     * to set up use <a href='file:doc/example/iswc-mysql.sql'>iswc-mysql.sql</a>
     */
    MYSQL,
    /**
     * to set up use <a href='file:doc/example/iswc-postgres.sql'>iswc-postgres.sql</a>
     */
    POSTGRES,;

    private static final Properties PROPERTIES = load("/db.properties");

    public IRI getBaseIRI() {
        return IRI.create(PROPERTIES.getProperty(prefix() + "uri"));
    }

    public IRI getIRI(String dbName) {
        return IRI.create(getBaseIRI() + ("/" + Objects.requireNonNull(dbName)));
    }

    public String getUser() {
        return PROPERTIES.getProperty(prefix() + "user");
    }

    public String getPwd() {
        return PROPERTIES.getProperty(prefix() + "password");
    }

    private String prefix() {
        return String.format("%s.", name().toLowerCase());
    }

    public D2RQGraphDocumentSource toDocumentSource(String dbName) {
        return D2RQGraphDocumentSource.create(getIRI(dbName), getUser(), getPwd());
    }

    public IRI toIRI(String uri) {
        return IRI.create(MYSQL.equals(this) ? uri : uri.toLowerCase());
    }

    public static List<ConnectionData> asList() {
        return Arrays.asList(values());
    }

    /**
     * Loads properties, first from System, then from file.
     *
     * @param file path
     * @return {@link Properties}
     */
    public static Properties load(String file) {
        Properties fromFile = new Properties();
        try (InputStream in = ConnectionData.class.getResourceAsStream(file)) {
            fromFile.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Properties res = new Properties(fromFile);
        System.getProperties().forEach((key, val) -> {
            if (!(key instanceof String)) return;
            String str = (String) key;
            if (Arrays.stream(values()).map(ConnectionData::prefix).anyMatch(str::startsWith)) {
                res.put(key, val);
            }
        });
        return res;
    }
}
